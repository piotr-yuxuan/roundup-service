(ns piotr-yuxuan.service-template.core
  (:require
   [piotr-yuxuan.service-template.db :as db]
   [piotr-yuxuan.service-template.exception :as st.exception]
   [piotr-yuxuan.service-template.math :as st.math]
   [piotr-yuxuan.service-template.starling-api.ops :as starling-api]
   [reitit.ring.malli]))

(defn select-matching-savings-goal
  "Retrieve the active savings goal matching a given name from Starling
  API and return the first match."
  [config {:keys [savings-goal-name] :as args}]
  (->> (starling-api/get-all-savings-goals config args)
       (filter (comp #{"ACTIVE"} :state))
       (filter (comp #(= savings-goal-name %) :name))
       ;; Don't expect any implicit ordering from Starling API.
       (sort-by :savingsGoalUid)
       first))

(defn job
  "Execute a round-up job for a specified week, including: retrieving
  transactions, calculating the round-up amount, confirming funds, and
  transferring the amount to the savings goal."
  [config {:keys [calendar-year calendar-week savings-goal-uid savings-goal-name] :as args}]
  ;; The week should be in the past so we know that all transactions
  ;; have completed. It is a wider problem to record the transactions
  ;; we're rounded up.
  (let [args (merge args (year+week-number->interval calendar-year calendar-week))
        {:keys [accountUid currency]} (->> (starling-api/get-accounts config args)
                                           (filter (comp #{"PRIMARY"} :accountType))
                                           (sort-by :createdAt)
                                           first)
        args (assoc args :account-uid accountUid)
        job-execution (or (db/find-roundup-job config args)
                          (db/insert-roundup-job! config args))

        ;; Record the savings goal UID.
        job-execution (->> (or (:savings-goal-uid job-execution)
                               ;; If given a savings-goal-uid we expect to use it, or we fail.
                               (and savings-goal-uid (:savings-goal-uid (starling-api/get-one-savings-goal config args)))
                               ;; Else, if given a name we consider it.
                               (and savings-goal-name (:savingsGoalUid (select-matching-savings-goal config args)))
                               ;; Otherwise, we create a savings
                               (:savingsGoalUid (starling-api/put-create-a-savings-goal config args)))

                           (assoc job-execution :savings-goal-uid)
                           (db/update-roundup-job! config))

        ;; Record the round-up amount at this point in time.
        {:keys [savings-goal-uid transfer-uid round-up-amount-in-minor-units]}
        (->> args
             (starling-api/get-settled-transactions-between config)
             (filter (comp #{"SETTLED"} :status))
             (filter (comp #{"OUT"} :direction))
             (map (comp (partial st.math/round-up-difference 2) :minorUnits :amount))
             (reduce +)
             (assoc job-execution :round-up-amount-in-minor-units)
             (db/update-roundup-job! config))

        confirmation-of-funds (starling-api/get-confirmation-of-funds config (assoc args :target-amount round-up-amount-in-minor-units))]
    (starling-api/put-add-money-to-saving-goal config
                                               (assoc args
                                                      :transfer-uid transfer-uid
                                                      :savings-goal-uid savings-goal-uid
                                                      :amount {:currency currency
                                                               :minorUnits round-up-amount-in-minor-units}))))
