# Business rules (transcribed from rules_1..rules_4 photos)

Source workbook: `Business Rule Requirement_14 Sep.xlsx`. Tabs visible: **Foundation Data Requirement**, **TCUC1_DQ+TM**, Sheet2, AEGIS Roles. Photos show only part of each sheet; columns to the right of I are cut off and rules_3 is blurry. Red text in the sheet = open questions from the business.

## Tab: Foundation Data Requirement (rules_1)
Columns: A BR Ref, B Business Requirement Details, C Business Requirement Rule, D Expected Outcome, E Requirement provided (date), F Decision on where this is built.

| BR Ref | Requirement | Rule | Expected outcome | Built in |
|---|---|---|---|---|
| BR-FD-001 | Data requirement: ALL MUREX and CORE trade transaction data on a daily basis | NA | Daily transactions are available in AEGIS | ADA (14-Sep-26) |
| BR-FD-002 | Trade transaction data identification, including all interface deals. *Open Q: can the "Counterparty Type" field identify internal party?* | IF Counterparty label = iWork portfolio code (original iWork file) OR Counterparty label contains "BONDS"/"BOND" + Counterparty Type = "Internal Party" THEN "INTERNAL DEAL" ELSE "EXTERNAL DEAL" | Every MUREX/CORE trade tagged **"ID"** (internal deal) or **"ED"** (external deal) | AEGIS |
| BR-FD-003 | iWork file ingested daily. Portfolios meeting the location filter criteria are in scope for AEGIS Desk Profile | Per-country criteria, below | Portfolio-to-desk mapping and notifications, below | AEGIS |

### BR-FD-003 location criteria
- **Singapore**: Country = "Singapore"; Business Unit = 'GFM' OR 'CAPMKT' OR 'VCCRTL' OR 'VECTOR' OR 'MERLION' OR [BU = 'Central Ops' AND Trader Name 'TLM CO4A / CO5A' OR 'TLM & DBSH' OR 'SG GLM' for GLM Desk] OR [BU = 'Central Ops' AND a named trader for Digital Asset Liquidity Management Desk]; Classification = ALL EXCEPT "Close"; MxEntity = ALL EXCEPT "Iwork only"; Risk Banking/Trading to identify Banking or Trading Book. *Open: "To include IBG and Finance".*
- **Hong Kong**: Country = "Hong Kong"; BU = "GFM"; Classification excl "Close"; MxEntity excl "iWork only"; Risk Banking/Trading; Legal entity includes DBS HK (HK Branch), DBS HK DUMMY LE (HK Branch), DBHBHK LE (HK Limited), DHK MA (HK Limited).
- **China**: Country = "China"; BU = "GFM"; Classification excl "Close"; MxEntity excl "iWork only"; Risk Banking/Trading.
- **Taiwan**: same pattern, Country = "Taiwan".
- **London**: Country = "London", rest cut off in photo.

### BR-FD-003 expected outcome
- Notify Desk Head when a new portfolio is added to a desk "as part of birth right". For a subdesk with a deviation, the notification also asks Desk Head to manually add the portfolio for the deviated subdesk.
- If a portfolio cannot be added to a desk, email + inbox to Desk Support to update data in iWork. AEGIS picks up corrected data to map to desk.
- iWork `AEGIS_Desk` and `AEGIS_Sub Desk` map to Desk and Sub Desk Profile in AEGIS (Portfolio Ownership).
- UPDATE: iWork will contain AEGIS_SUBDESK. AEGIS derives banking/trading book from iWork and applies the portfolio to all SUBDESKS under the DESK. Example: iWork gives AEGIS_DESK = CENTRAL DESK, AEGIS_SUBDESK = CENTRAL DESK - COLLATERAL; AEGIS uses that + risk banking/trading to derive CENTRAL DESK (BANKING) - COLLATERAL, puts the portfolio there, also applies it to the other subdesks under CENTRAL DESK (BANKING), and emails Desk Delegate cc Desk Head to remove it from subdesks where it doesn't apply.
- Portfolio Owner: existing iWork field (shown under Sub-Desk → Desk Mandate page). AEGIS_Portfolio Reviewer: delegate for the portfolio owner (Use Case Reviewer).

## Tab: TCUC1_DQ+TM (rules_2..rules_4)
Columns: A BR Ref, B Category, C Business Requirement Details, D Business Requirement Rule, **E Tag to be appended (BackEnd)**, F Tag Ref (Blotter), G Tag Description (Front End), H Data Rectification Workflow, I Expected Actions (USER), J Expected AEGIS action (cut off).

