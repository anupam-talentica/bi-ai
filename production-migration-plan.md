# PowerBI → Metabase Migration: Production-Grade Plan

> **Goal:** Build a reusable, configurable Java utility that transforms any Power BI project (.pbit/.pbix) to Metabase using an AI agent with human review loops, error handling, and production-grade reliability.

---

## Overview

This is a **generic, config-driven migration utility** that can handle 10–100+ PowerBI reports with:
- **Customization** via YAML/JSON config (SQL dialect, naming, chart mappings, pages to migrate)
- **AI-powered conversion** (DAX → SQL, visual mapping)
- **Human review loops** for quality assurance
- **Production monitoring** and error recovery
- **Reusable architecture** for ongoing migrations

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                  INPUT LAYER                        │
│  .pbix/.pbit files │ Config YAML │ Screenshots      │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│               EXTRACT AGENT                         │
│  - Unzip .pbix (Java ZIP)                           │
│  - Parse DataModelSchema (UTF-16 LE JSON)           │
│  - Parse Report/Layout (UTF-16 LE JSON)             │
│  - Optional: Vision model for screenshots           │
│  Tool: ExtractPbitTool                              │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│            TRANSFORM AGENT (LLM)                    │
│  - DAX measures → SQL (config: dialect)             │
│  - Map visual types → Metabase charts               │
│  - Apply naming conventions from config             │
│  - Filter pages per config.pagesToInclude           │
│  - Generate Metabase card definitions (JSON)        │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│            VALIDATE AGENT                           │
│  - Run generated SQL against target DB              │
│  - Check syntax and results                         │
│  - Flag errors for review                           │
│  Tool: ValidateSqlTool                              │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│         HUMAN REVIEW SERVICE  👤                    │
│  - Show original vs converted (diff view)           │
│  - Flag low-confidence conversions                  │
│  - Approve / reject / edit before publish           │
│  - Track review status per report                   │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│           PUBLISH AGENT                             │
│  - Create Metabase session                          │
│  - Create cards (questions) via REST API            │
│  - Create dashboard                                 │
│  - Add cards to dashboard with layout               │
│  - Set permissions and collections                  │
│  Tool: MetabaseApiTool                              │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│                  OUTPUT                             │
│  Metabase Dashboard URL │ Migration Report          │
└─────────────────────────────────────────────────────┘
```

---

## Technology Stack

| Component | Technology | Rationale |
|-----------|-----------|-----------|
| **Framework** | Spring Boot 3.x | Your expertise, production-ready ecosystem |
| **Agent Framework** | **LangChain4j** (`langchain4j-agentic`) | Java-native, mature, built-in agent orchestration with `@Agent` and `@Tool` |
| **LLM** | **Claude 3.5 Sonnet** (Anthropic API) | Excellent DAX→SQL, large context (200K), strong reasoning |
| **Vision** | Claude Vision or GPT-4o Vision | Screenshot parsing fallback |
| **HTTP Client** | Spring `RestClient` / `WebClient` | Native Spring integration for Metabase API |
| **Parsing** | Java ZIP + Jackson (UTF-16 LE) | Parse .pbix DataModelSchema and Report/Layout |
| **Config** | YAML (SnakeYAML) | Human-readable, easy to version control |
| **Database** | PostgreSQL (or your target) | For SQL validation and Metabase data source |
| **Monitoring** | Spring Actuator + Micrometer | Production metrics and health checks |
| **Logging** | SLF4J + Logback | Structured logging for debugging |

---

## Customization Config Schema

```yaml
# migration-config.yaml
targetDialect: postgres  # postgres | mysql | bigquery | snowflake

metabase:
  baseUrl: https://metabase.yourorg.com
  username: ${METABASE_USER}
  password: ${METABASE_PASSWORD}
  databaseId: 2  # Target database ID in Metabase
  collectionId: 10  # Optional: collection to create dashboards in

naming:
  dashboardNamePrefix: "Migrated - "
  cardNamePrefix: ""
  preserveOriginalNames: true

