ALTER TABLE roundup_job_execution
    ADD COLUMN calendar_year INTEGER,
    ADD COLUMN calendar_week INTEGER CHECK (calendar_week BETWEEN 1 AND 53);
--;;
UPDATE roundup_job_execution
SET calendar_year = EXTRACT(YEAR FROM week_start_date)::INTEGER,
    calendar_week = EXTRACT(WEEK FROM week_start_date)::INTEGER;
--;;
ALTER TABLE roundup_job_execution
    DROP CONSTRAINT unique_week_per_account;
--;;
ALTER TABLE roundup_job_execution
    ADD CONSTRAINT unique_week_per_account
        UNIQUE (calendar_year, calendar_week, account_uid);
--;;
ALTER TABLE roundup_job_execution
    DROP COLUMN week_start_date;
