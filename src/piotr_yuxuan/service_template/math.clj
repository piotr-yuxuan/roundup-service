(ns piotr-yuxuan.service-template.math
  (:import
   (java.math BigDecimal RoundingMode)))

(defn bankers-rounding
  "Too bad we just have to round up to the nearest integer above."
  [^long n]
  (-> (BigDecimal/valueOf n)
      (.setScale -2 RoundingMode/HALF_EVEN)
      (.longValueExact)))

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