chartOverrides:
  # Map PowerBI visual type → Metabase display type
  clusteredColumnChart: bar
  lineChart: line
  pieChart: pie
  table: table
  card: scalar
  map: map
  # Custom visuals
  choropleth: region_map
  # Fallback for unmapped types
  default: table

pagesToInclude:
  - "*"  # Or list specific page names: ["Overview", "Sales"]

conversion:
  confidenceThreshold: 0.7  # Flag for review if LLM confidence < 70%
  maxRetries: 3
  validateSql: true  # Run SQL validation before publish

humanReview:
  enabled: true
  reviewMode: flag_low_confidence  # all | flag_low_confidence | none
  outputPath: ./review-queue/
```

---

## Implementation Phases

### Phase 1: PowerBI Parsing (2–3 weeks)
**Goal:** Extract all metadata from .pbix files

- [ ] Implement `PbixParser` to unzip .pbix files
- [ ] Parse `DataModelSchema` (model.bim) as UTF-16 LE JSON
  - Extract tables, columns, data types
  - Extract measures (DAX expressions)
  - Extract relationships and hierarchies
  - Extract calculated columns
- [ ] Parse `Report/Layout` as UTF-16 LE JSON
  - Extract report pages
  - Extract visuals per page (type, position, filters)
  - Extract page-level filters
- [ ] Create normalized schema DTOs
- [ ] Write unit tests with sample .pbix files
- [ ] CLI command: `extract --pbix path.pbix --output extracted.json`

**Deliverable:** Java utility that outputs structured JSON from any .pbix

---

### Phase 2: DAX → SQL Conversion (2–3 weeks)
**Goal:** LLM-powered conversion with validation

- [ ] Integrate LangChain4j + Claude API
- [ ] Design prompt templates for DAX→SQL conversion
  - Include target dialect in prompt
  - Include table schema context
  - Request confidence score in response
- [ ] Implement `DaxToSqlConverter` with structured output
- [ ] Implement `SqlValidator` to test SQL against target DB
  - Syntax validation
  - Execution test (LIMIT 1)
  - Result shape validation
- [ ] Build retry logic for failed conversions
- [ ] Create test suite with common DAX patterns
  - Simple aggregations (SUM, COUNT, AVG)
  - Time intelligence (YTD, MTD, SAMEPERIODLASTYEAR)
  - CALCULATE with filters
  - Complex nested measures
- [ ] Track conversion confidence scores

**Deliverable:** Reliable DAX→SQL converter with >80% success rate

---

### Phase 3: Metabase Integration (1–2 weeks)
**Goal:** Programmatic dashboard creation

- [ ] Implement `MetabaseApiClient` (REST wrapper)
  - Session authentication
  - Create card (question)
  - Create dashboard
  - Add card to dashboard
  - Update dashboard layout
  - Manage collections
- [ ] Implement `VisualTypeMapper`
  - Map PowerBI visual types → Metabase display types
  - Apply config `chartOverrides`
  - Handle custom visuals
- [ ] Implement `CardPublisher`
  - Create cards from converted SQL
  - Set visualization settings
  - Handle card dependencies
- [ ] Implement `DashboardPublisher`
  - Create dashboard structure
  - Position cards (grid layout)
  - Set filters and parameters
- [ ] Error handling and rollback
  - Transaction-like behavior (delete on failure)
  - Partial migration recovery

**Deliverable:** Java service that creates Metabase dashboards via API

---

### Phase 4: Human Review System (1–2 weeks)
**Goal:** Quality assurance before publish

- [ ] Implement `HumanReviewService`
  - Queue items for review
  - Track review status (pending, approved, rejected, edited)
  - Store review decisions
- [ ] Create review output format
  - Original DAX vs converted SQL
  - Original visual type vs Metabase chart
  - Confidence scores
  - Validation results
- [ ] Build review CLI or web UI
  - Show side-by-side comparison
  - Allow SQL editing
  - Approve/reject actions
- [ ] Integration with pipeline
  - Pause before publish
  - Resume after approval
  - Re-validate after edits

**Deliverable:** Review system with approval workflow

---

### Phase 5: Agent Orchestration (2–3 weeks)
**Goal:** End-to-end pipeline with LangChain4j agents

- [ ] Design agent workflow
  - `ExtractAgent` → calls `ExtractPbitTool`
  - `TransformAgent` → LLM + config → card definitions
  - `ValidateAgent` → calls `ValidateSqlTool`
  - `ReviewAgent` → human-in-the-loop
  - `PublishAgent` → calls `MetabaseApiTool`
- [ ] Implement tools as `@Tool` classes
  - `ExtractPbitTool`
  - `ValidateSqlTool`
  - `MetabaseApiTool`
- [ ] Wire sequential workflow
  - `AgenticServices.sequenceBuilder()`
  - Pass config through `AgenticScope`
  - Handle agent failures and retries
- [ ] Implement supervisor pattern (optional)
  - Adaptive ordering based on complexity
  - Parallel processing for independent reports
- [ ] Add observability
  - Log agent decisions
  - Track conversion metrics
  - Monitor API calls

**Deliverable:** Fully orchestrated agent pipeline

---

### Phase 6: Production Hardening (2–3 weeks)
**Goal:** Reliability, monitoring, and scale

- [ ] Configuration management
  - Environment-specific configs
  - Secret management (Vault, AWS Secrets Manager)
  - Config validation on startup
- [ ] Error handling and recovery
  - Graceful degradation
  - Partial migration support
  - Resume from checkpoint
- [ ] Monitoring and observability
  - Spring Actuator health checks
  - Micrometer metrics (conversion rate, API latency)
  - Structured logging (JSON)
  - Alert on failures
- [ ] Performance optimization
  - Parallel report processing
  - Connection pooling
  - Caching for repeated conversions
- [ ] Documentation
  - API documentation
  - Config schema reference
  - Troubleshooting guide
  - Migration playbook
- [ ] Testing
  - Integration tests with real .pbix files
  - Metabase API mocks
  - End-to-end migration tests
  - Load testing (100 reports)

**Deliverable:** Production-ready utility with monitoring

---

## Project Structure

```
powerbi-metabase-agent/
├── src/main/java/com/credila/migration/
│   ├── config/
│   │   ├── MigrationConfig.java          # Config DTO (loads from YAML)
│   │   ├── MetabaseConfig.java
│   │   ├── ConversionConfig.java
│   │   └── ConfigValidator.java
│   ├── parser/
│   │   ├── PbixParser.java               # Unzip & orchestrate extraction
│   │   ├── DataModelExtractor.java       # Parse DataModelSchema (UTF-16 LE)
│   │   ├── LayoutExtractor.java          # Parse Report/Layout
│   │   └── model/
│   │       ├── ExtractedSchema.java      # Normalized schema DTOs
│   │       ├── Table.java
│   │       ├── Measure.java
│   │       ├── Visual.java
│   │       └── ReportPage.java
│   ├── conversion/
│   │   ├── DaxToSqlConverter.java        # LLM-powered DAX→SQL
│   │   ├── VisualTypeMapper.java         # PowerBI visual → Metabase chart
│   │   ├── SqlValidator.java             # Validate SQL against target DB
│   │   ├── ConfidenceScorer.java         # Score conversion confidence
│   │   └── model/
│   │       ├── MetabaseCard.java         # Card definition DTO
│   │       └── ConversionResult.java
│   ├── metabase/
│   │   ├── MetabaseApiClient.java        # REST API wrapper
│   │   ├── CardPublisher.java            # Create questions/cards
│   │   ├── DashboardPublisher.java       # Create dashboards
│   │   ├── SessionManager.java           # Auth and session handling
│   │   └── model/
│   │       ├── MetabaseCard.java
│   │       ├── MetabaseDashboard.java
│   │       └── ApiResponse.java
│   ├── review/
│   │   ├── HumanReviewService.java       # Review queue management
│   │   ├── ReviewItem.java               # Review item DTO
│   │   ├── ReviewRepository.java         # Store review state
│   │   └── ReviewOutputGenerator.java    # Generate review files
│   ├── orchestration/
│   │   ├── agents/
│   │   │   ├── ExtractAgent.java         # @Agent for extraction
│   │   │   ├── TransformAgent.java       # @Agent for conversion
│   │   │   ├── ValidateAgent.java        # @Agent for validation
│   │   │   ├── ReviewAgent.java          # @Agent for review coordination
│   │   │   └── PublishAgent.java         # @Agent for Metabase publish
│   │   ├── tools/
│   │   │   ├── ExtractPbitTool.java      # @Tool
│   │   │   ├── ValidateSqlTool.java      # @Tool
│   │   │   └── MetabaseApiTool.java      # @Tool
│   │   ├── MigrationPipeline.java        # Sequential workflow orchestration
│   │   ├── SupervisorAgent.java          # Adaptive orchestration (optional)
│   │   └── CheckpointManager.java        # Resume from failures
│   ├── monitoring/
│   │   ├── MigrationMetrics.java         # Micrometer metrics
│   │   ├── HealthIndicator.java          # Spring Actuator health
│   │   └── AuditLogger.java              # Structured audit logs
│   └── MigrationApplication.java
├── src/main/resources/
│   ├── prompts/
│   │   ├── dax-to-sql.txt                # DAX conversion prompt template
│   │   ├── visual-mapping.txt            # Visual type mapping prompt
│   │   └── confidence-scoring.txt        # Confidence evaluation prompt
│   ├── application.yml
│   └── migration-config.yaml             # Default config
├── src/test/java/
│   ├── parser/
│   │   └── PbixParserTest.java
│   ├── conversion/
│   │   ├── DaxToSqlConverterTest.java
│   │   └── SqlValidatorTest.java
│   ├── integration/
│   │   └── EndToEndMigrationTest.java
│   └── resources/
│       └── sample-reports/               # Test .pbix files
├── docs/
│   ├── API.md                            # API documentation
│   ├── CONFIG_REFERENCE.md               # Config schema reference
│   ├── TROUBLESHOOTING.md                # Common issues and fixes
│   └── MIGRATION_PLAYBOOK.md             # Step-by-step guide
├── review-queue/                         # Human review output
├── pom.xml
└── README.md
```

---

## Customization Config (Full Schema)

```yaml
# migration-config.yaml

