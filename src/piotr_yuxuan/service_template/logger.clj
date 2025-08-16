(ns piotr-yuxuan.service-template.logger
  (:require
   [com.brunobonacci.mulog :as log]
   [piotr-yuxuan.closeable-map :as closeable-map :refer [closeable-map*
                                                         with-tag]]))

(defn ->publishers
  [{::keys [publisher-names] :as config}]
  (cond-> []

    (contains? publisher-names :console-json)
    (conj {:type :console-json
           :pretty? true})

    (contains? publisher-names :file-json)
    (conj {:type :file-json
           :filename "log.json"
           :pretty? true})

    (contains? publisher-names :jvm-metrics)
    (conj {:type :jvm-metrics
           :sampling-interval (and :ms 60000)
           :jvm-metrics {:all true}})

    (contains? publisher-names :prometheus)
    (conj {:type :prometheus
           :push-gateway {:job "starling-roundup-service"
                          :endpoint (::prometheus-push-gateway config)}})

    (contains? publisher-names :zipkin)
    (conj {:type :zipkin
           :url (::zipkin-url config)})))

(defn start
  [config]
  (log/log ::start)
  (closeable-map*
   (let [publishers (->publishers config)]
     (log/set-global-context! (select-keys config [:app-name :version :env :commit]))
     (assoc config
            ::publishers publishers
            ::stop (with-tag ::closeable-map/fn
                     (log/start-publisher! {:type :multi
                                            :publishers publishers}))))))
