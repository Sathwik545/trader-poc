CREATE TABLE row_error (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id      BIGINT        NOT NULL,
    `row_number` INT          NOT NULL,
    message     VARCHAR(1000) NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_row_error_run FOREIGN KEY (run_id) REFERENCES pipeline_run (id),
    KEY idx_row_error_run_id (run_id)
) ENGINE = InnoDB;
