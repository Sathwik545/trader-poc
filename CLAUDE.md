# AEGIS detection engine demo (trader-poc)

Context for Claude Code. Read this first, then `docs/aegis-demo-implementation-plan.md`.

## What this is
A working demo of the AEGIS post-trade detection engine. A CSV of trades is uploaded, stored in MariaDB, then each trade's trader and desk are looked up, the business rules run, and one tag is written to `trades_tags` per rule the trade fails. A simple demo page shows the results.

## Decisions already made (2026-10-05, by Devender)
- **Dummy data for every step.** Trades: `docs/ada_deals_synthetic_10000.csv`. Trader, portfolio and desk profiles are invented seed data that match it. Prove the functionality first; real data comes later, so keep every source behind an interface.
- **Architecture follows `docs/architecture_sketch.jpeg`:** upload API → store all ~121 columns in `trades` → detection engine (trader by `M_TP_TRADER` → `trader_profile`; desk by `M_TP_PFOLIO` → `portfolio` → `desk_profile`) → run business rules → `trades_tags` (trade id, tag id, details).
- **Store first, then detect.** Detection reads the stored trades back.
- **Demo screen is in scope** (Phase 4): one static page served by the app.
- **Workflow API handoff is out** for now (optional Phase 5).
- **Stack:** Java 17, Spring Boot 3.5, Maven, MariaDB, Flyway. Rule conditions are SpEL expressions in a `detection_rule` table seeded from `rules.yaml`.
- **Defaults for open questions:** 1BankID = `M_TP_TRADER`; Murex rollover 19:00 compared with `M_TP_TIMSYS`; remote access = `OFF_PREMISES = 'Y'`; BR-UC1-008 = role TRADER and not licensed and Pre-RNF = N; out-of-scope trades are stored with `in_scope = false`.

## Source files in docs/
- `aegis-demo-implementation-plan.md`: the plan to build from (phases 0-4 with done-criteria).
- `aegis_detection_engine_prompt.md`: the original build prompt (functional design and tech spec).
- `AEGIS_business_rules.xlsx` and `source-notes/rules.md`: rules BR-FD-002/003 and BR-UC1-001..011.
- `ada_deal_columns.csv`: the 121 trade fields with types.
- `ada_deals_synthetic_10000.csv` + `generate_ada_deals.py` + `synthetic-data-README.md`: dummy trades and how they were seeded.
- `source-notes/`: transcriptions of the original screenshots.

## Data formats (from the synthetic file)
- Epoch fields are **microseconds** UTC. `M_TP_TIMSYS` is `HH:mm:ss`. `COB_DATE` is integer `yyyyMMdd`.
- Binary fields are Python bytes literals like `b'\x00\x00\x00\x00\x00'`.

## Expected counts in the 10k file (use as test assertions)
2,520 internal deals, 812 after-hours bookings (19:00-23:59), 528 off-premises, 404 excluded trader IDs, 302 unknown traders (UNKNOWN01-05), 301 trades on unmapped portfolios (`DEMOHK ORPHAN 01`, `DEMOSG TEST PF`).

## How to work
Build phase by phase, run `mvn verify` after each phase, and check the phase's done-criteria in the plan before moving on. Never put real bank data in code or fixtures.
