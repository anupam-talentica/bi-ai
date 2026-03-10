# PowerBI → Metabase Migration: One-Day POC Plan

> **Goal:** Build a minimal working proof-of-concept in one day that demonstrates the core migration flow: extract DAX from a .pbix → convert to SQL → create a Metabase card.

---

## What You'll Build Today

A **simple Java Spring Boot app** that:
1. ✅ Extracts one DAX measure from a sample .pbix file
2. ✅ Converts that DAX to SQL using Claude API
3. ✅ Creates a single Metabase card (question) via REST API
4. ✅ Prints the Metabase card URL

**Scope:** One report, one measure, one card. No dashboard layout, no config files, no human review — just prove the core flow works.

---

## Tech Stack (Minimal)

| Component | Technology |
|-----------|-----------|
| **Language** | Java 21 |
| **Framework** | Spring Boot 3.x (minimal: `spring-boot-starter`) |
| **LLM** | Claude API (direct HTTP, no LangChain4j yet) |
| **HTTP Client** | Spring `RestClient` |
| **JSON** | Jackson |
| **Build** | Maven |

---

## Project Structure (Minimal)

```
powerbi-metabase-poc/
├── src/main/java/com/credila/poc/
│   ├── PocApplication.java              # Main class
│   ├── PbixExtractor.java               # Extract DAX from .pbix
│   ├── DaxToSqlConverter.java           # Call Claude API
│   ├── MetabaseClient.java              # Call Metabase API
│   └── model/
│       ├── DaxMeasure.java              # Simple DTO
│       └── MetabaseCard.java            # Simple DTO
├── src/main/resources/
│   └── application.yml
├── sample-report.pbix                   # Test file
├── pom.xml
└── README.md
```

---

## Step-by-Step Implementation (One Day)

### Hour 1: Setup (30 min)

**Create Spring Boot project:**

```bash
# Use Spring Initializr or create manually
mkdir powerbi-metabase-poc
cd powerbi-metabase-poc
```

**Minimal `pom.xml`:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>
    
    <groupId>com.credila</groupId>
    <artifactId>powerbi-metabase-poc</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    
    <properties>
        <java.version>21</java.version>
    </properties>
    
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
    </dependencies>
</project>
```

**`application.yml`:**

```yaml
claude:
  api-key: ${CLAUDE_API_KEY}
  model: claude-3-5-sonnet-20241022

metabase:
  base-url: ${METABASE_URL}
  username: ${METABASE_USER}
  password: ${METABASE_PASSWORD}
  database-id: ${METABASE_DB_ID}
```

---

### Hour 2: Extract DAX from .pbix (1 hour)

**Goal:** Read one DAX measure from a .pbix file

**`PbixExtractor.java`:**

```java
@Component
public class PbixExtractor {
    
    public List<DaxMeasure> extractMeasures(String pbixPath) throws IOException {
        List<DaxMeasure> measures = new ArrayList<>();
        
        // .pbix is a ZIP file
        try (ZipFile zipFile = new ZipFile(pbixPath)) {
            // DataModelSchema is usually at "DataModelSchema" entry
            ZipEntry entry = zipFile.getEntry("DataModelSchema");
            if (entry == null) {
                throw new RuntimeException("DataModelSchema not found in .pbix");
            }
            
            // Read as UTF-16 LE
            try (InputStream is = zipFile.getInputStream(entry);
                 Reader reader = new InputStreamReader(is, StandardCharsets.UTF_16LE)) {
                
                String json = new String(reader.readAllBytes(), StandardCharsets.UTF_16LE);
                
                // Parse JSON
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(json);
                
                // Navigate to measures: model.tables[].measures[]
                JsonNode tables = root.path("model").path("tables");
                for (JsonNode table : tables) {
                    JsonNode measuresNode = table.path("measures");
                    for (JsonNode measure : measuresNode) {
                        String name = measure.path("name").asText();
                        String expression = measure.path("expression").asText();
                        measures.add(new DaxMeasure(name, expression));
                    }
                }
            }
        }
        
        return measures;
    }
}
```

**`DaxMeasure.java`:**

```java
@Data
@AllArgsConstructor
public class DaxMeasure {
    private String name;
    private String expression;
}
```

**Test:** Run with a sample .pbix and print extracted measures to console.

---

### Hour 3: Convert DAX to SQL via Claude (1.5 hours)

**Goal:** Send DAX to Claude API, get SQL back

**`DaxToSqlConverter.java`:**

```java
@Component
public class DaxToSqlConverter {
    
