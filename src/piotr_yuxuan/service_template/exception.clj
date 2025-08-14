(ns piotr-yuxuan.service-template.exception
  (:require
   [reitit.coercion.malli]
   [reitit.ring.malli]
   [reitit.ring.middleware.exception :as exception]
   [ring.util.http-status :as http-status]))

(defn short-circuit-handler
  [ex request]
  (let [ex-data (ex-data ex)]
    {:status (:status ex-data http-status/internal-server-error)
     :body (:body ex-data {:message (or (ex-message ex) "Internal server error")
                           :request (select-keys request [:request-method :uri :path-params :body-params])})}))

(def exception-middleware
  (exception/create-exception-middleware
   (merge
    exception/default-handlers
    {::short-circuit short-circuit-handler
     ::exception/wrap (fn [handler e request]
                        (println "ERROR" (pr-str (:uri request)))
                        (handler e request))})))
