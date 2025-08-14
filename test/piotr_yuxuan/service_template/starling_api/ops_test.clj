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

(deftest get-settledtransactions-between-test
  (let [api-base (mg/generate [:string {:min 10 :max 15}])
        token (mg/generate [:string {:min 10 :max 15}])
        account-uid (UUID/randomUUID)
        category-uid (UUID/randomUUID)
        min-timestamp (Instant/now)
        max-timestamp (Instant/now)]
    (testing "with entities returned"
      (let [{:keys [feedItems] :as body} (->> ops/get-settled-transactions-between-schema<- mg/generate :body)
            expected-request {:method :get
                              :url (str/join "/" [api-base "v2/feed/account" account-uid "settled-transactions-between"])
                              :headers {"accept" "application/json"
                                        "authorization" (str "Bearer " token)}
                              :query-params {:minTransactionTimestamp min-timestamp
                                             :maxTransactionTimestamp max-timestamp}}]
        (with-redefs [st.http/request->response
                      (fn [_ _ request]
                        (is (= expected-request request))
                        {:status http-status/ok
                         :body body})]
          (is (= feedItems
                 (ops/get-settled-transactions-between
                  {::ops/api-base api-base}
                  {:token token
                   :account-uid account-uid
                   :category-uid category-uid
                   :min-timestamp min-timestamp
                   :max-timestamp max-timestamp}))))))

    (testing "no entities found"
      (with-redefs [st.http/request->response (constantly
                                               {:status http-status/ok
                                                :body {:feedItems []}})]
        (is (-> (ops/get-settled-transactions-between
                 {::ops/api-base (mg/generate [:string {:min 10 :max 15}])}
                 {:token token
                  :account-uid account-uid
                  :category-uid category-uid
                  :min-timestamp min-timestamp
                  :max-timestamp max-timestamp})
                seq nil?))))))

(deftest get-all-savings-goals-test
  (let [api-base (mg/generate [:string {:min 10 :max 15}])
        token (mg/generate [:string {:min 10 :max 15}])
        account-uid (UUID/randomUUID)]
    (testing "with entities returned"
      (let [{:keys [savingsGoalList] :as body} (->> ops/get-all-savings-goals-schema<- mg/generate :body)
            expected-request {:method :get
                              :url (str/join "/" [api-base "v2/account" account-uid "savings-goals"])
                              :headers {"accept" "application/json"
                                        "authorization" (str "Bearer " token)}}]
        (with-redefs [st.http/request->response
                      (fn [_ _ request]
                        (is (= expected-request request))
                        {:status http-status/ok
                         :body body})]
          (is (= savingsGoalList
                 (ops/get-all-savings-goals
                  {::ops/api-base api-base}
                  {:token token
                   :account-uid account-uid}))))))

    (testing "no entities found"
      (with-redefs [st.http/request->response (constantly
                                               {:status http-status/ok
                                                :body {:savingsGoalList []}})]
        (is (-> (ops/get-all-savings-goals
                 {::ops/api-base (mg/generate [:string {:min 10 :max 15}])}
                 {:token token
                  :account-uid account-uid})
                seq nil?))))))

;; (deftest put-create-a-savings-goal-testt)

;; (deftest get-one-savings-goal-testt)

;; (deftest get-confirmation-of-funds-testt)

;; (deftest put-add-money-to-saving-goal-testt)
