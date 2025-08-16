(ns piotr-yuxuan.service-template.secret)

(deftype Secret [^String value]
  Object
  (toString [_] "***")
  ;; Overrides `equals` and `hashCode` to ensure correct a
  ;; Clojure-like, value-based behaviour in hash-based data structures
  ;; such as maps and sets.
  (equals [_ other] (->> (.value ^Secret other) (= value) (and (instance? Secret other))))
  (hashCode [_] (.hashCode value)))

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
  "`clj-http.client` middleware for outgoing requests."
  [client]
  (fn
    ([req] (client (-reveal-token req)))
    ([req respond raise] (client (-reveal-token req) respond raise))))
