(ns piotr-yuxuan.service-template.math
  (:import
   (java.math BigDecimal RoundingMode)))

(defn bankers-rounding
  "Too bad we just have to round up to the nearest integer above."
  [^long n]
  (-> (BigDecimal/valueOf n)
      (.setScale -2 RoundingMode/HALF_EVEN)
      (.longValueExact)))

(defn ceiling-rounding
  "Rounds `n` up (towards positive infinity) to the digit place
  specified by `scale`. This argument `scale` is the power-of-10 digit
  position to round to:

  - `scale = 0` → to 10^0 the unit
  - `scale = 1` → to 10^1, 10
  - `scale = 2` → to 10^2, 100
  - and so on."
  [scale ^long n]
  {:pre [(<= 0 scale)]}
  (-> (BigDecimal/valueOf n)
      ;; Negative scale means rounding to the left of the decimal point.
      (.setScale (- scale) RoundingMode/CEILING)
      (.longValueExact)))

(defn round-up-difference
  "Returns how much needs to be added to `n` to round it up to the given
  scale. `scale` is the power of 10 digit to round to (e.g. 2 =
  hundreds). If `n` is already rounded, returns 0."
  [scale ^long n]
  (- (ceiling-rounding scale n) n))
