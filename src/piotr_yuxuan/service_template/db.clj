(ns piotr-yuxuan.service-template.db
  (:require
   [migratus.core :as migratus]
   [next.jdbc :as jdbc]
   [next.jdbc.connection :as connection])
  (:import
   (com.zaxxer.hikari HikariDataSource)))

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
  (-> config
      (assoc ::datasource (->connection-pool config))
      (doto migrate)))
