CREATE TABLE trader_profile (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    one_bank_id         VARCHAR(50)  NOT NULL COMMENT 'matches trades.m_tp_trader',
    name                VARCHAR(255) NULL,
    aegis_role          VARCHAR(30)  NULL COMMENT 'TRADER, DESK SUPPORT, SALES, or null = no role found',
    licensed            BOOLEAN      NOT NULL DEFAULT TRUE,
    pre_rnf_approval    VARCHAR(1)   NULL COMMENT 'Y or N',
    hiring_manager      VARCHAR(255) NULL,
    allowed_portfolios  JSON         NULL COMMENT 'mandate: array of portfolio codes',
    allowed_fgts        JSON         NULL COMMENT 'mandate: array of family/group/type keys',
    allowed_currencies  JSON         NULL COMMENT 'mandate: array of currency codes',
    effective_date      DATE         NULL COMMENT 'mandate effective date',
    remote_allowed      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uq_trader_profile_one_bank_id (one_bank_id)
) ENGINE = InnoDB;
