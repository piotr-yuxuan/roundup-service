(ns piotr-yuxuan.service-template.exception
  (:require
   [reitit.coercion.malli]
   [reitit.ring.malli]
   [reitit.ring.middleware.exception :as exception]
   [ring.util.http-status :as http-status]))

(defn handler [message exception request]
  {:status http-status/internal-server-error
   :body {:message message
          :exception (.getClass exception)
          :data (ex-data exception)
          :uri (:uri request)}})

(def exception-middleware
  (exception/create-exception-middleware
   (merge
    exception/default-handlers
    {;; ex-data with :type ::error
     ::error (partial handler "Internal server.")

       ;; ex-data with ::exception or ::failure
     ::exception (partial handler "exception")

       ;; SQLException and all it's child classes
     java.sql.SQLException (partial handler "sql-exception")

       ;; override the default handler
     ::exception/default (partial handler "default")

       ;; print stack-traces for all exceptions
     ::exception/wrap (fn [handler e request]
                        (println "ERROR" (pr-str (:uri request)))
                        (handler e request))})))
