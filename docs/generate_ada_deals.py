#!/usr/bin/env python3
"""Synthetic ADA deal extract generator (trades-poc).

Columns and their order come from the Trader Mandate report photos (report_1..7).
Data types come from ada_deal_schema (schema_1..3 photos, 121 fields, rows 2-122);
see ada_deal_columns.csv. Integer per schema: M_EXT_REF (report also shows MAKER… text refs)
and COB_DATE (emitted as yyyymmdd; the report displays it as d/m/yy).
All values are fictitious. Usage: python3 generate_ada_deals.py [rows] [out.csv] [seed]
"""
import csv, random, sys
from datetime import datetime, timedelta, timezone

ROWS = int(sys.argv[1]) if len(sys.argv) > 1 else 10000
OUT = sys.argv[2] if len(sys.argv) > 2 else "ada_deals_synthetic_10000.csv"
SEED = int(sys.argv[3]) if len(sys.argv) > 3 else 42
rnd = random.Random(SEED)

# (name, type, source) ; source: schema_1 (rows 2-47) | schema_2-3 (rows 48-122)
COLUMNS = [
 ("M_NB","Integer","schema_1"),("M_CNT_TYPO","Text","schema_1"),("M_CONTRACT","Integer","schema_1"),
 ("M_TRN_STATUS","Text","schema_1"),("M_TP_DTETRN","Date/Time (epoch)","schema_1"),("M_TP_DTEEXP","Date/Time (epoch)","schema_1"),
 ("M_TP_TRADER","Text","schema_1"),("USER_NAME","Text","schema_1"),("M_TRN_FMLY","Text","schema_1"),
 ("M_TRN_GRP","Text","schema_1"),("M_TRN_TYPE","Text","schema_1"),("MX_TYPOLOGY","Text","schema_1"),
 ("M_INSTRLBL","Text","schema_1"),("M_TP_CNTRP","Text","schema_1"),("M_TP_PFOLIO","Text","schema_1"),
 ("M_TP_ENTITY","Text","schema_1"),("M_TP_TIMSYS","Time","schema_1"),("M_TP_DTESYS","Date/Time (epoch)","schema_1"),
 ("M_EXE_TRADER","Text","schema_1"),("M_LABEL","Text","schema_1"),("M_VAL_LEVEL","Blank/undetermined","schema_1"),
 ("M_BRW_NOMU1","Text","schema_1"),("M_BRW_NOM1","Integer","schema_1"),("M_TP_IQTYS","Integer","schema_1"),
 ("M_BRW_NOMU2","Text","schema_1"),("M_BRW_NOM2","Decimal","schema_1"),("M_RATE_MARG0","Decimal","schema_1"),
 ("M_TP_STRTGY","Blank/undetermined","schema_1"),("M_SAL_TRADER","Blank/undetermined","schema_1"),("M_SAL_MARG1","Integer","schema_1"),
 ("M_SRC_SYSTEM","Text","schema_1"),("M_EQ_PO","Binary","schema_1"),("M_D_STRADER","Blank/undetermined","schema_1"),
 ("M_TP_GID","Integer","schema_1"),("DESK_NAME","Blank/undetermined","schema_1"),("SUB_DESK_NAME","Blank/undetermined","schema_1"),
 ("TRADING_DESK","Text","schema_1"),("TRADING_SUBDESK","Text","schema_1"),("M_PACKAGE","Binary","schema_1"),
 ("M_PC_CODE","Mixed/Text","schema_1"),("M_TYPOLOGY","Integer","schema_1"),("M_PKG_TYPO","Blank/undetermined","schema_1"),
 ("M_LABEL_CTPYMX","Integer","schema_1"),("M_CTP_TYPE","Text","schema_1"),("M_CIN_NO","Text","schema_1"),
 ("M_PAR_CNTRY","Text","schema_1"),
 # ---- below: types from schema_2 / schema_3 photos ----
 ("M_MX2_INSTR","Text","schema_2-3"),("M_INSTRUMENT","Text","schema_2-3"),("M_SE_CODE","Blank/undetermined","schema_2-3"),
 ("M_ISIN","Blank/undetermined","schema_2-3"),("M_TRN_EXP","Integer","schema_2-3"),("M_D_MATURITYDT","Date/Time (epoch)","schema_2-3"),
 ("M_BRW_SDTE","Date/Time (epoch)","schema_2-3"),("M_XPFWFIXDT","Blank/undetermined","schema_2-3"),("M_D_VALUEDT","Date/Time (epoch)","schema_2-3"),
 ("M_TP_BUY","Text","schema_2-3"),("M_TYPE","Blank/undetermined","schema_2-3"),("M_TP_NOMINAL","Integer","schema_2-3"),
 ("M_TP_NOMCUR","Text","schema_2-3"),
 ("M_PRICE","Decimal","schema_2-3"),("M_DISC_RATE0","Binary","schema_2-3"),("M_DISC_RATE1","Binary","schema_2-3"),
 ("M_RATE_MARG1","Integer","schema_2-3"),("M_EXT_REF","Integer","schema_2-3"),("M_TP_CREATOR","Binary","schema_2-3"),
 ("M_VAL_STATUS","Text","schema_2-3"),("M_TP_INT","Text","schema_2-3"),("M_BCOMMENT0","Text","schema_2-3"),
 ("M_BCOMMENT1","Blank/undetermined","schema_2-3"),("M_BCOMMENT2","Blank/undetermined","schema_2-3"),("M_SCOMMENT0","Text","schema_2-3"),
 ("M_SCOMMENT1","Text","schema_2-3"),("M_SCOMMENT2","Text","schema_2-3"),("M_BPFOLIO","Text","schema_2-3"),
 ("M_SPFOLIO","Text","schema_2-3"),("M_BRW_PR1","Text","schema_2-3"),("M_BRW_PR2","Text","schema_2-3"),
 ("M_RATE_TYPE0","Binary","schema_2-3"),("M_RATE_TYPE1","Binary","schema_2-3"),("M_TP_RTMNDD0","Text","schema_2-3"),
 ("M_TP_RTMNDD1","Text","schema_2-3"),("M_RATE_FACT0","Integer","schema_2-3"),("M_RATE_FACT1","Integer","schema_2-3"),
 ("M_PL_INSCUR","Text","schema_2-3"),("M_MAR_CCY","Blank/undetermined","schema_2-3"),("M_BRW_CP","Blank/undetermined","schema_2-3"),
 ("M_TP_FEE1","Decimal","schema_2-3"),("M_TP_FEE2","Integer","schema_2-3"),("M_TP_FEE3","Integer","schema_2-3"),
 ("M_SE_ISST","Binary","schema_2-3"),("M_ISSUER","Text","schema_2-3"),("M_SE_ISS","Blank/undetermined","schema_2-3"),
 ("M_TP_FXCPCUR","Blank/undetermined","schema_2-3"),("M_RATING","Blank/undetermined","schema_2-3"),("TRADE_CATEG","Text","schema_2-3"),
 ("CORE_TRN_FMLY","Blank/undetermined","schema_2-3"),("CORE_TRN_GRP","Blank/undetermined","schema_2-3"),("CORE_TRN_TYPE","Blank/undetermined","schema_2-3"),
 ("CORE_BUY_SELL","Blank/undetermined","schema_2-3"),("CORE_TRADE_TYPE","Blank/undetermined","schema_2-3"),("CORE_TRADE_SUBTYPE","Blank/undetermined","schema_2-3"),
 ("BUSINESS_HOURS","Text","schema_2-3"),("M_H_TRNSTS","Blank/undetermined","schema_2-3"),("M_DT_TS","Date/Time (epoch)","schema_2-3"),
 ("M_USR_NAME","Blank/undetermined","schema_2-3"),("M_USR_ID","Blank/undetermined","schema_2-3"),("M_TP_STATUS2","Text","schema_2-3"),
 ("M_H_BRWNOMU1","Blank/undetermined","schema_2-3"),("M_H_BRW_NOM1","Blank/undetermined","schema_2-3"),("M_H_BRWNOMU2","Blank/undetermined","schema_2-3"),
 ("M_H_BRW_NOM2","Blank/undetermined","schema_2-3"),("M_TP_PRICE","Decimal","schema_2-3"),("OFF_PREMISES","Blank/undetermined","schema_2-3"),
 ("M_M_TRADERID","Blank/undetermined","schema_2-3"),("M_T_TRADERID","Blank/undetermined","schema_2-3"),("DESTINATION_TRADER_ID","Text","schema_2-3"),
 ("M_STAT_NAME","Blank/undetermined","schema_2-3"),("M_EVENT_TIME","Blank/undetermined","schema_2-3"),("M_LO_TIME","Blank/undetermined","schema_2-3"),
 ("TRADE_CATEG_TYPE","Text","schema_2-3"),("COB_DATE","Integer","schema_2-3"),
]

