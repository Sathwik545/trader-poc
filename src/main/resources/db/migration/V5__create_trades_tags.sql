-- One row per rule a trade fails. trade_id intentionally has no DB foreign key: trades is
-- partitioned by trade_date, and a partitioned table's primary key must include the
-- partition column, so trades.id alone is not a unique-indexable target for a FK.
CREATE TABLE trades_tags (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    trade_id    BIGINT    NOT NULL,
    tag_id      BIGINT    NOT NULL,
    run_id      BIGINT    NOT NULL,
    details     JSON      NULL COMMENT 'the values that failed the rule',
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_trades_tags_tag FOREIGN KEY (tag_id) REFERENCES detection_rule (id),
    KEY idx_trades_tags_trade_id (trade_id),
    KEY idx_trades_tags_tag_id (tag_id),
    KEY idx_trades_tags_run_id (run_id)
) ENGINE = InnoDB;
