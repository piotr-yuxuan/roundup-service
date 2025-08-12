(ns piotr-yuxuan.service-template.api
  (:require
   [malli.util :as mu]
   [muuntaja.core :as m]
   [piotr-yuxuan.closeable-map :as closeable-map :refer [closeable-map*]]
   [reitit.coercion.malli]
   [reitit.dev.pretty :as pretty]
   [reitit.openapi :as openapi]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.malli]
   [reitit.ring.middleware.exception :as exception]
   [reitit.ring.middleware.multipart :as multipart]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.authorization :as authorization]
   [ring.middleware.reload :as reload]
   [ring.util.http-status :as http-status]))

(defn routes
  [config]
  [["/openapi.json"
    {:get {:no-doc true
           :openapi {:info {:title "my-api"
                            :description "openapi3 docs with [malli](https://github.com/metosin/malli) and reitit-ring"
                            :version "0.0.1"}
                     :components {:securitySchemes {"bearer" {:type :http
                                                              :scheme "bearer"
                                                              :bearerFormat "JWT"}}}}
           :handler (openapi/create-openapi-handler)}}]

   ["/api/v0" {:tags #{"Trigger round up"}
               :openapi {:security [{"bearer" []}]}}
    ["/trigger-round-up" {:post {:summary "This is an idempotent action that triggers a round up for the week starting Monday midnight."
                                 :responses {200 {:body [:map [:secret :string]]}
                                             401 {:body [:map [:error :string]]}}
                                 :handler (constantly
                                           {:status http-status/ok
                                            :body {:secret "I am a little weasel"}})}}]]])

(defn ->router
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
                        exception/exception-middleware
                        ;; decoding request body
                        muuntaja/format-request-middleware
                        ;; coercing response bodys
                        coercion/coerce-response-middleware
                        ;; coercing request parameters
                        coercion/coerce-request-middleware
                        ;; multipart
                        multipart/multipart-middleware
                        authorization/wrap-authorization]}}))

(defn ->handler
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
  ([config]
   (let [server (jetty/run-jetty (fn [request]
                                   ((reload/wrap-reload (->handler config)) request))
                                 {:port 3000
                                  :join? false})]
     (println "server running in port 3000")
     (closeable-map*
      (assoc config ::server server)))))
