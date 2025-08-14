(ns piotr-yuxuan.service-template.starling-api.ops-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [malli.core :as m]
   [malli.generator :as mg]
   [malli.transform :as mt]
   [piotr-yuxuan.service-template.http :as st.http]
   [piotr-yuxuan.service-template.railway :refer [error ok]]
   [piotr-yuxuan.service-template.starling-api.entity :as entity]
   [piotr-yuxuan.service-template.starling-api.ops :as ops]
   [ring.util.http-status :as http-status])
  (:import
   (java.time Instant)
   (java.util UUID)))

(deftest get-accounts-test
  (testing "happy path, one account"
    (let [token (mg/generate [:string {:min 10 :max 15}])
          api-base (mg/generate [:string {:min 10 :max 15}])
          expected (mg/generate entity/Account)
          expected-request {:method :get
                            :url (str/join "/" [api-base "v2/accounts"])
                            :headers {"accept" "application/json"
                                      "authorization" (str "Bearer " token)}}]
      (with-redefs [st.http/request->response (fn [_ _ request]
                                                (is (= expected-request request))
                                                {:status http-status/ok
                                                 :body {:accounts [expected]}})]
        (is (= [expected]
               (ops/get-accounts
                {::ops/api-base api-base}
                {:token token})))))))

(deftest get-feed-transactions-between-test
  ;; (testing "happy path"
  ;;   )

  (testing "no transactions"
    (with-redefs [st.http/-request->response (constantly
                                              {:status http-status/ok
                                               :body {:feedItems []}})]
      (is (= (ok [])
             (ops/get-feed-transactions-between
              {::ops/api-base (mg/generate [:string {:min 10 :max 15}])}
              {:token (mg/generate [:string {:min 10 :max 15}])
               :account-uid (UUID/randomUUID)
               :category-uid (UUID/randomUUID)
               :min-timestamp (Instant/now)
               :max-timestamp (Instant/now)})))))

  (testing "bad request"
    (let [reason (rand-nth ["UNKNOWN_ACCOUNT" "UNKNOWN_CATEGORY"])]
      (with-redefs [st.http/-request->response (constantly {:status http-status/bad-request
                                                            :body {:errors [{:message reason}]
                                                                   :success false}})]
        (is (= (error {:body {:errors [{:message reason}]
                              :success false}
                       :status 400})
               (ops/get-feed-transactions-between
                {::ops/api-base (mg/generate [:string {:min 10 :max 15}])}
                {:token (mg/generate [:string {:min 10 :max 15}])
                 :account-uid (UUID/randomUUID)
                 :category-uid (UUID/randomUUID)
                 :min-timestamp (Instant/now)
                 :max-timestamp (Instant/now)})))))))

;; (deftest get-all-savings-goals-test)

;; (deftest put-create-a-savings-goal-testt)

;; (deftest get-one-savings-goal-testt)

;; (deftest get-confirmation-of-funds-testt)

;; (deftest put-add-money-to-saving-goal-testt)
