(ns piotr-yuxuan.service-template.starling-api.entity-test
  (:require
   [clojure.test :refer [deftest is]]
   [piotr-yuxuan.service-template.starling-api.entity :as entity]
   [malli.core :as m]))

(deftest non-neg-value-test
  (is (m/validate entity/NonNegInt 1))
  (is (m/validate entity/NonNegInt 1M))
  (is (not (m/validate entity/NonNegInt 1.5)))
  (is (not (m/validate entity/NonNegInt 1.5M)))
  (is (m/validate entity/NonNegInt 0))
  (is (m/validate entity/NonNegInt 0M))
  (is (m/validate entity/NonNegInt -0M))
  (is (not (m/validate entity/NonNegInt 0.0M)))
  (is (not (m/validate entity/NonNegInt -0.0M)))
  (is (not (m/validate entity/NonNegInt -1)))
  (is (not (m/validate entity/NonNegInt -1M)))
  (is (not (m/validate entity/NonNegInt -1.5)))
  (is (not (m/validate entity/NonNegInt -1.5M))))
