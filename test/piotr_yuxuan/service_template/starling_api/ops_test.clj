(ns piotr-yuxuan.service-template.starling-api.ops-test
  (:require
   [clojure.test :refer [deftest is]]
   [malli.generator :as mg]
   [piotr-yuxuan.service-template.starling-api.ops :as ops]))

(deftest get-accounts-test
  (doseq [response-body (take 15 (mg/generate ops/GetAccountResponse))]
    (with-redefs [ops/request->response
                  (fn [req]
                    ;; capture request for assertions
                    (is (= :get (:method req)))
                    (is (re-find #"localhost:80/v2/accounts" (:url req)))
                    (is (contains? (:headers req) "authorization") "No authorization header.")
                    (is (= "Bearer my-token" (get-in req [:headers "authorization"])) "Incorrect authorization token")
                    {:body response-body})]
      (let [accounts (ops/get-accounts
                      {::ops/api-base "http://localhost:80"}
                      {:token "my-token"})]
        (is (= (-> response-body :accounts) accounts))))))
