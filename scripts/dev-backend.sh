#!/usr/bin/env bash
# Runs the API locally against the docker compose Postgres + Redis, using values from .env.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .env ] || { echo "Missing .env. Copy .env.example to .env first." >&2; exit 1; }
set -a; source .env; set +a
cd backend && exec ./mvnw -q spring-boot:run
