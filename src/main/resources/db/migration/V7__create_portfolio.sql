CREATE TABLE portfolio (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    portfolio_code  VARCHAR(50)  NOT NULL COMMENT 'matches trades.m_tp_pfolio',
    country         VARCHAR(10)  NULL,
    business_unit   VARCHAR(50)  NULL,
    classification  VARCHAR(50)  NULL,
    mx_entity       VARCHAR(50)  NULL COMMENT 'iWork entity code; BR-FD-003 scope criterion',
    risk_book       VARCHAR(50)  NULL,
    aegis_desk      VARCHAR(100) NULL COMMENT 'joins desk_profile.desk',
    aegis_subdesk   VARCHAR(100) NULL COMMENT 'joins desk_profile.sub_desk',
    owner           VARCHAR(100) NULL,
    reviewer        VARCHAR(100) NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uq_portfolio_code (portfolio_code)
) ENGINE = InnoDB;
