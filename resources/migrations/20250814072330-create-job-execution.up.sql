CREATE TABLE roundup_job_execution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    week_start_date DATE NOT NULL,
    account_uid UUID,
    savings_goal_uid UUID,
    round_up_amount_in_minor_units NUMERIC(12, 0),

    CONSTRAINT unique_week_per_account UNIQUE (week_start_date, account_uid)
);
