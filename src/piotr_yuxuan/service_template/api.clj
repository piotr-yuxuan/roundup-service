(ns piotr-yuxuan.service-template.api
  "Define the web API for the service, including routes, OpenAPI/Swagger
  documentation, middleware for parameter coercion, content
  negotiation, exception handling, and authorization. Provide
  functions to build routers, handlers, and to start a Jetty server
  serving the API."
  (:require
   [clojure.java.io :as io]
   [com.brunobonacci.mulog :as log]
   [malli.core]
   [malli.util :as mu]
   [muuntaja.core :as m]
   [piotr-yuxuan.closeable-map :as closeable-map :refer [close! closeable-map*]]
   [piotr-yuxuan.service-template.core :as core]
   [piotr-yuxuan.service-template.exception :as st.exception]
   [piotr-yuxuan.service-template.math :as st.math]
   [piotr-yuxuan.service-template.secret :as secret]
   [reitit.coercion.malli]
   [reitit.dev.pretty :as pretty]
   [reitit.openapi :as openapi]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.middleware.multipart :as multipart]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.authorization :as authorization]
   [ring.middleware.reload :as reload]
   [ring.util.http-status :as http-status])
  (:import
   (java.time Instant
              Period
              Year
              ZoneId)
   (java.time.temporal WeekFields)
   (org.eclipse.jetty.server Server)))

;; Stop a Jetty server instance.
(defmethod close! Server
  [x]
  (.stop ^Server x))

(def RoundupJobExecution
  ;; API schema
  (malli.core/schema
   [:map {:description "A round-up job execution as recorded by the service."}
    [:id {:optional true} uuid?]
    [:account-uid uuid?]
    [:savings-goal-uid {:optional true} [:maybe uuid?]]
    [:round-up-amount-in-minor-units {:optional true} [:maybe st.math/NonNegInt64]]
    [:calendar-year st.math/NonNegInt64]
    [:calendar-week st.math/NonNegInt64]
    [:status {:optional true} [:enum
                               :status/running
                               :status/completed
                               :status/insufficient-funds
                               :status/failed]]]))

(defn year+week-number->interval
  "Given ISO year and week number, returns a `[start end)` tuple for
  that week. The period is P7D (7 days), representing a
  half-open [start, start+period) range. We consider that a week
  starts on Monday."
  ([^long year week]
   (year+week-number->interval year week (ZoneId/of "Europe/London")))
  ([^long year week zone]
   (let [week-fields (WeekFields/ISO)
         start (-> (.atDay (Year/of year) 1)
                   (.with (.weekOfYear week-fields) week)
                   (.with (.dayOfWeek week-fields) 1) ;; A week starts on a Monday.
                   (.atStartOfDay zone))]
     {:min-timestamp (.toInstant start)
      :max-timestamp (.toInstant (.plus start (Period/ofWeeks 1)))})))

(defn roundup-handler
  [config ^Instant now request]
  (let [{:keys [calendar-year calendar-week] :as args} (-> request :parameters :body)
        {:keys [min-timestamp max-timestamp]} (year+week-number->interval calendar-year calendar-week)
        args (merge args
                    {:min-timestamp min-timestamp
                     :max-timestamp max-timestamp})]
    (log/with-context {:account-uid (:account-uid args)
                       :calendar-year (:calendar-year args)
                       :calendar-week (:calendar-week args)}
      (log/trace ::round-up-job
        []
        (if (or (and (:business/allow-current-week? config) (.isAfter ^Instant min-timestamp now))
                (and (not (:business/allow-current-week? config)) (.isAfter ^Instant max-timestamp now)))
          (do (log/log ::week-not-in-the-past :args args)
              {:status http-status/bad-request
               :body {:message "Week should be in the past."
                      :args (assoc args :now now)}})
          {:status http-status/ok
           :body (core/job config (-> (:authorization request)
                                      (select-keys [:token])
                                      (merge args)))})))))

