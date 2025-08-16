(ns piotr-yuxuan.service-template.exception
  "Middleware and handlers for structured exception management in Ring
  applications, including short-circuiting, logging, and returning
  HTTP responses with appropriate status codes and messages."
  (:require
   [reitit.coercion.malli]
   [reitit.ring.malli]
   [reitit.ring.middleware.exception :as exception]
   [ring.util.http-status :as http-status]))

(defn short-circuit-handler
  "Handle exceptions by returning an HTTP response with a status and
  body derived from the exception data, including request context."
  [ex request]
  (let [ex-data (ex-data ex)]
    {:status (:status ex-data http-status/internal-server-error)
     :body (-> ex-data
               (:body {:request (select-keys request [:request-method :uri :path-params :body-params])})
               (update :message #(or % (ex-message ex) "Internal server error")))}))

(def exception-middleware
  "Wrap a Ring handler to catch exceptions, apply default and custom
  handlers, log errors, and return structured HTTP error responses."
  (exception/create-exception-middleware
   (merge
    exception/default-handlers
    {::short-circuit short-circuit-handler
     ::exception/wrap (fn [handler e request]
                        (println "ERROR" (pr-str (:uri request)))
                        (handler e request))})))
