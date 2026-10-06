# Prompt: Build the AEGIS Automated Detection & Rule Engine

> Paste this whole file into Claude (Claude Code works best, since it can create and run the project). Put these three files in the repo's `docs/` folder first, because the prompt refers to them:
> - `docs/AEGIS_business_rules.xlsx`: the business rules (BR Ref, Details, Rule, BackEnd tag, Blotter ref)
> - `docs/ada_deal_columns.csv`: the 121 trade fields in order, with data types
> - `docs/ada_deals_synthetic_10000.csv`: 10,000 synthetic trades for tests and demos

---

## Role
You are an expert Senior Java developer. You are building the **AEGIS Automated Detection and Rule Engine** as a new Spring Boot microservice, `aegis-deal-detection-service`.

## Context
AEGIS is a Global Financial Markets application. It monitors post-trade bookings against risk controls. Every day it ingests the trade transactions from Murex / CORE (delivered through ADA / iWork). It enriches each trade, decides whether the trade is internal or external, and runs a set of detection rules. Each rule that matches appends a **detection code / review tag** to the trade. Finally, it hands the tagged trades to the existing AEGIS workflow engine, which routes them to traders, Desk Support or Desk Delegates.

## Functional design
The engine is a pipeline. Steps run in order, and each step may process trades in parallel.

### Step 1: Data ingestion
- The input is a daily deal extract whose layout is defined in `docs/ada_deal_columns.csv` (121 columns, in order: position, field, data_type).
- Support **two sources** behind one `TradeSource` interface:
  1. **CSV upload**: `POST /api/v1/trades/upload` (multipart CSV) runs the full pipeline on the file and returns a run summary.
  2. **Scheduled Presto pull**: a `@Scheduled` job (cron set in config) that reads the same columns from Presto through `JdbcTemplate`. The production connection code lives in a separate internal repo (`rico-ada-service`) that you can't see. Implement a clean `PrestoTradeSource` with the query, connection settings and paging in config, and keep it disabled by default behind a Spring profile (`presto`).
- Parsing rules (taken from the data):
  - `Date/Time (epoch)` fields are **epoch microseconds** (UTC) → `Instant`.
  - `Time` fields are `HH:mm:ss` → `LocalTime`.
  - `Binary` fields arrive as Python bytes literals such as `b'\x00\x00\x00\x00\x00'` → decode to `byte[]`.
  - `COB_DATE` is an integer `yyyyMMdd` → `LocalDate`.
  - `Blank/undetermined` fields: keep them as nullable `String`.
  - Empty cells are `null`. Never fail the whole file on one bad row. Collect row-level errors and report them in the run summary.

### Step 2: Enrichment
For each trade, attach:
1. **Desk profile**: look up the desk / sub-desk for the trade's portfolio (`M_TP_PFOLIO`) in the AEGIS desk table, and attach the full desk record as JSON.
2. **Trader profile**: look up the trader in the AEGIS trader table by `M_TP_TRADER`, and attach the full profile (1BankID, AEGIS role, trader mandate: allowed portfolios, FGTs, currencies, effective dates, licence / Pre-RNF approval).
3. **Portfolio**: look up the iWork portfolio record (country, business unit, classification, MxEntity, banking/trading book, AEGIS_DESK, AEGIS_SUBDESK, owner, reviewer).

The output of this step is an `EnrichedTrade` (trade + desk + trader + portfolio). The desk, trader-mandate and iWork-portfolio tables were **not** provided. Design minimal reference tables for them, seed them with data that matches the synthetic CSV (portfolios `DEMOHK …`, traders `TRD0001`–`TRD0040`), and list the assumptions you made.

### Step 3: Internal / external classification, then in-scope checks
Tag every trade before any other rule runs (BR-FD-002):
```
IF   Counterparty label (M_TP_CNTRP) = an iWork portfolio code
  OR (Counterparty label contains "BONDS" or "BOND" AND Counterparty Type (M_CTP_TYPE) = "Internal Party")
THEN tag "ID"  (INTERNAL DEAL)
ELSE tag "ED"  (EXTERNAL DEAL)
```
Then apply the in-scope / out-of-scope filters (BR-FD-003 location criteria: country, business unit, classification ≠ "Close", MxEntity ≠ "iWork only", banking/trading book). Out-of-scope trades are stored but skip Step 4.

### Step 4: Detection rules and review-code tagging
- The rules are in `docs/AEGIS_business_rules.xlsx`. For every row, **"Business Requirement Rule"** is the condition and **"Tag to be appended (BackEnd)"** is the tag to add when it matches. **"Tag Ref (Blotter)"** (DQ / PT) is the tag's category.
- Evaluate **every** rule against every in-scope trade. A trade can collect **several** tags. Rule order is deterministic (by BR Ref).
- Shared conditions used by several rules:
  - **Excluded trader IDs** (BR-UC1-003/004): `DBSINTF`, `DBSINTFD`, `SETL%`, `BATCHUSER`, `AUTOJOB`, `ITGSUPP1`, `ITGSUPP2`, `FITX%`, `SYSTEM%`, `SYNSGCREDITUS%` (`%` = wildcard). These trades skip the trader / staff checks.
  - **Role exemption**: most trader-conduct rules apply only when the 1BankID role is **not** `DESK SUPPORT` or `SALES`.

