# AEGIS detection engine prompt (transcribed from prompt_1, prompt_2 photos)

Source: Sublime Text, file `prompts/aegis/automated_detection_engine.md` (also a copy under `aegis/aegis-deal-detection-service`). Transcribed from photos; wording lightly cleaned, line ends cut off on the right edge of prompt_2 are marked `[…]`.

## Role
You are an expert Senior Java Developer working on the AEGIS Automated detection and rule engine.

## Context
AEGIS is one of Global Financial Markets application primarily working on the post trades booking monitoring related to different risk controls monitoring. As part of this monitoring, AEGIS needs to ingest the daily transaction data from Murex / ADA and need to perform the different detection logics and the tag to the different use cases and also to the different detection codes.

## High Level Functional Design
The high level flow for the detection engine can be shown as different sequential and parallel steps. The high level design includes steps like data ingestion, apply in-scope / out of scope criteria for each use case, review detection step and final triggering the related workflows.

### 1. Data ingestion
a. The data ingestion step includes to process the data coming from iwork. The data from iwork can be fetched either from a file via DriveX or directly calling presto using jdbc connection. The sample schema and field definition is as per `ada_deal_schema.csv`.
b. AEGIS should process trade data and need to enrich the data with some additional data points. The data enrichment is mainly to identify additional data points related to the trade and enrich the trade data.
   i) Desk profile – Identify the related desk from AEGIS desk table and tag the complete json data of the desk.
   ii) Trader profile – Identify the related trader profile from AEGIS database based on `M_TP_TRADER` matching field and map the trader profile completely.
   iii) Portfolio – identify the related iwork portfolio data from iwork file and enrich with the trade data.
c. Once the enrichment is completed, the complete Trade + Desk profile + Trader profile + portfolio data can be send for next step.

(Note: there is no step 2 in the original; numbering jumps 1 → 3.)

### 3. Inscope / Out of Scope Rule
In this step, first step is to identify the trade as internal or external depends on the logic. Once the trade is tagged as Internal or external then different in scope or out of scope checks to be executed.

Logic for internal or external deals:
```
IF
    Counterparty label = iWork portfolio code (original iWork file) or
    Counterparty label contain "BONDS" or "BOND" + Counterparty Type = "Internal Party"
THEN "INTERNAL DEAL"
ELSE "EXTERNAL DEAL"
```

### 4. Usecase detection rule and review code tagging
In this step, all the deals either internal or external deals are verified against list of conditions and if the condition is met then the respective detection code or review tags are to be added to the trade data. All the conditions and detection codes and review tags are mentioned in the sheet 'TCUC1_DQ+TM' of `/Users/devender/git/prompts/aegis/Business Rule Requirement_14 Sep.xlsx`. The Column F is the Detection Code or Review Tag to be updated when the Column D – Business Requirement Rule is satisfied.
The key step is all the rules are to be executed in sequence and identify those rules that meet the criteria and can be added to the Trade. The Trade data may enriched with multiple Review Tags.

(Note from the photos: in the rules sheet the backend tag is actually in column E, "Tag to be appended (BackEnd)"; column F is "Tag Ref (Blotter)" with values DQ / PT. See rules.md.)

### 5. Triggering Workflows
In this step, each detection code will have its own workflows. Some codes requires to Send the data to Trader for their clarification and some detection codes requires to send to Data Rectification by Desk Support or Desk Delegated Inputter. The workflows are existing and in the proposed solution of the detection engine, the solution is to aggregate the daily deals by each detection code and use case and then trigger an API of aegis workflow engine service.

## Technical Specifications
Implement the above requirements as a Java and Spring Boot microservice. The data can be saved to MariaDB. Detailed instructions for the Code generation as […]

1. Use Java and latest JDK 17 features as and when required
2. Use Spring Boot 3.5 version and all the latest features
3. Implement common exception logic where all the application errors are to be handled at central place.
4. The data source can be either an CSV file or via a Presto JDBC template. Implement two different approaches, one is a POST API that can accept an CSV fi[le] input and should be able to process all the above steps. The another one is a scheduler job that can connect to Presto Using jdbc tables. The connection an[d] fetching data code is available in another repo: an internal Bitbucket repo `rico-ada-service` (branch `release/202608.04`), also local at `/Users/devender/git/rico/rico-ada-service`. (Not reachable from this project.)
5. Implement proper application logging as info, warning messages
6. As the trade data is quite huge and no of columns are around 123. Optimise mariadb table creation and if possible partition data based on month of the ye[ar] of the transaction date.
7. Implement all the business rules verification and detection code tagging as a rule engine and all these rules to be either configured as configuration fi[le or] load from database. Explore the various Java based rule engine frameworks to incorporate such a logic.
8. Provide a detailed technical implementation plan and details before actually do a code changes.
9. Ask questions wherever clarifications required such as mapping the ada_deal_schema with the business rules provided etc.