# Target SQL dialect for converted queries
targetDialect: postgres  # postgres | mysql | bigquery | snowflake | mssql

# Metabase connection settings
metabase:
  baseUrl: https://metabase.yourorg.com
  username: ${METABASE_USER}
  password: ${METABASE_PASSWORD}
  databaseId: 2                    # Target database ID in Metabase
  collectionId: 10                 # Optional: collection for dashboards
  timeout: 30000                   # API timeout in ms

# Naming conventions
naming:
  dashboardNamePrefix: "Migrated - "
  cardNamePrefix: ""
  preserveOriginalNames: true
  sanitizeNames: true              # Remove special chars

# Visual type mappings (PowerBI → Metabase)
chartOverrides:
  clusteredColumnChart: bar
  lineChart: line
  pieChart: pie
  donutChart: pie
  table: table
  matrix: table
  card: scalar
  gauge: gauge
  map: map
  scatterChart: scatter
  areaChart: area
  waterfallChart: waterfall
  funnelChart: funnel
  # Custom visuals
  choropleth: region_map
  # Fallback
  default: table

# Pages to migrate
pagesToInclude:
  - "*"  # Or list specific: ["Overview", "Sales", "Finance"]

# Conversion settings
conversion:
  confidenceThreshold: 0.7         # Flag for review if < 70%
  maxRetries: 3
  validateSql: true                # Run SQL validation before publish
  includeComments: true            # Add comments to generated SQL
  preserveFormatting: true         # Try to preserve number/date formats

