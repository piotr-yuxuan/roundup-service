(ns piotr-yuxuan.service-template.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [malli.generator :as mg]
   [piotr-yuxuan.service-template.core :as core]
   [piotr-yuxuan.service-template.starling-api.entity :as entity]
   [piotr-yuxuan.service-template.math :as math]
   [piotr-yuxuan.service-template.starling-api.ops :as starling-api]
   [reitit.ring.malli]
   [piotr-yuxuan.service-template.math :as st.math]
   [piotr-yuxuan.service-template.db :as db])
  (:import
   (clojure.lang ExceptionInfo)
   (java.util UUID)))

(deftest select-matching-savings-goal-test
  (testing "one savings goal active and with this name"
    (let [savings-goal-name "Holidays"
          expected-savings-goal {:savingsGoalUid "uuid-0001", :name savings-goal-name, :state "ACTIVE"}
          savings-goals [{:savingsGoalUid "uuid-0001", :name "Ski trip", :state "ACTIVE"}
                         expected-savings-goal]]
      (with-redefs [starling-api/get-all-savings-goals (fn [_ args]
                                                         (is (= savings-goal-name (:savings-goal-name args)))
                                                         savings-goals)]
        (is (= expected-savings-goal
               (core/select-matching-savings-goal {} {:savings-goal-name savings-goal-name}))))))
  (testing "multiple savings goals with same same, but only one active"
    (let [savings-goal-name "Holidays"
          expected-savings-goal {:savingsGoalUid "uuid-0001", :name savings-goal-name, :state "ACTIVE"}
          savings-goals [{:savingsGoalUid "uuid-0001", :name "Ski trip", :state "ACTIVE"}
                         expected-savings-goal
                         {:savingsGoalUid "uuid-0002", :name savings-goal-name, :state "RESTORING"}
                         {:savingsGoalUid "uuid-0003", :name savings-goal-name, :state "ARCHIVING"}]]
      (with-redefs [starling-api/get-all-savings-goals (fn [_ args]
                                                         (is (= savings-goal-name (:savings-goal-name args)))
                                                         savings-goals)]
        (is (= expected-savings-goal
               (core/select-matching-savings-goal {} {:savings-goal-name savings-goal-name}))))))
  (testing "multiple matching savings goals, stable output"
    (let [savings-goal-name "Holidays"
          expected-savings-goal {:savingsGoalUid "uuid-0100", :name savings-goal-name, :state "ACTIVE"}
          savings-goals [{:savingsGoalUid "uuid-0001", :name "Ski trip", :state "ACTIVE"}
                         expected-savings-goal
                         {:savingsGoalUid "uuid-0110", :name savings-goal-name, :state "ACTIVE"}
                         {:savingsGoalUid "uuid-0200", :name savings-goal-name, :state "ACTIVE"}]]
      (with-redefs [starling-api/get-all-savings-goals (fn [_ args]
                                                         (is (= savings-goal-name (:savings-goal-name args)))
                                                         savings-goals)]
        (is (= expected-savings-goal
               (core/select-matching-savings-goal {} {:savings-goal-name savings-goal-name})))))))

