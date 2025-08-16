(ns piotr-yuxuan.service-template.log
  (:require
   [com.brunobonacci.mulog :as u]
   [piotr-yuxuan.closeable-map :as closeable-map :refer [closeable-map*
                                                         with-tag]]))

(defn ->publishers
  [{::keys [publisher-names] :as config}]
  (cond-> []
    (contains? publisher-names :console-json)
    (conj {:type :console-json
           :pretty? true})

    (contains? publisher-names :jvm-metrics)
    (conj {:type :jvm-metrics
           :sampling-interval (and :ms 60000)
           :jvm-metrics {:all true}})

    (contains? publisher-names :prometheus)
    (conj {:type :prometheus
           :push-gateway {:job "starling-roundup-service"
                          :endpoint (::prometheus-push-gateway config)}})))

(defn start
  [config]
  (u/log ::start)
  (closeable-map*
   (let [publishers (->publishers config)]
     (assoc config
            ::publishers publishers
            ::stop (with-tag ::closeable-map/fn
                     (u/start-publisher! {:type :multi
                                          :publishers publishers}))))))