# Human review settings
humanReview:
  enabled: true
  reviewMode: flag_low_confidence  # all | flag_low_confidence | none
  outputPath: ./review-queue/
  autoApproveHighConfidence: false # Auto-approve if confidence > 95%

# Advanced
advanced:
  parallelReports: 5               # Process N reports in parallel
  checkpointEnabled: true          # Save progress for resume
  checkpointPath: ./checkpoints/
  screenshotFallback: true         # Use vision model if parsing fails
  customVisualHandling: best_effort  # best_effort | skip | manual
```

---

## Agent Design (LangChain4j)

### Tools

#### 1. ExtractPbitTool
```java
@Tool("Extract metadata from a PowerBI .pbix or .pbit file")
public ExtractedSchema extractPbit(
    @P("Path to .pbix or .pbit file") String pbitPath,
    @P("Config for extraction") MigrationConfig config
) {
    // Unzip, parse DataModelSchema + Report/Layout
    // Return normalized schema
}
```

#### 2. ValidateSqlTool
```java
@Tool("Validate generated SQL against target database")
public ValidationResult validateSql(
    @P("SQL query to validate") String sql,
    @P("Expected result shape") String expectedShape
) {
    // Run SQL with LIMIT 1
    // Check syntax, execution, result columns
}
```

#### 3. MetabaseApiTool
```java
@Tool("Publish cards and dashboards to Metabase")
public PublishResult publishToMetabase(
    @P("List of card definitions") List<MetabaseCard> cards,
    @P("Dashboard name") String dashboardName,
    @P("Metabase config") MetabaseConfig config
) {
    // Create session, cards, dashboard
    // Return dashboard URL
}
```

### Sequential Workflow

```java
// Wire agents in sequence
var pipeline = AgenticServices.sequenceBuilder()
    .agent(extractAgent)      // Calls ExtractPbitTool
    .agent(transformAgent)    // LLM: DAX→SQL + visual mapping
    .agent(validateAgent)     // Calls ValidateSqlTool
    .agent(reviewAgent)       // Human review coordination
    .agent(publishAgent)      // Calls MetabaseApiTool
    .build();

