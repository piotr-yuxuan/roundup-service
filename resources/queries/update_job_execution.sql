UPDATE roundup_job_execution
SET savings_goal_uid = ?,
    round_up_amount_in_minor_units = ?,
    status = ?
WHERE account_uid = ?
  AND calendar_year = ?
  AND calendar_week = ?
RETURNING *;
