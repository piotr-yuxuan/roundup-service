(ns piotr-yuxuan.service-template.starling-api.ops-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [malli.core :as m]
   [malli.generator :as mg]
   [malli.transform :as mt]
   [piotr-yuxuan.service-template.http :as st.http]
   [piotr-yuxuan.service-template.railway :refer [error ok]]
   [piotr-yuxuan.service-template.starling-api.entity :as entity]
   [piotr-yuxuan.service-template.starling-api.ops :as ops]
   [ring.util.http-status :as http-status]))

(deftest get-accounts-test
  (testing "happy path"
    (let [token (mg/generate [:string {:min 10 :max 15}])
          api-base (mg/generate [:string {:min 10 :max 15}])
          expected-request {:method :get
                            :url (str/join "/" [api-base "v2/accounts"])
                            :headers {"accept" "application/json"
                                      "authorization" (str "Bearer " token)}}
          expected-account (mg/generate entity/Account)]
      (with-redefs [st.http/-request->response (fn [request]
                                                 (is (= expected-request request))
                                                 {:status http-status/ok
                                                  :body {:accounts [(m/encode entity/Account expected-account mt/json-transformer)]}})]
        (is (= (ok {:accounts [expected-account]})
               (ops/get-accounts
                {::ops/api-base api-base}
                {:token token}))))))
  (testing "auth issue"
    (let [token (mg/generate [:string {:min 10 :max 15}])
          api-base (mg/generate [:string {:min 10 :max 15}])]
      (with-redefs [st.http/-request->response (constantly
                                                {:status http-status/forbidden
                                                 :body {:error_description "Could not validate provided access token"
                                                        :error "invalid_token"}})]
        (is (= (error {:body {:error_description "Could not validate provided access token",
                              :error "invalid_token"},
                       :status 403})
               (ops/get-accounts
                {::ops/api-base api-base}
                {:token token})))))))
