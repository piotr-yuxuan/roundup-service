CREATE TYPE roundup_job_status AS ENUM (
    'running',
    'completed',
    'insufficient_funds',
    'failed'
);
--;;
ALTER TABLE roundup_job_execution
ADD COLUMN status roundup_job_status NOT NULL DEFAULT 'running';
