(ns piotr-yuxuan.service-template.config
  "Define and validate the application configuration schema, including
  database connection parameters, Starling API settings, and CLI
  options. Provide utilities to load and decode configuration from
  command-line arguments or environment variables."
  (:require
   [malli.core :as m]
   [piotr-yuxuan.malli-cli :as malli-cli]
   [piotr-yuxuan.service-template.db :as db]
   [piotr-yuxuan.service-template.log :as log]
   [piotr-yuxuan.service-template.starling-api.ops :as starling-api]))

(def Config
  (m/schema
   [:map {:closed true
          :decode/args-transformer malli-cli/args-transformer}
    [:show-config? {:optional true}
     [boolean? {:description "Print actual configuration value and exit."
                :arg-number 0}]]
    [:help {:optional true}
     [boolean? {:description "Display usage summary and exit."
                :short-option "-h"
                :arg-number 0}]]

    [::log/publisher-names [:set {:default #{:console-json :jvm-metrics :prometheus}} keyword?]]
    [::log/prometheus-push-gateway [:string {:default "http://localhost:9091"}]]

    [::db/hostname [:string {:default "localhost", :long-option "--db-hostname"}]]
    [::db/port [pos-int? {:default 5432, :long-option "--db-port"}]]
    [::db/dbname [:string {:default "database", :long-option "--dbname"}]]
    [::db/username [:string {:default "user", :long-option "--db-username"}]]
    [::db/password [:string {:default "password"
                             :env-var "DB_PASSWORD"
                             :secret true}]]
    [::db/migrate? [boolean? {:default false, :long-option "--db-migrate"}]]
    [::starling-api/api-base [:string {:default "https://api-sandbox.starlingbank.com/api"}]]]))

(defn load-config
  "Parse and decode command-line arguments according to the
  configuration schema, returning a validated configuration map."
  [args]
  (m/decode Config args malli-cli/cli-transformer))
