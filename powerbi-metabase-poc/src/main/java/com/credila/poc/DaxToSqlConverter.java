package com.credila.poc;

import com.credila.poc.llm.LlmProvider;
import com.credila.poc.model.DataModelContext;
import com.credila.poc.model.DaxMeasure;
import org.springframework.stereotype.Component;

/**
 * Converts DAX measure expressions to SQL using a configurable LLM provider
 * (Claude, OpenAI, or Gemini). The provider is selected via {@code llm.provider}.
 */
@Component
public class DaxToSqlConverter {

    private final LlmProvider llmProvider;

    public DaxToSqlConverter(LlmProvider llmProvider) {
        this.llmProvider = llmProvider;
    }

    public String convertToSql(String daxExpression, String targetDialect) {
        return convertToSql(daxExpression, targetDialect, null);
    }

    public String convertToSql(String daxExpression, String targetDialect, DataModelContext dataModel) {
        StringBuilder contextBuilder = new StringBuilder();
        
        if (dataModel != null) {
            // Add table/column context
            if (!dataModel.getTables().isEmpty()) {
                contextBuilder.append("\nAvailable tables and columns in the data model:\n");
                for (DataModelContext.TableInfo table : dataModel.getTables()) {
                    contextBuilder.append("Table: ").append(table.getName()).append("\n");
                    for (DataModelContext.ColumnInfo column : table.getColumns()) {
                        contextBuilder.append("  - ").append(column.getName())
                                .append(" (").append(column.getDataType()).append(")\n");
                    }
                }
            }
            
            // Add measures context
            if (!dataModel.getMeasures().isEmpty()) {
                contextBuilder.append("\nAvailable measures in the Power BI model:\n");
                for (DaxMeasure measure : dataModel.getMeasures()) {
                    contextBuilder.append("- [").append(measure.getName()).append("]: ")
                            .append(measure.getExpression()).append("\n");
                }
                contextBuilder.append("\nIf the DAX expression references other measures (e.g., [Max date]), ");
                contextBuilder.append("you MUST inline their definitions.\n");
            }
        }
        
        String prompt = String.format("""
                You are a DAX to SQL conversion expert.
                
                Convert this DAX measure to %s SQL:
                
                ```dax
                %s
                ```
                %s
                Requirements:
                - Output ONLY the SQL query, no explanations, no markdown code fences
                - Use standard SQL syntax for %s
                - If the DAX references other measures (like [Max date]), you MUST inline their definitions
                - When referencing columns, use the exact lowercase column names from the schema (e.g., date, state, deaths, cases). Do NOT use spaces in column names; if the DAX says [Daily deaths] or [Deaths], use the schema column name "deaths".
                - When referencing tables, use UPPERCASE names with quotes (e.g., "COVID", "Date")
                - Column names with spaces or special characters must be double-quoted in PostgreSQL; prefer single-word lowercase columns from the schema to avoid syntax errors
                - If a referenced measure is not provided in the context, replace it with a reasonable placeholder
                - For text/label measures that concatenate strings with dates, if you don't know the actual table/column:
                  * Use CURRENT_DATE or a placeholder date like '2024-01-01'::date
                  * Example: to_char(CURRENT_DATE, 'Month DD, YYYY') instead of referencing unknown columns
                - Ensure the query is valid and executable without requiring specific tables
                - Preserve the business logic exactly
                - Always give the main result an explicit column alias (e.g., AS result, AS updated_text, AS value) so Metabase shows a proper column name instead of ?column?
                
                SQL:
                """, targetDialect, daxExpression, contextBuilder.toString(), targetDialect);

        String raw = llmProvider.complete(prompt);
        String cleaned = cleanSql(raw);
        return sanitizeColumnNames(cleaned);
    }

    private static String cleanSql(String sql) {
        String s = sql.trim();
        if (s.startsWith("```")) {
            int start = s.indexOf('\n');
            if (start > 0) s = s.substring(start + 1);
            int end = s.lastIndexOf("```");
            if (end > 0) s = s.substring(0, end).trim();
        }
        return s;
    }

    /**
     * Fix common LLM typos and map Power BI column names to actual schema names
     * to avoid "syntax error at or near" in PostgreSQL.
     */
    private static String sanitizeColumnNames(String sql) {
        String s = sql;
        // Fix typo "dail deaths" or "dail deaths" -> deaths
        s = s.replace("\"COVID\".dail deaths", "\"COVID\".deaths");
        s = s.replace("COVID.dail deaths", "\"COVID\".deaths");
        s = s.replace(".dail deaths", ".deaths");
        s = s.replace("\"dail deaths\"", "deaths");
        // Map "Daily deaths" (if quoted) to deaths
        s = s.replace("\"Daily deaths\"", "deaths");
        s = s.replace("\"COVID\".\"Daily deaths\"", "\"COVID\".deaths");
        return s;
    }
}
