(ns piotr-yuxuan.service-template.math-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [piotr-yuxuan.service-template.math :refer [round-up-difference]]
   [reitit.ring.malli]))

(deftest hardcoded-test-from-assignment
  (let [expected 1.58M]
    (testing "with doubles between 1 and 10"
      (let [actual (->> [4.35 5.20 0.87]
                        (map (partial round-up-difference 0))
                        (reduce +))]
        (is (= expected actual))))
    (testing "with longs"
      (let [actual (->> [435 520 87]
                        (map (comp (partial round-up-difference 2)))
                        (reduce +))]
        (is (= expected (/ actual 100)))))))

(deftest test-round-up-difference
  (testing "Exact multiples return zero"
    (is (= 0M (round-up-difference 2 100)))
    (is (= 0M (round-up-difference 1 50)))
    (is (= 0M (round-up-difference 0 7)))
    (is (= 0M (round-up-difference 3 -2000))))

  (testing "Positive numbers rounding up"
    (is (= 57M (round-up-difference 2 943)))
    (is (= 20M (round-up-difference 2 80)))
    (is (= 7M (round-up-difference 1 3)))
    (is (= 5M (round-up-difference 1 25)))
    (is (= 65M (round-up-difference 2 435)))
    (is (= 65M (round-up-difference 2 435M)))
    (is (= 0M (round-up-difference 0 6)))
    (is (= 0M (round-up-difference 0 3))))

  (testing "Negative numbers"
    (is (= 1M (round-up-difference 2 -101)))
    (is (= 99M (round-up-difference 2 -199)))
    (is (= 0M (round-up-difference 2 -100)))
    (is (= 2M (round-up-difference 1 -12)))
    (is (= 9M (round-up-difference 1 -19)))
    (is (= 0M (round-up-difference 0 -7))))

  (testing "Large numbers"
    (is (= 0M (round-up-difference 6 123000000)))
    (is (= 43211M (round-up-difference 5 123456789)))))
