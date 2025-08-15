(ns piotr-yuxuan.service-template.db
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
   [piotr-yuxuan.service-template.starling-api.entity :as entity]
   [piotr-yuxuan.service-template.exception :as st.exception])
  (:import
   (com.zaxxer.hikari HikariDataSource)))

(def RoundupJobExecution
  (m/schema
   [:map
    [:id {:optional true} uuid?]
    [:account-uid uuid?]
    [:savings-goal-uid {:optional true} [:maybe uuid?]]
    [:round-up-amount-in-minor-units {:optional true} entity/NonNegInt]
    [:calendar-year entity/NonNegInt]
    [:calendar-week entity/NonNegInt]
    [:status {:optional true} [:enum "running" "completed" "insufficient_founds" "failed"]]]))

(defn insert-roundup-job!
  "Create a new recors, returning the full record as from the database."
  [{::keys [datasource] :query/keys [insert-job-execution]} {:keys [account-uid savings-goal-uid round-up-amount-in-minor-units calendar-year calendar-week] :as round-up-job}]
  (let [prepared-parameters [:account-uid :savings-goal-uid :round-up-amount-in-minor-units :calendar-year :calendar-week]]
    (when-let [error (-> RoundupJobExecution
                         (mu/select-keys prepared-parameters)
                         (mu/update-properties assoc :closed true)
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
  "Update record fields, returning the whole record from the database.
  Write all columns considered as a `PUT`, not a partial write as a
  `PATCH`."
  [{::keys [datasource] :query/keys [update-job-execution]} {:keys [savings-goal-uid round-up-amount-in-minor-units status account-uid calendar-year calendar-week] :as round-up-job}]
  (let [prepared-parameters [:savings-goal-uid :round-up-amount-in-minor-units :status :account-uid :calendar-year :calendar-week]]
    (when-let [error (-> RoundupJobExecution
                         (mu/select-keys prepared-parameters)
                         mu/required-keys
                         (mu/update-properties assoc :closed true)
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
  "Retrieve record from database, return it or `nil` if not found."
  [{::keys [datasource] :query/keys [select_job_execution_by_account_uid_calendar_year_and_week]} {:keys [account-uid calendar-year calendar-week] :as args}]
  (let [prepared-parameters [:account-uid :calendar-year :calendar-week]]
    (when-let [error (-> RoundupJobExecution
                         (mu/select-keys prepared-parameters)
                         mu/required-keys
                         (mu/update-properties assoc :closed true)
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
              :query/update-job-execution (slurp (io/resource "queries/update_job_execution.sql"))
              :query/select_job_execution_by_account_uid_calendar_year_and_week (slurp (io/resource "queries/select_job_execution_by_account_uid_calendar_year_and_week.sql")))
       (doto migrate))))

(comment
  (require '[piotr-yuxuan.service-template.config :as config])
  (migratus/create (migration-config @user/app) "")
  (migratus/migrate (migration-config (assoc @user/app ::migrate? true)))
  (user/restart user/app (assoc (config/load-config []) ::migrate? true)))
