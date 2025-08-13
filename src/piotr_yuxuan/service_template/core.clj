(ns piotr-yuxuan.service-template.core
  (:require
   [malli.core :as m]
   [malli.experimental.time :as met]
   [malli.experimental.time.transform :as mett]
   [malli.registry :as mr]
   [malli.transform :as mt]
   [piotr-yuxuan.service-template.railway :refer [bind error ok]]
   [piotr-yuxuan.service-template.starling-api.ops :as starling-api]
   [reitit.ring.malli])
  (:import
   (java.math BigDecimal RoundingMode)
   (java.time Period Year ZoneId)
   (java.time.temporal WeekFields)
   (java.util UUID)
   (java.util UUID)))

(mr/set-default-registry!
 (mr/composite-registry
  (m/default-schemas)
  (met/schemas)))

(defn year+week-number->interval
  "Given ISO year and week number, returns a `[start end)` tuple for
  that week. The period is P7D (7 days), representing a
  half-open [start, start+period) range. We consider that a week
  starts on Monday."
  [^Year year week]
  (let [week-fields (WeekFields/ISO)
        start (-> (.atDay year 1)
                  (.with (.weekOfYear week-fields) week)
                  (.with (.dayOfWeek week-fields) 1) ;; Monday
                  (.atStartOfDay (ZoneId/systemDefault)))]
    [start (.plus start (Period/ofWeeks 1))]))

(m/encode inst?
          #inst"2025-08-12T12:45:56.000Z"
          mt/json-transformer)

(type #inst"2025-08-12T12:45:56.000Z")

(m/encode [:time/zoned-date-time {:pattern "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"}]
          (first (year+week-number->interval (Year/of 2025) 33))
          (mt/transformer
           mett/time-transformer
           mt/json-transformer))

(defn bankers-rounding
  "Too bad we just have to round up to the nearest integer above."
  [^long n]
  (-> (BigDecimal/valueOf n)
      (.setScale -2 RoundingMode/HALF_EVEN)
      (.longValueExact)))

(defn round-up-difference
  "Returns how much needs to be added to `n` to round it up to the given scale.
   `scale` is the power of 10 digit to round to (e.g. 2 = hundreds).
  If `n` is already rounded, returns 0."
  [scale ^long n]
  (let [rounded (-> (BigDecimal/valueOf n)
                    (.setScale (- scale) RoundingMode/CEILING)
                    (.longValueExact))]
    (- rounded n)))

(defn get-primary-account
  [{:keys [config args] :as ctx}]
  (let [accounts (starling-api/get-accounts config args)]
    (if-let [primary-account (->> accounts
                                  (filter (comp #{"PRIMARY"} :accountType))
                                  (sort-by :createdAt)
                                  first)]
      (ok (-> ctx
              (assoc :primary-account primary-account)
              (update :args assoc
                      :category-uid (:defaultCategory primary-account)
                      :account-uid (:accountUid primary-account))))
      (error {:step :get-primary-account
              :reason "No primary account found"}))))

(defn get-feed-transactions
  [{:keys [config args] :as ctx}]
  (if-let [all-txs (seq (starling-api/get-feed-transactions-between
                         config
                         args))]
    (ok (assoc ctx :all-txs all-txs))
    (error {:step :get-feed-transactions
            :reason "No transactions found"})))

(defn filter-eligible-transactions
  [{:keys [all-txs] :as ctx}]
  (if-let [eligible-txs (->> all-txs
                             (filter (comp #{"SETTLED"} :status))
                             (filter (comp #{"OUT"} :direction))
                             seq)]
    (ok (assoc ctx :eligible-txs eligible-txs))
    (error {:step :filter-eligible-transactions
            :reason "No eligible transactions"})))

(defn calculate-round-up
  [{:keys [eligible-txs] :as ctx}]
  (->> eligible-txs
       (map (comp (partial round-up-difference 2)
                  :minorUnits
                  :amount))
       (reduce +)
       (assoc {:currency "GBP"} :minorUnits)
       (assoc ctx :round-up-amount)
       ok))

(defn get-savings-goal
  [{:keys [config args] :as ctx}]
  (if-let [savings-goal (->> (starling-api/get-all-savings-goals config args)
                             (filter (comp #{"ACTIVE"} :state))
                             (sort-by :savingsGoalUid)
                             first)]
    (ok (update ctx :args assoc :savings-goal savings-goal))
    (error {:step :get-savings-goal :reason "No active savings goal found"})))

(defn get-confirmation-of-funds
  [{:keys [config args] :as ctx}]
  (if-let [confirmation (starling-api/get-confirmation-of-funds
                         config
                         (assoc args :target-amount (-> ctx :round-up-amount :minorUnits)))]
    (ok (assoc ctx :confirmation confirmation))
    (error {:step :confirmation-of-funds :reason "Failed to confirm funds"})))

(defn add-money-to-saving-goal
  [{:keys [config args] :as ctx}]
  (if-let [response (starling-api/put-add-money-to-saving-goal
                     config
                     (assoc args
                            :savings-goal-uid (-> args :savings-goal :savingsGoalUid)
                            :amount (-> ctx :round-up-amount)))]
    (ok (assoc ctx :transfer-status response))
    (error {:step :add-money-to-saving-goal :reason "Transfer failed"})))

(defn round-up
  [config args]
  (-> (ok {:config config
           :args args})
      (bind get-primary-account)
      (bind get-feed-transactions)
      (bind filter-eligible-transactions)
      (bind calculate-round-up)
      (bind get-savings-goal)
      (bind get-confirmation-of-funds)
      (bind add-money-to-saving-goal)))
