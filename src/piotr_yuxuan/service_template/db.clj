(ns piotr-yuxuan.service-template.db
  "Database access functions, schema validation, and lifecycle
  management for round-up job execution records, including migrations
  and connection pooling."
  (:require
   [clojure.java.io :as io]
   [malli.core :as m]
   [malli.error :as me]
   [malli.util :as mu]
   [migratus.core :as migratus]
   [next.jdbc :as jdbc]
   [next.jdbc.connection :as connection]
   [next.jdbc.result-set :as rs]
   [next.jdbc.types :refer [as-other]]
   [piotr-yuxuan.closeable-map :as closeable-map :refer [closeable-map*]]
   [piotr-yuxuan.service-template.exception :as st.exception]
   [piotr-yuxuan.service-template.math :refer [NonNegInt64]])
  (:import
   (com.zaxxer.hikari HikariDataSource)))

(def RoundupJobExecution
  "Malli application-level schema defining the structure and constraints
  of a round-up job execution record before it is inserted in the
  database."
  (m/schema
   [:map
    [:id {:optional true} uuid?]
    [:account-uid uuid?]
    [:savings-goal-uid {:optional true} [:maybe uuid?]]
    [:round-up-amount-in-minor-units {:optional true} [:maybe NonNegInt64]]
    [:calendar-year NonNegInt64]
    [:calendar-week NonNegInt64]
    [:status {:optional true} [:enum "running" "completed" "insufficient_founds" "failed"]]]))

(defn insert-roundup-job!
  "Validate and insert a new round-up job execution record into the
  database, returning the inserted record."
  [{::keys [datasource] :query/keys [insert-job-execution]} {:keys [account-uid savings-goal-uid round-up-amount-in-minor-units calendar-year calendar-week] :as round-up-job}]
  (let [prepared-parameters [:account-uid :savings-goal-uid :round-up-amount-in-minor-units :calendar-year :calendar-week]]
    (when-let [error (-> RoundupJobExecution
                         (mu/select-keys prepared-parameters)
                         (m/explain round-up-job))]
      (throw (ex-info "Invalid named parameters" {:type ::st.exception/short-circuit
                                                  :body {:round-up-job round-up-job
                                                         :explanation (me/humanize error)}}))))
  (-> datasource
      (jdbc/execute! [insert-job-execution account-uid savings-goal-uid round-up-amount-in-minor-units calendar-year calendar-week]
                     {:timeout 5
                      :builder-fn rs/as-unqualified-kebab-maps})
      first))

(defn update-roundup-job!
  "Validate and update all fields of an existing round-up job execution
  record (`PUT` semantics, not `PATCH`), returning the updated record
  or throwing if not found."
  [{::keys [datasource] :query/keys [update-job-execution]} {:keys [savings-goal-uid round-up-amount-in-minor-units status account-uid calendar-year calendar-week] :as round-up-job}]
  (let [prepared-parameters [:savings-goal-uid :round-up-amount-in-minor-units :status :account-uid :calendar-year :calendar-week]]
    (when-let [error (-> RoundupJobExecution
                         (mu/select-keys prepared-parameters)
                         mu/required-keys
                         (m/explain round-up-job))]
      (throw (ex-info "Invalid named parameters" {:type ::st.exception/short-circuit
                                                  :body {:round-up-job round-up-job
                                                         :explanation (me/humanize error)}}))))
  (let [[record] (jdbc/execute! datasource
                                [update-job-execution savings-goal-uid round-up-amount-in-minor-units (as-other status) account-uid calendar-year calendar-week]
                                {:timeout 5
                                 :builder-fn rs/as-unqualified-kebab-maps})]
    (when-not record
      (throw (ex-info "No round-up jobs found." {:type ::st.exception/short-circuit
                                                 :body {:round-up-job round-up-job}})))
    record))

(defn find-roundup-job
  "Validate parameters and retrieve a round-up job execution record by
  account UID, year, and week, returning the record or `nil` if
  absent."
  [{::keys [datasource] :query/keys [select_job_execution_by_account_uid_calendar_year_and_week]} {:keys [account-uid calendar-year calendar-week] :as args}]
  (let [prepared-parameters [:account-uid :calendar-year :calendar-week]]
    (when-let [error (-> RoundupJobExecution
                         (mu/select-keys prepared-parameters)
                         mu/required-keys
                         (m/explain args))]
      (throw (ex-info "Invalid named parameters" {:type ::st.exception/short-circuit
                                                  :body {:args args
                                                         :explanation (me/humanize error)}}))))
  (-> datasource
      (jdbc/execute!
       [select_job_execution_by_account_uid_calendar_year_and_week account-uid calendar-year calendar-week]
       {:timeout 5
        :builder-fn rs/as-unqualified-kebab-maps})
      first))

(defn ->connection-pool
  "Create and configure a `HikariDataSource` connection pool for
  PostgreSQL using provided connection parameters."
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
  "Return a Migratus configuration map for running database migrations
  if migration is enabled in the config."
  [{::keys [datasource migrate?]}]
  (when migrate?
    {:managed-connection? true
     :store :database
     :db {:datasource datasource}}))

(defn migrate
  "Execute pending database migrations using the Migratus configuration
  derived from the provided config."
  [config]
  (when-let [migration-config (migration-config config)]
    (migratus/migrate migration-config)))

(defn start
  "Return a closeable configuration map with an initialised datasource,
  SQL query templates, and optionally runs migrations."
  [config]
  (closeable-map*
   (-> config
       (assoc ::datasource (->connection-pool config)
              :query/insert-job-execution (slurp (io/resource "queries/insert_job_execution.sql"))
              :query/update-job-execution (slurp (io/resource "queries/update_job_execution.sql"))
              :query/select_job_execution_by_account_uid_calendar_year_and_week (slurp (io/resource "queries/select_job_execution_by_account_uid_calendar_year_and_week.sql")))
       (doto migrate))))

(comment
  (require '[piotr-yuxuan.service-template.config :as config])
  (migratus/create (migration-config @user/app) "")
  (migratus/migrate (migration-config (assoc @user/app ::migrate? true)))
  (user/restart user/app (assoc (config/load-config []) ::migrate? true)))
