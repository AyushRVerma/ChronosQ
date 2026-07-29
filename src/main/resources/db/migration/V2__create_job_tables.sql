CREATE TABLE jobs (
                      id UUID PRIMARY KEY,

                      queue_name VARCHAR(100) NOT NULL,
                      job_type VARCHAR(100) NOT NULL,
                      payload JSONB NOT NULL,

                      status VARCHAR(30) NOT NULL,
                      priority INTEGER NOT NULL DEFAULT 0,

                      available_at TIMESTAMPTZ NOT NULL,

                      schedule_type VARCHAR(30) NOT NULL,
                      interval_seconds BIGINT,

                      attempt_count INTEGER NOT NULL DEFAULT 0,
                      max_attempts INTEGER NOT NULL DEFAULT 3,

                      idempotency_key VARCHAR(200),

                      locked_by VARCHAR(100),
                      lease_expires_at TIMESTAMPTZ,

                      timeout_seconds INTEGER NOT NULL DEFAULT 60,

                      created_at TIMESTAMPTZ NOT NULL,
                      updated_at TIMESTAMPTZ NOT NULL,
                      completed_at TIMESTAMPTZ,

                      version BIGINT NOT NULL DEFAULT 0
);


CREATE TABLE job_executions (
                                id UUID PRIMARY KEY,

                                job_id UUID NOT NULL,
                                worker_id VARCHAR(100) NOT NULL,
                                attempt_number INTEGER NOT NULL,

                                status VARCHAR(30) NOT NULL,

                                started_at TIMESTAMPTZ NOT NULL,
                                finished_at TIMESTAMPTZ,

                                duration_ms BIGINT,

                                error_type VARCHAR(200),
                                error_message TEXT,

                                CONSTRAINT fk_job_executions_job
                                    FOREIGN KEY (job_id)
                                        REFERENCES jobs(id)
);


CREATE TABLE worker_nodes (
                              worker_id VARCHAR(100) PRIMARY KEY,

                              instance_name VARCHAR(200),
                              status VARCHAR(30) NOT NULL,

                              last_heartbeat_at TIMESTAMPTZ NOT NULL,
                              started_at TIMESTAMPTZ NOT NULL
);