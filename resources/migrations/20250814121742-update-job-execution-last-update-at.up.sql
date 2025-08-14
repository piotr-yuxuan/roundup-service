ALTER TABLE roundup_job_execution
ADD COLUMN IF NOT EXISTS last_update_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
--;;
CREATE OR REPLACE FUNCTION update_last_update_at_column()
RETURNS TRIGGER AS $$
BEGIN
   NEW.last_update_at = NOW();
   RETURN NEW;
END;
$$ LANGUAGE plpgsql;
--;;
CREATE TRIGGER roundup_job_execution_update_trigger
BEFORE UPDATE ON roundup_job_execution
FOR EACH ROW
EXECUTE FUNCTION update_last_update_at_column();
