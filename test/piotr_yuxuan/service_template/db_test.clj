(ns piotr-yuxuan.service-template.db-test
  (:require
   [clj-test-containers.core :as tc]
   [clojure.test :refer [deftest is testing]]
   [piotr-yuxuan.closeable-map :as closeable-map :refer [close!]]
   [piotr-yuxuan.service-template.db :as db]
   [piotr-yuxuan.service-template.exception :as st.exception])
  (:import
   (clojure.lang ExceptionInfo)
   (org.testcontainers.containers PostgreSQLContainer)))

(defmethod close! PostgreSQLContainer
  [x]
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
  (testing "happy path"
    (testing "with minimal columns"
      (with-open [container (closeable-map/closeable-map (tc/start! (tc/init {:container (PostgreSQLContainer. "postgres:18beta2")
                                                                              :exposed-ports [pg-port]})))
                  config (db/start (postgres-container->db-config container))]
        (let [record (db/insert-roundup-job! config
                                             {:calendar-week 32
                                              :calendar-year 2025
                                              :account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a"})
              expected {:account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a",
                        :savings-goal-uid nil,
                        :round-up-amount-in-minor-units nil,
                        :calendar-year 2025,
                        :calendar-week 32,
                        :status "running"}]
          (is (contains? record :last-update-at))
          (is (contains? record :id))
          (is (contains? record :status))
          (is (contains? record :transfer-uid))
          (is (= expected (dissoc record :id :last-update-at :transfer-uid))))))

    (testing "with more optional columns"
      (with-open [container (closeable-map/closeable-map (tc/start! (tc/init {:container (PostgreSQLContainer. "postgres:18beta2")
                                                                              :exposed-ports [pg-port]})))
                  config (db/start (postgres-container->db-config container))]
        (let [record (db/insert-roundup-job! config
                                             {:calendar-week 32
                                              :calendar-year 2025
                                              :account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a"
                                              :savings-goal-uid #uuid "8c32435a-f947-4a60-a420-b4a798e186cb"
                                              :round-up-amount-in-minor-units 1234})
              expected {:account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a"
                        :savings-goal-uid #uuid "8c32435a-f947-4a60-a420-b4a798e186cb"
                        :round-up-amount-in-minor-units 1234
                        :calendar-year 2025
                        :calendar-week 32
                        :status "running"}]
          (is (contains? record :last-update-at))
          (is (contains? record :id))
          (is (contains? record :status))
          (is (= expected (dissoc record :id :last-update-at :transfer-uid)))))))

  (testing "bad argument"
    (with-open [container (closeable-map/closeable-map (tc/start! (tc/init {:container (PostgreSQLContainer. "postgres:18beta2")
                                                                            :exposed-ports [pg-port]})))
                config (db/start (postgres-container->db-config container))]
      (let [ex (is (thrown-with-msg? ExceptionInfo #"Invalid named parameters"
                                     (db/insert-roundup-job! config
                                                             {:calendar-week 32
                                                              :calendar-year "bad"
                                                              :account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a"
                                                              :savings-goal-uid #uuid "8c32435a-f947-4a60-a420-b4a798e186cb"
                                                              :round-up-amount-in-minor-units 1234})))]
        (is (= {:type ::st.exception/short-circuit
                :body {:round-up-job {:calendar-week 32
                                      :calendar-year "bad"
                                      :account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a"
                                      :savings-goal-uid #uuid "8c32435a-f947-4a60-a420-b4a798e186cb"
                                      :round-up-amount-in-minor-units 1234}
                       :explanation {:calendar-year ["should be an integer"]}}}
               (ex-data ex)))))))

(deftest update-roundup-job-test
  (testing "no records found"
    (with-open [container (closeable-map/closeable-map (tc/start! (tc/init {:container (PostgreSQLContainer. "postgres:18beta2")
                                                                            :exposed-ports [pg-port]})))
                config (db/start (postgres-container->db-config container))]
      ;; No records previous inserted.
      (let [ex (is (thrown-with-msg? ExceptionInfo #"No round-up jobs found."
                                     (db/update-roundup-job! config {:calendar-week 32
                                                                     :calendar-year 2025
                                                                     :account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a"
                                                                     :savings-goal-uid #uuid "8c32435a-f947-4a60-a420-b4a798e186cb"
                                                                     :round-up-amount-in-minor-units 1234
                                                                     :status "running"})))]
        (is (= (ex-data ex)
               {:type ::st.exception/short-circuit
                :body {:round-up-job {:calendar-week 32
                                      :calendar-year 2025
                                      :account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a"
                                      :savings-goal-uid #uuid "8c32435a-f947-4a60-a420-b4a798e186cb"
                                      :round-up-amount-in-minor-units 1234
                                      :status "running"}}})))))

  (testing "partial update not allowed"
    (with-open [container (closeable-map/closeable-map (tc/start! (tc/init {:container (PostgreSQLContainer. "postgres:18beta2")
                                                                            :exposed-ports [pg-port]})))
                config (db/start (postgres-container->db-config container))]
      (let [expected {:calendar-week 32
                      :calendar-year 2025
                      :account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a"}]
        (db/insert-roundup-job! config expected)
        (let [ex (is (thrown-with-msg? ExceptionInfo #"Invalid named parameters"
                                       (db/update-roundup-job! config {:calendar-week 32
                                                                       :calendar-year 2025
                                                                       :account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a"})))]
          (is (= (ex-data ex)
                 {:type ::st.exception/short-circuit
                  :body {:round-up-job {:calendar-week 32
                                        :calendar-year 2025
                                        :account-uid #uuid "b9dcaf8a-ef55-4f3a-bbbf-a36b8ee6674a"}
                         :explanation {:savings-goal-uid ["missing required key"]
                                       :round-up-amount-in-minor-units ["missing required key"]
                                       :status ["missing required key"]}}}))))))

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
                                                :savings-goal-uid #uuid "8c32435a-f947-4a60-a420-b4a798e186cb"
                                                :status "running"})
                :round-up-amount-in-minor-units
                (= 1234)))))))

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
          (is (contains? actual :transfer-uid))
          (is (= expected (select-keys actual
                                       [:account-uid
                                        :calendar-year
                                        :calendar-week]))))))))
