-- Match the worker's queue-specific claim predicate. The older claim index
-- remains useful to the cross-queue scheduler promotion query.
CREATE INDEX idx_jobs_queue_claim
    ON jobs (status, queue_name, available_at, priority DESC, created_at ASC);

CREATE INDEX idx_jobs_dashboard_recent
    ON jobs (updated_at DESC, id);

CREATE INDEX idx_jobs_dashboard_completed
    ON jobs (completed_at)
    WHERE status = 'SUCCEEDED';

CREATE INDEX idx_jobs_running_worker
    ON jobs (locked_by)
    WHERE status = 'RUNNING';

CREATE INDEX idx_job_executions_dashboard_started
    ON job_executions (started_at);
