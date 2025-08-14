(ns piotr-yuxuan.service-template.db
  (:require
   [clojure.java.io :as io]
   [malli.core :as m]
   [malli.error :as me]
   [migratus.core :as migratus]
   [next.jdbc :as jdbc]
   [next.jdbc.connection :as connection]
   [piotr-yuxuan.closeable-map :as closeable-map :refer [closeable-map*]])
  (:import
   (com.zaxxer.hikari HikariDataSource)
   (java.time LocalDate)))

(def RoundupJobExecution
  (m/schema
   [:map
    [:id {:optional true} uuid?]
    [:account-uid [:maybe uuid?]]
    [:savings-goal-uid {:optional true} [:maybe uuid?]]
    [:round-up-amount-in-minor-units {:optional true} pos-int?]
    [:calendar-year [:maybe pos-int?]]
    [:calendar-week [:maybe pos-int?]]
    [:status {:optional true} [:enum "running" "completed" "insufficient_founds" "failed"]]]))

(defn insert-roundup-job!
  [{::keys [datasource] :query/keys [insert-job-execution]} {:keys [account-uid savings-goal-uid round-up-amount-in-minor-units calendar-year calendar-week] :as round-up-job}]
  (when-let [error (m/explain RoundupJobExecution round-up-job)]
    (throw (ex-info "Unexpected values" {:round-up-job round-up-job
                                         :explanation (me/humanize error)})))
  (jdbc/execute! datasource
                 [insert-job-execution account-uid savings-goal-uid round-up-amount-in-minor-units calendar-year calendar-week]
                 {:timeout 5}))

(defn update-roundup-job!
  [{::keys [datasource] :query/keys [update-job-execution]} {:keys [week-start-date account-uid savings-goal-uid round-up-amount-in-minor-units calendar_year calendar_week status id] :as round-up-job}]
  (when-let [error (m/explain RoundupJobExecution round-up-job)]
    (throw (ex-info "Unexpected values" {:round-up-job round-up-job
                                         :explanation (me/humanize error)})))
  (jdbc/execute! datasource
                 [update-job-execution week-start-date account-uid savings-goal-uid round-up-amount-in-minor-units calendar_year calendar_week status id]
                 {:timeout 5}))

(defn ->connection-pool
  ^HikariDataSource [{::keys [hostname port dbname username password]}]
  (connection/->pool
   HikariDataSource
   {:jdbcUrl (format "jdbc:postgresql://%s:%s/%s"
                     hostname
                     port
                     dbname)
    :username username
    :password password
    :maximumPoolSize 10
    :connectionTimeout (or 3000 :ms)}))

(defn migration-config
  [{::keys [datasource migrate?]}]
  (when migrate?
    {:managed-connection? true
     :store :database
     :db {:datasource datasource}}))

(defn migrate
  [config]
  (when-let [migration-config (migration-config config)]
    (migratus/migrate migration-config)))

(defn start
  [config]
  (closeable-map*
   (-> config
       (assoc ::datasource (->connection-pool config)
              :query/insert-job-execution (slurp (io/resource "queries/insert_job_execution.sql"))
              :query/update-job-execution (slurp (io/resource "queries/update_job_execution.sql")))
       (doto migrate))))
