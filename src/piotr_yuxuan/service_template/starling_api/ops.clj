(ns piotr-yuxuan.service-template.starling-api.ops
  "Perform HTTP operations against the Starling API, including accounts,
  feed items, savings goals, and fund confirmations."
  (:require
   [clj-http.client :as http]
   [clojure.string :as str]
   [com.brunobonacci.mulog :as log]
   [malli.core :as m]
   [piotr-yuxuan.service-template.http :as st.http]
   [piotr-yuxuan.service-template.math :refer [NonNegInt64]]
   [piotr-yuxuan.service-template.openapi-spec :as openapi-spec]
   [piotr-yuxuan.service-template.secret :as secret]
   [piotr-yuxuan.service-template.starling-api.entity :as entity]
   [reitit.ring.malli]))

(def get-accounts-schema<-
  "Validate response body containing a list of accounts."
  (m/schema [:map [:body [:map [:accounts [:sequential entity/Account]]]]]))

(defn get-accounts
  "Retrieve all accounts for a given token from the API."
  [{::keys [api-base]} {:keys [token]}]
  (log/trace ::get-accounts
    []
    (let [request {:method :get
                   :url (str/join "/" [api-base "v2/accounts"])
                   :headers {"accept" "application/json"
                             "authorization" "Bearer " :token token}}]
      (http/with-additional-middleware [secret/secret-token-reveal]
        (->> request
             (st.http/request->response :any get-accounts-schema<-)
             :body
             :accounts)))))

(def get-settled-transactions-between-schema->
  "Validate request headers and query parameters for transaction
  retrieval."
  (m/schema [:map
             [:query-params
              [:map
               [:minTransactionTimestamp inst?]
               [:maxTransactionTimestamp inst?]]]]))

(def get-settled-transactions-between-schema<-
  "Validate response body containing a list of feed items."
  (m/schema [:map [:body [:map [:feedItems [:sequential entity/FeedItem]]]]]))

(defn get-settled-transactions-between
  "Retrieve settled transactions between two timestamps for a given
  account."
  [{::keys [api-base]} {:keys [token account-uid min-timestamp max-timestamp]}]
  (log/trace ::get-settled-transactions-between
    []
    (let [request {:method :get
                   :url (str/join "/" [api-base "v2/feed/account" account-uid
                                       "settled-transactions-between"])
                   :headers {"accept" "application/json"
                             "authorization" "Bearer " :token token}
                   :query-params {:minTransactionTimestamp min-timestamp
                                  :maxTransactionTimestamp max-timestamp}}]
      (http/with-additional-middleware [secret/secret-token-reveal]
        (->> request
             (st.http/request->response get-settled-transactions-between-schema->
                                        get-settled-transactions-between-schema<-)
             :body
             :feedItems)))))

(def get-all-savings-goals-schema<-
  "Validate response body containing a list of savings goals."
  (m/schema [:map [:body [:map [:savingsGoalList [:sequential entity/SavingsGoalV2]]]]]))

(defn get-all-savings-goals
  "Retrieve all savings goals for a given account."
  [{::keys [api-base]} {:keys [token account-uid]}]
  (log/trace ::get-all-savings-goals
    []
    (let [request {:method :get
                   :url (str/join "/" [api-base "v2/account" account-uid
                                       "savings-goals"])
                   :headers {"accept" "application/json"
                             "authorization" "Bearer " :token token}}]

      (http/with-additional-middleware [secret/secret-token-reveal]
        (->> request
             (st.http/request->response :any get-all-savings-goals-schema<-)
             :body
             :savingsGoalList)))))

(def put-create-a-savings-goal-schema->
  "Validate request headers and body for creating a savings goal."
  (m/schema [:map [:body [:map {:closed true}
                          [:name [:string {:min 1}]]
                          [:currency entity/Currency]]]]))

(def put-create-a-savings-goal-schema<-
  "Validate response body confirming the creation of a savings goal."
  (m/schema [:map
             [:body [:map {:closed true}
                     [:savingsGoalUid :uuid]
                     [:success :boolean]]]]))

(defn put-create-a-savings-goal
  "Create a new savings goal for a given account with a specified name
  and currency."
  [{::keys [api-base]} {:keys [token account-uid savings-goal-name currency]}]
  (log/trace ::put-create-a-savings-goal
    []
    (let [request {:method :put
                   :url (str/join "/" [api-base "v2/account" account-uid "savings-goals"])
                   :headers {"accept" "application/json"
                             "content-type" "application/json"
                             "authorization" "Bearer " :token token}
                   :body {:name savings-goal-name
                          :currency currency}}]
      (http/with-additional-middleware [secret/secret-token-reveal]
        (->> request
             (st.http/request->response put-create-a-savings-goal-schema->
                                        put-create-a-savings-goal-schema<-)
             :body)))))