| BR Ref | Condition (summary, see the xlsx for the full text) | Tag | Blotter |
|---|---|---|---|
| BR-UC1-001 | Portfolio not found in any desk | DQDM001_PORTFOLIO_NOT_FOUND | DQ |
| BR-UC1-002 | Murex FGT (family, group, type), or CORE family/group/type/trade type/sub type, not in desk profile | DQDM002_FGT_NOT_FOUND | DQ |
| BR-UC1-003 | (after exclusions) 1BankID has no AEGIS role | DQTP001_STAFF_ROLE_NOT_FOUND | DQ |
| BR-UC1-004 | (after exclusions) trader not in any trader mandate, and not DESK SUPPORT/SALES | DQTP002_TRADER_NOT_FOUND | DQ |
| BR-UC1-005 | Portfolio not in the trader's mandate, and not DESK SUPPORT/SALES | TCTM001_PORTFOLIO_NOT_ALLOWED | PT |
| BR-UC1-006 | FGT not in the trader's mandate, and not DESK SUPPORT/SALES | TCTM002_FGT_NOT_ALLOWED | PT |
| BR-UC1-007 | Currency not in the trader's mandate, and not DESK SUPPORT/SALES | TCTM003_CURR_NOT_ALLOWED | PT |
| BR-UC1-008 | Role = TRADER, no licence, and Pre-RNF approval = N | TCTM004_TRADE_WO_LICENSE | PT |
| BR-UC1-009 | Trade insertion date before the mandate effective date for that portfolio / FGT / currency / remote working, and not DESK SUPPORT/SALES | TCTM005_TRADE_BEFORE_EFF_DATE | PT |
| BR-UC1-010 | Insertion time after the Murex rollover time on the same business date, and not DESK SUPPORT/SALES | TCTM006_LATE_TRADE_INSERTION | PT |
| BR-UC1-011 | Executed via remote access (VPN/Citrix) based on login-session info, and not DESK SUPPORT/SALES | TCTM007_REMOTE_ACCESS | PT |

### Step 5: Trigger workflows
- Group the day's tagged trades by **detection code + use case**, and call the existing **AEGIS workflow engine API** once per group (a REST client behind an interface, with the base URL in config, plus a stub implementation for local runs).
- Routing from the rules sheet: DQDM001 → `DESK SUPPORT`; DQDM002, DQTP002 and all TCTM* → `DESK DELEGATE`; DQTP001 → `STAFF PROFILE DELEGATE` + hiring manager. Store routing in config, not code.
- Retries and failures must be logged and visible in the run summary. A workflow failure must not lose the tags already saved.

## Technical specification
1. **Java 17** (records, sealed types, pattern matching, text blocks where they help) and **Spring Boot 3.5**, built with Maven.
2. **MariaDB** for storage, with **Flyway** migrations. The trade table has ~121 columns. Use sensible types (BIGINT for epoch µs, DECIMAL for amounts, VARBINARY for binary), index the lookup columns (M_NB, M_TP_PFOLIO, M_TP_TRADER, trade date), and **partition by month of the transaction date** (`M_TP_DTETRN`). Store tags in a child table (`trade_id`, `tag`, `br_ref`, `blotter_ref`, `run_id`, `created_at`) instead of a delimited string.
3. **Rule engine**: rules must be **data-driven**, loaded from a config file (YAML/JSON) or from a DB table, so a new rule or a changed condition needs no code change. Compare the options briefly (Easy Rules with MVEL/SpEL, Drools, plain SpEL predicates) and recommend one. My default is **SpEL expressions stored in a `detection_rule` table, seeded from YAML**, unless you find a strong reason not to.
4. **Central exception handling** with `@RestControllerAdvice`, typed exceptions and a consistent error body.
5. **Logging**: SLF4J at INFO for run milestones (rows read, rows in scope, tags per code, workflows triggered) and WARN for skipped rows and lookup misses. Never log full trade rows.
6. **Performance**: stream the CSV (don't load 10k+ rows into memory at once), batch inserts, and evaluate rules in parallel per trade chunk.
7. **Tests**: unit tests for each rule (one positive and one negative case per BR Ref), a parser test for the epoch-µs and bytes-literal formats, and an integration test (Testcontainers MariaDB) that loads `docs/ada_deals_synthetic_10000.csv` and checks the tag counts are non-zero for the seeded cases (unmapped portfolios, excluded / unknown traders, after-hours bookings, internal deals).
8. A README with how to run locally (docker-compose for MariaDB), the API, the config keys, and how to add a rule.

## How to work
1. **Plan first.** Before writing any code, give me a detailed technical implementation plan: modules and packages, the DB schema, the rule-engine choice and rule format, the mapping from each BR rule to the exact `ada_deal` fields it uses, and the reference tables you will invent. Then stop and wait for my approval.
2. **Ask questions** wherever the mapping is unclear. In particular: which field holds the 1BankID (`M_TP_TRADER` vs `M_EXE_TRADER` vs `USER_NAME`), the Murex rollover time, where VPN/Citrix login info comes from, how FGT maps for CORE trades, and how BR-UC1-008's licence check is defined (that rule is partly illegible in the source).
3. After approval, build it in small, reviewable steps, running the tests after each step.
