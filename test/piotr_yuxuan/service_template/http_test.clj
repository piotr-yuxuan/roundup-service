(ns piotr-yuxuan.service-template.http-test
  (:require
   [clj-http.client :as http]
   [clojure.test :refer [deftest is testing]]
   [jsonista.core :as j]
   [piotr-yuxuan.service-template.http :as st.http]
   [reitit.ring.malli]
   [ring.util.http-predicates :as http-predicates]
   [ring.util.http-status :as http-status]
   [ring.util.http-response :as http-response]))

(deftest -request->response-test
  (testing "success, JSON body"
    (with-redefs [http/request (constantly
                                {:status http-status/ok
                                 :headers {"content-type" "application/json"}
                                 :body (j/write-value-as-string {:ok true})})]
      (is (-> {:method :get
               :url "http://example.com"}
              st.http/-request->response
              :body
              (= {:ok true})))))

  (testing "success, no content"
    (with-redefs [http/request (constantly (http-response/no-content))]
      (is (-> {:method :get
               :url "http://example.com"}
              st.http/-request->response
              http-predicates/no-content?))))

  (testing "success, JSON body with charset"
    (with-redefs [http/request (fn [_req]
                                 {:status http-status/ok
                                  :headers {"content-type" "application/json; charset=utf-8"}
                                  :body (j/write-value-as-string {:ok true})})]
      (is (-> {:method :get
               :url "http://example.com"}
              st.http/-request->response
              :body
              (= {:ok true})))))

  (testing "success, non-JSON body"
    (with-redefs [http/request (constantly
                                {:status http-status/ok
                                 :headers {"content-type" "application/html; charset=utf-8"}
                                 :body "<html/>"})]
      (is (-> {:method :get
               :url "http://example.com"}
              st.http/-request->response
              :body
              (= "<html/>")))))

  (testing "error, unknown host"
    (with-redefs [http/request (fn [{:keys [ignore-unknown-host?]}]
                                 (is ignore-unknown-host?)
                                 nil)]
      (is (-> {:method :get
               :url "http://example.com"}
              st.http/-request->response
              (= (http-response/bad-request {:error "Unknown host."}))))))

  (testing "error, malformed JSON body"
    (with-redefs [http/request (constantly
                                {:status http-status/ok
                                 :headers {"content-type" "application/json"}
                                 :body "<html/>"})]
      (is (-> {:method :get
               :url "http://example.com"}
              st.http/-request->response
              (= {:status http-status/internal-server-error
                  :body "Malformed JSON body"})))))

  (testing "JSON error body"
    (with-redefs [http/request (constantly {:status http-status/bad-request
                                            :headers {"content-type" "application/json"}
                                            :body (j/write-value-as-string {:error "Entity not found."})})]
      (is (-> {:method :get
               :url "http://example.com"}
              st.http/-request->response
              (= {:status http-status/bad-request
                  :headers {"content-type" "application/json"}
                  :body {:error "Entity not found."}})))))

  (testing "client error"
    (with-redefs [http/request (constantly {:status http-status/bad-request
                                            :headers {"content-type" "application/json"}
                                            :body {:error "Entity not found."}})]
      (is (-> {:method :get
               :url "http://example.com"}
              st.http/-request->response
              (= {:status http-status/bad-request
                  :headers {"content-type" "application/json"}
                  :body {:error "Entity not found."}})))))

  (testing "server error"
    (with-redefs [http/request (constantly {:status http-status/internal-server-error
                                            :headers {"content-type" "application/json"}
                                            :body {:error "Internal error."}})]
      (is (-> {:method :get
               :url "http://example.com"}
              st.http/-request->response
              (= {:status http-status/internal-server-error
                  :headers {"content-type" "application/json"}
                  :body {:error "Internal error."}}))))))
