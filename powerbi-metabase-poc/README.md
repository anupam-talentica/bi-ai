# PowerBI → Metabase POC

Proof-of-concept: extract DAX measures from a `.pbix` file, convert to SQL via a configurable LLM (Claude, OpenAI, or Gemini), and create a Metabase card.

## What it does

1. **Extract** – Unzips the `.pbix` (ZIP), reads `DataModelSchema` (UTF-16 LE JSON), parses out DAX measures.
2. **Convert** – Sends the first measure’s DAX to the configured LLM (Claude, OpenAI, or Gemini) and gets back a SQL query.
3. **Publish** – Creates a Metabase card (native question) with that SQL and prints the card URL.

## Requirements

- **Java 21**
- **Maven 3.8+**
- **An API key for one LLM** – Claude (Anthropic), OpenAI, or Gemini (see Configuration)
- **Metabase instance** (optional; you can skip publish with `POC_SKIP_METABASE=true`)
- **A `.pbix` file** (Power BI report with at least one measure)

## Build

```bash
cd powerbi-metabase-poc
mvn clean package
```

## Run

### Full flow (extract + convert + create Metabase card)

**Using Gemini (default):**
```bash
export LLM_PROVIDER=gemini
export GEMINI_API_KEY=your-gemini-api-key
export METABASE_URL=https://metabase.yourorg.com
export METABASE_USER=your-email@example.com
export METABASE_PASSWORD=your-password
export METABASE_DB_ID=2

java -jar target/powerbi-metabase-poc-0.0.1-SNAPSHOT.jar /path/to/your/report.pbix
```

**Using Claude:** set `LLM_PROVIDER=claude` and `CLAUDE_API_KEY=sk-ant-...`  
**Using OpenAI:** set `LLM_PROVIDER=openai` and `OPENAI_API_KEY=sk-...`

### Extract + convert only (no Metabase)

Useful if you don’t have Metabase or only want to test DAX → SQL:

```bash
export GEMINI_API_KEY=your-gemini-api-key
export POC_SKIP_METABASE=true

java -jar target/powerbi-metabase-poc-0.0.1-SNAPSHOT.jar /path/to/your/report.pbix
```

### Run from IDE

1. Open the main class: **`src/main/java/com/credila/poc/PocApplication.java`**
2. Run it (e.g. right‑click → Run, or your IDE's "Run" for the main method).
3. Set **Program arguments** to the path of your `.pbix` file, e.g. `/path/to/your/report.pbix`
4. Set the same environment variables as above (or override in `src/main/resources/application.yml`).

| Option | Command / action |
|--------|-------------------|
| **JAR** | `java -jar target/powerbi-metabase-poc-0.0.1-SNAPSHOT.jar /path/to/report.pbix` |
| **IDE** | Run `PocApplication.java` with program args = path to `.pbix` |

## Configuration

### LLM provider (DAX → SQL)

Set **one** of the following; the active provider is chosen by `LLM_PROVIDER`.

| Env var / property        | Description                          | Default     |
|---------------------------|--------------------------------------|-------------|
| `LLM_PROVIDER`            | Which LLM to use: `claude`, `openai`, or `gemini` | gemini |
| `CLAUDE_API_KEY`          | Anthropic API key (required if provider=claude) | - |
| `CLAUDE_MODEL`            | Claude model                        | claude-3-5-sonnet-20241022 |
| `OPENAI_API_KEY`          | OpenAI API key (required if provider=openai) | - |
| `OPENAI_MODEL`            | OpenAI model                        | gpt-4o-mini |
| `GEMINI_API_KEY`          | Google Gemini API key (required if provider=gemini) | - |
| `GEMINI_MODEL`            | Gemini model                        | gemini-2.0-flash |

### Other

| Env var / property        | Description                          | Default     |
|---------------------------|--------------------------------------|-------------|
| `METABASE_URL`            | Metabase base URL                   | -           |
| `METABASE_USER`          | Metabase login                      | -           |
| `METABASE_PASSWORD`       | Metabase password                   | -           |
| `METABASE_DB_ID`          | Target database ID in Metabase      | 2           |
| `TARGET_DIALECT`          | SQL dialect: postgres, mysql, bigquery | postgres  |
| `POC_SKIP_METABASE`       | Skip Metabase publish               | false       |

## Project layout

```
src/main/java/com/credila/poc/
├── PocApplication.java    # Entry point + CLI
├── PocConfig.java         # POC options
├── PbixExtractor.java     # .pbix → DAX measures
├── DaxToSqlConverter.java # DAX → SQL (delegates to LLM provider)
├── MetabaseClient.java   # Metabase REST (session + create card)
├── llm/                   # Configurable LLM backends
│   ├── LlmProvider.java   # Interface
│   ├── ClaudeLlmProvider.java
│   ├── OpenAILlmProvider.java
│   └── GeminiLlmProvider.java
└── model/
    └── DaxMeasure.java    # DTO
```

## .pbix format notes

- `.pbix` is a ZIP. The app looks for `DataModelSchema` or `DataModel` and parses JSON.
- Schema is expected as UTF-16 LE; the extractor also tries UTF-16 BE and UTF-8.
- Measures are read from `model.tables[].measures[]` (name + expression).

If your file uses a different layout (e.g. XML schema), extraction may fail; check logs and the ZIP contents.

## Troubleshooting

| Issue | What to do |
|-------|------------|
| **DataModelSchema not found** | Unzip the `.pbix` and confirm an entry like `DataModelSchema` or `DataModel` exists. |
| **No measures found** | Ensure the report has at least one DAX measure in the data model. |
| **API key not set** | Set the env var for your chosen provider: `CLAUDE_API_KEY`, `OPENAI_API_KEY`, or `GEMINI_API_KEY`. |
| **No LlmProvider bean** | Set `LLM_PROVIDER` to one of: `claude`, `openai`, `gemini`. |
| **Metabase 401** | Check `METABASE_URL`, `METABASE_USER`, `METABASE_PASSWORD`. |
| **SQL invalid in Metabase** | POC does not validate SQL; fix the query in Metabase or refine the prompt. |

## Next steps (production)

See `production-migration-plan.md` in the repo for:

- Config-driven migration (YAML)
- All measures + visuals → full dashboard
- Human review loop
- LangChain4j agent pipeline
- SQL validation and error handling
