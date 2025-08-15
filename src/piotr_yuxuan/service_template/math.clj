(ns piotr-yuxuan.service-template.math
  (:require
   [malli.core :as m])
  (:import
   (java.math BigDecimal RoundingMode)))

(def NonNegInt64-max 9223372036854775807)

(def NonNegInt64
  (m/schema
   [:int {:min 0 :max NonNegInt64-max}]))

(defn round-up-difference
  "Returns how much needs to be added to `n` to round it up to the given
  scale. `scale` is the power of 10 digit to round to (e.g. 2 =
  hundreds). If `n` is already rounded, returns 0."
  [scale n]
  (let [factor (bigdec (Math/pow 10 scale))
        v (bigdec n)
        quotient (/ v factor)
        rounded (* (bigdec (Math/ceil (double quotient))) factor)]
    (- rounded v)))
