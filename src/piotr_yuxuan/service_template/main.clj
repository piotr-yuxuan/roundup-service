(ns piotr-yuxuan.service-template.main
  "Provide the main entry point for the service, including configuration
  loading, validation, and orchestrating the startup of database,
  Starling API, and web API components. Handle CLI options for
  displaying help or printing configuration."
  (:gen-class)
  (:require
   [clojure.pprint :as pprint]
   [malli.core :as m]
   [malli.error :as me]
   [piotr-yuxuan.closeable-map :as closeable-map :refer [closeable-map*]]
   [piotr-yuxuan.malli-cli :as malli-cli]
   [piotr-yuxuan.service-template.api :as api]
   [piotr-yuxuan.service-template.config :as config :refer [Config]]
   [piotr-yuxuan.service-template.db :as db]
   [piotr-yuxuan.service-template.logger :as logger]
   [piotr-yuxuan.service-template.secret :as secret]
   [piotr-yuxuan.service-template.starling-api.ops :as starling-api]))

(defn start
  "Initialise and start all service components (database, Starling API,
  web API) in a closeable context."
  [config]
  (closeable-map*
   (-> config
       logger/start
       db/start
       starling-api/start
       api/start)))

(defn -main
  "Parse CLI arguments, validate and optionally display the
  configuration, then start the service or exit based on provided
  flags."
  [& args]
  (let [config (config/load-config args)]
    (cond (not (m/validate Config config))
          (do (println "Invalid configuration value"
                       (me/humanize (m/explain Config config)))
              (Thread/sleep 60000) ; Leave some time to retrieve the logs.
              (System/exit 1))

          (:show-config? config)
          (do (pprint/pprint (m/encode Config config (malli-cli/secret-transformer {:secret-fn secret/->secret})))
              (System/exit 0))

          (:help config)
          (do (println (malli-cli/summary Config))
              (System/exit 0))

          :else
          (m/encode Config
                    (start config)
                    malli-cli/secret-transformer))))
