-- The 4 desks from docs/generate_ada_deals.py's DESKS mapping. allowed_fgts uses
-- "FAMILY/GROUP/TYPE" keys built from the 8 product combos in PRODUCTS.
-- CMU-T&M deliberately omits EQD/FUT, a FGT its portfolios (DEMOHK LRP LMGT,
-- DEMOHK CMU MMTM) do trade, so BR-UC1-002 (FGT not found/allowed) has something to fire on.
INSERT INTO desk_profile
    (desk, sub_desk, book, allowed_fgts, desk_head, desk_delegate, desk_support)
VALUES
('HKB Rates Desk', 'HKB Rates Desk', 'TRADING', '["CURR/FXD/FXD", "CURR/FXD/SWLEG", "IRD/IRS", "IRD/LN_BR", "IRD/LFUT", "IRD/SFUT", "EQD/FUT", "IRD/BOND"]', 'Head HKB', 'Delegate HKB', 'Support HKB'),
('CMU-FX swap', 'CMU-FX swap', 'TRADING', '["CURR/FXD/FXD", "CURR/FXD/SWLEG"]', 'Head CMU-FX', 'Delegate CMU-FX', 'Support CMU-FX'),
('CMU-T&M', 'CMU-MX MMU (T&M Facing)', 'TRADING', '["IRD/LN_BR", "IRD/IRS"]', 'Head CMU-TM', 'Delegate CMU-TM', 'Support CMU-TM'),
('HKL Rates Desk', 'HKL Rates Desk', 'TRADING', '["CURR/FXD/FXD", "CURR/FXD/SWLEG", "IRD/IRS", "IRD/LN_BR", "IRD/LFUT", "IRD/SFUT", "EQD/FUT", "IRD/BOND"]', 'Head HKL', 'Delegate HKL', 'Support HKL');
