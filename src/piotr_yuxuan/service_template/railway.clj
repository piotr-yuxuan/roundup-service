(ns piotr-yuxuan.service-template.railway
  "https://fsharpforfunandprofit.com/rop/")

(defn ok [v] {:ok v})
(defn error [err] {:error err})
(defn success? [res] (contains? res :ok))
(defn bind [res f]
  (if (success? res)
    (f (:ok res))
    res))
