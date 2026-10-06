# Synthetic ADA deal data

- `ada_deals_synthetic_10000.csv`: 10,000 fictitious trades × 121 columns, in the column order of the Trader Mandate report photos (report_1..7).
- `ada_deal_columns.csv`: position, field, data type (from ada_deal_schema photos schema_1..3, rows 2-122).
- `generate_ada_deals.py`: regenerate with `python3 generate_ada_deals.py [rows] [out.csv] [seed]` (stdlib only, seed 42 by default).

Formats: epoch fields are integer **microseconds** UTC (as in the source); Binary fields are Python bytes literals like `b'\x00\x00\x00\x00\x00'` (as in the source); M_TP_TIMSYS is HH:MM:SS; COB_DATE is an Integer yyyymmdd (the schema says Integer; the report displays d/m/yy). M_EXT_REF is numeric only because the schema says Integer, but the real report also has text refs like `MAKER…-…`.

Trade dates run Jul–Sep 2026. All names are invented: traders TRD0001–TRD0040, portfolios `DEMOHK …`, counterparties `ALPHA BANK LDN` etc. No rows copied from the photos.

Seeded rule cases (so the detection rules have something to find):
- ~4% trader IDs on the rules' exclusion list (BATCHUSER, AUTOJOB, SETL…, SYSTEM…), ~3% unknown traders (UNKNOWN01–05)
- ~3% portfolios with no desk (`DEMOHK ORPHAN 01`, `DEMOSG TEST PF`): BR-UC1-001
- ~8% booked 19:00–23:59 (BUSINESS_HOURS = N): BR-UC1-010
- ~5% OFF_PREMISES = Y: BR-UC1-011
- ~25% internal deals (counterparty = another portfolio code, or a "BONDS" book with M_CTP_TYPE = Internal Party): BR-FD-002
