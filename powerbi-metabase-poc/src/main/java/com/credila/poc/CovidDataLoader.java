package com.credila.poc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads USAFacts COVID-19 incident deaths and incident cases into the COVID table
 * so Metabase cards show real numbers (e.g. Total Deaths, Confirmed Cases by state).
 * Triggered by --load-data when running the application.
 */
@Component
public class CovidDataLoader {

    private static final Logger log = LoggerFactory.getLogger(CovidDataLoader.class);
    // Multiple fallbacks; GitHub often returns 404 without a proper User-Agent
    private static final String[] DEFAULT_DEATHS_URLS = {
            "https://github.com/reichlab/covid19-forecast-hub/raw/refs/heads/master/data-truth/usafacts/truth_usafacts-Incident%20Deaths.csv",
            "https://raw.githubusercontent.com/reichlab/covid19-forecast-hub/master/data-truth/usafacts/truth_usafacts-Incident%20Deaths.csv",
            "https://cdn.jsdelivr.net/gh/reichlab/covid19-forecast-hub@master/data-truth/usafacts/truth_usafacts-Incident%20Deaths.csv"
    };
    private static final String[] DEFAULT_CASES_URLS = {
            "https://github.com/reichlab/covid19-forecast-hub/raw/refs/heads/master/data-truth/usafacts/truth_usafacts-Incident%20Cases.csv",
            "https://raw.githubusercontent.com/reichlab/covid19-forecast-hub/master/data-truth/usafacts/truth_usafacts-Incident%20Cases.csv",
            "https://cdn.jsdelivr.net/gh/reichlab/covid19-forecast-hub@master/data-truth/usafacts/truth_usafacts-Incident%20Cases.csv"
    };

    @Value("${covid.data-url:}")
    private String configuredDataUrl;

    @Value("${covid.cases-url:}")
    private String configuredCasesUrl;

    private static final Map<String, String> STATE_FIPS = Map.ofEntries(
            Map.entry("01", "Alabama"), Map.entry("02", "Alaska"), Map.entry("04", "Arizona"),
            Map.entry("05", "Arkansas"), Map.entry("06", "California"), Map.entry("08", "Colorado"),
            Map.entry("09", "Connecticut"), Map.entry("10", "Delaware"),
            Map.entry("11", "District of Columbia"), Map.entry("12", "Florida"),
            Map.entry("13", "Georgia"), Map.entry("15", "Hawaii"), Map.entry("16", "Idaho"),
            Map.entry("17", "Illinois"), Map.entry("18", "Indiana"), Map.entry("19", "Iowa"),
            Map.entry("20", "Kansas"), Map.entry("21", "Kentucky"), Map.entry("22", "Louisiana"),
            Map.entry("23", "Maine"), Map.entry("24", "Maryland"), Map.entry("25", "Massachusetts"),
            Map.entry("26", "Michigan"), Map.entry("27", "Minnesota"), Map.entry("28", "Mississippi"),
            Map.entry("29", "Missouri"), Map.entry("30", "Montana"), Map.entry("31", "Nebraska"),
            Map.entry("32", "Nevada"), Map.entry("33", "New Hampshire"), Map.entry("34", "New Jersey"),
            Map.entry("35", "New Mexico"), Map.entry("36", "New York"), Map.entry("37", "North Carolina"),
            Map.entry("38", "North Dakota"), Map.entry("39", "Ohio"), Map.entry("40", "Oklahoma"),
            Map.entry("41", "Oregon"), Map.entry("42", "Pennsylvania"), Map.entry("44", "Rhode Island"),
            Map.entry("45", "South Carolina"), Map.entry("46", "South Dakota"), Map.entry("47", "Tennessee"),
            Map.entry("48", "Texas"), Map.entry("49", "Utah"), Map.entry("50", "Vermont"),
            Map.entry("51", "Virginia"), Map.entry("53", "Washington"), Map.entry("54", "West Virginia"),
            Map.entry("55", "Wisconsin"), Map.entry("56", "Wyoming")
    );

    private final JdbcTemplate jdbc;

