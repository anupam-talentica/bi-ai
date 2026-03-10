-- Setup script for COVID-19 US Tracking Sample database schema
-- Run this to create the tables that the Power BI report expects

-- Drop existing tables if they exist
DROP TABLE IF EXISTS COVID CASCADE;
DROP TABLE IF EXISTS "Date" CASCADE;
DROP TABLE IF EXISTS States CASCADE;

-- Main COVID data table (facts table)
CREATE TABLE COVID (
    id SERIAL PRIMARY KEY,
    date DATE NOT NULL,
    state VARCHAR(100),
    county VARCHAR(100),
    fips VARCHAR(10),
    cases INTEGER DEFAULT 0,
    deaths INTEGER DEFAULT 0,
    confirmed INTEGER DEFAULT 0,
    recovered INTEGER DEFAULT 0,
    active INTEGER DEFAULT 0,
    incident_rate DECIMAL(10,2),
    case_fatality_ratio DECIMAL(10,4),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Date dimension table
CREATE TABLE "Date" (
    date DATE PRIMARY KEY,
    year INTEGER,
    month INTEGER,
    day INTEGER,
    quarter INTEGER,
    day_of_week INTEGER,
    week_of_year INTEGER,
    month_name VARCHAR(20),
    day_name VARCHAR(20)
);

-- States dimension table
CREATE TABLE States (
    state_code VARCHAR(2) PRIMARY KEY,
    state_name VARCHAR(100) NOT NULL,
    region VARCHAR(50),
    population BIGINT
);

-- Insert sample data for testing
-- Date dimension (last 30 days)
INSERT INTO "Date" (date, year, month, day, quarter, day_of_week, week_of_year, month_name, day_name)
SELECT 
    d::date,
    EXTRACT(YEAR FROM d)::INTEGER,
    EXTRACT(MONTH FROM d)::INTEGER,
    EXTRACT(DAY FROM d)::INTEGER,
    EXTRACT(QUARTER FROM d)::INTEGER,
    EXTRACT(DOW FROM d)::INTEGER,
    EXTRACT(WEEK FROM d)::INTEGER,
    TO_CHAR(d, 'Month'),
    TO_CHAR(d, 'Day')
FROM generate_series(
    CURRENT_DATE - INTERVAL '30 days',
    CURRENT_DATE,
    INTERVAL '1 day'
) AS d;

-- Sample states
INSERT INTO States (state_code, state_name, region, population) VALUES
    ('CA', 'California', 'West', 39538223),
    ('TX', 'Texas', 'South', 29145505),
    ('FL', 'Florida', 'South', 21538187),
    ('NY', 'New York', 'Northeast', 20201249),
    ('PA', 'Pennsylvania', 'Northeast', 13002700),
    ('IL', 'Illinois', 'Midwest', 12812508),
    ('OH', 'Ohio', 'Midwest', 11799448),
    ('GA', 'Georgia', 'South', 10711908),
    ('NC', 'North Carolina', 'South', 10439388),
    ('MI', 'Michigan', 'Midwest', 10077331);

-- Sample COVID data (last 30 days for a few states)
INSERT INTO COVID (date, state, county, fips, cases, deaths, confirmed, recovered, active, incident_rate, case_fatality_ratio)
SELECT 
    d.date,
    s.state_name,
    'Sample County',
    s.state_code || '001',
    (RANDOM() * 1000)::INTEGER + 100,
    (RANDOM() * 50)::INTEGER + 1,
    (RANDOM() * 1000)::INTEGER + 100,
    (RANDOM() * 800)::INTEGER + 50,
    (RANDOM() * 200)::INTEGER + 10,
    RANDOM() * 100,
    RANDOM() * 0.05
FROM "Date" d
CROSS JOIN (SELECT state_code, state_name FROM States LIMIT 5) s;

-- Create indexes for better query performance
CREATE INDEX idx_covid_date ON COVID(date);
CREATE INDEX idx_covid_state ON COVID(state);
CREATE INDEX idx_covid_date_state ON COVID(date, state);

-- Verify data was inserted
SELECT 
    COUNT(*) as total_records,
    MIN(date) as earliest_date,
    MAX(date) as latest_date,
    COUNT(DISTINCT state) as num_states
FROM COVID;

COMMENT ON TABLE COVID IS 'COVID-19 tracking data - cases, deaths, and metrics by date and location';
COMMENT ON TABLE "Date" IS 'Date dimension table for time-based analysis';
COMMENT ON TABLE States IS 'US States dimension table';
