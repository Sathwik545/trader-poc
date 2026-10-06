# AEGIS deal detection service (demo)

A working demo of the AEGIS post-trade detection engine. A CSV of trades is uploaded and
stored in MariaDB. The engine then reads the stored trades back, looks up each trade's trader
and desk, classes the trade as an internal or external deal, runs the business rules and
writes one tag to `trades_tags` for every rule the trade fails.

Everything runs on dummy data: 10,000 synthetic trades and invented trader, portfolio and
desk profiles. No real bank data is in this repository.

## Run it

You need Java 17 or later and a local MariaDB (built and tested on 12.3).

1. Create the databases and the app login, once, as MariaDB root:
   ```
   mariadb -u root -p < db/create-local-database.sql
   ```
2. Start the app:
   ```
   .\run-demo.cmd
   ```
   It builds the jar and starts on http://localhost:8080. Flyway creates the tables and the
   seed data on first start.
3. Open http://localhost:8080 and click **Run sample file**, or upload from the command line:
   ```
   curl -F "file=@docs/ada_deals_synthetic_10000.csv" http://localhost:8080/api/v1/trades/upload
   ```

## Demo screen

One page at http://localhost:8080, plain HTML and JavaScript with no build step
(`src/main/resources/static/index.html`). It uses only the API below.

- **Run sample file** stores the 10,000 synthetic trades and runs detection. **Upload CSV**
  does the same for a file you choose.
- **Run summary**: rows read, stored and failed, trades in scope, internal and external deals,
  and any rows that could not be stored.
- **Trades tagged per rule**: one line per rule with its count. Click a rule to filter the
  trades below.
- **Flagged trades**: each tagged trade with its tags and the values that made each rule fire,
  25 per page.
- **Re-run detection** runs the rules again on the stored trades of the selected run.
  **Reload rules** re-reads `rules.yaml`.

`http://localhost:8080/?run=1&tag=TCTM006_LATE_TRADE_INSERTION` opens a run with one tag
selected.

To run the tests: `.\mvnw.cmd verify`. They use the `aegis_test` database and remove the runs
they create.

### Docker

`docker-compose.yml` starts a MariaDB 11 container with the same database name and login, for
machines where Docker is available. It has not been exercised: the machine this demo was built
on cannot run Docker, so the app and the tests were only ever run against a local MariaDB.
The integration tests therefore connect to a local MariaDB instead of using Testcontainers.

## What happens to a file

```
CSV ──▶ POST /api/v1/trades/upload
          │
          ▼
        parse and store every row ───────────────▶ trades        (all 121 columns)
          │                                        row_error     (rows that could not be read)
          ▼
        detection engine (reads the run's stored trades back)
          ├─ trader:  M_TP_TRADER ──▶ trader_profile
          ├─ desk:    M_TP_PFOLIO ──▶ portfolio ──▶ desk_profile
          ├─ BR-FD-002: internal (ID) or external (ED) deal
          ├─ BR-FD-003: scope filter
          └─ BR-UC1-001..011 on in-scope trades
          │
          ▼
        one row per rule that fires ─────────────▶ trades_tags   (trade id, tag id, details)
```

A bad row never fails the file: it is recorded in `row_error` with its row number and reason,
and the rest of the file is stored. A file that is unusable as a whole (empty, missing
columns, broken CSV) is rejected with a 400.

## API

| Method and path | What it does |
|---|---|
| `POST /api/v1/trades/upload` | Multipart upload, part named `file`. Stores the CSV, runs detection, returns the run summary. |
| `POST /api/v1/trades/sample` | Same, on the synthetic sample file kept on the server. Used by the demo screen. |
| `GET /api/v1/runs?limit=` | The most recent runs, newest first (default 20). |
| `GET /api/v1/runs/{id}` | The run summary again. |
| `POST /api/v1/runs/{id}/detect` | Re-runs detection on the trades already stored for the run and replaces its tags. |
| `GET /api/v1/runs/{id}/tags` | Trades tagged per rule, one line per rule. |
| `GET /api/v1/runs/{id}/trades?tag=&page=&size=` | Flagged trades with their tags and tag details. `tag` is optional; `size` is 1 to 500, default 50. |
| `GET /api/v1/rules` | The rules in the `detection_rule` table. |
| `POST /api/v1/rules/reload` | Re-reads `rules.yaml` into the table and the engine. |
| `GET /actuator/health` | Health check. |

