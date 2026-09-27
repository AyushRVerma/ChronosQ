ALTER TABLE jobs
    ADD COLUMN cron_expression VARCHAR(200),
    ADD COLUMN cron_timezone VARCHAR(100),
    ADD COLUMN missed_execution_policy VARCHAR(30);

ALTER TABLE jobs
    ADD CONSTRAINT chk_jobs_cron_configuration CHECK (
        (
            schedule_type = 'CRON'
            AND interval_seconds IS NULL
            AND cron_expression IS NOT NULL
            AND cron_timezone IS NOT NULL
            AND missed_execution_policy IN (
                'SKIP',
                'RUN_ONCE_IMMEDIATELY',
                'CATCH_UP_ALL'
            )
        )
        OR
        (
            schedule_type <> 'CRON'
            AND cron_expression IS NULL
            AND cron_timezone IS NULL
            AND missed_execution_policy IS NULL
        )
    );

CREATE INDEX idx_jobs_due_cron
    ON jobs (status, schedule_type, available_at)
    WHERE schedule_type = 'CRON';