;; This function has a private hint prefix and is not tested because
;; it is used for development purpose only.
(defn -delete-a-savings-goal
  "Delete a specific savings goal (development use only)."
  [{::keys [api-base]} {:keys [token account-uid savings-goal-uid]}]
  (let [request {:method :delete
                 :url (str/join "/" [api-base "v2/account" account-uid "savings-goals" savings-goal-uid])
                 :headers {"accept" "application/json"
                           "authorization" "Bearer " :token token}}]
    (http/with-additional-middleware [secret/secret-token-reveal]
      (->> request
           (st.http/request->response :any :any)
           :body))))

(def get-one-savings-goal-schema<-
  "Validate response body containing a single savings goal."
  (m/schema [:map
             [:body entity/SavingsGoalV2]]))

(defn get-one-savings-goal
  "Retrieve a specific savings goal by UID for a given account."
  [{::keys [api-base]} {:keys [token account-uid savings-goal-uid]}]
  (log/trace ::get-one-savings-goal
    []
    (let [request {:method :get
                   :url (str/join "/" [api-base "v2/account" account-uid "savings-goals" savings-goal-uid])
                   :headers {"accept" "application/json"
                             "authorization" "Bearer " :token token}}]
      (http/with-additional-middleware [secret/secret-token-reveal]
        (->> request
             (st.http/request->response :any get-one-savings-goal-schema<-)
             :body)))))

(def get-confirmation-of-funds-schema->
  "Validate request headers and query parameters for fund confirmation."
  (m/schema [:map [:query-params [:map [:targetAmountInMinorUnits NonNegInt64]]]]))

(def get-confirmation-of-funds-schema<-
  "Validate response body for fund confirmation status."
  (m/schema [:map
             [:body
              [:map
               [:requestedAmountAvailableToSpend :boolean]
               [:accountWouldBeInOverdraftIfRequestedAmountSpent :boolean]]]]))

(defn get-confirmation-of-funds
  "Check if a target amount is available to spend from an account."
  [{::keys [api-base]} {:keys [token account-uid target-amount]}]
  (log/trace ::get-confirmation-of-funds
    []
    (let [request {:method :get
                   :url (str/join "/" [api-base "v2/accounts" account-uid "confirmation-of-funds"])
                   :headers {"accept" "application/json"
                             "authorization" "Bearer " :token token}
                   :query-params {:targetAmountInMinorUnits target-amount}}]
      (http/with-additional-middleware [secret/secret-token-reveal]
        (->> request
             (st.http/request->response get-confirmation-of-funds-schema->
                                        get-confirmation-of-funds-schema<-)
             :body)))))

(def put-add-money-to-saving-goal-schema->
  "Validate request body for adding money to a savings goal."
  (m/schema
   [:map
    [:body
     [:map
      [:amount entity/CurrencyAndAmount]
      [:reference {:optional true} [:string {:max 100}]]]]]))

(def put-add-money-to-saving-goal-schema<-
  "Validate response body confirming transfer to a savings goal."
  (m/schema
   [:map
    [:body
     [:map
      [:transferUid :uuid]
      [:success :boolean]]]]))

(defn put-add-money-to-saving-goal
  "Transfer funds into a specified savings goal."
  [{::keys [api-base]} {:keys [token account-uid savings-goal-uid transfer-uid amount]}]
  (log/trace ::put-add-money-to-saving-goal
    []
    (let [request {:method :put
                   :url (str/join "/" [api-base "v2/account" account-uid "savings-goals" savings-goal-uid "add-money" transfer-uid])
                   :headers {"accept" "application/json"
                             "content-type" "application/json"
                             "authorization" "Bearer " :token token}
                   :body {:amount amount}}]
      (http/with-additional-middleware [secret/secret-token-reveal]
        (->> request
             (st.http/request->response put-add-money-to-saving-goal-schema->
                                        put-add-money-to-saving-goal-schema<-)
             :body)))))

(def api-reference-version
  "This is hard-coded because the code above and test have been
  developped against this version. Don't change it unless it breaks."
  "starling-openapi-1.0.0.json")

(defn start
  "Verify API compatibility with the reference OpenAPI version and
  return the configuration."
  [{::keys [api-base] :as config}]
  (log/trace ::start
    []
    (let [diff (openapi-spec/diff api-reference-version (str/join "/" [api-base "openapi.json"]))]
      (when-not (openapi-spec/compatible? diff)
        (throw (ex-info "The current version API is incompatible with the reference version, can't start."
                        {:incompatible-changes (println-str (openapi-spec/changes diff))})))))
  ;; No need to actually update the config here.
  config)
