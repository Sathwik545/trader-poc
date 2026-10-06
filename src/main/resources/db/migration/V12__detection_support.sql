-- Phase 2 additions: the ID/ED split per run, and per-rule expressions that produce the
-- "details" JSON written to trades_tags when a rule fires.
ALTER TABLE pipeline_run
    ADD COLUMN internal_deals INT NOT NULL DEFAULT 0 COMMENT 'trades classed ID by BR-FD-002',
    ADD COLUMN external_deals INT NOT NULL DEFAULT 0 COMMENT 'trades classed ED by BR-FD-002';

ALTER TABLE detection_rule
    ADD COLUMN details_spel JSON NULL COMMENT 'name -> SpEL expression, evaluated when the rule fires';
