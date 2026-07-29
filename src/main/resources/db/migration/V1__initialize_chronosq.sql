CREATE TABLE chronosq_metadata (metadata_key VARCHAR(100) PRIMARY KEY, metadata_value VARCHAR(500) NOT NULL
);

INSERT INTO chronosq_metadata (metadata_key, metadata_value)VALUES ('schema_phase', '1');