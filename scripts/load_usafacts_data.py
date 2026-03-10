#!/usr/bin/env python3
"""
Load USAFacts COVID-19 incident deaths into Postgres-friendly CSV.
Data source: reichlab/covid19-forecast-hub (USAFacts).
Output: state-level daily incident deaths (date, state_name, deaths).
Use to validate Power BI migration: Total Deaths California should match report (e.g. 63,501 as of 2021-07-19).
"""
import csv
import sys
import urllib.request
from collections import defaultdict
from pathlib import Path

# State FIPS (first 2 digits of county FIPS) -> state name
STATE_FIPS = {
    "01": "Alabama", "02": "Alaska", "04": "Arizona", "05": "Arkansas",
    "06": "California", "08": "Colorado", "09": "Connecticut",
    "10": "Delaware", "11": "District of Columbia", "12": "Florida",
    "13": "Georgia", "15": "Hawaii", "16": "Idaho", "17": "Illinois",
    "18": "Indiana", "19": "Iowa", "20": "Kansas", "21": "Kentucky",
    "22": "Louisiana", "23": "Maine", "24": "Maryland", "25": "Massachusetts",
    "26": "Michigan", "27": "Minnesota", "28": "Mississippi", "29": "Missouri",
    "30": "Montana", "31": "Nebraska", "32": "Nevada", "33": "New Hampshire",
    "34": "New Jersey", "35": "New Mexico", "36": "New York",
    "37": "North Carolina", "38": "North Dakota", "39": "Ohio",
    "40": "Oklahoma", "41": "Oregon", "42": "Pennsylvania",
    "44": "Rhode Island", "45": "South Carolina", "46": "South Dakota",
    "47": "Tennessee", "48": "Texas", "49": "Utah", "50": "Vermont",
    "51": "Virginia", "53": "Washington", "54": "West Virginia",
    "55": "Wisconsin", "56": "Wyoming",
}

USAFACTS_INCIDENT_DEATHS_URL = (
    "https://raw.githubusercontent.com/reichlab/covid19-forecast-hub/master"
    "/data-truth/usafacts/truth_usafacts-Incident%20Deaths.csv"
)


def main():
    script_dir = Path(__file__).resolve().parent
    csv_path = script_dir / "usafacts_incident_deaths.csv"
    out_path = script_dir / "covid_state_daily_deaths.csv"

    # Download if not present (~35MB, may take 1–2 min)
    if not csv_path.exists():
        print("Downloading USAFacts Incident Deaths (~35MB)...", file=sys.stderr)
        urllib.request.urlretrieve(USAFACTS_INCIDENT_DEATHS_URL, csv_path)
        print("Downloaded to", csv_path, file=sys.stderr)

    # Aggregate by (date, state): sum of incident deaths
    agg = defaultdict(int)
    with open(csv_path, newline="", encoding="utf-8") as f:
        r = csv.DictReader(f)
        for row in r:
            date = row["date"]
            loc = row["location"]
            if len(loc) < 2:
                continue
            state_fips = loc[:2]
            state_name = STATE_FIPS.get(state_fips)
            if not state_name:
                continue
            try:
                value = int(row["value"])
            except ValueError:
                continue
            agg[(date, state_name)] += value

    # Write state-level daily deaths for Postgres
    with open(out_path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["date", "state", "deaths"])
        for (date, state), deaths in sorted(agg.items()):
            w.writerow([date, state, deaths])

    print("Wrote", out_path, "-", len(agg), "rows (date, state)", file=sys.stderr)
    return str(out_path)


if __name__ == "__main__":
    main()
