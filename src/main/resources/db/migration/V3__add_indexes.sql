CREATE INDEX idx_jobs_claim
    ON jobs (
             status,
             available_at,
             priority DESC,
             created_at ASC
        );


CREATE INDEX idx_jobs_expired_leases
    ON jobs (
             status,
             lease_expires_at
        );


CREATE INDEX idx_job_executions_job
    ON job_executions (
                       job_id,
                       attempt_number
        );


CREATE UNIQUE INDEX idx_jobs_idempotency
    ON jobs (idempotency_key)
    WHERE idempotency_key IS NOT NULL;