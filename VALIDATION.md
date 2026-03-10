# Validating the Power BI → Metabase POC

Use **one concrete data point** from the Power BI report to confirm the pipeline and data are correct: **Total Deaths in California = 63,501** (as of the report date July 19, 2021).

---

## Step 1: Load real COVID data (USAFacts)

The sample data in Postgres is random. To validate, load the same source the report uses (USAFacts):

```bash
cd /Users/anupamg/Desktop/Code/BI/scripts
chmod +x load_and_validate.sh load_usafacts_data.py
./load_and_validate.sh
```

This will:

1. Download USAFacts Incident Deaths CSV (from reichlab/covid19-forecast-hub) if needed  
2. Aggregate to state-level daily deaths  
3. Truncate and load into the `COVID` table in Postgres  
4. Print **Total Deaths California (through July 19, 2021)** — it should be **63,501**

If the script reports a different number, the USAFacts snapshot may differ slightly from the exact source/date Power BI used; the process is still valid if the pipeline runs and the number is in the same ballpark.

---

## Step 2: Create the “Total Deaths by State” card in Metabase

### Option A: Via the pipeline (migrate the DAX measure)

If the .pbix has a measure named **Total Deaths** (or similar):

```bash
cd /Users/anupamg/Desktop/Code/BI/powerbi-metabase-poc

# List measures to see exact names
java -jar target/powerbi-metabase-poc-0.0.1-SNAPSHOT.jar "../COVID-19 US Tracking Sample.pbit" --list-measures

# Migrate the "Total Deaths" measure (use exact name from list)
java -jar target/powerbi-metabase-poc-0.0.1-SNAPSHOT.jar "../COVID-19 US Tracking Sample.pbit" "Total Deaths"
```

Then open the created Metabase card and confirm **California = 63,501** (or the number from Step 1).

### Option B: Manual validation card

1. In Metabase: **New → SQL query**.  
2. Select the database that points to your Postgres (the one with the `COVID` table).  
3. Paste and run:

```sql
SELECT
    state AS "State",
    SUM(deaths)::int AS "Total Deaths"
FROM "COVID"
WHERE date <= '2021-07-19'
GROUP BY state
ORDER BY "Total Deaths" DESC;
```

4. Save the question (e.g. name: **Total Deaths by State**).  
5. In the result table, find **California** and confirm the value is **63,501**.

---

## Step 3: Confirm validation

| Check | Expected |
|-------|----------|
| Script output: “Total Deaths California (through July 19, 2021)” | **63,501** (or very close) |
| Metabase card: California row | **63,501** (or same as script) |

If both match (or are very close), the POC is validated: same data point as Power BI is available in Metabase via the pipeline or the validation SQL.

---

## Quick reference

| Item | Value |
|------|--------|
| **Validation metric** | Total Deaths, California |
| **Report date** | July 19, 2021 |
| **Expected value** | 63,501 |
| **Data source** | USAFacts (via reichlab/covid19-forecast-hub) |
| **Table** | `"COVID"` (columns: `date`, `state`, `deaths`) |

---

## Troubleshooting

- **Docker**: Ensure Postgres is running (`docker ps | grep credila-postgres`).  
- **Table name**: Use double-quoted `"COVID"` in SQL (case-sensitive).  
- **Different number**: USAFacts updates over time; 63,501 is for the report’s snapshot. As long as the pipeline runs and the number is plausible, the POC is still validated.
