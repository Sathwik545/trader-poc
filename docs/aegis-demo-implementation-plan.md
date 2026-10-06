# AEGIS Detection Engine: demo implementation plan

Status: draft for Devender's review, 2026-10-05, revised the same day to follow Devender's architecture sketch. No code written yet.

## 1. Goal and ground rules

Build a working demo of the AEGIS detection engine, following Devender's architecture sketch (2026-10-05): a CSV of trades is uploaded to the deal detection API, read and stored in MariaDB, then the detection engine looks up each trade's trader and desk, runs the business rules, and writes one tag per failed rule to a trade-tags table.

- **Dummy data everywhere.** Trades come from `synthetic-data/ada_deals_synthetic_10000.csv`. Trader, portfolio and desk profiles are invented reference tables seeded to match that CSV.
- **Swappable later.** Every data source sits behind an interface, so moving to real data means adding a new implementation and switching a Spring profile, not changing the engine.
- **Stack:** Java 17, Spring Boot 3.5, Maven, MariaDB with Flyway migrations.
- **Rules are data.** Conditions live in config and a DB table, not in Java `if` statements.
- **Core first.** Phases 0 to 3 build exactly what the sketch shows. Phase 4 adds the demo screen (confirmed by Devender, 2026-10-05). The workflow handoff is not in the sketch and stays optional.

## 2. Architecture (matches the sketch)

One Spring Boot service, `aegis-deal-detection-service`:

```
User ── CSV file ──▶ Deal detection API  (POST /api/v1/trades/upload)
                          │
                          ▼
               Read file, parse, store ─────────────▶ trades            (all ~121 columns)
                          │
                          ▼
               Detection engine (reads the stored trades of this run)
                 ├─ Identify trader:  M_TP_TRADER ──▶ trader_profile, portfolio
                 ├─ Identify desk:    M_TP_PFOLIO ──▶ portfolio ──▶ desk_profile
                 └─ Run business rules (BR-FD-002 ID/ED first, then BR-UC1-001..011)
                          │
                          ▼
               Each rule that matches adds a tag ────▶ trades_tags       (trade id, tag id, details)
```

Two stages, as in the sketch: **store first, then detect.** The upload stores every row in `trades`, then the engine reads that run's trades back in chunks, enriches them, evaluates the rules and writes `trades_tags`. Re-running detection on stored trades (for example after a rule change) needs no re-upload: `POST /api/v1/runs/{id}/detect`.

### Swap points (interfaces)

| Interface | Demo implementation | Real implementation later |
|---|---|---|
| `TradeSource` | `CsvTradeSource` (upload API) | `PrestoTradeSource` via `JdbcTemplate`, profile `presto`, written as a stub now |
| `TraderProfileProvider` | `trader_profile` table seeded from `seed/trader_profile.yaml` | AEGIS staff / 1BankID roles and trader mandate |
| `PortfolioProvider` | `portfolio` table seeded from `seed/portfolio.csv` | Daily iWork file |
| `DeskProfileProvider` | `desk_profile` table seeded from `seed/desk_profile.yaml` | AEGIS desk table |
| `RemoteSessionProvider` | Reads the trade's `OFF_PREMISES` flag | VPN / Citrix login logs |

The three profile tables are loaded into in-memory maps once per run, so 10,000 trades cost a handful of queries, not 30,000.

### Packages

```
com.demo.aegis
├── api           DealDetectionController (upload, run, results), @RestControllerAdvice error handling
├── ingest        TradeSource, CsvTradeSource, PrestoTradeSource (stub), TradeParser, field converters, TradeWriter
├── engine        DetectionEngine, TraderIdentifier, DeskIdentifier, EnrichedTrade record
├── rules         DetectionRule, RuleContext, RuleEngine (SpEL), RuleLoader, helper functions
├── persist       repositories and JdbcTemplate batch writers
└── config        typed @ConfigurationProperties (rollover time, exclusion list, sources)
```

## 3. Data model (MariaDB, Flyway)

Table names follow the sketch.

