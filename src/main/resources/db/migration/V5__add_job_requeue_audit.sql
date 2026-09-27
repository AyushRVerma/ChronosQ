CREATE TABLE job_requeues (
    id UUID PRIMARY KEY,
    source_job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    new_job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    actor VARCHAR(200) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_job_requeues_source UNIQUE (source_job_id),
    CONSTRAINT uq_job_requeues_new UNIQUE (new_job_id)
);

CREATE INDEX idx_job_requeues_created_at ON job_requeues (created_at DESC);
