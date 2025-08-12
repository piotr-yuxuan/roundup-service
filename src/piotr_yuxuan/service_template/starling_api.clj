(ns piotr-yuxuan.service-template.starling-api
  (:require
   [clj-http.client :as http]
   [clojure.string :as str]
   [jsonista.core :as j]
   [malli.core :as m]
   [malli.transform :as mt]
   [piotr-yuxuan.service-template.entity :as entity]
   [reitit.ring.malli]
   [safely.core :refer [safely]])
  (:import
   (java.util UUID)))

(defn request->response
  [request]
  (safely (http/request (assoc request :throw-exceptions false))
    :on-error
    :circuit-breaker ::api
    :retry-delay [:random-exp-backoff :base 300 :+/- 0.35 :max 25000]
    :max-retries 5))

(def GetAccountResponse
  (m/schema
   [:map {:closed true}
    [:accounts
     [:sequential entity/Account]]]))

(def GetAccountResponse-body-decoder
  (m/decoder GetAccountResponse mt/json-transformer))

(defn get-accounts
  [{:keys [token]}]
  (-> (request->response
       {:method :get
        :url "https://api-sandbox.starlingbank.com/api/v2/accounts"
        :headers {"accept" "application/json"
                  "authorization" (str "Bearer " token)}})
      :body
      (jsonista.core/read-value jsonista.core/keyword-keys-object-mapper)
      GetAccountResponse-body-decoder))
