# PowerBI → Metabase AI Agent: Migration Plan

> **Profile:** Java/Spring Boot Developer (12 years experience)
> **Target:** Migrate 10–100 PowerBI reports fully to Metabase using an AI-powered agent

---

## 1. Is It Possible? ✅ Yes

This is absolutely doable. The key enablers are:

- **PowerBI `.pbix` files** are ZIP archives — you can extract JSON metadata, data models, DAX measures, and layout info programmatically
- **Metabase has a full REST API** to create dashboards, cards (questions), collections, and data source connections
- **LLMs are excellent** at converting DAX → SQL, understanding visual intent, and mapping concepts across tools
- **Screenshots** can be handled via vision models to infer layout and chart types

> ⚠️ **Hardest parts:** DAX → SQL conversion (DAX is complex) and faithfully recreating layout/visual fidelity.

---

## 2. Effort Estimation

| Phase | What It Involves | Effort |
|-------|-----------------|--------|
| **Phase 1** | Parse `.pbix` files, extract DAX, data model, layout | 2–3 weeks |
| **Phase 2** | DAX → SQL conversion via LLM | 2–3 weeks |
| **Phase 3** | Metabase API integration (create cards, dashboards) | 1–2 weeks |
| **Phase 4** | Screenshot → layout inference (vision model) | 1–2 weeks |
| **Phase 5** | Orchestration, error handling, human review loop | 2–3 weeks |
| **Phase 6** | Testing on your 10–100 reports | 2–3 weeks |

- **MVP:** ~4–6 weeks
- **Production-grade agent:** ~3–4 months (solo developer)

---

## 3. Technology Stack

### Core Agent Framework
| Tool | Notes |
|------|-------|
| **LangChain4j** | Java-native LLM framework, Spring Boot compatible, mature ecosystem — **recommended** |
| **Spring AI** | Newer, native Spring Boot integration, simpler but less feature-rich |

> Avoid Python-first tools (LangGraph, LangChain Python) unless you're comfortable switching ecosystems.

### LLM
| Tool | Notes |
|------|-------|
| **Claude (Anthropic API)** | Excellent for DAX→SQL, large context window, strong code/logic conversion — **recommended** |
| **GPT-4o** | Strong alternative, also supports vision for screenshot parsing |

### PowerBI Parsing
- `.pbix` is a ZIP — use standard **Java ZIP libraries**
- Extract `DataModel/model.bim` (JSON) → tables, measures, relationships
- Extract `Report/Layout` (JSON) → visual positions and chart types

### DAX → SQL Conversion
- LLM-powered conversion with structured prompt chains
- Build a **validation step** that runs the SQL against your actual DB and checks for errors
- Target SQL dialect depends on your underlying database (PostgreSQL, Snowflake, BigQuery, etc.)

### Metabase Integration
- **Metabase REST API** (well-documented)
- Use Spring's `RestClient` / `WebClient` — fits naturally into your Spring Boot stack
- Supports: creating dashboards, questions/cards, collections, data source connections

### Screenshot Handling
- **Claude Vision** or **GPT-4o Vision** to infer chart type, layout, and filters
- Use as fallback or cross-validation against `.pbix` parsed data

### Orchestration
- **LangChain4j Agents** for multi-step pipeline orchestration
- Or **Spring Batch** for a pure Java pipeline (no LLM orchestration overhead)

---

## 4. Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│                  INPUT LAYER                        │
│  .pbix files │ JSON/XML exports │ Screenshots       │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│               PARSING AGENT                         │
│  - Unzip .pbix                                      │
│  - Extract DataModel/model.bim (DAX, tables)        │
│  - Extract Report/Layout (visuals, positions)       │
│  - Vision model for screenshots                     │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│            CONVERSION AGENT (LLM)                   │
│  - DAX measures → SQL queries                       │
│  - Map PowerBI visual types → Metabase chart types  │
│  - Map data source connections                      │
│  - SQL validation against target DB                 │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│           HUMAN REVIEW LOOP  👤                     │
│  - Flag low-confidence conversions                  │
│  - Show diff: original vs converted                 │
│  - Approve / correct before publishing              │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│           METABASE PUBLISHING AGENT                 │
│  - Create questions/cards via REST API              │
│  - Recreate dashboard layout                        │
│  - Set up collections and permissions               │
└─────────────────────────────────────────────────────┘
```

---

## 5. Recommended Project Structure (Spring Boot)

```
powerbi-metabase-agent/
├── src/main/java/com/yourorg/agent/
│   ├── parser/
│   │   ├── PbixParser.java              # Unzip & extract .pbix
│   │   ├── DataModelExtractor.java      # Parse model.bim JSON
│   │   └── LayoutExtractor.java         # Parse Report/Layout JSON
│   ├── conversion/
│   │   ├── DaxToSqlConverter.java       # LLM-powered DAX→SQL
│   │   ├── VisualTypeMapper.java        # PowerBI visual → Metabase chart
│   │   └── SqlValidator.java            # Run & validate converted SQL
│   ├── metabase/
│   │   ├── MetabaseApiClient.java       # REST API wrapper
│   │   ├── DashboardPublisher.java      # Create dashboards
│   │   └── CardPublisher.java           # Create questions/cards
│   ├── orchestration/
│   │   ├── MigrationPipeline.java       # LangChain4j agent / Spring Batch
│   │   └── HumanReviewService.java      # Review queue logic
│   └── AgentApplication.java
├── src/main/resources/
│   ├── prompts/
│   │   ├── dax-to-sql.txt               # DAX conversion prompt template
│   │   └── visual-mapping.txt           # Visual type mapping prompt
│   └── application.yml
└── pom.xml
```

---

## 6. Key Questions to Resolve Before Starting

These will significantly affect your design decisions:

| # | Question | Why It Matters |
|---|----------|---------------|
| 1 | **What is your underlying database?** (PostgreSQL, Snowflake, BigQuery?) | DAX→SQL translation depends heavily on the target SQL dialect |
| 2 | **How consistent are your PowerBI reports?** | Consistent naming/modeling conventions = much higher conversion accuracy |
| 3 | **100% fidelity or ~80% + human review?** | A human-in-the-loop step is strongly recommended for production use |
| 4 | **Who validates migrated dashboards?** | Need a QA process where humans confirm Metabase output matches original |
| 5 | **Are data sources already in a DB Metabase can connect to?** | If PowerBI pulls from Excel/CSV, that's a separate data migration problem |
| 6 | **One-time migration or ongoing?** | Changes the architecture significantly (batch job vs continuous pipeline) |

---

## 7. Recommended Starting Point

> **Spring Boot + LangChain4j + Claude API**

### Step-by-step approach:
1. **Start with Phase 1** — write a Spring Boot utility to unzip a `.pbix` and print the extracted DAX measures and visual types. No LLM needed yet.
2. **Then Phase 2** — send extracted DAX to Claude API and get SQL back. Validate it against your DB.
3. **Build a simple Metabase card** from the converted SQL using the REST API.
4. **Only then** add orchestration (LangChain4j / Spring Batch) to chain everything together.

> Get the **hardest problem (DAX conversion) working first** before building the full pipeline.

---

## 8. Useful Resources

| Resource | Link |
|----------|------|
| LangChain4j Docs | https://docs.langchain4j.dev |
| Spring AI Docs | https://docs.spring.io/spring-ai/reference |
| Metabase API Docs | https://www.metabase.com/docs/latest/api-documentation |
| Anthropic API Docs | https://docs.anthropic.com |
| PowerBI .pbix format reference | https://learn.microsoft.com/en-us/power-bi/developer/projects/projects-overview |

---

*Document generated: March 2026*
