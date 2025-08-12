(ns piotr-yuxuan.service-template.starling-api.ops
  (:require
   [clj-http.client :as http]
   [clojure.string :as str]
   [jsonista.core :as j]
   [malli.core :as m]
   [malli.transform :as mt]
   [piotr-yuxuan.service-template.openapi-spec :as openapi-spec]
   [piotr-yuxuan.service-template.starling-api.entity :as entity]
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
        :url (str/join "/" [api-base "v2/accounts"])
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
       :url (str/join "/" [api-base "v2/feed/account" account-uid "category" category-uid "transactions-between"])
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
       :url (str/join "/" [api-base "v2/account" account-uid "savings-goals"])
       :headers {"accept" "application/json"
                 "authorization" (str "Bearer " token)}}
      request->response
      :body
      (jsonista.core/read-value jsonista.core/keyword-keys-object-mapper)
      SavingsGoalsV2-body-decoder))

(def PutCreateASavingsGoalRequestBody
  (m/schema
   [:map {:closed true}
    [:name [:string {:min 1}]]
    [:currency entity/Currency]]))

(def PutCreateASavingsGoalRequestBody-encoder
  (comp jsonista.core/write-value-as-string
        (m/encoder PutCreateASavingsGoalRequestBody (mt/transformer
                                                     mt/strip-extra-keys-transformer
                                                     mt/json-transformer))))

(def CreateOrUpdateSavingsGoalResponseV2
  (m/schema
   [:map {:closed true}
    [:savingsGoalUid uuid?]
    [:success boolean?]]))

(def CreateOrUpdateSavingsGoalResponseV2-decoder
  (m/decoder CreateOrUpdateSavingsGoalResponseV2 (mt/transformer
                                                  mt/strip-extra-keys-transformer
                                                  mt/json-transformer)))

(defn put-create-a-savings-goal
  [{::keys [api-base]} {:keys [token account-uid]}]
  (-> {:method :put
       :url (str/join "/" [api-base "v2/account" account-uid "savings-goals"])
       :headers {"accept" "application/json"
                 "content-type" "application/json"
                 "authorization" (str "Bearer " token)}
       :body (PutCreateASavingsGoalRequestBody-encoder
              {:name "Round it up!"
               :currency "GBP"})}
      request->response
      :body
      (jsonista.core/read-value jsonista.core/keyword-keys-object-mapper)
      CreateOrUpdateSavingsGoalResponseV2-decoder))

(defn delete-a-savings-goal
  [{::keys [api-base]} {:keys [token account-uid savings-goal-uid]}]
  (-> {:method :delete
       :url (str/join "/" [api-base "v2/account" account-uid "savings-goals" savings-goal-uid])
       :headers {"accept" "application/json"
                 "content-type" "application/json"
                 "authorization" (str "Bearer " token)}}
      request->response
      :body
      (jsonista.core/read-value jsonista.core/keyword-keys-object-mapper)))

(def SavingsGoalV2-body-decoder
  (m/decoder entity/SavingsGoalV2 (mt/transformer
                                   mt/strip-extra-keys-transformer
                                   mt/json-transformer)))

(defn get-one-savings-goal
  [[{::keys [api-base]} {:keys [token account-uid savings-goal-uid]}]]
  (-> {:method :get
       :url (str/join "/" [api-base "v2/account" account-uid "savings-goals" savings-goal-uid])
       :headers {"accept" "application/json"
                 "authorization" (str "Bearer " token)}}
      request->response
      :body
      (jsonista.core/read-value jsonista.core/keyword-keys-object-mapper)
      SavingsGoalV2-body-decoder))

(def ConfirmationOfFundsResponse
  (m/schema
   [:map {:closed true}
    [:requestedAmountAvailableToSpend boolean?]
    [:accountWouldBeInOverdraftIfRequestedAmountSpent boolean?]]))

(def ConfirmationOfFundsResponse-body-decoder
  (m/decoder ConfirmationOfFundsResponse (mt/transformer
                                          mt/strip-extra-keys-transformer
                                          mt/json-transformer)))

(defn get-confirmation-of-funds
  [[{::keys [api-base]} {:keys [token account-uid target-amount]}]]
  (-> {:method :get
       :url (str/join "/" [api-base "v2/accounts" account-uid "confirmation-of-funds"])
       :headers {"accept" "application/json"
                 "authorization" (str "Bearer " token)}
       :query-params {"targetAmountInMinorUnits" (m/encode int? target-amount mt/json-transformer)}}
      request->response
      :body
      (jsonista.core/read-value jsonista.core/keyword-keys-object-mapper)
      ConfirmationOfFundsResponse-body-decoder))

(def TopUpRequestV2
  (m/schema
   [:map {:closed true}
    [:amount entity/CurrencyAndAmount]
    [:reference {:optional true} [:string {:max 100}]]]))

(def TopUpRequestV2-encoder
  (comp jsonista.core/write-value-as-string
        (m/encoder TopUpRequestV2 (mt/transformer
                                   mt/strip-extra-keys-transformer
                                   mt/json-transformer))))

(def SavingsGoalTransferResponseV2
  (m/schema
   [:map {:closed true}
    [:transferUid uuid?]
    [:success boolean?]]))

(def SavingsGoalTransferResponseV2-body-decoder
  (m/decoder SavingsGoalTransferResponseV2 (mt/transformer
                                            mt/strip-extra-keys-transformer
                                            mt/json-transformer)))

