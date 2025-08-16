(ns piotr-yuxuan.service-template.config
  "Define and validate the application configuration schema, including
  database connection parameters, Starling API settings, and CLI
  options. Provide utilities to load and decode configuration from
  command-line arguments or environment variables."
  (:require
   [babashka.process :as process]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.brunobonacci.mulog :as log]
   [malli.core :as m]
   [piotr-yuxuan.malli-cli :as malli-cli]
   [piotr-yuxuan.malli-cli.utils :refer [deep-merge]]
   [piotr-yuxuan.service-template.db :as db]
   [piotr-yuxuan.service-template.logger :as logger]
   [piotr-yuxuan.service-template.starling-api.ops :as starling-api]))

(def service-name
  "starling-roundup-service")

(defn env
  []
  (or (System/getenv "ENV") "local"))

(defn version
  []
  (some->> (io/resource (str service-name ".version"))
           slurp
           str/trim))

(defmacro commit
  []
  (-> (process/process "git rev-parse HEAD" {:out :string})
      process/check
      :out
      str/trim))

(def Config
  (m/schema
   [:map {:closed true
          :decode/args-transformer malli-cli/args-transformer}
    [:app-name :string]
    [:env :string]
    [:version :string]
    [:commit :string]
    [:show-config? {:optional true}
     [boolean? {:description "Print actual configuration value and exit."
                :arg-number 0}]]
    [:help {:optional true}
     [boolean? {:description "Display usage summary and exit."
                :short-option "-h"
                :arg-number 0}]]

    [::logger/publisher-names [:set {:default #{:jvm-metrics :prometheus :zipkin}} keyword?]]
    [::logger/prometheus-push-gateway [:string {:default "http://localhost:9091", :long-option "--prometheus-push-url"}]]
    [::logger/zipkin-url [:string {:default "http://localhost:9411/", :long-option "--zipkin-url"}]]

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
  (log/trace ::load-config
    []
    (as-> {:app-name service-name
           :env (env)
           :version (version)
           :commit (commit)}
        $
      (deep-merge $ (m/decode Config args malli-cli/cli-transformer)))))
