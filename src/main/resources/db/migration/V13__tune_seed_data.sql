-- Seed corrections found when the stored 10k trades were checked against the profiles.

-- BR-FD-003 names the country in full.
UPDATE portfolio SET country = 'Hong Kong' WHERE country = 'HK';

-- Only CMU-T&M should miss a product it trades (EQD/FUT), so BR-UC1-002 fires on a small,
-- explainable set. The synthetic file books every product on every portfolio, so the other
-- desks must allow all eight.
UPDATE desk_profile
   SET allowed_fgts = '["CURR/FXD/FXD", "CURR/FXD/SWLEG", "IRD/IRS", "IRD/LN_BR", "IRD/LFUT", "IRD/SFUT", "EQD/FUT", "IRD/BOND"]'
 WHERE desk = 'CMU-FX swap';

UPDATE desk_profile
   SET allowed_fgts = '["CURR/FXD/FXD", "CURR/FXD/SWLEG", "IRD/IRS", "IRD/LN_BR", "IRD/LFUT", "IRD/SFUT", "IRD/BOND"]'
 WHERE desk = 'CMU-T&M';

-- TRD0005/06 were missing DEMOHK IN FI SY, which the scope filter removes, so their narrower
-- mandate could never trigger BR-UC1-005. They now miss an in-scope portfolio instead.
UPDATE trader_profile
   SET allowed_portfolios = '["DEMOHK IRD DY", "DEMOHK IRDEXO CK", "DEMOHK CMU SWTM", "DEMOHK LRP LMGT", "DEMOHK CMU MMTM", "DEMOHK IN FI SY"]'
 WHERE one_bank_id IN ('TRD0005', 'TRD0006');