# ---- fictitious reference data (shapes copied from the report, names invented) ----
PRODUCTS = [  # typo, fmly, grp, type, instr label, typology code, weight
 ("FX Spot","CURR","FXD","FXD","USD/HKD",1189,40),
 ("FX Swap","CURR","FXD","SWLEG","USD/HKD",1187,12),
 ("IRS Vanilla","IRD","IRS","","HKD HIBOR 3M",1403,10),
 ("IRD Loan Depo","IRD","LN_BR","","HKD DEPOSIT",1366,8),
 ("IRD Bond Future","IRD","LFUT","","UK GILTS 10Y FUT",1325,6),
 ("IRD IR Future","IRD","SFUT","","SOFR 3M FUT",1361,6),
 ("EQD Future","EQD","FUT","","HSI FUT",1120,6),
 ("IRD Bond","IRD","BOND","","HKD T-BILL 2630",1331,4),
]
MX_TYPOLOGY_OVERRIDE = {"FX Swap": ["FX Outright","FX Spot"]}
TRADERS = [f"TRD{n:04d}" for n in range(1, 41)]
TRADER_NAMES = {t: f"Trader {t[3:]} - DEMO BANK" for t in TRADERS}
EXCLUDED_IDS = ["BATCHUSER","AUTOJOB","ITGSUPP1","ITGSUPP2","DBSINTF","DBSINTFD","SETL_HK01","FITX_02","SYSTEM_EOD"]
PORTFOLIOS = ["DEMOHK IRD DY","DEMOHK IRDEXO CK","DEMOHK FXD CK","DEMOHK CMU SWTM","DEMOHK LRP LMGT","DEMOHK CMU MMTM","DEMOHK IN FI SY"]
UNMAPPED_PORTFOLIOS = ["DEMOHK ORPHAN 01","DEMOSG TEST PF"]
CLEARING = ["DEMO CLEARING-C"]
EXT_CPTY = [("ALPHA BANK LDN","GB"),("BETA BANK HKG","HK"),("GAMMA BANK SIN","SG"),("DELTA BANK FRA","DE"),
            ("EPSILON BANK NYC","US"),("ZETA BANK ZUR","CH"),("ETA BANK AMS","NL"),("THETA BANK DXB","AE"),
            ("IOTA BANK TPE","TW"),("KAPPA BANK SYD","AU")]
