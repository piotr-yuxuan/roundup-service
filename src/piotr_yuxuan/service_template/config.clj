(ns piotr-yuxuan.service-template.config
  (:require
   [malli.core :as m]
   [piotr-yuxuan.malli-cli :as malli-cli]
   [piotr-yuxuan.service-template.db :as db]))

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

    [::db/hostname [string? {:default "localhost", :long-option "--db-hostname"}]]
    [::db/port [pos-int? {:default 5432, :long-option "--db-port"}]]
    [::db/dbname [string? {:default "database", :long-option "--dbname"}]]
    [::db/username [string? {:default "user", :long-option "--db-username"}]]
    [::db/password [string? {:default "password"
                             :env-var "DB_PASSWORD"
                             :secret true}]]
    [::db/migrate? [boolean? {:default false, :long-option "--db-migrate"}]]]))

(defn load-config
  [args]
  (m/decode Config args malli-cli/cli-transformer))