    @Value("${claude.api-key}")
    private String apiKey;
    
    @Value("${claude.model}")
    private String model;
    
    private final RestClient restClient = RestClient.create();
    
    public String convertToSql(String daxExpression, String targetDialect) {
        String prompt = String.format("""
            You are a DAX to SQL conversion expert.
            
            Convert this DAX measure to %s SQL:
            
            ```dax
            %s
            ```
            
            Requirements:
            - Output ONLY the SQL query, no explanations
            - Use standard SQL syntax for %s
            - Assume tables and columns exist as named in DAX
            - Preserve the business logic exactly
            
            SQL:
            """, targetDialect, daxExpression, targetDialect);
        
        // Call Claude API
        Map<String, Object> requestBody = Map.of(
            "model", model,
            "max_tokens", 1024,
            "messages", List.of(
                Map.of("role", "user", "content", prompt)
            )
        );
        
        String response = restClient.post()
            .uri("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .body(requestBody)
            .retrieve()
            .body(String.class);
        
        // Parse response
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response);
        String sql = root.path("content").get(0).path("text").asText();
        
        return sql.trim();
    }
}
```

**Test:** Convert a simple DAX like `Total Sales = SUM(Sales[Amount])` and print the SQL.

---

### Hour 4: Create Metabase Card (1.5 hours)

**Goal:** Use Metabase REST API to create a card with the converted SQL

**`MetabaseClient.java`:**

```java
@Component
public class MetabaseClient {
    
    @Value("${metabase.base-url}")
    private String baseUrl;
    
    @Value("${metabase.username}")
    private String username;
    
    @Value("${metabase.password}")
    private String password;
    
    @Value("${metabase.database-id}")
    private int databaseId;
    
    private final RestClient restClient = RestClient.create();
    private String sessionToken;
    
    public void authenticate() {
        Map<String, String> credentials = Map.of(
            "username", username,
            "password", password
        );
        
        String response = restClient.post()
            .uri(baseUrl + "/api/session")
            .header("content-type", "application/json")
            .body(credentials)
            .retrieve()
            .body(String.class);
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response);
        this.sessionToken = root.path("id").asText();
    }
    
    public String createCard(String name, String sql) {
        Map<String, Object> cardRequest = Map.of(
            "name", name,
            "dataset_query", Map.of(
                "type", "native",
                "native", Map.of(
                    "query", sql
                ),
                "database", databaseId
            ),
            "display", "table",
            "visualization_settings", Map.of()
        );
        
        String response = restClient.post()
            .uri(baseUrl + "/api/card")
            .header("X-Metabase-Session", sessionToken)
            .header("content-type", "application/json")
            .body(cardRequest)
            .retrieve()
            .body(String.class);
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response);
        int cardId = root.path("id").asInt();
        
        return baseUrl + "/question/" + cardId;
    }
}
```

---

### Hour 5: Wire It All Together (1 hour)

**`PocApplication.java`:**

```java
@SpringBootApplication
public class PocApplication implements CommandLineRunner {
    
    @Autowired
    private PbixExtractor extractor;
    
    @Autowired
    private DaxToSqlConverter converter;
    
    @Autowired
    private MetabaseClient metabaseClient;
    
    public static void main(String[] args) {
        SpringApplication.run(PocApplication.class, args);
    }
    
    @Override
    public void run(String... args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java -jar poc.jar <path-to-pbix>");
            return;
        }
        
        String pbixPath = args[0];
        
        // Step 1: Extract
        System.out.println("📦 Extracting DAX measures from " + pbixPath);
        List<DaxMeasure> measures = extractor.extractMeasures(pbixPath);
        System.out.println("Found " + measures.size() + " measures");
        
        if (measures.isEmpty()) {
            System.out.println("No measures found. Exiting.");
            return;
        }
        
        // Take first measure for POC
        DaxMeasure measure = measures.get(0);
        System.out.println("\n📊 Converting measure: " + measure.getName());
        System.out.println("DAX: " + measure.getExpression());
        
        // Step 2: Convert
        System.out.println("\n🤖 Converting DAX to SQL via Claude...");
        String sql = converter.convertToSql(measure.getExpression(), "postgres");
        System.out.println("SQL: " + sql);
        
        // Step 3: Publish
        System.out.println("\n📤 Creating Metabase card...");
        metabaseClient.authenticate();
        String cardUrl = metabaseClient.createCard(measure.getName(), sql);
        
