(ns piotr-yuxuan.service-template.http
  "Provide HTTP request and response utilities with JSON handling,
  schema validation via Malli, and structured error handling. It
  includes functions to encode request bodies, decode JSON responses,
  and enforce schemas for both requests and upstream responses."
  (:require
   [clj-http.client :as http]
   [com.brunobonacci.mulog :as log]
   [jsonista.core :as j]
   [malli.core :as m]
   [malli.error :as me]
   [malli.transform :as mt]
   [piotr-yuxuan.service-template.exception :as st.exception]
   [reitit.ring.malli]
   [ring.util.http-predicates :as http-predicates]
   [ring.util.http-response :as http-response]
   [ring.util.http-status :as http-status]
   [ring.util.response :as response]
   [safely.core :refer [safely-fn]])
  (:import
   (com.fasterxml.jackson.core JsonParseException)))

(defn write-json-body
  "Convert the `:body` of a request map to a JSON string, returning a
  400 error on malformed input."
  [{:keys [body] :as request}]
  (try
    (if body
      (update request :body j/write-value-as-string jsonista.core/keyword-keys-object-mapper)
      request)
    (catch JsonParseException _
      {:status http-status/bad-request
       :body "Malformed JSON body"})))

(defn read-json-body
  "Parse the JSON `:body` of a response map into Clojure data, returning
  a 500 error on malformed JSON."
  [{:keys [body] :as response}]
  (try
    (if (and (string? body) (seq body))
      (update response :body j/read-value jsonista.core/keyword-keys-object-mapper)
      response)
    (catch JsonParseException _
      {:status http-status/internal-server-error
       :body "Malformed JSON body"})))

(defn -request->response
  "Execute an HTTP request using clj-http as a client, handling network
  errors and JSON decoding, and returning a Ring-style response map."
  [request]
  (let [response (safely-fn
                  (fn []
                    (log/trace ::http/request
                      [:method (:method request) :url (:url request)]
                      (http/request (assoc (write-json-body request)
                                           :throw-exceptions false
                                           :ignore-unknown-host? true))))
                  :circuit-breaker ::http
                  :retry-delay [:random-exp-backoff :base 300 :+/- 0.35 :max 25000]
                  :max-retries 5
                  :tracking-capture (fn [r] {:http-status (:http-status r)}))
        content-type (some-> response (response/find-header "content-type") val)
        json-body? (some->> content-type (re-find #"(?i)application/json"))]
    (cond (not response) (http-response/bad-request {:error "Unknown host."})
          (and (:body response) json-body?) (read-json-body response)
          :else response)))

(defn request->response
  "Validate the request against a schema, execute the request, validate
  the response against a schema, and short-circuit with structured
  exceptions on any validation or HTTP error."
  [request-schema response-schema request]
  (if-let [explanation (me/humanize (m/explain request-schema request))]
    (throw (ex-info "An upstream request doesn't conform to its excepted schema."
                    {:type ::st.exception/short-circuit
                     :body {:request (select-keys request [:body :status])
                            :explanation explanation}}))
    (let [response (as-> request $
                     (m/encode request-schema $ mt/json-transformer)
                     (-request->response $))]
      (cond
        (http-predicates/client-error? response)
        (throw (ex-info "Upstream client error"
                        {:type ::st.exception/short-circuit
                         :body {:request (select-keys request [:method :url :body :status :query-params])
                                :response (select-keys response [:body :status])}}))

        (http-predicates/server-error? response)
        (throw (ex-info "Upstream server error"
                        {:type ::st.exception/short-circuit
                         :status http-status/bad-gateway
                         :body {:request (select-keys request [:method :url :body :status])
                                :response (select-keys response [:body :status])}}))

        (http-predicates/success? response)
        (let [response (m/decode response-schema response
                                 (mt/transformer
                                  mt/strip-extra-keys-transformer
                                  mt/json-transformer))]
          (when-let [explanation (me/humanize (m/explain response-schema response))]
            (throw (ex-info "An upstream response doesn't conform to its expected schema."
                            {:type ::st.exception/short-circuit
                             :body {:request (select-keys request [:method :url :body :status :query-params])
                                    :response (select-keys response [:body :status])
                                    :explanation explanation}})))
          response)

        :else
        (throw (ex-info "Unexpected upstream response"
                        {:type ::st.exception/short-circuit
                         :body {:request (select-keys request [:method :url :body :status :query-params])
                                :response (select-keys response [:body :status])}}))))))
