-- Validation query: Total Deaths by State (matches Power BI report)
-- Run this in Metabase as a Native SQL question to validate migration.
-- Expected: California = 63,501 (as of report date July 19, 2021)

SELECT
    state AS "State",
    SUM(deaths)::int AS "Total Deaths"
FROM "COVID"
WHERE date <= '2021-07-19'
GROUP BY state
ORDER BY "Total Deaths" DESC;