        System.out.println("\n✅ SUCCESS!");
        System.out.println("Metabase card created: " + cardUrl);
    }
}
```

---

### Hour 6: Test and Refine (1 hour)

**Run the POC:**

```bash
# Set environment variables
export CLAUDE_API_KEY=sk-ant-...
export METABASE_URL=https://metabase.yourorg.com
export METABASE_USER=your-email@example.com
export METABASE_PASSWORD=your-password
export METABASE_DB_ID=2

# Build
mvn clean package

# Run with sample .pbix
java -jar target/powerbi-metabase-poc-0.0.1-SNAPSHOT.jar sample-report.pbix
```

**Expected output:**

```
📦 Extracting DAX measures from sample-report.pbix
Found 5 measures

📊 Converting measure: Total Sales
DAX: SUM(Sales[Amount])

🤖 Converting DAX to SQL via Claude...
SQL: SELECT SUM(amount) FROM sales

📤 Creating Metabase card...

✅ SUCCESS!
Metabase card created: https://metabase.yourorg.com/question/123
```

**Validate:**
- Open the Metabase URL
- Check if the card shows data
- Verify the SQL is correct

---

## What's NOT in the POC (But Will Be in Production)

| Feature | POC | Production |
|---------|-----|------------|
| **Config file** | ❌ Hardcoded | ✅ YAML config |
| **Multiple measures** | ❌ First only | ✅ All measures |
| **Visual mapping** | ❌ Skip | ✅ Map chart types |
| **Dashboard creation** | ❌ Single card | ✅ Full dashboard |
| **Layout** | ❌ Skip | ✅ Recreate positions |
| **Human review** | ❌ Skip | ✅ Review loop |
| **SQL validation** | ❌ Skip | ✅ Test against DB |
| **Error handling** | ❌ Minimal | ✅ Retry, logging |
| **LangChain4j agents** | ❌ Direct code | ✅ Agent orchestration |
| **Batch processing** | ❌ One report | ✅ Multiple reports |

---

## Success Criteria for POC

At the end of the day, you should have:

- ✅ A .pbix file successfully unzipped and parsed
- ✅ At least one DAX measure extracted
- ✅ That DAX converted to valid SQL via Claude
- ✅ A Metabase card created with that SQL
- ✅ The card visible and working in Metabase

**If you achieve this, the core concept is proven.** Everything else is engineering.

---

## Potential Issues and Quick Fixes

| Issue | Quick Fix |
|-------|-----------|
| **Can't find DataModelSchema in .pbix** | List all ZIP entries; it might be named differently or nested |
| **UTF-16 LE parsing fails** | Try UTF-16 BE or UTF-8; some .pbix files vary |
| **Claude returns explanation, not just SQL** | Refine prompt: "Output ONLY the SQL, no markdown, no explanation" |
| **Metabase API returns 401** | Check credentials; try manual login first to verify |
| **SQL has syntax errors** | Manually fix for POC; add validation in production |
| **No data in Metabase card** | Check if target DB has the table; verify `database-id` is correct |

---

## Cursor Workflow (How to Build This in One Day)

### Phase 1: Setup (use Cursor to generate)
1. Ask Cursor: "Create a Spring Boot 3.2 Maven project with minimal dependencies (starter, web, jackson, lombok)"
2. Ask Cursor: "Add application.yml with placeholders for Claude API key and Metabase config"

### Phase 2: Extraction (use Cursor to implement)
1. Ask Cursor: "Implement PbixExtractor.java that unzips a .pbix file and reads the DataModelSchema entry as UTF-16 LE JSON"
2. Ask Cursor: "Parse the JSON to extract all DAX measures (name and expression) and return as List<DaxMeasure>"
3. Test with your sample .pbix

### Phase 3: Conversion (use Cursor to implement)
1. Ask Cursor: "Implement DaxToSqlConverter.java that calls Claude API to convert DAX to PostgreSQL SQL"
2. Ask Cursor: "Use Spring RestClient to POST to https://api.anthropic.com/v1/messages with the DAX in the prompt"
3. Test with a simple DAX like `SUM(Sales[Amount])`

### Phase 4: Metabase (use Cursor to implement)
1. Ask Cursor: "Implement MetabaseClient.java with authenticate() and createCard(name, sql) methods using Metabase REST API"
2. Ask Cursor: "Use Spring RestClient to POST to /api/session and /api/card"
3. Test by creating a card with hardcoded SQL first

### Phase 5: Integration (use Cursor to wire)
1. Ask Cursor: "Wire PbixExtractor, DaxToSqlConverter, and MetabaseClient in PocApplication.java as a CommandLineRunner"
2. Ask Cursor: "Take first measure, convert it, and create a Metabase card; print the card URL"
3. Run end-to-end test

### Phase 6: Polish (if time remains)
1. Add error handling (try-catch)
2. Add logging (SLF4J)
3. Write a README with setup instructions

---

## Sample .pbix for Testing

**Option 1:** Use one of your existing reports (simplest one)

**Option 2:** Create a minimal .pbix in PowerBI Desktop:
1. Connect to a sample database (or CSV)
2. Create one simple measure: `Total Sales = SUM(Sales[Amount])`
3. Add one visual (table or bar chart)
4. Save as .pbix

**Option 3:** Download a sample from Microsoft:
- [PowerBI Sample Reports](https://learn.microsoft.com/en-us/power-bi/create-reports/sample-datasets)

---

## Timeline (One Day)

| Time | Task | Status |
|------|------|--------|
| **9:00 – 9:30** | Project setup, pom.xml, application.yml | ⏳ |
| **9:30 – 10:30** | Implement PbixExtractor, test with sample .pbix | ⏳ |
| **10:30 – 12:00** | Implement DaxToSqlConverter, test with Claude API | ⏳ |
| **12:00 – 13:00** | Lunch break | ☕ |
| **13:00 – 14:30** | Implement MetabaseClient, test card creation | ⏳ |
| **14:30 – 15:30** | Wire everything in PocApplication, run end-to-end | ⏳ |
| **15:30 – 16:30** | Debug, fix issues, test with real .pbix | ⏳ |
| **16:30 – 17:00** | Document findings, write README | ⏳ |

---

## What You'll Learn from the POC

| Question | What POC Answers |
|----------|------------------|
| **Can we parse .pbix reliably?** | Yes/No + what format variations exist |
| **How good is Claude at DAX→SQL?** | Accuracy for simple measures; identifies hard cases |
| **Does Metabase API work as expected?** | Auth, card creation, any quirks |
| **What's the end-to-end latency?** | Time from .pbix to Metabase card |
| **What breaks?** | Edge cases, encoding issues, API errors |

---

## After the POC: Next Steps

If the POC succeeds, you'll have proven the core concept. Then:

1. **Expand scope:**
   - Extract all measures (not just first)
   - Extract visuals and map to Metabase chart types
   - Create full dashboard (not just one card)

2. **Add production features:**
   - Config file (YAML)
   - SQL validation against DB
   - Human review loop
   - Error handling and retry
   - Logging and monitoring

3. **Adopt LangChain4j:**
   - Replace direct Claude API calls with LangChain4j
   - Implement agents and tools
   - Build sequential workflow

4. **Scale:**
   - Batch processing for multiple reports
   - Parallel execution
   - Checkpoint and resume

5. **Follow the production plan** for the remaining phases.

---

## Quick Reference: API Endpoints

### Claude API
```bash
POST https://api.anthropic.com/v1/messages
Headers:
  x-api-key: YOUR_API_KEY
  anthropic-version: 2023-06-01
  content-type: application/json
