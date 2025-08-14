(ns piotr-yuxuan.service-template.starling-api.entity
  "Only keep the attributes that are interesting to us."
  (:require
   [clojure.test.check.generators :as gen]
   [malli.core :as m]))

(def NonNegInt
  (m/schema
   [:and {:gen/gen (gen/one-of [(gen/large-integer* {:min 0})
                                (gen/fmap bigdec (gen/large-integer* {:min 0}))])}
    number?
    [:fn (fn [x]
           (cond
             (and (int? x) (<= 0 x)) true
             (instance? java.math.BigDecimal x) (and (zero? (.scale ^BigDecimal x))
                                                     (<= 0 (.intValue x)))
             :else false))]]))

(def Currency
  (m/schema
   [:enum "UNDEFINED" "AED" "AFN" "ALL" "AMD" "ANG" "AOA" "ARS" "AUD" "AWG" "AZN" "BAM" "BBD" "BDT" "BGN" "BHD" "BIF" "BMD" "BND" "BOB" "BOV" "BRL" "BSD" "BTN" "BWP" "BYN" "BYR" "BZD" "CAD" "CDF" "CHE" "CHF" "CHW" "CLF" "CLP" "CNY" "COP" "COU" "CRC" "CUC" "CUP" "CVE" "CZK" "DJF" "DKK" "DOP" "DZD" "EGP" "ERN" "ETB" "EUR" "FJD" "FKP" "GBP" "GEL" "GHS" "GIP" "GMD" "GNF" "GTQ" "GYD" "HKD" "HNL" "HRK" "HTG" "HUF" "IDR" "ILS" "INR" "IQD" "IRR" "ISK" "JMD" "JOD" "JPY" "KES" "KGS" "KHR" "KMF" "KPW" "KRW" "KWD" "KYD" "KZT" "LAK" "LBP" "LKR" "LRD" "LSL" "LTL" "LYD" "MAD" "MDL" "MGA" "MKD" "MMK" "MNT" "MOP" "MRO" "MRU" "MUR" "MVR" "MWK" "MXN" "MXV" "MYR" "MZN" "NAD" "NGN" "NIO" "NOK" "NPR" "NZD" "OMR" "PAB" "PEN" "PGK" "PHP" "PKR" "PLN" "PYG" "QAR" "RON" "RSD" "RUB" "RUR" "RWF" "SAR" "SBD" "SCR" "SDG" "SEK" "SGD" "SHP" "SLL" "SLE" "SOS" "SRD" "SSP" "STD" "STN" "SVC" "SYP" "SZL" "THB" "TJS" "TMT" "TND" "TOP" "TRY" "TTD" "TWD" "TZS" "UAH" "UGX" "USD" "USN" "USS" "UYI" "UYU" "UZS" "VEF" "VES" "VND" "VUV" "WST" "XAF" "XAG" "XAU" "XBA" "XBB" "XBC" "XBD" "XCD" "XCG" "XDR" "XOF" "XPD" "XPF" "XPT" "XSU" "XTS" "XUA" "XXX" "YER" "ZAR" "ZMW" "ZWL" "ZWG"]))

(def Account
  (m/schema
   [:map {:closed true}
    [:name :string]
    [:createdAt inst?]
    [:defaultCategory :uuid]
    [:currency Currency]
    [:accountType [:enum "PRIMARY" "ADDITIONAL" "LOAN" "FIXED_TERM_DEPOSIT" "SAVINGS"]]
    [:accountUid :uuid]]))

(def CurrencyAndAmount
  (m/schema
   [:map {:closed true}
    ;; Why is this a string in transactions-between, but an enum in Account?
    [:currency Currency]
    [:minorUnits [:int {:min 0}]]]))

(def FeedItem
  (m/schema
   [:map {:closed true}
    [:feedItemUid :uuid]
    [:categoryUid :uuid]
    [:amount CurrencyAndAmount]
    [:sourceAmount CurrencyAndAmount]
    [:direction [:enum "IN" "OUT"]]
    [:updatedAt inst?]
    [:transactionTime inst?]
    [:settlementTime inst?]
    [:retryAllocationUntilTime inst?]
    [:status [:enum "UPCOMING" "UPCOMING_CANCELLED" "PENDING", "REVERSED", "SETTLED", "DECLINED", "REFUNDED", "RETRYING", "ACCOUNT_CHECK"]]]))

(def SavingsGoalV2
  (m/schema
   [:map {:closed true}
    [:savingsGoalUid :uuid]
    [:name :string]
    [:totalSaved CurrencyAndAmount]
    [:state [:enum "CREATING" "ACTIVE" "ARCHIVING" "ARCHIVED" "RESTORING" "PENDING"]]]))