(deftest get-primary-account-test
  (testing "one primary account found"
    (dotimes [_ 15]
      (let [[expected-account :as accounts] (->> (mg/generate [:sequential {:min 1 :max 1} entity/Account])
                                                 (map-indexed (fn [i acc] (assoc acc :accountType (if (zero? i) "PRIMARY" "FIXED_TERM_DEPOSIT")))))]
        (with-redefs [starling-api/get-accounts (constantly accounts)]
          (is (= expected-account (core/get-primary-account {} {})))))))

  (testing "multiple primary accounts, stable output"
    (dotimes [_ 15]
      (let [accounts (->> (mg/generate [:sequential {:min 5} entity/Account])
                          (map-indexed (fn [i acc] (assoc acc :accountType (if (odd? i) "PRIMARY" "FIXED_TERM_DEPOSIT")))))
            called? (atom 0)]
        (with-redefs [starling-api/get-accounts (fn [_ _] (swap! called? inc) (shuffle accounts))]
          (is (= (core/get-primary-account {} {})
                 (core/get-primary-account {} {})
                 (core/get-primary-account {} {})))
          (is (= 3 @called?))))))

  (testing "throw if no primary accounts found"
    (let [called? (atom 0)]
      (with-redefs [starling-api/get-accounts (fn [_ _] (swap! called? inc) nil)]
        (is (thrown-with-msg? ExceptionInfo #"No primary account found" (core/get-primary-account {} {})))
        (is (= 1 @called?))))))

(deftest resolve-savings-goal-uid-test
  (testing "return a savings goal if found a matching name"
    (let [savings-goal-name "Valentine's"
          expected (UUID/randomUUID)
          savings-goal {:savingsGoalUid expected, :name savings-goal-name, :state "ACTIVE"}]
      (with-redefs [core/select-matching-savings-goal (constantly savings-goal)
                    starling-api/put-create-a-savings-goal (fn [& args] (throw (ex-info "shouldn't be called" {})))]
        (is (= expected (core/resolve-savings-goal-uid {} {:savings-goal-name savings-goal-name}))))))
  (testing "create and return a savings goal otherwise"
    (let [savings-goal-name "Valentine's"
          expected (UUID/randomUUID)
          savings-goal {:savingsGoalUid expected, :name savings-goal-name, :state "ACTIVE"}]
      (with-redefs [core/select-matching-savings-goal (fn [& args] (throw (ex-info "shouldn't be called" {})))
                    starling-api/put-create-a-savings-goal (constantly savings-goal)]
        (is (= expected (core/resolve-savings-goal-uid {} {})))))))

(deftest insufficient-funds?-test
  (let [target-amount (mg/generate math/NonNegInt64)]
    (with-redefs [starling-api/get-confirmation-of-funds
                  (fn [_ args]
                    (is (-> args :target-amount (= target-amount)))
                    {:accountWouldBeInOverdraftIfRequestedAmountSpent true
                     :requestedAmountAvailableToSpend true})]
      (is (core/insufficient-funds? {} {} {:round-up-amount-in-minor-units target-amount})))
    (with-redefs [starling-api/get-confirmation-of-funds
                  (fn [_ args]
                    (is (-> args :target-amount (= target-amount)))
                    {:accountWouldBeInOverdraftIfRequestedAmountSpent true
                     :requestedAmountAvailableToSpend false})]
      (is (core/insufficient-funds? {} {} {:round-up-amount-in-minor-units target-amount})))
    (with-redefs [starling-api/get-confirmation-of-funds
                  (fn [_ args]
                    (is (-> args :target-amount (= target-amount)))
                    {:accountWouldBeInOverdraftIfRequestedAmountSpent false
                     :requestedAmountAvailableToSpend false})]
      (is (core/insufficient-funds? {} {} {:round-up-amount-in-minor-units target-amount})))
    (with-redefs [starling-api/get-confirmation-of-funds
                  (fn [_ args]
                    (is (-> args :target-amount (= target-amount)))
                    {:accountWouldBeInOverdraftIfRequestedAmountSpent false
                     :requestedAmountAvailableToSpend true})]
      (is (false? (core/insufficient-funds? {} {} {:round-up-amount-in-minor-units target-amount}))))))

(deftest resolve-round-up-amount-in-minor-units-test
  (dotimes [_ 15]
    (testing "only consider outgoing transactions that have settled"
      (let [[x :as settled-transactions] (->> (mg/generate [:sequential {:min 1 :max 1} entity/FeedItem])
                                              (map #(assoc % :status "SETTLED" :direction "OUT")))]
        (with-redefs [starling-api/get-settled-transactions-between (constantly settled-transactions)]
          (is (->> (:amount x)
                   :minorUnits
                   (st.math/round-up-difference 2)
                   (= (core/resolve-round-up-amount {} {}))))))
      (with-redefs [starling-api/get-settled-transactions-between (constantly [])]
        (is (zero? (core/resolve-round-up-amount {} {}))))
      (testing "additional transactions are filtered out and don't make it to the final result"
        (let [transactions (map-indexed #(if (zero? (mod %1 3)) %2 (assoc %2 :status "SETTLED" :direction "OUT"))
                                        (mg/generate [:sequential {:min 10} entity/FeedItem]))]
          (is (= (with-redefs [starling-api/get-settled-transactions-between
                               (constantly transactions)]
                   (core/resolve-round-up-amount {} {}))
                 (with-redefs [starling-api/get-settled-transactions-between
                               (constantly (->> transactions
                                                (filter (comp #{"SETTLED"} :status))
                                                (filter (comp #{"OUT"} :direction))))]
                   (core/resolve-round-up-amount {} {})))))))))

(deftest add-money-to-savings-goal->status-test
  (testing "round-up is completed"
    (with-redefs [starling-api/put-add-money-to-saving-goal (constantly {:success true})]
      (is (= :status/completed (core/add-money-to-savings-goal->status {} {} {} "GBP")))))
  (testing "insufficient funds"
    (with-redefs [starling-api/put-add-money-to-saving-goal (constantly {:errors [{:message "INSUFFICIENT_FUNDS"}]})]
      (is (= :status/insufficient-funds (core/add-money-to-savings-goal->status {} {} {} "GBP")))))
  (testing "idempotency mismatch"
    (with-redefs [starling-api/put-add-money-to-saving-goal (constantly {:errors [{:message "IDEMPOTENCY_MISMATCH"}]})]
      (is (= :status/failed (core/add-money-to-savings-goal->status {} {} {} "GBP")))))
  (testing "don't let an unknown error slip through"
    (with-redefs [starling-api/put-add-money-to-saving-goal (constantly {:errors [{:message "IDEMPOTENCY_MISMATCH"}]})]
      (is (= :status/failed (core/add-money-to-savings-goal->status {} {} {} "GBP"))))))

(deftest job-test
  (testing "early return if job-execution is in terminal status"
    (let [call-log (atom {})
          account-uuid (UUID/randomUUID)
          expected {:status (rand-nth (vec core/terminal-status?))}]
      (with-redefs [core/get-primary-account (fn [& _] (swap! call-log update :core/get-primary-account (fnil inc 0))
                                               {:accountUid account-uuid})
                    core/resolve-round-up-amount (fn [& _] (swap! call-log update :core/resolve-round-up-amount (fnil inc 0)) nil)
                    core/resolve-savings-goal-uid (fn [& _] (swap! call-log update :core/resolve-savings-goal-uid (fnil inc 0)) nil)
                    db/find-roundup-job (fn [config args] (swap! call-log update :db/find-roundup-job (fnil inc 0))
                                          (is (= {:account-uid account-uuid} args))
                                          expected)
                    db/insert-roundup-job! (fn [& _] (swap! call-log update :db/insert-roundup-job! (fnil inc 0)) nil)
                    db/update-roundup-job! (fn [& _] (swap! call-log update :db/insert-roundup-job! (fnil inc 0)) nil)]
        (is (= expected (core/job {} {}))))
      (is (= {:core/get-primary-account 1
              :db/find-roundup-job 1}
             @call-log))))
  (testing "round-up amount"
    (testing "do not resolve if already present"
      (let [call-log (atom {})
            account-uuid (UUID/randomUUID)
            savings-goal-uuid (UUID/randomUUID)
            find-roundup-job-value {:account-uid account-uuid
                                    :round-up-amount-in-minor-units 213
                                    :savings-goal-uid savings-goal-uuid}]
        (with-redefs [core/get-primary-account (fn [& _] (swap! call-log update :core/get-primary-account (fnil inc 0))
                                                 {:accountUid account-uuid})
                      core/resolve-round-up-amount (fn [& _] (swap! call-log update :core/resolve-round-up-amount (fnil inc 0)) nil)
                      core/resolve-savings-goal-uid (fn [& _] (swap! call-log update :core/resolve-savings-goal-uid (fnil inc 0)) nil)
                      db/find-roundup-job (fn [_ args] (swap! call-log update :db/find-roundup-job (fnil inc 0))
                                            (is (= {:account-uid account-uuid} args))
                                            find-roundup-job-value)
                      db/insert-roundup-job! (fn [_ args] (swap! call-log update :db/insert-roundup-job! (fnil inc 0)) nil)
                      db/update-roundup-job! (fn [config job-execution]
                                               (swap! call-log update :db/update-roundup-job! (fnil inc 0))
                                               job-execution)
                      core/insufficient-funds? (fn [& _] (swap! call-log update :core/insufficient-funds? (fnil inc 0)) true)
                      core/add-money-to-savings-goal->status (fn [& _] (swap! call-log update :core/add-money-to-savings-goal->status (fnil inc 0)) true)]
          (is (= (assoc find-roundup-job-value
                        :status :status/insufficient-funds)
                 (core/job {} {})))
          (is (= {:core/get-primary-account 1,
                  :db/find-roundup-job 1,
                  :core/insufficient-funds? 1,
                  :db/update-roundup-job! 1}
                 @call-log)))))
    (testing "resolve if not present"
      (let [call-log (atom {})
            account-uuid (UUID/randomUUID)
            savings-goal-uuid (UUID/randomUUID)
            find-roundup-job-value {:account-uid account-uuid
                                    :savings-goal-uid savings-goal-uuid}
            roundup-amount 213]
        (with-redefs [core/get-primary-account (fn [& _] (swap! call-log update :core/get-primary-account (fnil inc 0))
                                                 {:accountUid account-uuid})
                      core/resolve-round-up-amount (fn [& _]
                                                     (swap! call-log update :core/resolve-round-up-amount (fnil inc 0))
                                                     roundup-amount)
                      core/resolve-savings-goal-uid (fn [& _] (swap! call-log update :core/resolve-savings-goal-uid (fnil inc 0)) nil)
                      db/find-roundup-job (fn [_ args] (swap! call-log update :db/find-roundup-job (fnil inc 0))
                                            (is (= {:account-uid account-uuid} args))
                                            find-roundup-job-value)
                      db/insert-roundup-job! (fn [_ args] (swap! call-log update :db/insert-roundup-job! (fnil inc 0)) nil)
                      db/update-roundup-job! (fn [config job-execution]
                                               (swap! call-log update :db/update-roundup-job! (fnil inc 0))
                                               job-execution)
                      core/insufficient-funds? (fn [& _] (swap! call-log update :core/insufficient-funds? (fnil inc 0)) true)
                      core/add-money-to-savings-goal->status (fn [& _] (swap! call-log update :core/add-money-to-savings-goal->status (fnil inc 0)) true)]
          (is (= (assoc find-roundup-job-value
                        :round-up-amount-in-minor-units roundup-amount
                        :status :status/insufficient-funds)
                 (core/job {} {})))
          (is (= {:core/get-primary-account 1,
                  :db/find-roundup-job 1,
                  :core/resolve-round-up-amount 1
                  :core/insufficient-funds? 1,
                  :db/update-roundup-job! 2}
                 @call-log))))))
  (testing "savings goals"
    (testing "do not resolve if already present"
      (let [call-log (atom {})
            account-uuid (UUID/randomUUID)
            savings-goal-uuid (UUID/randomUUID)
            find-roundup-job-value {:account-uid account-uuid
                                    :savings-goal-uid savings-goal-uuid}
            roundup-amount 213]
        (with-redefs [core/get-primary-account (fn [& _] (swap! call-log update :core/get-primary-account (fnil inc 0))
                                                 {:accountUid account-uuid})
                      core/resolve-round-up-amount (fn [& _]
                                                     (swap! call-log update :core/resolve-round-up-amount (fnil inc 0))
                                                     roundup-amount)
                      core/resolve-savings-goal-uid (fn [& _] (swap! call-log update :core/resolve-savings-goal-uid (fnil inc 0)) nil)
                      db/find-roundup-job (fn [_ args] (swap! call-log update :db/find-roundup-job (fnil inc 0))
                                            (is (= {:account-uid account-uuid} args))
                                            find-roundup-job-value)
                      db/insert-roundup-job! (fn [_ args] (swap! call-log update :db/insert-roundup-job! (fnil inc 0)) nil)
                      db/update-roundup-job! (fn [config job-execution]
                                               (swap! call-log update :db/update-roundup-job! (fnil inc 0))
                                               job-execution)
                      core/insufficient-funds? (fn [& _] (swap! call-log update :core/insufficient-funds? (fnil inc 0)) true)
                      core/add-money-to-savings-goal->status (fn [& _] (swap! call-log update :core/add-money-to-savings-goal->status (fnil inc 0)) true)]
          (is (= (assoc find-roundup-job-value
                        :round-up-amount-in-minor-units roundup-amount
                        :status :status/insufficient-funds)
                 (core/job {} {})))
          (is (= {:core/get-primary-account 1,
                  :db/find-roundup-job 1,
                  :core/resolve-round-up-amount 1
                  :core/insufficient-funds? 1,
                  :db/update-roundup-job! 2}
                 @call-log)))))
    (testing "resolve if not present"
      (let [call-log (atom {})
            account-uuid (UUID/randomUUID)
            savings-goal-uuid (UUID/randomUUID)
            find-roundup-job-value {:account-uid account-uuid}
            roundup-amount 213]
        (with-redefs [core/get-primary-account (fn [& _] (swap! call-log update :core/get-primary-account (fnil inc 0))
                                                 {:accountUid account-uuid})
                      core/resolve-round-up-amount (fn [& _]
                                                     (swap! call-log update :core/resolve-round-up-amount (fnil inc 0))
                                                     roundup-amount)
                      core/resolve-savings-goal-uid (fn [& _] (swap! call-log update :core/resolve-savings-goal-uid (fnil inc 0))
                                                      savings-goal-uuid)
                      db/find-roundup-job (fn [_ args] (swap! call-log update :db/find-roundup-job (fnil inc 0))
                                            (is (= {:account-uid account-uuid} args))
                                            find-roundup-job-value)
                      db/insert-roundup-job! (fn [_ args] (swap! call-log update :db/insert-roundup-job! (fnil inc 0)) nil)
                      db/update-roundup-job! (fn [config job-execution]
                                               (swap! call-log update :db/update-roundup-job! (fnil inc 0))
                                               job-execution)
                      core/insufficient-funds? (fn [& _] (swap! call-log update :core/insufficient-funds? (fnil inc 0)) true)
                      core/add-money-to-savings-goal->status (fn [& _] (swap! call-log update :core/add-money-to-savings-goal->status (fnil inc 0)) true)]
          (is (= (assoc find-roundup-job-value
                        :round-up-amount-in-minor-units roundup-amount
                        :savings-goal-uid savings-goal-uuid
                        :status :status/insufficient-funds)
                 (core/job {} {})))
          (is (= {:core/get-primary-account 1,
                  :db/find-roundup-job 1,
                  :core/resolve-round-up-amount 1
                  :core/resolve-savings-goal-uid 1
                  :core/insufficient-funds? 1,
                  :db/update-roundup-job! 3}
                 @call-log))))))
  (testing "persist failed job and early return if round-up amount is zero"
    (let [call-log (atom {})
          account-uuid (UUID/randomUUID)
          savings-goal-uuid (UUID/randomUUID)
          find-roundup-job-value {:account-uid account-uuid
                                  :round-up-amount-in-minor-units 0
                                  :savings-goal-uid savings-goal-uuid}]
      (with-redefs [core/get-primary-account (fn [& _] (swap! call-log update :core/get-primary-account (fnil inc 0))
                                               {:accountUid account-uuid})
                    core/resolve-round-up-amount (fn [& _] (swap! call-log update :core/resolve-round-up-amount (fnil inc 0)) nil)
                    core/resolve-savings-goal-uid (fn [& _] (swap! call-log update :core/resolve-savings-goal-uid (fnil inc 0)) nil)
                    db/find-roundup-job (fn [_ args] (swap! call-log update :db/find-roundup-job (fnil inc 0))
                                          (is (= {:account-uid account-uuid} args))
                                          find-roundup-job-value)
                    db/insert-roundup-job! (fn [_ args] (swap! call-log update :db/insert-roundup-job! (fnil inc 0)) nil)
                    db/update-roundup-job! (fn [config job-execution]
                                             (swap! call-log update :db/update-roundup-job! (fnil inc 0))
                                             job-execution)
                    core/insufficient-funds? (fn [& _] (swap! call-log update :core/insufficient-funds? (fnil inc 0)) true)
                    core/add-money-to-savings-goal->status (fn [& _] (swap! call-log update :core/add-money-to-savings-goal->status (fnil inc 0)) true)]
        (is (= (assoc find-roundup-job-value
                      :status :status/failed)
               (core/job {} {})))
        (is (= {:core/get-primary-account 1,
                :db/find-roundup-job 1,
                :db/update-roundup-job! 1}
               @call-log)))))
  (testing "persist and early return if insufficient funds"
    (let [call-log (atom {})
          account-uuid (UUID/randomUUID)
          savings-goal-uuid (UUID/randomUUID)
          find-roundup-job-value {:account-uid account-uuid
                                  :round-up-amount-in-minor-units 123
                                  :savings-goal-uid savings-goal-uuid}]
      (with-redefs [core/get-primary-account (fn [& _] (swap! call-log update :core/get-primary-account (fnil inc 0))
                                               {:accountUid account-uuid})
                    core/resolve-round-up-amount (fn [& _] (swap! call-log update :core/resolve-round-up-amount (fnil inc 0)) nil)
                    core/resolve-savings-goal-uid (fn [& _] (swap! call-log update :core/resolve-savings-goal-uid (fnil inc 0)) nil)
                    db/find-roundup-job (fn [_ args] (swap! call-log update :db/find-roundup-job (fnil inc 0))
                                          (is (= {:account-uid account-uuid} args))
                                          find-roundup-job-value)
                    db/insert-roundup-job! (fn [_ args] (swap! call-log update :db/insert-roundup-job! (fnil inc 0)) nil)
                    db/update-roundup-job! (fn [config job-execution]
                                             (swap! call-log update :db/update-roundup-job! (fnil inc 0))
                                             job-execution)
                    core/insufficient-funds? (fn [& _] (swap! call-log update :core/insufficient-funds? (fnil inc 0)) true)
                    core/add-money-to-savings-goal->status (fn [& _] (swap! call-log update :core/add-money-to-savings-goal->status (fnil inc 0)) true)]
        (is (= (assoc find-roundup-job-value
                      :status :status/insufficient-funds)
               (core/job {} {})))
        (is (= {:core/get-primary-account 1,
                :db/find-roundup-job 1,
                :core/insufficient-funds? 1
                :db/update-roundup-job! 1}
               @call-log)))))
  (testing "persist and return execution job on happy path"
    (let [call-log (atom {})
          account-uuid (UUID/randomUUID)
          savings-goal-uuid (UUID/randomUUID)
          find-roundup-job-value {:account-uid account-uuid
                                  :round-up-amount-in-minor-units 123
                                  :savings-goal-uid savings-goal-uuid}]
      (with-redefs [core/get-primary-account (fn [& _] (swap! call-log update :core/get-primary-account (fnil inc 0))
                                               {:accountUid account-uuid})
                    core/resolve-round-up-amount (fn [& _] (swap! call-log update :core/resolve-round-up-amount (fnil inc 0)) nil)
                    core/resolve-savings-goal-uid (fn [& _] (swap! call-log update :core/resolve-savings-goal-uid (fnil inc 0)) nil)
                    db/find-roundup-job (fn [_ args] (swap! call-log update :db/find-roundup-job (fnil inc 0))
                                          (is (= {:account-uid account-uuid} args))
                                          find-roundup-job-value)
                    db/insert-roundup-job! (fn [_ args] (swap! call-log update :db/insert-roundup-job! (fnil inc 0)) nil)
                    db/update-roundup-job! (fn [config job-execution]
                                             (swap! call-log update :db/update-roundup-job! (fnil inc 0))
                                             job-execution)
                    core/insufficient-funds? (fn [& _] (swap! call-log update :core/insufficient-funds? (fnil inc 0)) false)
                    core/add-money-to-savings-goal->status (fn [& _] (swap! call-log update :core/add-money-to-savings-goal->status (fnil inc 0)) :status/completed)]
        (is (= (assoc find-roundup-job-value
                      :status :status/completed)
               (core/job {} {})))
        (is (= {:core/get-primary-account 1,
                :db/find-roundup-job 1,
                :core/insufficient-funds? 1
                :db/update-roundup-job! 1
                :core/add-money-to-savings-goal->status 1}
               @call-log))))))