Body:
  {
    "model": "claude-3-5-sonnet-20241022",
    "max_tokens": 1024,
    "messages": [{"role": "user", "content": "Your prompt"}]
  }
```

### Metabase API
```bash
# Authenticate
POST https://metabase.yourorg.com/api/session
Body: {"username": "...", "password": "..."}
Response: {"id": "session-token"}

# Create card
POST https://metabase.yourorg.com/api/card
Headers: X-Metabase-Session: session-token
Body:
  {
    "name": "Card Name",
    "dataset_query": {
      "type": "native",
      "native": {"query": "SELECT ..."},
      "database": 2
    },
    "display": "table",
    "visualization_settings": {}
  }
Response: {"id": 123, ...}
```

---

## Resources

- [Metabase API Docs](https://www.metabase.com/docs/latest/api-documentation)
- [Anthropic API Docs](https://docs.anthropic.com)
- [Spring RestClient Docs](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)
- [PowerBI .pbix Format](https://learn.microsoft.com/en-us/power-bi/developer/projects/projects-overview)

---

## POC Success = Green Light for Production

If you can:
- ✅ Extract DAX from .pbix
- ✅ Convert to SQL with Claude
- ✅ Create a working Metabase card

Then the **production plan is feasible**. The rest is "just" engineering: config, orchestration, review, error handling, and scale.

---

*POC Plan — March 2026*
