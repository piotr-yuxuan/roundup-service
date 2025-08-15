ALTER TABLE roundup_job_execution
ALTER COLUMN round_up_amount_in_minor_units TYPE NUMERIC(12,0)
USING round_up_amount_in_minor_units::NUMERIC(12,0);
