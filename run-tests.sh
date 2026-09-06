#!/bin/bash
# Convenience script for running the full local test suite, including
# SmartbookingApplicationTests.contextLoads (which needs a real DB connection).
#
# Usage: bash run-tests.sh

set -e

echo "Starting Postgres (if not already running)..."
docker compose up -d db

echo "Loading environment variables from .env..."
set -a
source .env
set +a

echo "Running tests..."
mvn test