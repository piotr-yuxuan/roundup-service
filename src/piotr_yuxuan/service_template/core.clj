(ns piotr-yuxuan.service-template.core
  (:require
   [com.brunobonacci.mulog :as log]
   [piotr-yuxuan.service-template.db :as db]
   [piotr-yuxuan.service-template.exception :as st.exception]
   [piotr-yuxuan.service-template.math :as st.math]
   [piotr-yuxuan.service-template.starling-api.ops :as starling-api]
   [reitit.ring.malli])
  (:import
   (java.util UUID)))

(defn select-matching-savings-goal
  "Retrieve the active savings goal matching a given name from Starling
  API and return the first match."
  [config {:keys [savings-goal-name] :as args}]
  (->> (starling-api/get-all-savings-goals config args)
       (filter (comp #{"ACTIVE"} :state))
       (filter (comp #(= savings-goal-name %) :name))
       ;; Stable output: don't expect any implicit ordering from Starling API.
       (sort-by :savingsGoalUid)
       first))

(defn get-primary-account
  [config args]
  (let [;; Don't expect any implicit ordering from Starling API.
        primary-account (->> (starling-api/get-accounts config args)
                             (filter (comp #{"PRIMARY"} :accountType))
                             (sort-by :createdAt)
                             first)]
    (when-not primary-account
      (throw (ex-info "No primary account found"
                      {:type ::st.exception/short-circuit})))
    primary-account))

(defn resolve-savings-goal-uid
  [config {:keys [savings-goal-name] :as args}]
  (->> (or (and savings-goal-name (:savingsGoalUid (select-matching-savings-goal config args)))
           ;; If no savings goals have this name, we create one and return its uid.
           (:savingsGoalUid (starling-api/put-create-a-savings-goal config args)))))

(defn insufficient-funds?
  [config args {:keys [round-up-amount-in-minor-units]}]
  (let [{:keys [accountWouldBeInOverdraftIfRequestedAmountSpent requestedAmountAvailableToSpend]} (starling-api/get-confirmation-of-funds config (assoc args :target-amount round-up-amount-in-minor-units))]
    (or accountWouldBeInOverdraftIfRequestedAmountSpent
        (not requestedAmountAvailableToSpend))))

(defn resolve-round-up-amount
  [config args]
  (->> (starling-api/get-settled-transactions-between config args)
       (filter (comp #{"SETTLED"} :status))
       (filter (comp #{"OUT"} :direction))
       (map (comp (partial st.math/round-up-difference 2) :minorUnits :amount))
       (reduce +)))

;; account-uid "595a8a9e-14f8-43fa-a883-4391bfa6c23f"
;; savings-goal-uid "5f824aa3-5561-4fbe-adfe-7c59bd9f12ff"
;; transfer-uid "640fc791-a37e-400c-8584-34b2704b0828"
(defonce transfer-uid (UUID/randomUUID))
;; week 32: min-timestamp "2025-08-03T23:00:00Z"
;;          max-timestamp "2025-08-10T23:00:00Z"

(defn add-money-to-savings-goal->status
  [config args job-execution currency]
  (let [args {:token (:token args)
              :account-uid (:account-uid args)
              :transfer-uid (:transfer-uid job-execution)
              :savings-goal-uid (:savings-goal-uid job-execution)
              :amount {:currency currency
                       :minorUnits (:round-up-amount-in-minor-units job-execution)}}
        outcome (starling-api/put-add-money-to-saving-goal config args)]
    (cond (:success outcome)
          :status/completed

          (->> (:errors outcome)
               (some (comp #{"INSUFFICIENT_FUNDS"} :message)))
          :status/insufficient-funds

          (->> (:errors outcome)
               (some (comp #{"IDEMPOTENCY_MISMATCH"} :message)))
          :status/failed

          :else
          (throw (ex-info "Unexpected Starling API errors"
                          {:type ::st.exception/short-circuit
                           :body (:errors outcome)})))))

(def terminal-status?
  #{:status/completed :status/failed})

(defn job
  "Execute a round-up job for a specified week, including: retrieving
  transactions, calculating the round-up amount, confirming funds, and
  transferring the amount to the savings goal."
  [config args]
  (let [{:keys [currency accountUid]} (get-primary-account config args)
        args (assoc args :account-uid accountUid)
        job-execution (or (db/find-roundup-job config args)
                          (db/insert-roundup-job! config args))]
    (log/with-context {:account-uid accountUid}
      (if (->> job-execution :status terminal-status?)
        (do (log/log ::job-has-terminal-status :status (->> job-execution :status))
            job-execution)
        (let [job-execution (if (:round-up-amount-in-minor-units job-execution)
                              job-execution
                              (->> (resolve-round-up-amount config args)
                                   (assoc job-execution :round-up-amount-in-minor-units)
                                   (db/update-roundup-job! config)))
              job-execution (if (:savings-goal-uid job-execution)
                              job-execution
                              (->> (resolve-savings-goal-uid config args)
                                   (assoc job-execution :savings-goal-uid)
                                   (db/update-roundup-job! config)))]
          (->> (cond (zero? (:round-up-amount-in-minor-units job-execution)) :status/failed
                     (insufficient-funds? config args job-execution) :status/insufficient-funds
                     :else (add-money-to-savings-goal->status config args job-execution currency))
               (assoc job-execution :status)
               (db/update-roundup-job! config)))))))
