CREATE TABLE detection_rule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    br_ref          VARCHAR(20)   NOT NULL COMMENT 'e.g. BR-UC1-005',
    tag             VARCHAR(100)  NOT NULL COMMENT 'e.g. TCTM001_PORTFOLIO_NOT_ALLOWED',
    category        VARCHAR(100)  NULL,
    blotter_ref     VARCHAR(10)   NULL COMMENT 'DQ or PT',
    condition_spel  VARCHAR(2000) NOT NULL,
    workflow_role   VARCHAR(50)   NULL,
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order      INT           NOT NULL DEFAULT 0,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uq_detection_rule_tag (tag)
) ENGINE = InnoDB;
