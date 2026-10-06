-- The 7 DEMOHK iWork portfolios from docs/generate_ada_deals.py (PORTFOLIOS/DESKS), so trade
-- lookups by m_tp_pfolio match what's actually in the synthetic CSV.
-- DEMOHK ORPHAN 01 and DEMOSG TEST PF are deliberately NOT seeded here: BR-UC1-001 needs
-- portfolios referenced by trades but absent from this table.
-- DEMOHK IN FI SY gets mx_entity = 'iWork only' so the BR-FD-003 scope filter visibly
-- removes some of its trades.
INSERT INTO portfolio
    (portfolio_code, country, business_unit, classification, mx_entity, risk_book, aegis_desk, aegis_subdesk, owner, reviewer)
VALUES
('DEMOHK IRD DY', 'HK', 'GFM', 'TRADING', 'MXDEMOHK', 'BOOK-RATES-01', 'HKB Rates Desk', 'HKB Rates Desk', 'Owner A', 'Reviewer A'),
('DEMOHK IRDEXO CK', 'HK', 'GFM', 'TRADING', 'MXDEMOHK', 'BOOK-RATES-02', 'HKB Rates Desk', 'HKB Rates Desk', 'Owner A', 'Reviewer A'),
('DEMOHK FXD CK', 'HK', 'GFM', 'TRADING', 'MXDEMOHK', 'BOOK-FX-01', 'HKB Rates Desk', 'HKB Rates Desk', 'Owner B', 'Reviewer B'),
('DEMOHK CMU SWTM', 'HK', 'GFM', 'TRADING', 'MXDEMOHK', 'BOOK-FX-02', 'CMU-FX swap', 'CMU-FX swap', 'Owner C', 'Reviewer C'),
('DEMOHK LRP LMGT', 'HK', 'GFM', 'TRADING', 'MXDEMOHK', 'BOOK-TM-01', 'CMU-T&M', 'CMU-MX MMU (T&M Facing)', 'Owner D', 'Reviewer D'),
('DEMOHK CMU MMTM', 'HK', 'GFM', 'TRADING', 'MXDEMOHK', 'BOOK-TM-02', 'CMU-T&M', 'CMU-MX MMU (T&M Facing)', 'Owner D', 'Reviewer D'),
('DEMOHK IN FI SY', 'HK', 'GFM', 'TRADING', 'iWork only', 'BOOK-RATES-03', 'HKL Rates Desk', 'HKL Rates Desk', 'Owner E', 'Reviewer E');
