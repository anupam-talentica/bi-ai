# Testing Instructions

## What I Fixed

1. **Enhanced the LLM prompt** to provide full data model context (tables, columns, measures)
2. **Created the database schema** with the `COVID` table and sample data
3. **The SQL now works** - I tested it and it executes successfully

## Current Status

✅ Database schema created with `COVID` table  
✅ Sample data inserted (155 records across 5 states)  
✅ SQL query tested and working  
✅ Code rebuilt with enhanced context

## To Test the Fix

### Option 1: Re-run the POC to Create a New Card

Go to terminal 6 (which has your environment variables) and run:

```bash
cd /Users/anupamg/Desktop/Code/BI/powerbi-metabase-poc
java -jar target/powerbi-metabase-poc-0.0.1-SNAPSHOT.jar "../COVID-19 US Tracking Sample.pbit"
```

This will create a NEW Metabase card with the correct SQL that references the `COVID` table.

### Option 2: Manually Fix the Existing Card

1. Go to http://localhost:3000/question/38-updated
2. Click the "..." menu → "Edit query"
3. Replace the SQL with:

```sql
SELECT 'Data provided by Johns Hopkins. Because of the frequency of data upates, they may not reflect the exact numbers reported by government organizations or the news media. For more information or to download the data, please click the logo below.  Data updated through ' || to_char((SELECT MAX(date) FROM "COVID"), 'Month DD, YYYY') || '.' AS updated_text;
```

4. Click "Save"

### Option 3: Check the Latest Card

The last successful run created card at: http://localhost:3000/question/39

Try opening that card - it should work now that the database schema exists.

## What the SQL Does

The generated SQL:
- Concatenates a static text string
- Queries `MAX(date)` from the `COVID` table
- Formats the date as "Month DD, YYYY"
- Returns the complete message as a single text field

## Database Details

- **Table:** `COVID` (uppercase, case-sensitive)
- **Key columns:** `date`, `state`, `cases`, `deaths`, `confirmed`, etc.
- **Sample data:** 155 records from Feb 6 to Mar 8, 2026
- **States:** California, Texas, Florida, New York, Pennsylvania

## Verify Database

To check the database contents:

```bash
docker exec -it credila-postgres psql -U metabase -d metabase

# Then run:
SELECT COUNT(*), MIN(date), MAX(date) FROM "COVID";
\d "COVID"
\q
```
