(ns piotr-yuxuan.service-template.db-test
  (:require
   [clj-test-containers.core :as tc]
   [clojure.test :refer [deftest is testing]]
   [piotr-yuxuan.closeable-map :refer [close!]]
   [piotr-yuxuan.closeable-map :as closeable-map]
   [piotr-yuxuan.service-template.db :as db])
  (:import
   [clojure.lang ExceptionInfo]
   (org.testcontainers.containers PostgreSQLContainer)))

(defmethod close! PostgreSQLContainer
  [x]
  (println ::close)
  (.stop ^PostgreSQLContainer x))

(def pg-port 5432)

(defn postgres-container->db-config
  [container]
  {::db/migrate? true
   ::db/hostname (.getHost (:container container))
   ::db/port (-> container :mapped-ports (get pg-port))
   ::db/dbname (.getDatabaseName (:container container))
   ::db/username (.getUsername (:container container))
   ::db/password (.getPassword (:container container))})

(deftest insert-roundup-job-test
  (with-open [container (closeable-map/closeable-map (tc/start! (tc/init {:container (PostgreSQLContainer. "postgres:18beta2")
                                                                          :exposed-ports [pg-port]})))
              config (db/start (postgres-container->db-config container))]
    (testing "happy path"

      (let [[actual :as ret] (db/insert-roundup-job! config
                                                     {:calendar-week 32
                                                      :calendar-year 2025
                                                      :account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a"
                                                      :savings-goal-uid #uuid "8c32435a-f947-4a60-a420-b4a798e186cb"
                                                      :round-up-amount-in-minor-units 1234})
            expected {:account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a",
                      :savings-goal-uid #uuid "8c32435a-f947-4a60-a420-b4a798e186cb",
                      :round-up-amount-in-minor-units 1234M,
                      :calendar-year 2025,
                      :calendar-week 32,
                      :status "running"}]
        (is (= 1 (count ret)))
        (is (contains? actual :last-update-at))
        (is (contains? actual :id))
        (is (contains? actual :status))
        (is (= expected (dissoc actual :id :last-update-at)))))
    (testing "bad argument"
      (is (thrown-with-msg? ExceptionInfo #"Unexpected values"
                            (db/insert-roundup-job! config
                                                    {:calendar-week 32
                                                     :calendar-year "bad"
                                                     :account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a"
                                                     :savings-goal-uid #uuid "8c32435a-f947-4a60-a420-b4a798e186cb"
                                                     :round-up-amount-in-minor-units 1234}))))))