| Table | Purpose | Notes |
|---|---|---|
| `trades` | One row per trade, all 121 fields | BIGINT for epoch µs, DECIMAL for amounts, VARBINARY for binary fields, nullable VARCHAR for "Blank/undetermined". Added columns: `id`, `run_id`, `trade_date` (DATE from `M_TP_DTETRN`), `deal_class` (ID/ED), `in_scope`. **Partitioned by month of `trade_date`** (RANGE COLUMNS, Jul to Dec 2026 plus `pmax`). Indexes on `M_NB`, `M_TP_PFOLIO`, `M_TP_TRADER`, `trade_date`. |
| `trades_tags` | Tags per trade | `id`, `trade_id`, `tag_id` (→ `detection_rule`), `run_id`, `details` (JSON: the values that failed, e.g. portfolio and mandate list), `created_at`. One row per failed rule. |
| `detection_rule` | Rule and tag master | `tag_id`, `br_ref`, `tag` (e.g. DQDM001_PORTFOLIO_NOT_FOUND), `category`, `blotter_ref` (DQ/PT), `condition_spel`, `workflow_role`, `enabled`, `sort_order`. Seeded from `rules.yaml`. |
| `trader_profile` | Trader looked up by `M_TP_TRADER` | `one_bank_id`, `name`, `aegis_role` (TRADER, DESK SUPPORT, SALES or null), `licensed`, `pre_rnf_approval`, `hiring_manager`, and the mandate as JSON: `allowed_portfolios`, `allowed_fgts`, `allowed_currencies`, `effective_date`, `remote_allowed`. |
| `portfolio` | iWork portfolio, looked up by `M_TP_PFOLIO` | `portfolio_code`, `country`, `business_unit`, `classification`, `mx_entity`, `risk_book`, `aegis_desk`, `aegis_subdesk`, `owner`, `reviewer`. |
| `desk_profile` | Desk and sub-desk | `desk`, `sub_desk`, `book` (BANKING/TRADING), `allowed_fgts` (JSON), `desk_head`, `desk_delegate`, `desk_support`. |
| `pipeline_run` | Run summary | File name, start/end, rows read/failed/stored, rows in scope, tags per rule. |
| `row_error` | Bad rows | `run_id`, `row_number`, `message`. A bad row never fails the file. |

## 4. Parsing the trade file

From the column list and the synthetic file:

- Epoch fields (`M_TP_DTETRN`, `M_TP_DTESYS`, …) are **microseconds UTC** → `Instant`.
- `M_TP_TIMSYS` is `HH:mm:ss` → `LocalTime`.
- Binary fields are Python bytes literals like `b'\x00\x00\x00\x00\x00'` → `byte[]`.
- `COB_DATE` is integer `yyyyMMdd` → `LocalDate`.
- Empty cell → `null`. Errors are collected per row in `row_error`.
- The CSV is streamed (Apache Commons CSV) and written in batches of 500.

## 5. Rule engine design

**Choice: SpEL expressions stored in `detection_rule`, seeded from `rules.yaml`.**

- Easy Rules is lightweight but adds little over SpEL and the project is no longer maintained.
- Drools is powerful but heavy for 11 rules and needs a DRL learning curve.
- SpEL ships with Spring, needs no extra dependency, and a business user can read a one-line condition.

How it works:

1. `RuleLoader` reads enabled rules ordered by BR Ref and compiles each expression once.
2. For each in-scope trade the engine builds a `RuleContext` (trade, trader profile with mandate, portfolio, desk, config) and evaluates **every** rule. A trade can collect several tags.
3. Shared logic is exposed as helper functions so conditions stay short: `excluded(trader)`, `exempt()` (role is DESK SUPPORT or SALES), `mandateHas('portfolio', value)`, `afterRollover()`, `remoteSession()`.
4. Trades are evaluated in parallel chunks; tags are batch-inserted.
5. `POST /api/v1/rules/reload` reloads rules without a restart. Adding a rule means adding a YAML/DB row, no Java change.

Example rule:

```yaml
- brRef: BR-UC1-005
  category: Trader Conduct - Trader Mandate
  tag: TCTM001_PORTFOLIO_NOT_ALLOWED
  blotter: PT
  workflowRole: DESK DELEGATE
  condition: "!excluded(trade.trader) && !exempt() && trader != null && !mandateHas('portfolio', trade.portfolio)"
```

## 6. Rule to field mapping (with demo assumptions)

1BankID is assumed to be `M_TP_TRADER` (open question 1).

