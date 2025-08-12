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

;; Step: get the primary account
(defn get-primary-account
  [config bag]
  (let [accounts (starling-api/get-accounts config bag)]
    (if-let [primary-account (->> accounts
                                  (filter (comp #{"PRIMARY"} :accountType))
                                  (sort-by :createdAt)
                                  first)]
      (ok (assoc bag
                 :category-uid (:defaultCategory primary-account)
                 :account-uid (:accountUid primary-account)))
      (error {:step :get-primary-account
              :reason "No primary account found"}))))

;; Step: get feed transactions between timestamps for the default category
(defn get-feed-transactions
  [config bag]
  (if-let [all-txs (seq (starling-api/get-feed-transactions-between
                         config
                         bag))]
    (ok (assoc bag :all-txs all-txs))
    (error {:step :get-feed-transactions
            :reason "No transactions found"})))

;; Step: filter eligible transactions
(defn filter-eligible-transactions
  [_ {:keys [all-txs] :as bag}]
  (if-let [eligible-txs (->> all-txs
                             (filter (comp #{"SETTLED"} :status))
                             (filter (comp #{"OUT"} :direction))
                             seq)]
    (ok (assoc bag :eligible-txs eligible-txs))
    (error {:step :filter-eligible-transactions
            :reason "No eligible transactions"})))

;; Step: calculate round-up amount
(defn calculate-round-up
  [_ {:keys [eligible-txs] :as bag}]
  (->> eligible-txs
       (map (comp (partial round-up-difference 2)
                  :minorUnits
                  :amount))
       (reduce +)
       (assoc {:currency "GBP"} :minorUnits)
       (assoc bag :round-up-amount)
       ok))

;; Step: get savings goal (active and stable sorted)
(defn get-savings-goal
  [config bag]
  (if-let [savings-goal (->> (starling-api/get-all-savings-goals config bag)
                             (filter (comp #{"ACTIVE"} :state))
                             (sort-by :savingsGoalUid)
                             first)]
    (ok (assoc bag :savings-goal savings-goal))
    (error {:step :get-savings-goal :reason "No active savings goal found"})))

;; Step: confirmation of funds
(defn get-confirmation-of-funds
  [config bag]
  (if-let [confirmation (starling-api/get-confirmation-of-funds
                         config
                         (assoc bag :target-amount (-> bag :round-up-amount :minorUnits)))]
    (ok (assoc bag :confirmation confirmation))
    (error {:step :confirmation-of-funds :reason "Failed to confirm funds"})))

;; Step: transfer money to saving goal
(defn add-money-to-saving-goal
  [config bag]
  (if-let [response (starling-api/put-add-money-to-saving-goal
                     config
                     (assoc bag
                            :transfer-uid (UUID/randomUUID)
                            :savings-goal-uid (-> bag :savings-goal :savingsGoalUid)
                            :amount (-> bag :round-up-amount)))]
    (ok (assoc bag :transfer-status response))
    (error {:step :add-money-to-saving-goal :reason "Transfer failed"})))

(defn round-up
  [config token]
  (-> (ok {:token token
           :min-timestamp #inst "2024-12-30T00:00:00Z"
           :max-timestamp #inst "2025-09-01T12:34:56.000Z"})
      (bind (partial get-primary-account config))
      (bind (partial get-feed-transactions config))
      (bind (partial filter-eligible-transactions config))
      (bind (partial calculate-round-up config))
      (bind (partial get-savings-goal config))
      (bind (partial get-confirmation-of-funds config))
      (bind (partial add-money-to-saving-goal config))))
