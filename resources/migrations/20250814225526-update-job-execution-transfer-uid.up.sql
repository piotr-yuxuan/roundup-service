ALTER TABLE roundup_job_execution
ADD COLUMN transfer_uid UUID UNIQUE DEFAULT gen_random_uuid();
--;;
UPDATE roundup_job_execution
SET transfer_uid = gen_random_uuid()
WHERE transfer_uid IS NULL;
--;;
ALTER TABLE roundup_job_execution
ALTER COLUMN transfer_uid SET NOT NULL;
--;;
-- We want to enforce idempotency, the database user used by the service can't update this column.
DO $$
DECLARE
    role_name text := current_user;
BEGIN
    EXECUTE format('REVOKE UPDATE (transfer_uid) ON roundup_job_execution FROM %I', role_name);
END
$$;
