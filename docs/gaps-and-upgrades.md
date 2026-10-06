# AEGIS detection demo: gaps, suggestions and upgrades

Status on 2026-10-05: Phases 0 to 4 of the demo plan are built and tested (57 automated tests
pass). This lists what is missing or weak, what to confirm with the business, and what to
build next, in the order I would do it.

## Summary

| Priority | Item | Type | Size |
|---|---|---|---|
| 1 | Confirm the rule assumptions with the business | Decision | Small |
| 2 | Click through the demo screen once | Check | Small |
| 3 | Stop the same file being loaded twice | Gap | Small |
| 4 | Tests for the untested paths | Gap | Small |
| 5 | Mandate effective date per portfolio / product / currency | Gap | Medium |
| 6 | Workflow handoff to the AEGIS workflow engine | Upgrade | Medium |
| 7 | Elasticsearch and Kibana for search and dashboards | Upgrade | Medium |
| 8 | Login and access control | Upgrade | Medium |
| 9 | Real data sources in place of the dummy tables | Upgrade | Large |
| 10 | Scheduled trade feed from Presto | Upgrade | Medium |
| 11 | Scope rules for the other countries | Upgrade | Medium |
| 12 | Docker and Testcontainers | Gap | Small, once Docker runs |

## 1. Decisions needed from the business

The rules sheet left these open. The demo uses a default for each; a wrong default changes the
counts.

| Question | Demo default | Effect if different |
|---|---|---|
| Which field is the trader's 1BankID? | `M_TP_TRADER` | Every trader rule looks up a different ID. |
| Does the exclusion list apply to all trader rules or only 003 and 004? | All of 003 to 011 | If only 003/004, system IDs get tagged for late booking and remote access; rules 010 and 011 rise. |
| Murex rollover time, and is it per entity? | 19:00 for everything; 19:00:00 counts as late | Rule 010 count changes. |
| Rule 008 licence check (middle of the rule is unreadable in the source) | Role TRADER, not licensed, Pre-RNF = N | Rule 008 count changes. |
| Where does remote-access information come from? | The trade's `OFF_PREMISES` flag | Real VPN / Citrix logs would replace it. |
| Should a trader with no mandate get the mandate tags too? | No, only rule 004 | If yes, rules 005 to 007 rise. |
| Are there rules after BR-UC1-011? | Only the 11 photographed | New rules are new lines in `rules.yaml`. |
| How do CORE trades map to products? | Same lookup on the `CORE_*` fields | Untested: the sample data is Murex only. |

## 2. Gaps in what is built

### Functional
- **Duplicate uploads.** Loading the same file twice stores it twice under two run ids.
  Suggestion: reject, or warn, when a file with the same name and content hash was already
  loaded.
- **Mandate effective date is one date per trader.** The sheet implies a date per portfolio,
  product and currency. Needs a mandate table with one row per permission.
- **Remote working permission is unused.** `trader_profile.remote_allowed` exists but rule 011
  flags every remote booking regardless.
- **Legal entity is not checked** in the Hong Kong scope rule.
- **Internal/external and scope rules are in code and config,** not in `rules.yaml` with the
  other 11. Changing them needs a developer.
- **A whole-file rejection leaves a FAILED run** in `pipeline_run`, and rows buffered before a
  mid-file break are not stored.
- **No way to delete or archive a run** from the API or the screen.

### Testing
- Not exercised: two uploads at once; files over 10,000 rows; trade dates outside Jul to Dec
  2026; a rule that errors on some trades at run time; a database outage mid-run; files that
  are not UTF-8.
- The demo screen was checked by screenshot only. Its buttons were not clicked in a real
  browser by the developer.
- Tests run against a local MariaDB, not a throwaway container, so they need the `aegis_test`
  database to exist.

### Technical
- **Docker has never been run** for this project (the build machine has no WSL).
  `docker-compose.yml` is untested and there is no container image for the app.
- **Text columns are 100 characters** (comments 500) to fit MariaDB's row size limit. A longer
  value fails that row.
