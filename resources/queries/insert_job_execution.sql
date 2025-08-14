INSERT INTO roundup_job_execution(
    account_uid,
    savings_goal_uid,
    round_up_amount_in_minor_units,
    calendar_year,
    calendar_week
    -- Ignoring status, so it uses default value.
)
VALUES (?, ?, ?, ?, ?)
RETURNING *;