    public CovidDataLoader(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    private List<String> getDeathsUrls() {
        List<String> urls = new ArrayList<>();
        if (configuredDataUrl != null && !configuredDataUrl.isBlank()) {
            urls.add(configuredDataUrl.trim());
        }
        for (String u : DEFAULT_DEATHS_URLS) {
            urls.add(u);
        }
        return urls;
    }

    /** Resolve path: strip file:// and use as-is for local files. */
    private Path toPath(String pathOrUrl) {
        String path = pathOrUrl.trim();
        if (path.startsWith("file://")) path = path.substring(7);
        return Path.of(path);
    }

    /** When COVID_DATA_URL points to a local deaths file, derive path for Incident Cases in the same folder. */
    private Path deriveCasesPath(Path deathsPath) {
        String name = deathsPath.getFileName().toString();
        String casesName = name.replace("Deaths", "Cases").replace("deaths", "cases");
        if (casesName.equals(name)) {
            return null;
        }
        return deathsPath.getParent() != null ? deathsPath.getParent().resolve(casesName) : Path.of(casesName);
    }

    /** Prefer local file from COVID_DATA_URL for deaths; otherwise download from URLs. */
    private String fetchDeathsCsv() {
        if (configuredDataUrl != null && !configuredDataUrl.isBlank()) {
            Path file = toPath(configuredDataUrl);
            if (Files.isRegularFile(file)) {
                try {
                    String csv = Files.readString(file);
                    log.info("Using local deaths file: {}", file);
                    return csv;
                } catch (Exception e) {
                    log.warn("Failed to read local file {}: {}", file, e.getMessage());
                }
            }
        }
        return fetchFromUrls(getDeathsUrls());
    }

    /** Prefer COVID_CASES_DATA_URL, then local file derived from COVID_DATA_URL (same dir, "Cases" not "Deaths"), else download. */
    private String fetchCasesCsv() {
        // 1. Explicit cases URL/path
        if (configuredCasesUrl != null && !configuredCasesUrl.isBlank()) {
            String pathOrUrl = configuredCasesUrl.trim();
            if (pathOrUrl.startsWith("file://") || (!pathOrUrl.contains("://") && pathOrUrl.length() > 0)) {
                Path p = toPath(pathOrUrl);
                if (Files.isRegularFile(p)) {
                    try {
                        String csv = Files.readString(p);
                        log.info("Using local cases file (COVID_CASES_DATA_URL): {}", p);
                        return csv;
                    } catch (Exception e) {
                        log.warn("Failed to read COVID_CASES_DATA_URL file {}: {}", p, e.getMessage());
                    }
                }
            } else {
                String csv = fetchFromUrls(List.of(pathOrUrl));
                if (csv != null && !csv.isBlank()) return csv;
            }
        }
        // 2. Derived path: same folder as deaths file, "Incident Cases" instead of "Incident Deaths"
        if (configuredDataUrl != null && !configuredDataUrl.isBlank()) {
            Path deathsPath = toPath(configuredDataUrl);
            if (Files.isRegularFile(deathsPath)) {
                Path casesPath = deriveCasesPath(deathsPath);
                if (casesPath != null) {
                    if (Files.isRegularFile(casesPath)) {
                        try {
                            String csv = Files.readString(casesPath);
                            log.info("Using local cases file (derived): {}", casesPath);
                            return csv;
                        } catch (Exception e) {
                            log.warn("Failed to read local cases file {}: {}", casesPath, e.getMessage());
                        }
                    } else {
                        log.info("Cases file not found at {} (Confirmed Cases will be 0 unless you set COVID_CASES_DATA_URL)", casesPath);
                    }
                }
            }
        }
        // 3. Download from default URLs
        List<String> urls = new ArrayList<>();
        for (String u : DEFAULT_CASES_URLS) urls.add(u);
        return fetchFromUrls(urls);
    }

    private String fetchFromUrls(List<String> urls) {
        RestClient client = RestClient.builder()
                .defaultHeader("User-Agent", "PowerBI-Metabase-POC/1.0 (https://github.com)")
                .build();
        for (String url : urls) {
            if (url.startsWith("file://") || (!url.contains("://") && Files.isRegularFile(Path.of(url)))) {
                try {
                    Path p = url.startsWith("file://") ? toPath(url) : Path.of(url);
                    if (Files.isRegularFile(p)) return Files.readString(p);
                } catch (Exception e) {
                    log.warn("Failed to read {}: {}", url, e.getMessage());
                }
                continue;
            }
            try {
                String csv = client.get().uri(url).retrieve().body(String.class);
                if (csv != null && !csv.isBlank()) return csv;
            } catch (Exception e) {
                log.warn("Failed to download from {}: {}", url, e.getMessage());
            }
        }
        return null;
    }

    private Map<String, Integer> aggregateCsv(String csv) {
        Map<String, Integer> agg = new HashMap<>();
        String[] lines = csv.split("\n");
        if (lines.length < 2) return agg;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) continue;
            String[] parts = line.split(",", -1);
            if (parts.length < 3) continue;
            String date = parts[0].trim();
            String location = parts[1].trim();
            if (location.length() < 2) continue;
            String state = STATE_FIPS.get(location.substring(0, 2));
            if (state == null) continue;
            try {
                int value = Integer.parseInt(parts[2].trim());
                agg.merge(date + "|" + state, value, Integer::sum);
            } catch (NumberFormatException ignored) { }
        }
        return agg;
    }

    /**
     * Download USAFacts incident deaths and incident cases, aggregate by (date, state), load into COVID table.
     */
    public void load() {
        log.info("Loading USAFacts COVID-19 data (deaths and cases)...");

        String deathsCsv = fetchDeathsCsv();
        if (deathsCsv == null || deathsCsv.isBlank()) {
            throw new IllegalStateException(
                    "Could not load USAFacts deaths data. Set COVID_DATA_URL to a CSV URL or local file path (e.g. .../truth_usafacts-Incident Deaths.csv), or run without --load-data.");
        }

        Map<String, Integer> deathsAgg = aggregateCsv(deathsCsv);
        log.info("Aggregated {} (date, state) rows from deaths CSV.", deathsAgg.size());

        Map<String, Integer> casesAgg = new HashMap<>();
        String casesCsv = fetchCasesCsv();
        if (casesCsv != null && !casesCsv.isBlank()) {
            casesAgg = aggregateCsv(casesCsv);
            log.info("Aggregated {} (date, state) rows from cases CSV.", casesAgg.size());
        } else {
            log.warn("Could not load incident cases CSV; Confirmed Cases will be 0. Put truth_usafacts-Incident Cases.csv in the same folder as the deaths file, or allow download.");
        }

        java.util.Set<String> allKeys = new java.util.HashSet<>(deathsAgg.keySet());
        allKeys.addAll(casesAgg.keySet());
        log.info("Merged {} (date, state) rows. Loading into COVID table...", allKeys.size());

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS "COVID" (
                date DATE NOT NULL,
                state VARCHAR(100),
                cases INTEGER DEFAULT 0,
                deaths INTEGER DEFAULT 0
            )
            """);
        try {
            jdbc.execute("ALTER TABLE \"COVID\" ADD COLUMN cases INTEGER DEFAULT 0");
        } catch (Exception ignored) { /* column may already exist */ }
        jdbc.execute("TRUNCATE TABLE \"COVID\"");

        String sql = "INSERT INTO \"COVID\" (date, state, cases, deaths) VALUES (?, ?, ?, ?)";
        int[] count = {0};
        for (String key : allKeys) {
            String[] ds = key.split("\\|", 2);
            int cases = casesAgg.getOrDefault(key, 0);
            int deaths = deathsAgg.getOrDefault(key, 0);
            jdbc.update(sql, java.sql.Date.valueOf(ds[0]), ds[1], cases, deaths);
            count[0]++;
            if (count[0] % 10_000 == 0) {
                log.info("Inserted {} rows...", count[0]);
            }
        }

        log.info("Loaded {} rows into COVID table (cases + deaths).", allKeys.size());
    }
}