| BR Ref | Tag | Fields and lookups used | Demo assumption |
|---|---|---|---|
| BR-FD-002 | ID / ED | `M_TP_CNTRP`, `M_CTP_TYPE`, iWork portfolio codes | As written in the rules sheet |
| BR-FD-003 | in scope | iWork `country`, `business_unit`, `classification`, `mx_entity` | Only the Hong Kong criteria are modelled (all demo trades are HK). Trades whose portfolio is missing from iWork stay in scope so BR-UC1-001 can fire. |
| BR-UC1-001 | DQDM001_PORTFOLIO_NOT_FOUND | `M_TP_PFOLIO` → `portfolio` → `desk_profile` | No desk found for the portfolio |
| BR-UC1-002 | DQDM002_FGT_NOT_FOUND | `M_TRN_FMLY/GRP/TYPE` (Murex) or `CORE_TRN_*`, `CORE_TRADE_TYPE/SUBTYPE` (CORE) → desk `allowed_fgt` | CORE path checks the same way; all demo trades are Murex |
| BR-UC1-003 | DQTP001_STAFF_ROLE_NOT_FOUND | `M_TP_TRADER` → `trader_profile.aegis_role` | After the exclusion list; fires if the ID is unknown or has no role |
| BR-UC1-004 | DQTP002_TRADER_NOT_FOUND | `M_TP_TRADER` → `trader_profile` mandate | After exclusions, not DESK SUPPORT/SALES |
| BR-UC1-005 | TCTM001_PORTFOLIO_NOT_ALLOWED | `M_TP_PFOLIO` vs mandate portfolios | Skipped when the trader has no mandate at all (004 covers that) |
| BR-UC1-006 | TCTM002_FGT_NOT_ALLOWED | FGT vs mandate FGTs | Same |
| BR-UC1-007 | TCTM003_CURR_NOT_ALLOWED | `M_TP_NOMCUR` (and `M_BRW_NOMU1/2` for FX) vs mandate currencies | Same |
| BR-UC1-008 | TCTM004_TRADE_WO_LICENSE | `trader_profile.aegis_role`, `licensed`, `pre_rnf_approval` | Role = TRADER and not licensed and Pre-RNF = N (the middle of this rule is illegible in the source) |
| BR-UC1-009 | TCTM005_TRADE_BEFORE_EFF_DATE | `M_TP_DTESYS` vs mandate `effective_date` for the matching portfolio/FGT/currency | Insertion date earlier than the matching row's effective date |
| BR-UC1-010 | TCTM006_LATE_TRADE_INSERTION | `M_TP_TIMSYS` vs Murex rollover time | Rollover time is config, default **19:00** (matches the seeded after-hours trades) |
| BR-UC1-011 | TCTM007_REMOTE_ACCESS | `OFF_PREMISES` = Y via `RemoteSessionProvider` | Stands in for VPN/Citrix login data |

Common exclusion list (config): `DBSINTF`, `DBSINTFD`, `SETL%`, `BATCHUSER`, `AUTOJOB`, `ITGSUPP1`, `ITGSUPP2`, `FITX%`, `SYSTEM%`, `SYNSGCREDITUS%`.

## 7. Dummy reference data

Seeded to match the synthetic CSV, and designed so **every rule fires at least once**.

- **`portfolio`:** the 7 `DEMOHK …` portfolios, country Hong Kong, BU GFM, each mapped to a desk. One portfolio is given MxEntity "iWork only" so the scope filter visibly removes some trades. `DEMOHK ORPHAN 01` and `DEMOSG TEST PF` are deliberately absent.
- **`desk_profile`:** HKB Rates Desk, HKL Rates Desk, CMU-T&M, CMU-FX swap, each with allowed FGTs. One desk omits a FGT it actually trades (for example EQD/FUT) so BR-UC1-002 fires.
- **`trader_profile`:** TRD0001 to TRD0040. Most are TRADER; 2 are DESK SUPPORT and 2 are SALES (exempt); 2 have no AEGIS role (003); 2 are unlicensed with Pre-RNF = N (008). Most mandates cover the portfolios, FGTs and currencies the trader books; a few are narrower (missing one portfolio, one FGT or one currency) to trigger 005 to 007, and a few have an effective date of 2026-08-15 to trigger 009. UNKNOWN01 to 05 are not seeded (003 and 004).

Seeded cases already in the CSV (counted from the file): 2,520 internal deals, 812 after-hours bookings, 528 off-premises, 404 excluded trader IDs, 302 unknown traders, 301 trades on unmapped portfolios.

## 8. API