(defn routes
  "Define the API route structure, including OpenAPI and application
  endpoints."
  [{:keys [version commit] :as config}]
  [["/openapi.json"
    {:get {:no-doc true
           :openapi {:info {:title "Starling round-up service"
                            :description (slurp (io/resource "OpenAPI frontpage description.md"))
                            :version (format "%s (commit %s)" version (subs commit 0 7))}
                     :components {:securitySchemes {"bearer" {:type :http
                                                              :scheme "bearer"
                                                              :bearerFormat "JWT"}}}}
           :handler (openapi/create-openapi-handler)}}]

   ["/api/v0" {:tags #{"Trigger round up"}
               :openapi {:security [{"bearer" []}]}}
    ["/trigger-round-up" {:post {:summary "This is an idempotent action that triggers a round up for a given week."
                                 :parameters {:body [:map
                                                     [:calendar-year {:title "Calendar year"
                                                                      :description "Calendar year of the week you want to round up."
                                                                      :json-schema/default 2025}
                                                      pos-int?]
                                                     [:calendar-week {:title "Calendar week"
                                                                      :description "Calendar week, starting on Monday midnight, and finishing Monday midnight of the following week."
                                                                      :json-schema/default 32}
                                                      [:int {:min 1 :max 53}]]
                                                     [:savings-goal-name {:optional true
                                                                          :title "Name of the target savings goal"
                                                                          :description "If you provide this parameter, the round up amount will be added to the savings goal of this name, or we will create one."
                                                                          :json-schema/default "Round it up!"}
                                                      [:string {:min 1 :max 100}]]]}
                                 :responses {http-status/ok {:body RoundupJobExecution}
                                             http-status/bad-gateway {:body [:map {:closed false :description "Something was wrong with the services we depend on like the Starling API or our database."}]}
                                             http-status/bad-request {:body [:map {:closed false :description "The request is not semantically correct."}]}}
                                 :handler (fn [request] (roundup-handler config (Instant/now) request))}}]]])

(defn ->router
  "Build a Ring router with middleware, coercion, and exception
  handling."
  [config]
  (ring/router
   (routes config)
   {;:reitit.middleware/transform dev/print-request-diffs ;; pretty diffs
    :exception pretty/exception
    :data {:coercion (reitit.coercion.malli/create
                      {;; set of keys to include in error messages
                       :error-keys #{#_:type :coercion :in :schema :value :errors :humanized #_:transformed}
                       ;; schema identity function (default: close all map schemas)
                       :compile mu/closed-schema
                       ;; strip-extra-keys (affects only predefined transformers)
                       :strip-extra-keys true
                       ;; add/set default values
                       :default-values true
                       ;; malli options
                       :options nil})
           :muuntaja m/instance
           :middleware [;; swagger & openapi
                        swagger/swagger-feature
                        openapi/openapi-feature
                        ;; query-params & form-params
                        parameters/parameters-middleware
                        ;; content-negotiation
                        muuntaja/format-negotiate-middleware
                        ;; encoding response body
                        muuntaja/format-response-middleware
                        ;; exception handling
                        st.exception/exception-middleware
                        ;; decoding request body
                        muuntaja/format-request-middleware
                        ;; coercing response bodys
                        coercion/coerce-response-middleware
                        ;; coercing request parameters
                        coercion/coerce-request-middleware
                        ;; multipart
                        multipart/multipart-middleware
                        authorization/wrap-authorization
                        secret/secret-token-hide]}}))

(defn ->handler
  "Create a Ring handler combining the router and Swagger UI endpoints."
  [config]
  (ring/ring-handler
   (->router config)
   (ring/routes
    (swagger-ui/create-swagger-ui-handler
     {:path "/"
      :config {:validatorUrl nil
               :urls [{:name "swagger", :url "swagger.json"}
                      {:name "openapi", :url "openapi.json"}]
               :urls.primaryName "openapi"
               :operationsSorter "alpha"}})
    (ring/create-default-handler))))

(defn start
  "Launch a Jetty server with the API handler on port 3000 and return
  the configuration with the server instance."
  [{::keys [port] :as config}]
  (log/trace ::start
    []
    (closeable-map*
     (let [server (jetty/run-jetty (fn [request]
                                     ((reload/wrap-reload (->handler config)) request))
                                   {:port port
                                    :join? false})]
       (println (format "Server running in port %s." port))
       (log/log ::running :port port)
       (assoc config ::server server)))))
