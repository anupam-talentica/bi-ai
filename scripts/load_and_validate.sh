#!/bin/bash
# Load USAFacts COVID-19 data into Postgres and run validation query.
# Usage: ./load_and_validate.sh [postgres_container]
# Default container: credila-postgres

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONTAINER="${1:-credila-postgres}"
CSV="$SCRIPT_DIR/covid_state_daily_deaths.csv"

echo "=== 1. Generate state-level daily deaths CSV ==="
python3 "$SCRIPT_DIR/load_usafacts_data.py"

echo ""
echo "=== 2. Load into Postgres (container: $CONTAINER) ==="
# Truncate and load only (date, state, deaths); other columns stay default
docker exec -i "$CONTAINER" psql -U metabase -d metabase << EOF
TRUNCATE TABLE "COVID";
EOF

# Copy CSV into container and load via \copy (date, state, deaths)
docker cp "$CSV" "$CONTAINER:/tmp/covid_state_daily_deaths.csv"
docker exec -i "$CONTAINER" psql -U metabase -d metabase -c "\\COPY \"COVID\"(date, state, deaths) FROM '/tmp/covid_state_daily_deaths.csv' WITH (FORMAT csv, HEADER true);"

echo ""
echo "=== 3. Validation: Total Deaths by State (top 5) ==="
docker exec -i "$CONTAINER" psql -U metabase -d metabase -t -A -F' | ' -c "
SELECT state AS \"State\", SUM(deaths)::int AS \"Total Deaths\"
FROM \"COVID\"
GROUP BY state
ORDER BY SUM(deaths) DESC
LIMIT 5;
"

echo ""
echo "=== 4. California Total Deaths (all time in dataset) ==="
docker exec -i "$CONTAINER" psql -U metabase -d metabase -t -c "
SELECT SUM(deaths)::int AS \"Total Deaths California\"
FROM \"COVID\"
WHERE state = 'California';
"

echo ""
echo "=== 5. California Total Deaths as of 2021-07-19 (Power BI report date) ==="
docker exec -i "$CONTAINER" psql -U metabase -d metabase -t -c "
SELECT SUM(deaths)::int AS \"Total Deaths California (through July 19, 2021)\"
FROM \"COVID\"
WHERE state = 'California' AND date <= '2021-07-19';
"

echo ""
echo "Done. Compare 'Total Deaths California (through July 19, 2021)' to Power BI: 63,501."
