(ns piotr-yuxuan.service-template.db
  (:require
   [migratus.core :as migratus]
   [next.jdbc :as jdbc]
   [next.jdbc.connection :as connection]
   [piotr-yuxuan.closeable-map :as closeable-map :refer [closeable-map*]]
   [piotr-yuxuan.service-template.config :as config])
  (:import
   (com.zaxxer.hikari HikariDataSource)
   (java.time LocalDate)))

(defn insert-roundup-job!
  [{::keys [datasource]} {:keys [week-start-date account-uid savings-goal-uid round-up-amount-in-minor-units]}]
  (jdbc/execute!
   datasource
   ["INSERT INTO roundup_job_execution
        (week_start_date, account_uid, savings_goal_uid, round_up_amount_in_minor_units)
      VALUES (?, ?, ?, ?)
      RETURNING *"
    week-start-date
    account-uid
    savings-goal-uid
    round-up-amount-in-minor-units]))

(defn update-roundup-job!
  [{::keys [datasource]} {:keys [id week-start-date account-uid savings-goal-uid round-up-amount-in-minor-units]}]
  (jdbc/execute!
   datasource
   ["UPDATE roundup_job_execution
      SET week_start_date = ?,
          account_uid = ?,
          savings_goal_uid = ?,
          round_up_amount_in_minor_units = ?
      WHERE id = ?"
    week-start-date
    account-uid
    savings-goal-uid
    round-up-amount-in-minor-units
    id]))

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
