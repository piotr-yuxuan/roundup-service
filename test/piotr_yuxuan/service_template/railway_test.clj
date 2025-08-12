(ns piotr-yuxuan.service-template.railway-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [piotr-yuxuan.service-template.railway :as railway]))

(deftest ok-test
  (is (= {:ok 42} (railway/ok 42)))
  (is (= {:ok "foo"} (railway/ok "foo"))))

(deftest error-test
  (is (= {:error :fail} (railway/error :fail)))
  (is (= {:error {:reason "bad"}} (railway/error {:reason "bad"}))))

(deftest success?-test
  (is (true? (railway/success? (railway/ok 1))))
  (is (false? (railway/success? (railway/error "err"))))
  (testing "only explicit success, no neutral maps"
    (is (false? (railway/success? {})))))

(deftest bind-test
  (testing "pass value forward"
    (is (-> (railway/ok 1)
            (railway/bind (comp railway/ok inc))
            (railway/bind (comp railway/ok inc))
            (= {:ok 3}))))
  (testing "short-circuit on error"
    (is (-> (railway/ok 1)
            (railway/bind (comp railway/ok inc))
            (railway/bind (constantly (railway/error :bad)))
            (railway/bind (comp railway/ok inc))
            (= {:error :bad})))))
