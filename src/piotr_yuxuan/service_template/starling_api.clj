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
  (m/decoder GetAccountResponse (mt/transformer
                                 mt/strip-extra-keys-transformer
                                 mt/json-transformer)))

(defn get-accounts
  [{::keys [api-base]} {:keys [token]}]
  (-> (request->response
       {:method :get
        :url (str/join "/" [api-base "accounts"])
        :headers {"accept" "application/json"
                  "authorization" (str "Bearer " token)}})
      :body
      (jsonista.core/read-value jsonista.core/keyword-keys-object-mapper)
      GetAccountResponse-body-decoder))

(def GetFeedTransactionsBetween
  (m/schema
   [:map {:closed true}
    [:feedItems
     [:sequential entity/FeedItem]]]))

(def GetFeedTransactionsBetween-body-decoder
  (m/decoder GetFeedTransactionsBetween (mt/transformer
                                         mt/strip-extra-keys-transformer
                                         mt/json-transformer)))

(defn get-feed-transactions-between
  [{::keys [api-base]} {:keys [token account-uid category-uid min-timestamp max-timestamp]}]
  (-> {:method :get
       :url (str/join "/" [api-base "feed/account" account-uid "category" category-uid "transactions-between"])
       :headers {"accept" "application/json"
                 "authorization" (str "Bearer " token)}
       :query-params {"minTransactionTimestamp" (m/encode inst? min-timestamp mt/json-transformer)
                      "maxTransactionTimestamp" (m/encode inst? max-timestamp mt/json-transformer)}}
      request->response
      :body
      (jsonista.core/read-value jsonista.core/keyword-keys-object-mapper)
      GetFeedTransactionsBetween-body-decoder))

(def SavingsGoalsV2
  (m/schema
   [:map {:closed true}
    [:savingsGoalList
     [:sequential entity/SavingsGoalV2]]]))

(def SavingsGoalsV2-body-decoder
  (m/decoder SavingsGoalsV2 (mt/transformer
                             mt/strip-extra-keys-transformer
                             mt/json-transformer)))

(defn get-all-savings-goals
  [{::keys [api-base]} {:keys [token account-uid]}]
  (-> {:method :get
       :url (str/join "/" [api-base "account" account-uid "savings-goals"])
       :headers {"accept" "application/json"
                 "authorization" (str "Bearer " token)}}
      request->response
      :body
      (jsonista.core/read-value jsonista.core/keyword-keys-object-mapper)
      SavingsGoalsV2-body-decoder))

