# Fix for "column max_date does not exist" Error

## Problem

The error occurred because:

1. The DAX expression `FORMAT([Max date],"mmmm dd, yyyy")` references another measure `[Max date]`
2. The LLM was converting this without context about what `[Max date]` is
3. The generated SQL tried to use `max_date` as a column name, but:
   - No such column exists in the database
   - The database doesn't have any data or schema yet

## Solution Implemented

### Changes Made

1. **Enhanced Data Model Extraction** (`PbixExtractor.java`)
   - Now extracts full data model context (tables, columns, measures)
   - Created `DataModelContext` class to hold this information
   - Provides complete context to the LLM for better conversion

2. **Improved DAX to SQL Conversion** (`DaxToSqlConverter.java`)
   - Accepts full `DataModelContext` instead of just a list of measures
   - Provides table/column schema to the LLM
   - Instructs LLM to inline measure definitions when referenced
   - Tells LLM to use placeholders (like `CURRENT_DATE`) when actual data doesn't exist

3. **Updated Application Flow** (`PocApplication.java`)
   - Now extracts full data model context
   - Passes complete context to the converter

### How to Test

Run the application from terminal 6 (which has the environment variables set):

```bash
cd /Users/anupamg/Desktop/Code/BI/powerbi-metabase-poc
java -jar target/powerbi-metabase-poc-0.0.1-SNAPSHOT.jar "../COVID-19 US Tracking Sample.pbit"
```

The LLM should now:
1. See all 8 measures including `[Max date]`
2. Inline the `[Max date]` definition into the SQL
3. Generate valid SQL that doesn't reference non-existent columns

### Expected Behavior

For the "Updated" measure, the LLM should now generate SQL that:
- Inlines the `[Max date]` measure logic
- Uses actual table/column references from the data model
- Falls back to `CURRENT_DATE` or a placeholder if the actual data doesn't exist

### Next Steps (if SQL still fails)

If the generated SQL still references non-existent columns, you have two options:

#### Option A: Quick Fix - Manually Edit the SQL in Metabase

1. Go to http://localhost:3000/question/38-updated
2. Click "Edit" or open the SQL editor
3. Replace the SQL with:

```sql
SELECT 'Data provided by Johns Hopkins. Because of the frequency of data upates, they may not reflect the exact numbers reported by government organizations or the news media. For more information or to download the data, please click the logo below. Data updated through ' || to_char(CURRENT_DATE, 'Month DD, YYYY') || '.' AS updated_text;
```

This will work without requiring any tables or data.

#### Option B: Proper Fix - Create Database Schema and Load Data

1. **Check what tables/columns were extracted:**
   - The app now logs "Found X table(s) and Y measure(s)"
   - Review the Power BI data model to understand the schema

2. **Create the actual database schema:**
   - The Postgres database is currently empty
   - You need to create tables and load data that match the Power BI model
   - For the COVID-19 sample, this likely includes a facts table with date columns

3. **Connect to Postgres and create schema:**

```bash
# Connect to the Postgres database
docker exec -it credila-postgres psql -U metabase -d metabase

# Create a sample table (adjust based on your actual Power BI model)
CREATE TABLE covid_data (
    date DATE,
    state VARCHAR(100),
    cases INTEGER,
    deaths INTEGER
);

# Insert sample data
INSERT INTO covid_data (date, state, cases, deaths) VALUES
    ('2024-01-01', 'California', 1000, 10),
    ('2024-01-02', 'California', 1100, 12);
```

4. **Re-run the POC** - the LLM should now generate SQL that references the actual table

## Understanding the Root Cause

The "Updated" measure is a **text label**, not a data query. In Power BI:
- It references `[Max date]` which is likely `MAX(covid_data[date])` or similar
- Power BI resolves this in the context of the loaded data model
- When converting to SQL, we need either:
  - The actual table/column to query (Option B)
  - A placeholder like `CURRENT_DATE` (Option A)
