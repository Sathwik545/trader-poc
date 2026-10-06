CREATE TABLE desk_profile (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    desk            VARCHAR(100) NOT NULL,
    sub_desk        VARCHAR(100) NOT NULL,
    book            VARCHAR(20)  NULL COMMENT 'BANKING or TRADING',
    allowed_fgts    JSON         NULL COMMENT 'array of family/group/type keys the desk may trade',
    desk_head       VARCHAR(100) NULL,
    desk_delegate   VARCHAR(100) NULL,
    desk_support    VARCHAR(100) NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uq_desk_profile_desk_subdesk (desk, sub_desk)
) ENGINE = InnoDB;
