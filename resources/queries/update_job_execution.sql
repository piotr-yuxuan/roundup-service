UPDATE roundup_job_execution
SET account_uid = ?,
    savings_goal_uid = ?,
    round_up_amount_in_minor_units = ?,
    calendar_year = ?,
    calendar_week = ?,
    status = COALESCE(?, status)
WHERE id = ?;
