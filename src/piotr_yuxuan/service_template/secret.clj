(ns piotr-yuxuan.service-template.secret)

(deftype Secret [^String value]
  Object
  (toString [_] "***")
  ;; Overrides `equals` and `hashCode` to ensure correct a
  ;; Clojure-like, value-based behaviour in hash-based data structures
  ;; such as maps and sets.
  (equals [_ other] (->> (.value ^Secret other) (= value) (and (instance? Secret other))))
  (hashCode [_] (.hashCode value)))

(defn ->secret
  [^String x]
  (Secret. x))

(defn value
  [^Secret x]
  (.-value x))

(defn secret-token-hide
  "Ring middleware for incoming requests."
  [handler]
  (letfn [(hide-token [request]
            (update request :authorization
                    (fn [{:keys [token] :as authorization}]
                      (if (string? token)
                        (update authorization :token #(Secret. %))
                        authorization))))]
    (fn
      ([request]
       (handler (hide-token request)))
      ([request respond raise]
       (handler (hide-token request) respond raise)))))

(defn -reveal-token
  [{:keys [headers] :as request}]
  (if-let [token-value (and (some-> headers (get "authorization") (= "Bearer "))
                            (instance? Secret (:token headers))
                            (.-value ^Secret (:token headers)))]
    (-> request
        (update :headers dissoc :token)
        (assoc-in [:headers "authorization"] (str "Bearer " token-value)))
    request))

(defn secret-token-reveal
  "Middleware for `clj-http.client` that transparently replaces a
placeholder `Secret` token with a proper `Authorization` header, as
close as possible to the actual HTTP call.

**Rationale**

In many systems, secrets should not be materialised too early or
stored in plain text throughout the request lifecycle. Instead, they
should remain wrapped (here, in the
`piotr_yuxuan.service_template.secret.Secret` record) until the last
possible moment. This middleware ensures that:

- If no `:token` header is present, the request is passed through
  unchanged.
- If a plain string is provided under `:token`, nothing happens:
  only wrapped secrets are eligible for injection.
- If an `:authorization` header is already present and not exactly
  `\"Bearer \"`, it is preserved: the middleware never overrides an
  existing non-empty token.
- If the `:authorization` header is the placeholder value `\"Bearer
  \"` and the `:token` entry is a `Secret`, then the secret’s value is
  revealed and spliced into the header, producing `\"Bearer
  <token-value>\"`.
- After injection, the temporary `:token` header is removed, so only
  a standard `Authorization` header is sent over the wire.

**Example**

``` clojure
(http/with-additional-middleware [secret-token-reveal]
  (http/request {:method :get
                 :url \"http://example.com\"
                 :headers {\"authorization\" \"Bearer \"
                           :token (Secret. \"s3cr3t\")}}))
```
Results in an outgoing request with:
``` clojure
{\"authorization\" \"Bearer s3cr3t\"}
```"
  [client]
  (fn
    ([req] (client (-reveal-token req)))
    ([req respond raise] (client (-reveal-token req) respond raise))))
