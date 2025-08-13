(ns piotr-yuxuan.service-template.http
  (:require
   [clj-http.client :as http]
   [jsonista.core :as j]
   [reitit.ring.malli]
   [ring.util.http-response :as http-response]
   [ring.util.http-status :as http-status]
   [ring.util.response :as response]
   [safely.core :refer [safely]])
  (:import
   (com.fasterxml.jackson.core JsonParseException)))

(defn read-json-body
  [{:keys [body] :as response}]
  (try
    (if (string? body)
      (update response :body jsonista.core/read-value jsonista.core/keyword-keys-object-mapper)
      response)
    (catch JsonParseException _
      {:status http-status/internal-server-error
       :body "Malformed JSON body"})))

(defn -request->response
  [request]
  (let [response (safely (http/request (assoc request
                                              :throw-exceptions false
                                              :ignore-unknown-host? true))
                   :on-error
                   :circuit-breaker ::http
                   :retry-delay [:random-exp-backoff :base 300 :+/- 0.35 :max 25000]
                   :max-retries 5)
        content-type (some-> response (response/find-header "content-type") val)
        json-body? (some->> content-type (re-find #"(?i)application/json"))]
    (cond (not response) (http-response/bad-request {:error "Unknown host."})
          (and (:body response) json-body?) (read-json-body response)
          :else response)))