BOND_CPTY = ["INTERNAL BONDS HK","DEMO BOND BOOK"]
DESKS = {"DEMOHK IRD DY":("HKB Rates Desk","HKB Rates Desk"),"DEMOHK IRDEXO CK":("HKB Rates Desk","HKB Rates Desk"),
         "DEMOHK FXD CK":("HKB Rates Desk","HKB Rates Desk"),"DEMOHK CMU SWTM":("CMU-FX swap","CMU-FX swap"),
         "DEMOHK LRP LMGT":("CMU-T&M","CMU-MX MMU (T&M Facing)"),"DEMOHK CMU MMTM":("CMU-T&M","CMU-MX MMU (T&M Facing)"),
         "DEMOHK IN FI SY":("HKL Rates Desk","HKL Rates Desk")}
SRC = ["360T","SWAPSWIRE","DEMOK","CQG","TRHK-BGHK","TRHK-TRHK",""]
CCYS = ["USD","HKD","AUD","GBP","EUR","SGD"]
STATUSES = [("DEAD","DEAD TRADE",70),("LIVE","LIVE",25),("MKT_OP","LIVE",5)]
COMMENTS_B = ["<DEMO Bank HK Branch>","FIX<360T>TRD<DEMO.TRADER>","Full Fill Order","Partial Fill Order","<Dealing 2000-2 Match>","FIRM<ALPHA>",""]
COMMENTS_S = ["<>ACCT<>","ISDA2021","FIRM<BETA>","<>",""]
B0 = "b'\\x00'"; B1 = "b'\\x01'"; B5 = "b'\\x00\\x00\\x00\\x00\\x00'"; B16 = "b'" + "\\x00" * 16 + "'"