Errors always have the same body: `timestamp`, `status`, `error`, `message`, `path`.

## Results on the sample file

`docs/ada_deals_synthetic_10000.csv`, 10,000 trades:

| Measure | Count |
|---|---|
| Rows read / stored / failed | 10,000 / 10,000 / 0 |
| Stored in the Jul / Aug / Sep 2026 partitions | 3,395 / 3,372 / 3,233 |
| Internal deals (ID) / external deals (ED) | 2,520 / 7,480 |
| In scope / out of scope | 8,624 / 1,376 |

| BR Ref | Tag | Blotter | Trades tagged |
|---|---|---|---|
| BR-UC1-001 | DQDM001_PORTFOLIO_NOT_FOUND | DQ | 301 |
| BR-UC1-002 | DQDM002_FGT_NOT_FOUND | DQ | 187 |
| BR-UC1-003 | DQTP001_STAFF_ROLE_NOT_FOUND | DQ | 680 |
| BR-UC1-004 | DQTP002_TRADER_NOT_FOUND | DQ | 259 |
| BR-UC1-005 | TCTM001_PORTFOLIO_NOT_ALLOWED | PT | 303 |
| BR-UC1-006 | TCTM002_FGT_NOT_ALLOWED | PT | 21 |
| BR-UC1-007 | TCTM003_CURR_NOT_ALLOWED | PT | 13 |
| BR-UC1-008 | TCTM004_TRADE_WO_LICENSE | PT | 403 |
| BR-UC1-009 | TCTM005_TRADE_BEFORE_EFF_DATE | PT | 210 |
| BR-UC1-010 | TCTM006_LATE_TRADE_INSERTION | PT | 627 |
| BR-UC1-011 | TCTM007_REMOTE_ACCESS | PT | 406 |

The file holds 812 after-hours bookings and 528 off-premises trades. BR-UC1-010 and 011 tag
fewer (627 and 406) because out-of-scope trades, system trader IDs on the exclusion list, and
DESK SUPPORT / SALES staff are not flagged. These counts are asserted in
`DetectionIntegrationTest`.

## Rules

The 11 BR-UC1 rules are data, not code. `src/main/resources/rules.yaml` holds each rule's
condition as a Spring Expression Language (SpEL) expression; the file's header lists the names
an expression can use. At start-up and on reload the file is copied into the `detection_rule`
table.

```yaml
- brRef: BR-UC1-010
  category: Trader Conduct - Trader Mandate
  tag: TCTM006_LATE_TRADE_INSERTION
  blotter: PT
  workflowRole: DESK DELEGATE
  condition: "!excluded(trade.trader) && !exempt() && afterRollover()"
  details:
    trader: "trade.trader"
    insertionTime: "trade.insertionTime"
```

`details` are evaluated when the rule fires and stored as JSON in `trades_tags.details`.

### Add or change a rule without rebuilding

1. Copy `src/main/resources/rules.yaml` to `config/rules.yaml` in the folder you start the app
   from. A file there takes precedence over the copy inside the jar.
2. Edit it: change a condition, or add a block with a new `brRef` and `tag`.
3. `curl -X POST http://localhost:8080/api/v1/rules/reload`
4. `curl -X POST http://localhost:8080/api/v1/runs/{id}/detect` to apply it to stored trades.

A file with an expression that does not parse is refused with a 400 and the rules in force
stay as they were. A rule removed from the file is switched off, not deleted, because earlier
tags still refer to it. A rule that needs data the expressions cannot reach yet needs a new
helper in `RuleContext`.

BR-FD-002 (internal / external) is in `DealClassifier`, and the BR-FD-003 scope criteria are
in `application.yml`; neither is in `rules.yaml`.

## Configuration

In `src/main/resources/application.yml`; each can be overridden on the command line
(`.\run-demo.cmd --aegis.detection.murex-rollover-time=18:30`) or by environment variable.

