(ns piotr-yuxuan.service-template.secret-test
  (:require
   [clj-http.client :as http]
   [clj-http.core :as core.http]
   [clojure.test :refer [deftest is testing]]
   [piotr-yuxuan.service-template.secret :as secret]
   [reitit.ring.malli]
   [ring.util.http-predicates :as http-predicates]
   [ring.util.http-status :as http-status])
  (:import
   (piotr_yuxuan.service_template.secret Secret)))

(deftest request->response-test
  (testing "Noop if no token"
    (with-redefs [core.http/request (constantly {:status http-status/ok})]
      (http/with-additional-middleware [secret/secret-token-reveal]
        (is (-> {:method :get
                 :url "http://example.com"}
                http/request
                http-predicates/success?)))))
  (testing "Noop if token but existing header"
    (let [existing-token "existing-token"]
      (with-redefs [core.http/request (fn [{:keys [headers]}]
                                        (is (-> headers (get "authorization") (= (str "Bearer " existing-token))))
                                        {:status http-status/ok})]
        (http/with-additional-middleware [secret/secret-token-reveal]
          (is (-> {:method :get
                   :url "http://example.com"
                   :headers {"authorization" (str "Bearer " existing-token)
                             :token "ignored-token"}}
                  http/request
                  http-predicates/success?))))))
  (testing "Noop if token is not a secret"
    (let [my-token "my-token"]
      (with-redefs [core.http/request (fn [{:keys [headers]}]
                                        (is (-> headers (get "authorization") (= "Bearer ")))
                                        {:status http-status/ok})]
        (http/with-additional-middleware [secret/secret-token-reveal]
          (is (-> {:method :get
                   :url "http://example.com"
                   :headers {"authorization" "Bearer "
                             :token my-token}}
                  http/request
                  http-predicates/success?))))))
  (testing "insert token"
    (let [my-token "my-token"]
      (with-redefs [core.http/request (fn [{:keys [headers]}]
                                        (is (-> headers (get "authorization") (= (str "Bearer " my-token))))
                                        {:status http-status/ok})]
        (http/with-additional-middleware [secret/secret-token-reveal]
          (is (-> {:method :get
                   :url "http://example.com"
                   :headers {"authorization" "Bearer "
                             :token (Secret. my-token)}}
                  http/request
                  http-predicates/success?)))))))