START = datetime(2026, 7, 1, tzinfo=timezone.utc)
def us(dt): return int(dt.timestamp()) * 1_000_000          # epoch microseconds, as in the report
def wchoice(items, idx): return rnd.choices(items, weights=[i[idx] for i in items])[0]

def row(i):
    p = wchoice(PRODUCTS, 6); typo, fmly, grp, typ, instr, typology, _ = p
    trade_day = START + timedelta(days=rnd.randrange(0, 92))
    while trade_day.weekday() >= 5: trade_day += timedelta(days=1)
    # mostly office hours; ~8% late bookings after a 19:00 rollover
    hour = rnd.randint(19, 23) if rnd.random() < 0.08 else rnd.randint(7, 18)
    t = trade_day.replace(hour=hour, minute=rnd.randrange(60), second=rnd.randrange(60))
    tenor = {"FXD": rnd.choice([2, 2, 7, 30]), "SWLEG": rnd.choice([7, 30, 90, 180])}.get(typ, rnd.choice([30, 90, 365, 730, 1825]))
    exp = trade_day + timedelta(days=tenor)
    status, categ, _ = wchoice(STATUSES, 2)
    r = rnd.random()
    trader = rnd.choice(EXCLUDED_IDS) if r < 0.04 else ("UNKNOWN%02d" % rnd.randint(1, 5) if r < 0.07 else rnd.choice(TRADERS))
    pf = rnd.choice(UNMAPPED_PORTFOLIOS) if rnd.random() < 0.03 else rnd.choice(PORTFOLIOS)
    desk, sub = DESKS.get(pf, ("", ""))
    k = rnd.random()   # counterparty: internal portfolio / internal bond book / clearing / external
    if k < 0.20: cp, ctp_type, cntry = rnd.choice([x for x in PORTFOLIOS if x != pf]), "Internal Party", "HK"
    elif k < 0.25: cp, ctp_type, cntry = rnd.choice(BOND_CPTY), "Internal Party", "HK"
    elif k < 0.40 and fmly in ("EQD",) or grp in ("LFUT","SFUT"): cp, ctp_type, cntry = CLEARING[0], "External", "HK"
    else: cp, cntry = rnd.choice(EXT_CPTY); ctp_type = "External"
    buy = rnd.choice("BS")
    if fmly == "CURR":
        nom = rnd.choice([1, 2, 3, 5, 9, 10, 25, 50, 100, 200]) * 1_000_000; ccy1, ccy2 = "USD", "HKD"
        price = round(rnd.uniform(7.75, 7.85), 5); nom2 = round(nom * price); rate = 0
    elif grp in ("LFUT","SFUT","FUT"):
        nom = rnd.randint(1, 30); ccy1, ccy2 = "", ""; price = round(rnd.uniform(80, 26000), 3); nom2 = 0; rate = 0
    else:
        nom = rnd.choice([5, 10, 15, 20, 50, 100, 200, 300]) * 1_000_000; ccy1 = ccy2 = rnd.choice(["HKD","USD","AUD"])
        price = round(rnd.uniform(95, 101), 5) if grp == "BOND" else 0; nom2 = nom; rate = round(rnd.uniform(2.5, 4.5), 3)
    signed = nom if buy == "B" else -nom
    nb = 337_000_000 + i * 397 + rnd.randrange(397)
    gid = rnd.randrange(2_355_000_000, 2_400_000_000) if rnd.random() < 0.7 else ""
    off_prem = "Y" if rnd.random() < 0.05 else ""
    cob = trade_day + timedelta(days=rnd.choice([0, 1, 14, 30]))
    vals = {
     "M_NB": nb, "M_CNT_TYPO": typo, "M_CONTRACT": nb + 688_000_000 + rnd.randrange(1000),
     "M_TRN_STATUS": status, "M_TP_DTETRN": us(trade_day), "M_TP_DTEEXP": us(exp),
     "M_TP_TRADER": trader, "USER_NAME": TRADER_NAMES.get(trader, trader),
     "M_TRN_FMLY": fmly, "M_TRN_GRP": grp, "M_TRN_TYPE": typ,
     "MX_TYPOLOGY": rnd.choice(MX_TYPOLOGY_OVERRIDE.get(typo, [typo])), "M_INSTRLBL": instr,
     "M_TP_CNTRP": cp, "M_TP_PFOLIO": pf, "M_TP_ENTITY": "DEMOHK",
     "M_TP_TIMSYS": t.strftime("%H:%M:%S"), "M_TP_DTESYS": us(trade_day),
     "M_EXE_TRADER": trader if rnd.random() < 0.15 else "", "M_LABEL": "", "M_VAL_LEVEL": "",
     "M_BRW_NOMU1": ccy1, "M_BRW_NOM1": signed, "M_TP_IQTYS": signed,
     "M_BRW_NOMU2": ccy2, "M_BRW_NOM2": nom2, "M_RATE_MARG0": rate,
     "M_TP_STRTGY": "", "M_SAL_TRADER": "", "M_SAL_MARG1": 0, "M_SRC_SYSTEM": rnd.choice(SRC),
     "M_EQ_PO": B5, "M_D_STRADER": "", "M_TP_GID": gid, "DESK_NAME": "", "SUB_DESK_NAME": "",
     "TRADING_DESK": desk, "TRADING_SUBDESK": sub, "M_PACKAGE": B5,
     "M_PC_CODE": rnd.choice(["102D","102D","102B","602B"]), "M_TYPOLOGY": typology,
     "M_PKG_TYPO": "MM FundingSwap Float" if typo == "IRD Loan Depo" and rnd.random() < 0.2 else "",
     "M_LABEL_CTPYMX": rnd.choice([43303, 41939, 72985, 571, 289, 250, 384]), "M_CTP_TYPE": ctp_type,
     "M_CIN_NO": "XDEMO%07d" % rnd.randrange(10**7) if rnd.random() < 0.4 else "", "M_PAR_CNTRY": cntry,
     "M_MX2_INSTR": instr, "M_INSTRUMENT": instr, "M_SE_CODE": "HK%010d" % rnd.randrange(10**10) if grp == "BOND" else "",
     "M_ISIN": "", "M_TRN_EXP": us(exp), "M_D_MATURITYDT": us(exp), "M_BRW_SDTE": us(trade_day + timedelta(days=2 if fmly == "CURR" else 0)),
     "M_XPFWFIXDT": "", "M_D_VALUEDT": us(trade_day + timedelta(days=2)), "M_TP_BUY": buy, "M_TYPE": "",
     "M_TP_NOMINAL": nom, "M_TP_NOMCUR": ccy1 or rnd.choice(CCYS), "M_PRICE": price if grp in ("LFUT","SFUT","FUT","BOND") else 0,
     "M_DISC_RATE0": B0, "M_DISC_RATE1": B0, "M_RATE_MARG1": 0,
     "M_EXT_REF": rnd.choice(["", "", gid, rnd.randrange(10**12, 10**13)]),
     "M_TP_CREATOR": B5, "M_VAL_STATUS": rnd.choice(["CMAT","CMAT","CMAT","CON","MAT"]),
     "M_TP_INT": "Y" if ctp_type == "Internal Party" else "N",
     "M_BCOMMENT0": rnd.choice(COMMENTS_B), "M_BCOMMENT1": rnd.choice(COMMENTS_B), "M_BCOMMENT2": rnd.choice(COMMENTS_S),
     "M_SCOMMENT0": rnd.choice(COMMENTS_B), "M_SCOMMENT1": rnd.choice(COMMENTS_B), "M_SCOMMENT2": rnd.choice(COMMENTS_S),
     "M_BPFOLIO": pf if buy == "B" else cp, "M_SPFOLIO": cp if buy == "B" else pf,
     "M_BRW_PR1": "P" if grp in ("IRS","LN_BR") and rnd.random() < 0.5 else "", "M_BRW_PR2": "R" if grp in ("IRS","LN_BR") and rnd.random() < 0.5 else "",
     "M_RATE_TYPE0": B0, "M_RATE_TYPE1": B1 if grp == "IRS" else B0,
     "M_TP_RTMNDD0": "HKD DEPOSIT-XSW" if grp == "LN_BR" else "", "M_TP_RTMNDD1": "HKD HIBOR 3M" if grp == "IRS" else "",
     "M_RATE_FACT0": 1 if grp in ("IRS","LN_BR") else 0, "M_RATE_FACT1": 1 if grp == "IRS" else 0,
     "M_PL_INSCUR": "HKD" if rnd.random() < 0.1 else "", "M_MAR_CCY": "", "M_BRW_CP": "",
     "M_TP_FEE1": 0, "M_TP_FEE2": 0, "M_TP_FEE3": 0, "M_SE_ISST": B16 if grp in ("LFUT","SFUT","FUT","BOND") else "",
     "M_ISSUER": rnd.choice(["Y","N",""]), "M_SE_ISS": "DEMO MA" if grp == "BOND" else "",
     "M_TP_FXCPCUR": "", "M_RATING": "NR" if grp == "BOND" else "", "TRADE_CATEG": categ,
     "CORE_TRN_FMLY": "", "CORE_TRN_GRP": "", "CORE_TRN_TYPE": "", "CORE_BUY_SELL": "", "CORE_TRADE_TYPE": "", "CORE_TRADE_SUBTYPE": "",
     "BUSINESS_HOURS": "Y" if 7 <= hour < 19 else "N", "M_H_TRNSTS": "", "M_DT_TS": us(t),
     "M_USR_NAME": "", "M_USR_ID": "", "M_TP_STATUS2": status,
     "M_H_BRWNOMU1": "", "M_H_BRW_NOM1": "", "M_H_BRWNOMU2": "", "M_H_BRW_NOM2": "",
     "M_TP_PRICE": price if price else round(rnd.uniform(2.5, 4.5), 4),
     "OFF_PREMISES": off_prem, "M_M_TRADERID": "", "M_T_TRADERID": "",
     "DESTINATION_TRADER_ID": trader if rnd.random() < 0.05 else "", "M_STAT_NAME": "", "M_EVENT_TIME": "", "M_LO_TIME": "",
     "TRADE_CATEG_TYPE": "MUREX", "COB_DATE": int(cob.strftime("%Y%m%d")),
    }
    return [vals[c[0]] for c in COLUMNS]

if __name__ == "__main__":
    assert len({c[0] for c in COLUMNS}) == len(COLUMNS)
    with open(OUT, "w", newline="") as f:
        w = csv.writer(f); w.writerow([c[0] for c in COLUMNS])
        for i in range(ROWS): w.writerow(row(i))
    with open("ada_deal_columns.csv", "w", newline="") as f:
        w = csv.writer(f); w.writerow(["position","field","data_type","source"])
        for n, (name, typ, src) in enumerate(COLUMNS, 1): w.writerow([n, name, typ, src])
    print(f"wrote {ROWS} rows x {len(COLUMNS)} columns to {OUT}")
