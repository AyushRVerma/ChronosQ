-- Existing attempts cannot be reconstructed reliably after available_at changes.
-- Leave their snapshot NULL; new claims capture the due time atomically.
ALTER TABLE job_executions
    ADD COLUMN scheduled_available_at TIMESTAMPTZ;
