(ns piotr-yuxuan.service-template.railway
  "Inspired by https://fsharpforfunandprofit.com/rop/.
   Simpler than https://github.com/fmnoise/flow.")

(defn ok [v] {:ok v})
(defn error [err] {:error err})
(defn success? [res] (contains? res :ok))
(defn bind [res f]
  (if (success? res)
    (f (:ok res))
    res))
