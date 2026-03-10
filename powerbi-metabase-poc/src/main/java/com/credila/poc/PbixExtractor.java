package com.credila.poc;

import com.credila.poc.model.DataModelContext;
import com.credila.poc.model.DaxMeasure;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extracts DAX measures from a Power BI .pbix or .pbit file.
 * .pbix is a ZIP archive; we read DataModelSchema (or DataModel) as UTF-16 LE JSON.
 */
@Component
public class PbixExtractor {

    private static final Logger log = LoggerFactory.getLogger(PbixExtractor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String[] SCHEMA_ENTRY_NAMES = {
            "DataModelSchema",
            "DataModel",
            "Report/Layout"  // fallback: some layouts embed model info
    };

    public List<DaxMeasure> extractMeasures(String pbixPath) throws IOException {
        DataModelContext context = extractDataModel(pbixPath);
        return context.getMeasures();
    }

    public DataModelContext extractDataModel(String pbixPath) throws IOException {
        Path path = Path.of(pbixPath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("File not found or not a regular file: " + pbixPath);
        }

        DataModelContext context = new DataModelContext();
        
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            String schemaEntry = findSchemaEntry(zipFile);
            if (schemaEntry == null) {
                log.warn("No known schema entry found. Available entries: {}", listEntryNames(zipFile));
                return context;
            }

            ZipEntry entry = zipFile.getEntry(schemaEntry);
            if (entry == null) {
                return context;
            }

            byte[] bytes;
            try (InputStream is = zipFile.getInputStream(entry)) {
                bytes = is.readAllBytes();
            }

            // Try UTF-16 LE first (common for Power BI), then UTF-8
            String json = decodeBytes(bytes);
            JsonNode root = MAPPER.readTree(json);

            // Path: model.tables[].measures[] (common in Power BI schema)
            JsonNode model = root.path("model");
            if (model.isMissingNode()) {
                model = root;
            }
            JsonNode tables = model.path("tables");
            if (!tables.isArray()) {
                log.debug("No 'model.tables' array in schema");
                return context;
            }

            List<DaxMeasure> measures = new ArrayList<>();
            List<DataModelContext.TableInfo> tableInfos = new ArrayList<>();
            
            for (JsonNode table : tables) {
                String tableName = table.path("name").asText("");
                DataModelContext.TableInfo tableInfo = new DataModelContext.TableInfo(tableName);
                
                // Extract columns
                JsonNode columns = table.path("columns");
                if (columns.isArray()) {
                    for (JsonNode column : columns) {
                        String colName = column.path("name").asText("");
                        String dataType = column.path("dataType").asText("string");
                        if (!colName.isEmpty()) {
                            tableInfo.getColumns().add(new DataModelContext.ColumnInfo(colName, dataType));
                        }
                    }
                }
                
                if (!tableName.isEmpty()) {
                    tableInfos.add(tableInfo);
                }
                
                // Extract measures
                JsonNode measuresNode = table.path("measures");
                if (!measuresNode.isArray()) continue;
                for (JsonNode measure : measuresNode) {
                    String name = measure.path("name").asText("").trim();
                    String expression = measure.path("expression").asText("").trim();
                    if (!name.isEmpty() && !expression.isEmpty()) {
                        measures.add(new DaxMeasure(name, expression));
                    }
                }
            }
            
            context.setTables(tableInfos);
            context.setMeasures(measures);
            log.info("Extracted {} table(s) and {} measure(s) from {}", 
                    tableInfos.size(), measures.size(), pbixPath);
        }
        return context;
    }

    private String findSchemaEntry(ZipFile zipFile) {
        Enumeration<? extends ZipEntry> e = zipFile.entries();
        while (e.hasMoreElements()) {
            String name = e.nextElement().getName();
            for (String candidate : SCHEMA_ENTRY_NAMES) {
                if (name.equals(candidate)) {
                    return name;
                }
            }
            // Some .pbix have nested path like "DataModel/schema" or "model.bim"
            if (name.endsWith("model.bim") || name.contains("DataModelSchema") || name.equals("DataModel")) {
                return name;
            }
        }
        return null;
    }

    private String listEntryNames(ZipFile zipFile) {
        List<String> names = new ArrayList<>();
        zipFile.stream().forEach(entry -> names.add(entry.getName()));
        return String.join(", ", names);
    }

    private static String decodeBytes(byte[] bytes) {
        // Prefer UTF-16 LE (Power BI often uses this), then UTF-8
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        try {
            return new String(bytes, StandardCharsets.UTF_16LE);
        } catch (Exception e) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
