# How the AEGIS detection demo works, start to end

This follows one trade file from the moment the app starts to the moment a reviewer sees a
tagged trade on the screen. Each step names the tables it touches and the class that does the
work, so the code can be found from here.

## The flow at a glance

```
 App starts
   ├─ Flyway creates tables and loads the dummy profiles
   └─ rules.yaml is copied into detection_rule and compiled
        │
 User uploads a CSV (or clicks "Run sample file")
        │
        ▼
 STAGE 1: STORE
   read the file row by row ─▶ check each value ─▶ write to `trades` in batches of 500
                                    │
                                    └─ bad row ─▶ `row_error`, and carry on
        │
        ▼
 STAGE 2: DETECT   (reads the stored trades back, 1,000 at a time)
   for each trade:
     1. find the trader      M_TP_TRADER ─▶ trader_profile
     2. find the desk        M_TP_PFOLIO ─▶ portfolio ─▶ desk_profile
     3. internal or external deal (ID / ED)
     4. in scope or not
     5. run the 11 rules  ─▶ one row in `trades_tags` per rule the trade breaks
        │
        ▼
 Run summary saved to `pipeline_run` and returned
        │
        ▼
 Demo screen shows the summary, the count per rule, and each flagged trade with the reason
```

Store first, then detect: the two stages are separate on purpose. Detection can be run again
on trades already stored, for example after a rule changes, without uploading the file again.

## Step 0: What happens when the app starts

| What | Detail | Where |
|---|---|---|
| Tables are created | Flyway runs the migration files in order, once per database. | `src/main/resources/db/migration/V1`–`V8`, `V12` |
| Dummy profiles are loaded | 7 portfolios, 4 desks, 40 traders. | `V9`–`V11`, adjusted by `V13` |
| Rules are loaded | `rules.yaml` is read, each condition is checked for syntax, and the rules are written to `detection_rule`. | `RuleLoader`, `RuleEngine` |

After start-up the database has reference data and rules, but no trades until a file is
uploaded.

## Step 1: The file arrives

The user either uploads a CSV on the demo screen or with
`POST /api/v1/trades/upload`, or clicks **Run sample file**, which loads
`docs/ada_deals_synthetic_10000.csv` from the server.

The controller (`DealDetectionController`) does three things in order: store the file, run
detection on it, return the summary.

A new row is added to `pipeline_run` with status `RUNNING`. Its id is the **run id**; every
trade, tag and error from this file carries it.

## Step 2: Stage 1, store

**Reading (`CsvTradeSource`).** The file is read as a stream, one row at a time, so a large
file is never held in memory. Columns are matched by header name, so their order does not
matter and extra columns are ignored. If the file is empty, has no header, or lacks any of the
121 expected columns, the whole file is rejected with a clear message.

**Checking each value (`TradeParser`, `FieldConverters`).** The 121 fields and their types
come from `ada_deal_columns.csv`.

| Field type | Example in the file | Stored as |
|---|---|---|
| Whole number | `337000003` | number |
| Decimal | `12061.785` | decimal |
| Date/time | `1783296000000000` (microseconds since 1970, UTC) | number; the trade date is worked out from `M_TP_DTETRN` |
| Time | `10:14:08` | text, after checking it is a real time |
| Binary | `b'\x00\x00\x00\x00\x00'` | bytes |
| Text | `DEMOHK IRD DY` | text |
| Empty cell | | null |

**Writing (`TradeWriter`).** Good rows are written to `trades` 500 at a time. The table is
split into monthly partitions by trade date, so July, August and September trades sit in
separate partitions.

**When a row is bad.** A row that cannot be read (a letter where a number should be, the wrong
number of columns, no trade date) is written to `row_error` with its row number and the
reason, and the next row is processed. If the database refuses a batch (for example a value
too long for its column), that batch is retried one row at a time so only the bad row is lost.
One bad row never fails the file.

At the end of this stage `pipeline_run` holds rows read, rows stored and rows failed, and the
status is `STORED`. Read always equals stored plus failed.

## Step 3: Stage 2, detect

`DetectionEngine` first loads all traders, portfolios and desks into memory once, then reads
the run's trades back from the database 1,000 at a time. Trades within a chunk are evaluated
in parallel.

For each trade:

### 3.1 Find the trader
The trade's `M_TP_TRADER` is looked up in `trader_profile`. If found, the engine now knows the
trader's role, whether they are licensed, and their mandate (allowed portfolios, products,
currencies, and the date it starts). If not found, the trader is "unknown".

### 3.2 Find the desk
The trade's `M_TP_PFOLIO` is looked up in `portfolio`. That row names a desk and sub-desk,
which is looked up in `desk_profile` to get the products the desk may trade. A portfolio that
is not in `portfolio` has no desk.

