(ns piotr-yuxuan.service-template.math
  "Tough math for sum :)"
  (:require
   [malli.core :as m]))

(def NonNegInt64-max
  "Define the maximum value for a non-negative 64-bit integer (2^63-1)."
  9223372036854775807)

(def NonNegInt64
  "Validate a number as a non-negative 64-bit integer within the range 0
  to `NonNegInt64-max.`"
  (m/schema
   [:int {:min 0 :max NonNegInt64-max}]))

(defn round-up-difference
  "Return how much needs to be added to `n` to round it up to the given
  scale. `scale` is the power of 10 digit to round to (e.g. 2 =
  hundreds). If `n` is already rounded, returns 0."
  ^long [scale n]
  (when-not (contains? #{Long Integer} (type scale))
    (throw (IllegalArgumentException.
            (str "Unsupported type for scale: " (type scale)))))

  (when-not (contains? #{Long Integer} (type n))
    (throw (IllegalArgumentException.
            (str "Unsupported type for n: " (type n)))))

  (let [rounded-n (loop [i 0 acc 1]
                    (if (< i scale)
                      (recur (inc i) (* acc 10))
                      acc))
        remainder (mod n rounded-n)]
    (if (zero? remainder)
      0
      (- rounded-n remainder))))
