#!/bin/bash

# PowerBI to Metabase POC - Run Script
# This script runs the POC with proper environment configuration

# Check if GEMINI_API_KEY is set
if [ -z "$GEMINI_API_KEY" ]; then
    echo "ERROR: GEMINI_API_KEY environment variable is not set"
    echo ""
    echo "Please set it with:"
    echo "  export GEMINI_API_KEY=your-api-key-here"
    echo ""
    echo "Or use a different LLM provider:"
    echo "  export LLM_PROVIDER=claude"
    echo "  export CLAUDE_API_KEY=your-claude-key"
    exit 1
fi

# Default values (can be overridden)
export LLM_PROVIDER=${LLM_PROVIDER:-gemini}
export METABASE_URL=${METABASE_URL:-http://localhost:3000}
export METABASE_USER=${METABASE_USER:-metabase}
export METABASE_PASSWORD=${METABASE_PASSWORD:-metabase}
export METABASE_DB_ID=${METABASE_DB_ID:-2}
export TARGET_DIALECT=${TARGET_DIALECT:-postgres}

echo "Running PowerBI to Metabase POC..."
echo "LLM Provider: $LLM_PROVIDER"
echo "Metabase URL: $METABASE_URL"
echo "Target Dialect: $TARGET_DIALECT"
echo ""

# Run the application
java -jar target/powerbi-metabase-poc-0.0.1-SNAPSHOT.jar "../COVID-19 US Tracking Sample.pbit"
