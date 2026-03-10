package com.credila.poc;

import com.credila.poc.model.DataModelContext;
import com.credila.poc.model.DaxMeasure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.util.List;

@SpringBootApplication
public class PocApplication {

    private static final Logger log = LoggerFactory.getLogger(PocApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(PocApplication.class, args);
    }

    @Bean
    @Profile("!test")
    public CommandLineRunner run(
            ApplicationContext ctx,
            PbixExtractor extractor,
            DaxToSqlConverter converter,
            MetabaseClient metabaseClient,
            CovidDataLoader covidDataLoader,
            PocConfig config
    ) {
        return args -> {
            // Parse --load-data: load USAFacts COVID data into Postgres (same DB Metabase uses)
            String[] argsCopy = args.clone();
            boolean loadData = false;
            int write = 0;
            for (String a : argsCopy) {
                if ("--load-data".equals(a)) {
                    loadData = true;
                    continue;
                }
                argsCopy[write++] = a;
            }
            if (write < argsCopy.length) {
                argsCopy = java.util.Arrays.copyOf(argsCopy, write);
            }

            if (loadData) {
                System.out.println("Loading USAFacts COVID-19 data into database...");
                covidDataLoader.load();
                System.out.println("COVID data loaded. Proceeding with migration.");
                if (argsCopy.length < 1) {
                    System.out.println("No .pbix path provided. Use: java -jar ... <path-to-pbix> [measure-name]");
                    exit(ctx, 0);
                    return;
                }
            }

            if (argsCopy.length < 1) {
                printUsage();
                exit(ctx, 0);
                return;
            }

            String pbixPath = argsCopy[0];
            String measureNameFilter = argsCopy.length >= 2 ? argsCopy[1].trim() : null; // optional: e.g. "Total Deaths"
            String targetDialect = config.getTargetDialect();

            log.info("Extracting data model from {}", pbixPath);
            DataModelContext dataModel = extractor.extractDataModel(pbixPath);
            List<DaxMeasure> measures = dataModel.getMeasures();
            
            System.out.println("Found " + dataModel.getTables().size() + " table(s) and " + measures.size() + " measure(s)");

            if (measures.isEmpty()) {
                System.out.println("No measures found in .pbix. Exiting.");
                exit(ctx, 1);
                return;
            }

            // If second arg is --list-measures, print and exit
            if ("--list-measures".equals(measureNameFilter)) {
                System.out.println("\nAvailable measures (use name as 2nd argument to migrate that measure):");
                for (int i = 0; i < measures.size(); i++) {
                    System.out.println("  " + (i + 1) + ". " + measures.get(i).getName());
                }
                System.out.println("\n  Or use 'State Summary' to create a card with Location, Confirmed Cases, Total Deaths by state.");
                exit(ctx, 0);
                return;
            }

            // If second arg is "State Summary", create the state-level detail card (no LLM)
            if ("State Summary".equalsIgnoreCase(measureNameFilter)) {
                if (config.isSkipMetabase()) {
                    System.out.println("State Summary SQL:\n" + getStateSummarySql());
                    exit(ctx, 0);
                    return;
                }
                log.info("Creating COVID-19 State Summary card");
                metabaseClient.authenticate();
                String cardUrl = metabaseClient.createCard("COVID-19 State Summary", getStateSummarySql());
                System.out.println("\nSUCCESS");
                System.out.println("State summary card (Location, Confirmed Cases, Total Deaths): " + cardUrl);
                exit(ctx, 0);
                return;
            }

            // Pick measure: by name if provided, else first
            DaxMeasure measure = measureNameFilter == null || measureNameFilter.isEmpty()
                    ? measures.get(0)
                    : measures.stream()
                            .filter(m -> m.getName().equalsIgnoreCase(measureNameFilter))
                            .findFirst()
                            .orElseGet(() -> {
                                System.out.println("Measure '" + measureNameFilter + "' not found. Using first measure.");
                                return measures.get(0);
                            });
            System.out.println("\nConverting measure: " + measure.getName());
            System.out.println("DAX: " + measure.getExpression());

            log.info("Converting DAX to {} SQL via LLM (provider: {})", targetDialect, config.getLlmProvider());
            String sql = converter.convertToSql(measure.getExpression(), targetDialect, dataModel);
            System.out.println("SQL: " + sql);

            if (config.isSkipMetabase()) {
                System.out.println("\n[POC] Metabase publish skipped (poc.skip-metabase=true). Success up to SQL conversion.");
                exit(ctx, 0);
                return;
            }

            log.info("Creating Metabase card");
            metabaseClient.authenticate();
            String cardUrl = metabaseClient.createCard(measure.getName(), sql);

            System.out.println("\nSUCCESS");
            System.out.println("Metabase card: " + cardUrl);
            exit(ctx, 0);
        };
    }

    private static void exit(ApplicationContext ctx, int code) {
        SpringApplication.exit(ctx, () -> code);
    }

    private static String getStateSummarySql() {
        return """
            SELECT
                state AS "Location",
                SUM(COALESCE(cases, 0))::int AS "Confirmed Cases",
                SUM(deaths)::int AS "Total Deaths"
            FROM "COVID"
            GROUP BY state
            ORDER BY "Total Deaths" DESC
            """.trim();
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar powerbi-metabase-poc.jar [--load-data] <path-to-pbix> [measure-name | --list-measures]");
        System.out.println("  --load-data      Load USAFacts COVID-19 data into Postgres first (so cards show real numbers).");
        System.out.println("  measure-name     Optional. Migrate this measure (e.g. 'Total deaths'), or 'State Summary' for Location / Confirmed Cases / Total Deaths by state.");
        System.out.println("  --list-measures  List all measure names and exit.");
        System.out.println();
        System.out.println("Environment variables:");
        System.out.println("  LLM_PROVIDER       - LLM for DAX→SQL: claude | openai | gemini (default: gemini)");
        System.out.println("  CLAUDE_API_KEY     - Anthropic API key (required if LLM_PROVIDER=claude)");
        System.out.println("  OPENAI_API_KEY     - OpenAI API key (required if LLM_PROVIDER=openai)");
        System.out.println("  GEMINI_API_KEY     - Google Gemini API key (required if LLM_PROVIDER=gemini)");
        System.out.println("  METABASE_URL       - Metabase base URL (default: http://localhost:3000)");
        System.out.println("  METABASE_USER      - Metabase login email");
        System.out.println("  METABASE_PASSWORD  - Metabase password");
        System.out.println("  METABASE_DB_ID     - Target database ID in Metabase (default: 2)");
        System.out.println("  COVID_DATA_URL     - Path or URL for deaths CSV (e.g. .../truth_usafacts-Incident Deaths.csv)");
        System.out.println("  COVID_CASES_DATA_URL - Path or URL for cases CSV so Confirmed Cases is non-zero (e.g. .../truth_usafacts-Incident Cases.csv)");
        System.out.println("  POSTGRES_URL       - Postgres JDBC URL for --load-data (default: jdbc:postgresql://localhost:5432/metabase)");
        System.out.println("  POSTGRES_USER      - Postgres user (default: metabase)");
        System.out.println("  POSTGRES_PASSWORD  - Postgres password (default: metabase)");
        System.out.println("  TARGET_DIALECT     - SQL dialect: postgres, mysql, bigquery (default: postgres)");
        System.out.println("  POC_SKIP_METABASE  - Set to true to only run extract + convert (no publish)");
    }
}
