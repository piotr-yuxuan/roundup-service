(ns piotr-yuxuan.service-template.api-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [piotr-yuxuan.service-template.api :as api]
   [ring.util.http-status :as http-status]
   [piotr-yuxuan.service-template.core :as core])
  (:import
   (java.time Instant ZoneId)))

(deftest year+week-number->interval-test
  (testing "week starts on Monday"
    (testing "week 33 of year 2025 starts on Monday 11th August"
      (is (= {:min-timestamp (Instant/parse "2024-08-11T23:00:00Z")
              :max-timestamp (Instant/parse "2024-08-18T23:00:00Z")}
             (api/year+week-number->interval 2024 33 (ZoneId/of "Europe/London"))
             (api/year+week-number->interval 2024 33)))
      ;; 2025-08-11 00:00 PDT = 2025-08-11T07:00:00Z
      ;; 2025-08-18 00:00 PDT = 2025-08-18T07:00:00Z
      (is (= {:min-timestamp (Instant/parse "2025-08-11T07:00:00Z")
              :max-timestamp (Instant/parse "2025-08-18T07:00:00Z")}
             (api/year+week-number->interval 2025 33 (ZoneId/of "America/Los_Angeles"))))))
  (testing "week 13 of 2025 includes the DST spring-forward (Sunday 30th March)"
    (is (= {:min-timestamp (Instant/parse "2025-03-24T00:00:00Z")
            :max-timestamp (Instant/parse "2025-03-30T23:00:00Z")}
           (api/year+week-number->interval 2025 13 (ZoneId/of "Europe/London"))
           (api/year+week-number->interval 2025 13)))
    ;; Starts Monday 24th March at 00:00 PDT = 2025-03-24T07:00:00Z
    ;; Ends Monday 31st March at 00:00 PDT = 2025-03-31T07:00:00Z
    (is (= {:min-timestamp (Instant/parse "2025-03-24T07:00:00Z")
            :max-timestamp (Instant/parse "2025-03-31T07:00:00Z")}
           (api/year+week-number->interval 2025 13 (ZoneId/of "America/Los_Angeles")))))
  (testing "week 43 of 2025 includes the DST fall-back (Sunday 26th October)"
    (is (= {:min-timestamp (Instant/parse "2025-10-19T23:00:00Z")
            :max-timestamp (Instant/parse "2025-10-27T00:00:00Z")}
           (api/year+week-number->interval 2025 43 (ZoneId/of "Europe/London"))
           (api/year+week-number->interval 2025 43)))
    ;; Starts Monday 20th Oct at 00:00 PDT = 2025-10-20T07:00:00Z
    ;; Ends Monday 27th Oct at 00:00 PDT (still DST, fall back happens 2 Nov) = 2025-10-27T07:00:00Z
    (is (= {:min-timestamp (Instant/parse "2025-10-20T07:00:00Z")
            :max-timestamp (Instant/parse "2025-10-27T07:00:00Z")}
           (api/year+week-number->interval 2025 43 (ZoneId/of "America/Los_Angeles"))))))

(deftest roundup-handler-test
  (testing "bad request if week not in the past"
    (let [now (Instant/parse "2025-01-01T00:00:00Z")]
      (is (= {:status http-status/bad-request
              :body {:message "Week should be in the past.",
                     :args {:savings-goal-name nil,
                            :min-timestamp (Instant/parse "2025-10-19T23:00:00Z")
                            :max-timestamp (Instant/parse "2025-10-27T00:00:00Z")
                            :now (Instant/parse "2025-01-01T00:00:00Z")}}}
             (api/roundup-handler {} now {:parameters {:body {:calendar-year 2025 :calendar-week 43}}})))))
  (testing "calls core/job for a week in the past"
    (let [now (Instant/parse "2025-08-28T13:37:00Z")
          expected-body {:message "Success!"}]
      (with-redefs [core/job (constantly expected-body)]
        (is (= {:status http-status/ok
                :body expected-body}
               (api/roundup-handler {}
                                    now
                                    {:parameters {:body {:calendar-year 2025
                                                         :calendar-week 30}}})))))))