### 3.3 Internal or external deal (BR-FD-002)
The trade is an **internal deal (ID)** if its counterparty is one of the bank's own portfolio
codes, or the counterparty name contains "BOND" and the counterparty type is "Internal Party".
Otherwise it is an **external deal (ED)**. This is a label, not a pass or fail.
(`DealClassifier`)

### 3.4 In scope or not (BR-FD-003)
A trade is in scope when its portfolio is Hong Kong, business unit GFM, not classified
"Close", and not "iWork only". Out-of-scope trades are kept but skip the rules. A trade whose
portfolio is unknown stays in scope, so that rule 001 can report it. (`ScopeFilter`)

### 3.5 Run the rules
Each of the 11 rules is a one-line condition from `rules.yaml`. For every in-scope trade,
every rule is evaluated. When a condition is true, the trade has broken that rule and one row
is written to `trades_tags`: the trade id, the rule's id, the run id, and a small JSON object
with the values that caused it. (`RuleEngine`, `RuleContext`)

**A tag means a failed check.** A trade that passes every rule gets no rows in `trades_tags`.
A trade can break several rules and get several tags.

Several rules skip two groups: system and interface user IDs on the exclusion list (such as
`BATCHUSER` or `SETL…`), and staff whose role is DESK SUPPORT or SALES.

### 3.6 Save the results
For each chunk, in one database transaction: `trades.deal_class` and `trades.in_scope` are
updated, and the new tags are inserted. When all chunks are done, `pipeline_run` gets the
in-scope count, the ID and ED counts, the count per rule, and status `COMPLETED`.

## Step 4: A worked example

One real trade from the sample file:

| Field | Value |
|---|---|
| Deal number | 337011983 |
| Trader | TRD0018 |
| Portfolio | DEMOSG TEST PF |
| Counterparty | EPSILON BANK NYC |
| Product | CURR/FXD/SWLEG |
| Booked at | 16:14:51 |

1. **Trader:** TRD0018 is found. Role TRADER, **not licensed**, Pre-RNF approval N.
2. **Desk:** `DEMOSG TEST PF` is not in the `portfolio` table, so there is no desk.
3. **Internal or external:** the counterparty is an outside bank, so **ED**.
4. **Scope:** the portfolio is unknown, so the trade stays in scope.
5. **Rules:** three fire.

| Tag written | Why |
|---|---|
| DQDM001_PORTFOLIO_NOT_FOUND | The portfolio maps to no desk. |
| TCTM001_PORTFOLIO_NOT_ALLOWED | The portfolio is not in TRD0018's mandate. |
| TCTM004_TRADE_WO_LICENSE | TRD0018 is a trader without a licence. |

Rule 010 (late booking) does not fire because 16:14 is before the 19:00 rollover, so no tag is
written for it.

## Step 5: Seeing the results

The demo screen at `http://localhost:8080` calls the API and shows:

| On screen | API call | Comes from |
|---|---|---|
| Run summary | `GET /api/v1/runs/{id}` | `pipeline_run`, `row_error` |
| Trades tagged per rule | `GET /api/v1/runs/{id}/tags` | `trades_tags` joined to `detection_rule` |
| Flagged trades and reasons | `GET /api/v1/runs/{id}/trades?tag=` | `trades_tags` joined to `trades` |

## Step 6: Changing a rule

1. The business changes a rule. Someone edits the matching line in `config/rules.yaml` (a copy
   of the shipped file placed next to the running app).
2. **Reload rules** on the screen, or `POST /api/v1/rules/reload`. The file is checked; if an
   expression is wrong it is refused and the current rules stay in force.
3. **Re-run detection**, or `POST /api/v1/runs/{id}/detect`. The run's old tags are deleted and
   detection runs again on the stored trades.

No rebuild, no restart, no re-upload.

## What each table holds after a run of the sample file

| Table | Rows | Meaning |
|---|---|---|
| `pipeline_run` | 1 | The run and its totals |
| `trades` | 10,000 | Every trade, labelled ID/ED and in or out of scope |
| `trades_tags` | 3,410 | One per broken rule, across 2,551 trades |
| `row_error` | 0 | The sample file has no bad rows |
| `trader_profile`, `portfolio`, `desk_profile` | 40, 7, 4 | Reference data, unchanged by a run |
| `detection_rule` | 11 | The rules, unchanged by a run |

## What does not happen yet

- Nobody is notified. Each rule names who should act (Desk Support, Desk Delegate, Staff
  Profile Delegate), but tags are not sent to the AEGIS workflow engine.
- Trades are not pulled automatically. A person uploads a file; the scheduled feed from Presto
  is a placeholder.
- The reference data is invented. Real trader, portfolio and desk data would replace the three
  seed tables.

See `gaps-and-upgrades.md` for the full list.