(defn put-add-money-to-saving-goal
  [[{::keys [api-base]} {:keys [token account-uid savings-goal-uid transfer-uid amount]}]]
  (-> {:method :put
       :url (str/join "/" [api-base "v2/account" account-uid "savings-goals" savings-goal-uid "add-money" transfer-uid])
       :headers {"accept" "application/json"
                 "authorization" (str "Bearer " token)
                 "content-type" "application/json"}
       :body (TopUpRequestV2-encoder {:amount amount})}
      request->response
      :body
      (jsonista.core/read-value jsonista.core/keyword-keys-object-mapper)
      SavingsGoalTransferResponseV2-body-decoder))

(def api-reference-version
  "This is hard-coded because the code above and test have been
  developped against this version. Don't change it unless it breaks."
  "starling-openapi-1.0.0.json")

"https://api-sandbox.starlingbank.com/api/v2"
"https://api-sandbox.starlingbank.com/api/openapi.json"

(defn start
  [{::keys [api-base] :as config}]
  (let [diff (openapi-spec/diff api-reference-version (str/join "/" [api-base "openapi.json"]))]
    (when-not (openapi-spec/compatible? diff)
      (let [incompatible-changes (openapi-spec/changes diff)]
        (println incompatible-changes)
        (throw (ex-info "The current version API is incompatible with the reference version, can't start."
                        {:incompatible-changes incompatible-changes})))))
  config)

(comment
  (def account-uid "4e3b8bbc-1e8a-48b5-9122-6ab08e2eea5e")
  (def category-uid "4e3b0c59-bdbb-49a2-ac50-0237a927a63f")
  (def min-timestamp #inst"2020-08-10T12:34:56.000Z")
  (def max-timestamp #inst"2025-08-12T12:45:56.000Z")
  (def api-base "https://api-sandbox.starlingbank.com/api")
  (def transfer-uid (UUID/randomUUID))
  (def target-amount 1)
  (def amount {:currency "GBP"
               :minorUnits 1})
  (def currency "GBP")
  (def token "eyJhbGciOiJQUzI1NiIsInppcCI6IkdaSVAifQ.H4sIAAAAAAAA_21Ty3KkMAz8lRTnKDUMxgxzy21_YD9AyHLGFbAp2ySb2tp_X4NhCFO50d16tCzxtzAhFNcCRwOKB_cSIvre2LcO7fsLuaF4LsLUpQjBFZaMJeiyu4BASYBN2wDJqhHU6krJSwrmP2NxLZu6PlVl1ZbPhcG4EKIVop0JJHKTjb9cr9j_NirX7k4XIUCeaw1CljV0dUsgLqqR1CgpqUq1o3tnu2dQLUs4lcmSuHQCsG7PINtOnrikc6U4ZaSxXok4hD2r1U0J54YrEE1NcDl3EvRZUyurGrt67hPIjTw_SnYKt8UqWBz46hnV04MQv8YHwSi20WjD_sj3JsQDswKlfDJ5ZWXiHWQlRqTbwPfIHX96E_kJp3hz3oS0MjBWmQ-jJuxzcIc9WlqtEXoF5Gz0rs-NZmbVnNXGDxiNs-A06Mmq1QBNIbphm4MHNGv2gFZh5KvinpOPDS5hA0dMCK-U4CxueMkc8Yt5kzJYi2SwB4EZ8G2tmbX9E6JHG5Bmz3caekdp-r12JsDNz_DIrlneadNvrXLvA7VEeSY2YzyAcJTyPgJ-pFUEeHO7jwO3jnrgljrfmTycTs_-Q4ld_KHWLuaidGM19awgjb2fUeAY04DTuMIRtzNJ_3-6onRMzqtv7Y_s1vfI_pAP7tPe-cizAaDw8UiNSmfq-06XVTwuufj3H7iFnQ6yBAAA.zCx4cTfgc83weSE1PTOd-gAPoK9Y1oASSPhMnog9EYYj_pIKNE3CiSpVKYozI8WYy6ZpQvzHo7y9eTYo1FhdywNiN3whMWke9Gh9tlYe4cyHqpCvRT50gBwAdmKERRcxFePQU7rxtNf09BroHVbXkcWVEkMF0rF8UcYiAqKNlHDkDxGfzJxKlV1XGdV4hXPvBlWJjTH2aw7XoVwG7GSz1zYdArSjT7u6eeK7HsXSnt1zaUTevTU005iu1FR9AGosypghw1Wo8G7yrpazakw88RyYVmQiWC9rFvnkkm1PIfprbqI0ea2dbZhy3DCKeU7IT1IstudgFALH02mD3eYJVb6zukITunVOGN5aRRc44mjiYdDDKVQHgErR6FOR8yzOizHJN5nOeAUy3RSnJfhGr0E-HjU1wjppjOIPLWpOmGslGXhcPKT4KPUv9CelYdetYow6Cmzf_hq0EqqmN7jci259fo9AAZ3JhEnHlYbLfHCpYuT6jUzSP3cy3l6JcecEJlFwr4cKDMO6bxaRF17O6KSI_jMmZIrjm_U5YKGsPLYSnjPNjulHziTu5RdQFAL1PRXatT90HP4Fn2161NQpLWcvH6BoG9olaC2AEutaD9ZZNuN5899PIJNLqs_hzj155Rj8UnmZskzMPlB4IcCtovmF5XSAxC5aPZKZN3urQ58"))
