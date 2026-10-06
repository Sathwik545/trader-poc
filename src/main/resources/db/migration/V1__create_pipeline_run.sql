CREATE TABLE pipeline_run (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name       VARCHAR(255)        NULL,
    status          VARCHAR(20)         NOT NULL DEFAULT 'RUNNING',
    started_at      TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at        TIMESTAMP           NULL,
    rows_read       INT                 NOT NULL DEFAULT 0,
    rows_stored     INT                 NOT NULL DEFAULT 0,
    rows_failed     INT                 NOT NULL DEFAULT 0,
    rows_in_scope   INT                 NOT NULL DEFAULT 0,
    tags_per_rule   JSON                NULL,
    created_at      TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB;