| Key | Default | Meaning |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` (environment) | `localhost`, `3306`, `aegis`, `aegis`, `aegis` | Database connection. |
| `SERVER_PORT` (environment) | `8080` | HTTP port. |
| `spring.servlet.multipart.max-file-size` | `50MB` | Largest upload accepted. |
| `aegis.demo.sample-file` | `docs/ada_deals_synthetic_10000.csv` | File the **Run sample file** button loads, relative to the folder the app is started from. |
| `aegis.ingest.batch-size` | `500` | Trades written per database batch. |
| `aegis.ingest.max-errors-shown` | `50` | Row errors returned in a run summary; all are kept in `row_error`. |
| `aegis.detection.chunk-size` | `1000` | Stored trades evaluated per chunk. |
| `aegis.detection.rules-locations` | `file:./config/rules.yaml`, then `classpath:rules.yaml` | Where the rules file is looked for; the first that exists is used. |
| `aegis.detection.scope` | Hong Kong, BU GFM, not "Close", not "iWork only" | BR-FD-003 criteria. |
| `aegis.detection.murex-rollover-time` | `19:00` | BR-UC1-010 compares `M_TP_TIMSYS` with this. |
| `aegis.detection.exclusion-list` | `DBSINTF`, `DBSINTFD`, `SETL%`, `BATCHUSER`, `AUTOJOB`, `ITGSUPP1`, `ITGSUPP2`, `FITX%`, `SYSTEM%`, `SYNSGCREDITUS%` | Trader IDs the trader rules skip; `%` is a wildcard. |

## Assumptions

Open questions from the business rules sheet, and the defaults this demo uses.

| Question | Demo default |
|---|---|
| Which field is the 1BankID? | `M_TP_TRADER`. |
| Murex rollover time | 19:00, compared with `M_TP_TIMSYS` as given; 19:00:00 itself counts as after rollover. |
| Remote access source (VPN / Citrix) | The trade's `OFF_PREMISES = 'Y'` flag. |
| BR-UC1-008, whose middle is illegible in the source | Role is TRADER, not licensed, and Pre-RNF approval is N. |
| Which rules does the exclusion list apply to? | BR-UC1-003 to 011. The sheet names it for 003 and 004; the demo also keeps system IDs out of the trader-conduct rules. Remove `!excluded(trade.trader)` from a rule in `rules.yaml` to change that. |
| A trader with no mandate at all | Tagged by BR-UC1-004 only; the mandate rules 005 to 007 and 009 skip them. |
| Mandate effective date (BR-UC1-009) | One date per trader, compared with the UTC date of `M_TP_DTESYS`. The sheet implies a date per portfolio / FGT / currency. |
| Trades on a portfolio missing from iWork | Stay in scope, so BR-UC1-001 can flag them. |
| Out-of-scope trades | Stored with `in_scope = false`, classed ID / ED, not run through the rules. |
| Scope criteria | Only Hong Kong is modelled; the legal-entity list is not checked. |
| CORE trades | FGT key built from the `CORE_*` fields the same way; the sample data is Murex only, so this path is untested. |
| Text column width | `VARCHAR(100)` (comments 500), to fit MariaDB's row size limit with 121 columns. A longer value fails that row only. |

## Making it real

Each data source sits behind an interface, so a real source is a new implementation, not a
change to the engine.

| Interface | Demo implementation | Real source |
|---|---|---|
| `TradeSource` | `CsvTradeSource` (upload) | `PrestoTradeSource`, a stub behind the `presto` profile |
| `TraderProfileProvider` | `trader_profile` table | AEGIS staff roles and trader mandate |
| `PortfolioProvider` | `portfolio` table | The daily iWork file |
| `DeskProfileProvider` | `desk_profile` table | The AEGIS desk table |
| `RemoteSessionProvider` | `OFF_PREMISES` flag | VPN / Citrix login logs |

Not built: the workflow handoff to the AEGIS workflow engine, the scheduled Presto pull, and
the scope criteria for countries other than Hong Kong.

## Project layout

```
src/main/java/com/demo/aegis
├── api       REST controller and the common error body
├── ingest    CSV reading, parsing, batch writing, run summary
├── engine    detection engine, trader and desk lookup, ID/ED, scope filter
├── rules     rule loading from YAML, SpEL evaluation, the names rules can use
├── persist   JdbcTemplate repositories and the table-backed providers
└── config    typed configuration
src/main/resources
├── db/migration   Flyway migrations: tables, then seed data
├── static/index.html   the demo screen
├── rules.yaml     the 11 rules
└── ada_deal_columns.csv   the 121 trade fields and their types
docs/              the plan, the rules workbook, the synthetic data and its generator
```
