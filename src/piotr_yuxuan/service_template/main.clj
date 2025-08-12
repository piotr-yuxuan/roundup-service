(ns piotr-yuxuan.service-template.main
  (:gen-class)
  (:require
   [clojure.pprint :as pprint]
   [malli.core :as m]
   [piotr-yuxuan.closeable-map :as closeable-map :refer [closeable-map*]]
   [piotr-yuxuan.malli-cli :as malli-cli]
   [piotr-yuxuan.service-template.api :as api]
   [piotr-yuxuan.service-template.config :as config :refer [Config]]
   [piotr-yuxuan.service-template.db :as db]
   [piotr-yuxuan.service-template.starling-api.ops :as starling-api]))

(defn start
  [config]
  (closeable-map*
   (-> config
       db/start
       starling-api/start
       api/start)))

(defn -main
  [& args]
  (let [config (config/load-config args)]
    (cond (not (m/validate Config config))
          (do (println "Invalid configuration value"
                       (m/explain Config config))
              (Thread/sleep 60000) ; Leave some time to retrieve the logs.
              (System/exit 1))

          (:show-config? config)
          (do (pprint/pprint config)
              (System/exit 0))

          (:help config)
          (do (println (malli-cli/summary Config))
              (System/exit 0))

          :else
          (m/encode Config
                    (start config)
                    malli-cli/secret-transformer))))