Common exclusion list for trader checks ("EXCLUDE the following data in the TRADER ID field"): DBSINTF, DBSINTFD (interface deals), SETL% (settlement trades), BATCHUSER, AUTOJOB, ITGSUPP1, ITGSUPP2, FITX%, SYSTEM%, SYNSGCREDITUS%. `%` = SQL-style wildcard.

"Not DESK SUPPORT or SALES" below means: 1BankID Role is not "DESK SUPPORT" or "SALES".

| BR Ref | Category | Check | Rule (col D) | Tag (col E) | Blotter | Workflow to |
|---|---|---|---|---|---|---|
| BR-UC1-001 | DQ – Desk Mandate | Portfolio mapped to a valid desk/sub-desk | Portfolio from MUREX/CORE trade can't be found in any desk | DQDM001_PORTFOLIO_NOT_FOUND | DQ | DESK SUPPORT |
| BR-UC1-002 | DQ – Desk Mandate | FGT permitted for assigned desk/sub-desk | FGT (Family, Group, Type) from MUREX not found in desk profile OR Family/Group/Type/Trade Type/Trade Sub Type from CORE not found. *Open Q: trader mandate has no Trade Type/Sub Type, is everything flagged?* | DQDM002_FGT_NOT_FOUND | DQ | DESK DELEGATE |
| BR-UC1-003 | DQ – Staff Profile | Staff onboarded in AEGIS | After exclusion list: 1BankID has no AEGIS role | DQTP001_STAFF_ROLE_NOT_FOUND | DQ | STAFF PROFILE DELEGATE + Hiring Manager |
| BR-UC1-004 | DQ – Trader Profile | Trader onboarded in AEGIS | After exclusion list: 1BankID in Trader ID not in any Trader Mandate AND not DESK SUPPORT/SALES | DQTP002_TRADER_NOT_FOUND | DQ | DESK DELEGATE (desk via portfolio mapping) |
| BR-UC1-005 | Trader Conduct – Trader Mandate | Trader mandated for the portfolio | Trade's portfolio not in the trader mandate AND not DESK SUPPORT/SALES | TCTM001_PORTFOLIO_NOT_ALLOWED | PT | DESK DELEGATE |
| BR-UC1-006 | Trader Conduct – Trader Mandate | Trader mandated for the FGT | Trade's FGT not in trader mandate AND not DESK SUPPORT/SALES | TCTM002_FGT_NOT_ALLOWED | PT | DESK DELEGATE |
| BR-UC1-007 | Trader Conduct – Trader Mandate | Trader mandated for the currency | Trade currency not in trader mandate AND not DESK SUPPORT/SALES | TCTM003_CURR_NOT_ALLOWED | PT | DESK DELEGATE |
| BR-UC1-008 | Trader Conduct – Trader Mandate | Trader has trading license / approval | 1BankID has ROLE = "TRADER" … AND Pre-RNF APPROVAL = N (middle of rule cut off) | TCTM004_TRADE_WO_LICENSE | PT | DESK DELEGATE |
| BR-UC1-009 | Trader Conduct – Trader Mandate | Booked before mandate effective date | Trade insertion date before Trader Mandate Effective Date for portfolio, FGT, currency, remote working (outside premise/hours) AND not DESK SUPPORT/SALES | TCTM005_TRADE_BEFORE_EFF_DATE | PT | DESK DELEGATE |
| BR-UC1-010 | Trader Conduct – Trader Mandate | Booking outside office hours | Trade insertion time after Murex rollover time on same business date AND not DESK SUPPORT/SALES | TCTM006_LATE_TRADE_INSERTION | PT | DESK DELEGATE |
| BR-UC1-011 | Trader Conduct – Trader Mandate | Booking via remote access (VPN/Citrix) | Trade executed via remote channel based on user login session info AND not DESK SUPPORT/SALES. *Open: CORE VPN login info may not exist; MUREX Citrix uses login info vs trade insertion time.* | TCTM007_REMOTE_ACCESS | PT | DESK DELEGATE |

Workflows go to the AEGIS role "via inbox". Typical expected user action (col I) for TCTM rows, as Desk Delegate: (1) rectify data in AEGIS within 3 business days, or (2) validate not a data issue, see Clarification Workflow. Expected AEGIS action (col J, partly visible): start ageing T+3 business days; if action 1 tag `TCTM001_RECTIFIED`, if action 2 start ageing from clarification date and after clarification workflow tag `TCTM001_RECTIFIED` or `TCTM001_REVIEWED_WITH…` (cut off). DQ rows: "Action by DESK SUPPORT / STAFF … data to be rectified in … business days".

Rows after BR-UC1-011 were not photographed.