- **Partitions stop at December 2026.** Later trades go to a catch-all partition; new monthly
  partitions need adding over time.
- **Detection runs inside the upload request.** Fine for 10,000 rows (about 5 seconds); a very
  large file would hold the request open. Suggestion: run it in the background and let the
  screen poll for the result.
- **The database password is a default in the config file.** Acceptable for a demo only.

## 3. Upgrades

### 3.1 Workflow handoff (the plan's optional Phase 5)
Today a tag records who should act but nobody is told. Build: group a run's tags by rule and
send one request per group to the AEGIS workflow engine, routed by the role already stored on
each rule (DQDM001 to Desk Support; DQDM002, DQTP002 and all TCTM to Desk Delegate; DQTP001 to
Staff Profile Delegate and the hiring manager). Put it behind an interface with a mock for
local runs, retry on failure, and show the outcome in the run summary. A workflow failure must
not lose tags already saved.

### 3.2 Elasticsearch and Kibana
MariaDB stays the system of record; Elasticsearch becomes a searchable copy.
- After detection, send the run's trades to Elasticsearch in bulk: one document per trade
  (about 25 useful fields, trader role, desk, ID/ED, in scope, list of tags) and one document
  per tag with its reason.
- Kibana dashboards: exceptions per rule over time, by trader, desk and portfolio; internal
  versus external; bookings by hour; drill-down to one trade.
- Off by default and behind an interface, so the app runs without it. Re-running detection
  replaces that run's documents.
- Needs Elasticsearch and Kibana installed locally (no Docker here), about 2 to 4 GB of
  memory, and a login or API key.

### 3.3 Login and access control
The API and the screen are open to anyone who can reach the port. Before any shared
environment: sign-in (the bank's single sign-on), roles for who may upload, re-run detection
and reload rules, and an audit record of who did what.

### 3.4 Real data sources
Each source already sits behind an interface, so each is a new implementation rather than a
change to the engine.

| Interface | Today | Replace with |
|---|---|---|
| `TraderProfileProvider` | `trader_profile` seed table | AEGIS staff roles and trader mandate |
| `PortfolioProvider` | `portfolio` seed table | The daily iWork file |
| `DeskProfileProvider` | `desk_profile` seed table | The AEGIS desk table |
| `RemoteSessionProvider` | `OFF_PREMISES` flag | VPN / Citrix login logs |
| `TradeSource` | CSV upload | Presto (below) |

Real data must never be put in the repository or the test fixtures.

### 3.5 Scheduled trade feed from Presto
`PrestoTradeSource` is a placeholder. Build the query, connection settings and paging, a
scheduled daily job, and a guard so a day is not loaded twice.

### 3.6 Other countries
Only Hong Kong's scope rule is modelled. Singapore, China, Taiwan and London each need their
criteria added; Singapore's has several special cases, and London's is cut off in the source.

### 3.7 After a tag is raised
The rules sheet describes what follows a tag: an ageing clock (T+3 business days), and a
"rectified" or "reviewed" outcome. None of that exists. It probably belongs with the workflow
engine, but the tag table would need a status and dates.

### 3.8 Operations
- A rule-change history: who changed which rule, when, and the old and new condition.
- Metrics and alerts: run duration, rows failed, tags per rule against a normal range.
- Housekeeping: archive or drop old partitions and old runs.
- A rule editor on the screen, so the business can change a condition without editing a file.

## 4. Suggested order

1. **Before the demo:** items 1 and 2 in the summary. They cost nothing and remove the
   likeliest surprises.
2. **Straight after:** duplicate-upload check, the missing tests, and the per-permission
   effective date. These make the current scope solid.
3. **Next phase, pick by audience:** workflow handoff if the goal is to show the full control
   loop; Elasticsearch and Kibana if the goal is exploration and reporting.
4. **Before any real data:** login and access control, then the real data sources one at a
   time, each with its own reconciliation against a known day.