- `POST /api/v1/trades/upload` (multipart CSV): stores the file in `trades`, runs detection, returns the run summary.
- `POST /api/v1/runs/{id}/detect`: re-runs detection on already stored trades (clears that run's tags first).
- `GET /api/v1/runs/{id}` summary; `GET /api/v1/runs/{id}/tags` counts per rule; `GET /api/v1/runs/{id}/trades?tag=…` flagged trades with their tag details, paged.
- `GET /api/v1/rules`, `POST /api/v1/rules/reload`.

## 9. Additions beyond the sketch

- **Workflow handoff (optional, not yet decided):** group `trades_tags` by tag and send one call per group to a mock AEGIS workflow API (routing from the rules sheet: DQDM001 → DESK SUPPORT; DQDM002, DQTP002, all TCTM → DESK DELEGATE; DQTP001 → STAFF PROFILE DELEGATE + hiring manager). Behind a `WorkflowClient` interface so the real API can replace the mock.
- **Demo screen (in scope):** one static page served by the app (plain HTML and JS, no build step) at `/`. It lets you upload a CSV or click "Run sample file" for the 10k dummy trades, then shows the run summary (rows read, stored, failed, ID vs ED), a count per rule, and a table of flagged trades filterable by tag, with each trade's tags and failure details. It uses only the API in section 8.

## 10. Phases and done-criteria

### Phase 0: Project setup
Maven project, Spring Boot 3.5, Java 17 target, Flyway migrations for the sketch's tables, seed data, docker-compose for MariaDB.
**Done when:** the app starts against MariaDB, migrations run, the three profile tables are seeded, and `/actuator/health` is UP.

### Phase 1: Upload and store (sketch boxes 1-2)
Upload API, streaming CSV parser, batch writes to the partitioned `trades` table, run summary, row errors, central error handling and logging.
**Done when:** uploading the 10,000-row file stores 10,000 rows in `trades` with zero lost rows, spread across the Jul, Aug and Sep 2026 partitions.

### Phase 2: Detection engine (sketch boxes 3-5)
Trader identification (`M_TP_TRADER` → `trader_profile`), desk identification (`M_TP_PFOLIO` → `portfolio` → `desk_profile`), ID/ED classification, scope filter, SpEL rule engine with all 11 rules, writes to `trades_tags`.
**Done when:** every one of the 11 tags has a non-zero count in `trades_tags`, the ID count is 2,520, BR-UC1-001 is 301 (unmapped portfolios stay in scope), BR-UC1-010 and BR-UC1-011 match the after-hours and off-premises trades left after the scope filter (at most 812 and 528; exact figures recorded in the README), and a rule changed in YAML takes effect after `/rules/reload` and `/detect` without a rebuild.

### Phase 3: Package and test
Unit tests (one positive and one negative case per rule), parser tests (epoch µs, bytes literal, COB_DATE), an integration test with Testcontainers MariaDB over the full 10k file, a README, and one command to run everything (`docker compose up` for the app plus database).
**Done when:** `mvn verify` is green and `docker compose up` brings up a working demo from a clean checkout.

### Phase 4: Demo screen
The static page described in section 9.
**Done when:** someone can open the page, run the sample file or upload a CSV, and see the run summary, per-rule counts and the flagged trades with their tag details without using any other tool.

### Phase 5 (optional): Workflow handoff
As described in section 9, only if you want it.

## 11. Making it real later

1. Replace each seed table with the real source by adding a provider implementation (desk table, trader profile and mandate, iWork portfolio file).
2. Fill in `PrestoTradeSource` with the query from `rico-ada-service` and enable the `presto` profile and scheduler.
3. Replace `RemoteSessionProvider` with the VPN/Citrix login feed.
4. Add the other BR-FD-003 countries (Singapore, China, Taiwan, London) as scope rules.
5. If the workflow handoff is built, point its client at the real AEGIS workflow engine.

## 12. Open questions

Defaults are in brackets; the build can start on them.

1. **Repository.** Where should the code live? [Build and run it here until a GitHub repo is attached, then push.]
2. **1BankID field.** `M_TP_TRADER`, `M_EXE_TRADER` or `USER_NAME`? [`M_TP_TRADER`]
3. **Murex rollover time.** Per entity or one value, and in which time zone? [19:00, compared with `M_TP_TIMSYS` as given]
4. **BR-UC1-008 licence rule.** The middle of the condition is illegible. [Role TRADER and not licensed and Pre-RNF = N]
5. **Remote access source.** What feed carries VPN/Citrix logins? [`OFF_PREMISES` = Y for the demo]
6. **CORE trades.** How do CORE family/group/type map to desk FGTs? [Same lookup on `CORE_*` fields; demo data is Murex only]
7. **Rules after BR-UC1-011.** Are there more rows in the sheet that weren't photographed? [Build the 11 we have; new rules are YAML rows]
8. **Tag details.** What should `trades_tags.details` hold? [The values that failed the rule, as JSON]
9. **Out-of-scope trades.** Store them (flagged `in_scope = false`) or drop them? [Store]
10. **Workflow handoff.** Build it after the demo screen, or leave it out of this demo? [Leave it out for now]
