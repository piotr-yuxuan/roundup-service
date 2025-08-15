(ns piotr-yuxuan.service-template.math-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [malli.core :as m]
   [piotr-yuxuan.service-template.math :refer [NonNegInt64 NonNegInt64-max
                                               round-up-difference]]
   [reitit.ring.malli])
  (:import
   (java.lang IllegalArgumentException)))

(deftest NonNegInt64-test
  (is (m/validate NonNegInt64 1))
  (is (not (m/validate NonNegInt64 1.5)))
  (is (m/validate NonNegInt64 0))
  (is (not (m/validate NonNegInt64 -1)))
  (is (not (m/validate NonNegInt64 -1.5)))

  (testing "standard Clojure numerics and API"
    (is (m/validate (m/schema NonNegInt64) (long 1513311315315361536)))
    (is (m/validate (m/schema NonNegInt64) (int 15133113)))
    (is (m/validate (m/schema NonNegInt64) (long 15133113))))
  (testing "Does not autobox"
    (is (not (m/validate (m/schema NonNegInt64) (inc (bigint NonNegInt64-max)))))
    (is (not (m/validate (m/schema NonNegInt64) (bigdec 1))))
    (is (not (m/validate (m/schema NonNegInt64) (bigint 1))))))

(deftest hardcoded-test-from-assignment
  (testing "no float, no double"
    (is (thrown-with-msg? IllegalArgumentException
                          #"Unsupported type for n: class java.lang.Float"
                          (round-up-difference 0 (float 1.58))))
    (is (thrown-with-msg? IllegalArgumentException
                          #"Unsupported type for n: class java.lang.Double"
                          (round-up-difference 0 (double 1.58)))))
  (testing "with longs"
    (is (->> [435 520 87]
             (map (comp (partial round-up-difference 2) long))
             (reduce +)
             (= 158))))
  (testing "with ints"
    (is (->> [435 520 87]
             (map (comp (partial round-up-difference 2) long))
             (reduce +)
             (= 158)))))

(deftest test-round-up-difference
  (testing "Exact multiples return zero"
    (is (= 0 (round-up-difference 2 100)))
    (is (= 0 (round-up-difference 1 50)))
    (is (= 0 (round-up-difference 0 7)))
    (is (= 0 (round-up-difference 3 -2000))))

  (testing "Positive numbers rounding up"
    (is (= 57 (round-up-difference 2 943)))
    (is (= 20 (round-up-difference 2 80)))
    (is (= 7 (round-up-difference 1 3)))
    (is (= 5 (round-up-difference 1 25)))
    (is (= 65 (round-up-difference 2 435)))
    (is (= 0 (round-up-difference 0 6)))
    (is (= 0 (round-up-difference 0 3))))

  (testing "Negative numbers"
    (is (= 1 (round-up-difference 2 -101)))
    (is (= 99 (round-up-difference 2 -199)))
    (is (= 0 (round-up-difference 2 -100)))
    (is (= 2 (round-up-difference 1 -12)))
    (is (= 9 (round-up-difference 1 -19)))
    (is (= 0 (round-up-difference 0 -7))))

  (testing "Large numbers"
    (is (= 0 (round-up-difference 6 123000000)))
    (is (= 43211 (round-up-difference 5 123456789)))))
