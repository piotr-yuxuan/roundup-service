(ns piotr-yuxuan.service-template.math-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [piotr-yuxuan.service-template.math :refer [ceiling-rounding round-up-difference]]
   [reitit.ring.malli]))

(deftest test-ceiling-rounding
  (testing "Precondition check"
    (is (thrown? AssertionError (ceiling-rounding -1 100))))

  (testing "Actual use case: rounding up to 10^2."
    (is (= 1300 (ceiling-rounding 2 1300)))
    (is (= 1300 (ceiling-rounding 2 1299)))
    (is (= 1300 (ceiling-rounding 2 1209)))
    (is (= 200 (ceiling-rounding 2 200)))
    (is (= 200 (ceiling-rounding 2 199)))
    (is (= 200 (ceiling-rounding 2 101)))
    (is (= 100 (ceiling-rounding 2 100)))
    (is (= 100 (ceiling-rounding 2 99)))
    (is (= 100 (ceiling-rounding 2 9)))
    (is (= 100 (ceiling-rounding 2 1)))
    (is (= 0 (ceiling-rounding 2 0))))

  (testing "No rounding needed (already at target)"
    (is (= 100 (ceiling-rounding 2 100)))
    (is (= 0 (ceiling-rounding 1 0)))
    (is (= -300 (ceiling-rounding 2 -300))))

  (testing "Positive rounding up"
    (is (= 200 (ceiling-rounding 2 143)))
    (is (= 1500 (ceiling-rounding 2 1444)))
    (is (= 130 (ceiling-rounding 1 123)))
    (is (= 42 (ceiling-rounding 0 42))))

  (testing "Negative numbers — still rounding towards +∞"
    ;; Ceiling rounds towards +∞, so -101 → -100
    (is (= -100 (ceiling-rounding 2 -101)))
    (is (= -10 (ceiling-rounding 1 -12)))
    (is (= -900 (ceiling-rounding 2 -999))))

  (testing "Large numbers"
    (is (= 123000000 (ceiling-rounding 6 123000000)))
    (is (= 124000000 (ceiling-rounding 6 123456789))))

  (testing "Scale = 0 (units)"
    (is (= 42 (ceiling-rounding 0 42)))
    (is (= 43 (ceiling-rounding 0 43)))
    (is (= -42 (ceiling-rounding 0 -42)))
    (is (= -41 (ceiling-rounding 0 -41)))))

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