// Execute
var result = pipeline.execute(Map.of(
    "pbitPath", "/path/to/report.pbix",
    "config", migrationConfig
));
```

---

## Key Design Decisions

### 1. Human Review Loop
**Decision:** Always include human review for production migrations

- Flag low-confidence conversions (< 70%)
- Generate side-by-side comparison files
- Pause pipeline until approval
- Allow SQL editing before publish

**Rationale:** DAX→SQL is complex; 100% automated fidelity is unrealistic. Human validation ensures business logic correctness.

### 2. Config-Driven
**Decision:** All customization via YAML config, not hardcoded

- SQL dialect
- Chart mappings
- Naming conventions
- Pages to migrate
- Review thresholds

**Rationale:** Makes utility reusable across different organizations, databases, and preferences.

### 3. Validation Before Publish
**Decision:** Always validate SQL against target DB before creating Metabase cards

- Syntax check
- Execution test
- Result shape validation

**Rationale:** Catch errors early; avoid creating broken Metabase cards.

### 4. LangChain4j over Spring AI
**Decision:** Use LangChain4j for agent orchestration

**Rationale:**
- Built-in `@Agent` and `@Tool` abstractions
- Sequential workflow support
- Mature ecosystem
- Better for complex multi-agent pipelines

Spring AI is simpler but requires more manual orchestration code.

### 5. Checkpoint and Resume
**Decision:** Save progress at each phase; support resume from checkpoint

**Rationale:** Large migrations (100 reports) may take hours; failures should not require full restart.

---

## Critical Success Factors

### 1. Understand Your Data Model
- **Before starting:** Document your PowerBI data model
- Identify common DAX patterns used across reports
- Understand data sources and how they map to target DB

### 2. Start with Representative Samples
- Pick 3–5 reports that cover:
  - Simple aggregations
  - Complex DAX (time intelligence, CALCULATE)
  - Various visual types
  - Different page layouts
- Get these working end-to-end before scaling

### 3. Measure and Iterate
- Track conversion success rate
- Identify common failure patterns
- Refine prompts and mappings based on real data

### 4. Set Realistic Expectations
- **Target:** 80–90% automated conversion
- **Accept:** 10–20% will need manual refinement
- **Plan for:** Ongoing prompt tuning as you encounter edge cases

---

## Pre-Flight Questions (Resolve Before Starting)

| # | Question | Impact |
|---|----------|--------|
| 1 | **What is your target database?** (PostgreSQL, MySQL, Snowflake, BigQuery?) | Determines SQL dialect for DAX conversion |
| 2 | **Are data sources already in a DB Metabase can connect to?** | If PowerBI uses Excel/CSV, need separate data migration |
| 3 | **How consistent are your PowerBI reports?** (naming, modeling conventions) | Consistency = higher conversion accuracy |
| 4 | **Who will perform human review?** (BI analyst, developer, business user?) | Affects review UI complexity |
| 5 | **Is this one-time or ongoing?** | Ongoing = need version control, change detection |
| 6 | **What's your acceptable fidelity level?** (80% automated + 20% manual?) | Sets expectations and review process |
| 7 | **Do you use custom PowerBI visuals?** | May need manual mapping or skip |
| 8 | **What's your Metabase version?** | API compatibility check |

---

## Dependencies (pom.xml)

```xml
<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    
    <!-- LangChain4j -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
        <version>0.35.0</version>
    </dependency>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-agentic</artifactId>
        <version>0.35.0</version>
    </dependency>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-anthropic</artifactId>
        <version>0.35.0</version>
    </dependency>
    
    <!-- JSON parsing -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.dataformat</groupId>
        <artifactId>jackson-dataformat-yaml</artifactId>
    </dependency>
    
    <!-- Database -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
    
    <!-- Monitoring -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>
    
    <!-- Utilities -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## Usage (CLI)

