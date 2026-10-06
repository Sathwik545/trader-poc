# ada_deal_schema and sample report (transcribed from schema_1, report_1..7 photos)

## ada_deal_schema.csv (schema_1)
Two columns: Field, Data Type. schema_1 shows rows 2-47; rows 48-122 (schema_2, schema_3) are in /mnt/project-files/synthetic-data/ada_deal_columns.csv. 121 fields total, same order as the report columns. Column BG of the report is M_TP_NOMCUR.

| # | Field | Type |
|---|---|---|
| 2 | M_NB | Integer |
| 3 | M_CNT_TYPO | Text |
| 4 | M_CONTRACT | Integer |
| 5 | M_TRN_STATUS | Text |
| 6 | M_TP_DTETRN | Date/Time (epoch) |
| 7 | M_TP_DTEEXP | Date/Time (epoch) |
| 8 | M_TP_TRADER | Text |
| 9 | USER_NAME | Text |
| 10 | M_TRN_FMLY | Text |
| 11 | M_TRN_GRP | Text |
| 12 | M_TRN_TYPE | Text |
| 13 | MX_TYPOLOGY | Text |
| 14 | M_INSTRLBL | Text |
| 15 | M_TP_CNTRP | Text |
| 16 | M_TP_PFOLIO | Text |
| 17 | M_TP_ENTITY | Text |
| 18 | M_TP_TIMSYS | Time |
| 19 | M_TP_DTESYS | Date/Time (epoch) |
| 20 | M_EXE_TRADER | Text |
| 21 | M_LABEL | Text |
| 22 | M_VAL_LEVEL | Blank/undetermined |
| 23 | M_BRW_NOMU1 | Text |
| 24 | M_BRW_NOM1 | Integer |
| 25 | M_TP_IQTYS | Integer |
| 26 | M_BRW_NOMU2 | Text |
| 27 | M_BRW_NOM2 | Decimal |
| 28 | M_RATE_MARG0 | Decimal |
| 29 | M_TP_STRTGY | Blank/undetermined |
| 30 | M_SAL_TRADER | Blank/undetermined |
| 31 | M_SAL_MARG1 | Integer |
| 32 | M_SRC_SYSTEM | Text |
| 33 | M_EQ_PO | Binary |
| 34 | M_D_STRADER | Blank/undetermined |
| 35 | M_TP_GID | Integer |
| 36 | DESK_NAME | Blank/undetermined |
| 37 | SUB_DESK_NAME | Blank/undetermined |
| 38 | TRADING_DESK | Text |
| 39 | TRADING_SUBDESK | Text |
| 40 | M_PACKAGE | Binary |
| 41 | M_PC_CODE | Mixed/Text |
| 42 | M_TYPOLOGY | Integer |
| 43 | M_PKG_TYPO | Blank/undetermined |
| 44 | M_LABEL_CTPYMX | Integer |
| 45 | M_CTP_TYPE | Text |
| 46 | M_CIN_NO | Text |
| 47 | M_PAR_CNTRY | Text |

## Sample report (report_1..7)
File: `ASIAKCC_ASIAKCC_Trader_Mandate_Report_2026-07-01_2026-07-31__20260902_160547` (CSV opened in Excel, sheet ASIAKCC_ASIAKCC_Trader_Mandate_). One trader's July 2026 trades, ~47+ rows visible, columns A..DQ (~121 columns). This is real-looking production data; don't copy rows into code or fixtures. Use synthetic data built from the shapes below.

