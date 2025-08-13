(ns piotr-yuxuan.service-template.http
  (:require
   [clj-http.client :as http]
   [jsonista.core :as j]
   [malli.core :as m]
   [malli.error :as me]
   [malli.transform :as mt]
   [piotr-yuxuan.service-template.railway :refer [error ok]]
   [reitit.ring.malli]
   [ring.util.http-predicates :as http-predicates]
   [ring.util.http-response :as http-response]
   [ring.util.http-status :as http-status]
   [ring.util.response :as response]
   [safely.core :refer [safely]])
  (:import
   (com.fasterxml.jackson.core JsonParseException)))

(defn write-json-body
  [{:keys [body] :as request}]
  (try
    (if body
      (update request :body j/write-value-as-string jsonista.core/keyword-keys-object-mapper)
      request)
    (catch JsonParseException _
      {:status http-status/bad-request
       :body "Malformed JSON body"})))

(defn read-json-body
  [{:keys [body] :as response}]
  (try
    (if (and (string? body) (seq body))
      (update response :body j/read-value jsonista.core/keyword-keys-object-mapper)
      response)
    (catch JsonParseException _
      {:status http-status/internal-server-error
       :body "Malformed JSON body"})))

(defn -request->response
  [request]
  (let [response (safely (http/request (assoc (write-json-body request)
                                              :throw-exceptions false
                                              :ignore-unknown-host? true))
                   :on-error
                   :circuit-breaker ::http
                   :retry-delay [:random-exp-backoff :base 300 :+/- 0.35 :max 25000]
                   :max-retries 0)
        content-type (some-> response (response/find-header "content-type") val)
        json-body? (some->> content-type (re-find #"(?i)application/json"))]
    (cond (not response) (http-response/bad-request {:error "Unknown host."})
          (and (:body response) json-body?) (read-json-body response)
          :else response)))

(defn request->response
  [request-schema response-schema request]
  (if-let [explanation (me/humanize (m/explain request-schema request))]
    (error {:explanation explanation
            :type :invalid-request})
    (let [response (as-> request $
                     (m/encode request-schema $ mt/json-transformer)
                     (-request->response $))]
      (cond
        (http-predicates/client-error? response) (error (select-keys response [:body :status]))
        (http-predicates/server-error? response) (error (select-keys response [:body :status]))
        (http-predicates/success? response) (let [response (m/decode response-schema response
                                                                     (mt/transformer
                                                                      mt/strip-extra-keys-transformer
                                                                      mt/json-transformer))]
                                              (if (m/validate response-schema response)
                                                (ok response)
                                                (error (me/humanize (m/explain response-schema response)))))
        :else (error "Unexpected response status")))))
