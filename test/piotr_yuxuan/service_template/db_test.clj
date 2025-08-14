(ns piotr-yuxuan.service-template.db-test
  (:require
   [clj-test-containers.core :as tc]
   [clojure.test :refer [deftest is testing]]
   [malli.core :as m]
   [piotr-yuxuan.closeable-map :refer [close!]]
   [piotr-yuxuan.closeable-map :as closeable-map]
   [piotr-yuxuan.service-template.db :as db])
  (:import
   (clojure.lang ExceptionInfo)
   (org.postgresql.util PSQLException)
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

(deftest non-neg-value-test
  (is (m/validate db/non-neg-value? 1))
  (is (not (m/validate db/non-neg-value? 1.5)))
  (is (m/validate db/non-neg-value? 0))
  (is (not (m/validate db/non-neg-value? -1)))
  (is (not (m/validate db/non-neg-value? -1.5)))
  (is (not (m/validate db/non-neg-value? 1.5M)))
  (is (m/validate db/non-neg-value? 1M)))

(deftest insert-roundup-job-test
  (testing "happy path"
    (with-open [container (closeable-map/closeable-map (tc/start! (tc/init {:container (PostgreSQLContainer. "postgres:18beta2")
                                                                            :exposed-ports [pg-port]})))
                config (db/start (postgres-container->db-config container))]
      (let [record (db/insert-roundup-job! config
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
        (is (contains? record :last-update-at))
        (is (contains? record :id))
        (is (contains? record :status))
        (is (= expected (dissoc record :id :last-update-at))))))

  (testing "bad argument"
    (with-open [container (closeable-map/closeable-map (tc/start! (tc/init {:container (PostgreSQLContainer. "postgres:18beta2")
                                                                            :exposed-ports [pg-port]})))
                config (db/start (postgres-container->db-config container))]
      (is (thrown-with-msg? ExceptionInfo #"Unexpected values"
                            (db/insert-roundup-job! config
                                                    {:calendar-week 32
                                                     :calendar-year "bad"
                                                     :account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a"
                                                     :savings-goal-uid #uuid "8c32435a-f947-4a60-a420-b4a798e186cb"
                                                     :round-up-amount-in-minor-units 1234})))))

  (with-open [container (closeable-map/closeable-map (tc/start! (tc/init {:container (PostgreSQLContainer. "postgres:18beta2")
                                                                          :exposed-ports [pg-port]})))
              config (db/start (postgres-container->db-config container))]
    (testing "happy path"
      (let [actual (db/insert-roundup-job! config
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
        (is (contains? actual :last-update-at))
        (is (contains? actual :id))
        (is (contains? actual :status))
        (is (= expected (dissoc actual :id :last-update-at)))))))

(deftest update-roundup-job-test
  (testing "no records found"
    (with-open [container (closeable-map/closeable-map (tc/start! (tc/init {:container (PostgreSQLContainer. "postgres:18beta2")
                                                                            :exposed-ports [pg-port]})))
                config (db/start (postgres-container->db-config container))]
      ;; No records previous inserted.
      (is (thrown-with-msg? ExceptionInfo #"No round-up jobs found."
                            (db/update-roundup-job! config {:calendar-week 32
                                                            :calendar-year 2025
                                                            :account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a"})))))

  (testing "partial update not allowed"
    (with-open [container (closeable-map/closeable-map (tc/start! (tc/init {:container (PostgreSQLContainer. "postgres:18beta2")
                                                                            :exposed-ports [pg-port]})))
                config (db/start (postgres-container->db-config container))]
      (let [expected {:calendar-week 32
                      :calendar-year 2025
                      :account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a"}]
        (db/insert-roundup-job! config expected)
        (is (thrown-with-msg? PSQLException #"ERROR: null value in column \"status\" of relation \"roundup_job_execution\" violates not-null constraint"
                              (db/update-roundup-job! config {:calendar-week 32
                                                              :calendar-year 2025
                                                              :account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a"
                                                              ;; The whole of the modifiable fields should go here.
                                                              }))))))

  (testing "happy path"
    (with-open [container (closeable-map/closeable-map (tc/start! (tc/init {:container (PostgreSQLContainer. "postgres:18beta2")
                                                                            :exposed-ports [pg-port]})))
                config (db/start (postgres-container->db-config container))]
      (let [expected {:calendar-week 32
                      :calendar-year 2025
                      :account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a"}]
        (db/insert-roundup-job! config expected)
        (is (-> (db/update-roundup-job! config {:calendar-week 32
                                                :calendar-year 2025
                                                :account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a"
                                                :round-up-amount-in-minor-units 1234
                                                :status "running"})
                :round-up-amount-in-minor-units
                (= 1234M)))))))

(deftest find-roundup-job-test
  (with-open [container (closeable-map/closeable-map (tc/start! (tc/init {:container (PostgreSQLContainer. "postgres:18beta2")
                                                                          :exposed-ports [pg-port]})))
              config (db/start (postgres-container->db-config container))]
    (let [expected {:calendar-week 32
                    :calendar-year 2025
                    :account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a"}]
      (testing "record doesn't exist" ;; not inserted
        (is (nil? (db/find-roundup-job config
                                       {:calendar-week 32
                                        :calendar-year 2025
                                        :account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a"}))))
      (testing "happy path"
        (db/insert-roundup-job! config expected)
        (let [actual (db/find-roundup-job config
                                          {:calendar-week 32
                                           :calendar-year 2025
                                           :account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a"})]
          (is (contains? actual :last-update-at))
          (is (contains? actual :id))
          (is (contains? actual :status))
          (is (= expected (select-keys actual
                                       [:account-uid
                                        :calendar-year
                                        :calendar-week]))))))))