Column headers in order (A → DQ), as far as legible:
M_NB, M_CNT_TYPO, M_CONTRACT, M_TRN_STATUS, M_TP_DTETRN, M_TP_DTEEXP, M_TP_TRADER, USER_NAME, M_TRN_FMLY, M_TRN_GRP, M_TRN_TYPE, MX_TYPOLOGY, M_INSTRLBL, M_TP_CNTRP, M_TP_PFOLIO, M_TP_ENTITY, M_TP_TIMSYS, M_TP_DTESYS, M_EXE_TRADER, M_LABEL?, M_VAL_LEVEL?, M_BRW_NOMU1, M_BRW_NOM1, M_TP_IQTYS, M_BRW_NOMU2, M_BRW_NOM2, M_RATE_MARG0, M_TP_STRTGY, M_SAL_TRADER, M_SAL_MARG1, M_SRC_SYSTEM, M_EQ_PO, M_D_STRADER, M_TP_GID, DESK_NAME, SUB_DESK_NAME, TRADING_DESK, TRADING_SUBDESK, M_PACKAGE, M_PC_CODE, M_TYPOLOGY, M_PKG_TYPO, M_LABEL_CTPYMX, M_CTP_TYPE, M_CIN_NO, M_PAR_CNTRY, M_MX2_INSTR, M_INSTRUMENT, M_SE_CODE, M_ISIN, M_TRN_EXP, M_D_MATURITYDT, M_BRW_SDTE, M_XPFWFIXDT, M_D_VALUEDT, M_TP_BUY, M_TYPE, M_TP_NOMINAL, (BG cut off), M_PRICE, M_DISC_RATE0, M_DISC_RATE1, M_RATE_MARG1, M_EXT_REF, M_TP_CREATOR, M_VAL_STATUS, M_TP_INT, M_BCOMMENT0, M_BCOMMENT1, M_BCOMMENT2, M_SCOMMENT0, M_SCOMMENT1, M_SCOMMENT2, M_BPFOLIO, M_SPFOLIO, M_BRW_PR1, M_BRW_PR2, M_RATE_TYPE0, M_RATE_TYPE1, M_TP_RTMNDD0, M_TP_RTMNDD1, M_RATE_FACT0, M_RATE_FACT1, M_PL_INSCUR, M_MAR_CCY, M_BRW_CP, M_TP_FEE1, M_TP_FEE2, M_TP_FEE3, M_SE_ISST, M_ISSUER, M_SE_ISS, M_TP_FXCPCUR, M_RATING, TRADE_CATEG, CORE_TRN_FMLY, CORE_TRN_GRP, CORE_TRN_TYPE, CORE_BUY_SELL, CORE_TRADE_TYPE, CORE_TRADE_SUBTYPE, BUSINESS_HOURS, M_H_TRNSTS, M_DT_TS, M_USR_NAME, M_USR_ID, M_TP_STATUS2, M_H_BRWNOMU1, M_H_BRW_NOM1, M_H_BRWNOMU2, M_H_BRW_NOM2, M_TP_PRICE, OFF_PREMISES, M_M_TRADERID, M_T_TRADERID, DESTINATION_TRADER_ID, M_STAT_NAME, M_EVENT_TIME, M_LO_TIME, TRADE_CATEG_TYPE, COB_DATE.

Value shapes seen:
- Dates/times as epoch in **microseconds** (e.g. 1.78344E+15 ≈ mid-2026). TIMSYS as HH:MM:SS. COB_DATE as d/m/yy (14/8/26, 1/9/26).
- M_TRN_STATUS / M_TP_STATUS2: DEAD, LIVE, MKT_OP. TRADE_CATEG: DEAD TRADE, LIVE.
- M_CNT_TYPO / MX_TYPOLOGY: FX Spot, FX Swap, FX Outright, IRS Vanilla, IRD Loan Depo, IRD Bond Future, IRD IR Future, EQD Future, IRD Bond.
- M_TRN_FMLY/GRP/TYPE (FGT): CURR/FXD/FXD, CURR/FXD/SWLEG, IRD/IRS, IRD/LN_BR, IRD/LFUT, IRD/SFUT, EQD/FUT, IRD/BOND.
- Portfolios: DBSHK IRD DY, DBSHK IRDEXO CK, DBSHK FXD CK, DBSHK CMU SWTM, DBSHK LRP LMGT, DHBHK IN FI SY. Counterparties look like portfolio codes for internal legs (ABU ABU-COD, DBS CLEARING-C) or bank names for external (STAN LDN, HSBC HKG).
- M_CTP_TYPE: "External" in all visible rows. M_PAR_CNTRY: AE, GB, SG, HK, DE, US…
- M_SRC_SYSTEM: 360T, SWAPSWIRE, DBSK, CQG, TRHK-BGHK, TRHK-TRHK.
- Binary columns serialized as Python bytes literals, e.g. `b'\x00\x00\x00\x00\x00'`, `b'\x01'`. The extractor should decode these.
- TRADING_DESK / SUBDESK: HKB Rates Desk, CMU-T&M, CMU-FX swap, HKL Rates Desk. DESK_NAME and SUB_DESK_NAME empty.
- BUSINESS_HOURS: Y/N. OFF_PREMISES mostly empty. TRADE_CATEG_TYPE: MUREX.