```bash
# Full migration with config
java -jar migration-agent.jar migrate \
  --pbix /path/to/report.pbix \
  --config migration-config.yaml

# Extract only (for inspection)
java -jar migration-agent.jar extract \
  --pbix /path/to/report.pbix \
  --output extracted.json

# Batch migration (directory of .pbix files)
java -jar migration-agent.jar migrate-batch \
  --input-dir /path/to/reports/ \
  --config migration-config.yaml \
  --parallel 5

# Resume from checkpoint
java -jar migration-agent.jar resume \
  --checkpoint ./checkpoints/migration-20260307.json
```

---

## Success Metrics

| Metric | Target | How to Measure |
|--------|--------|----------------|
| **Conversion success rate** | >80% | % of DAX measures successfully converted to valid SQL |
| **Visual mapping accuracy** | >90% | % of visuals mapped to appropriate Metabase chart type |
| **Human review rate** | <30% | % of conversions requiring human intervention |
| **API success rate** | >95% | % of Metabase API calls succeeding |
| **End-to-end time** | <5 min/report | Average time from .pbix to published dashboard |
| **Zero data loss** | 100% | All measures and visuals accounted for (even if flagged) |

---

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| **Complex DAX fails to convert** | Human review loop; build library of DAX→SQL patterns; iterative prompt refinement |
| **Metabase API rate limits** | Implement backoff/retry; batch operations; connection pooling |
| **Data model mismatch** | Validate table/column names against target DB; flag mismatches early |
| **Custom PowerBI visuals** | Map to closest Metabase type; document manual steps; screenshot fallback |
| **Large .pbix files** | Stream parsing; memory-efficient ZIP handling; process pages incrementally |
| **Concurrent migrations** | Use database for state; implement locking; support parallel processing |

---

## Next Steps

1. **Resolve pre-flight questions** (target DB, data availability, review process)
2. **Set up project** (Spring Boot + LangChain4j + dependencies)
3. **Phase 1:** Get .pbix parsing working with one sample file
4. **Phase 2:** Get DAX→SQL working for simple measures
5. **Phase 3:** Create one Metabase card via API
6. **Then:** Build full pipeline, add review, harden for production

---

## Resources

- [LangChain4j Docs](https://docs.langchain4j.dev)
- [LangChain4j Agentic Module](https://docs.langchain4j.dev/tutorials/agentic)
- [Metabase API Docs](https://www.metabase.com/docs/latest/api-documentation)
- [Anthropic API Docs](https://docs.anthropic.com)
- [PowerBI .pbix Format](https://learn.microsoft.com/en-us/power-bi/developer/projects/projects-overview)
- [Spring Boot Docs](https://docs.spring.io/spring-boot/docs/current/reference/html/)

---

*Production Plan — March 2026*
