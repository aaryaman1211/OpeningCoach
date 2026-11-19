#!/usr/bin/env bash
# Builds OpeningCoach if needed and starts it on http://localhost:8080 (frontend included).
# Usage: ./run.sh            build only when sources changed, then start
#        ./run.sh --rebuild  always rebuild first
set -euo pipefail
cd "$(dirname "$0")"

JAR=api/target/openingcoach-api-1.0.0.jar

needs_build() {
  [ ! -f "$JAR" ] && return 0
  [ "${1:-}" = "--rebuild" ] && return 0
  # any source newer than the jar?
  [ -n "$(find core/src api/src frontend/src frontend/index.html -newer "$JAR" -type f 2>/dev/null | head -1)" ]
}

if needs_build "${1:-}"; then
  echo "Building the frontend..."
  (cd frontend && { [ -d node_modules ] || npm install --no-audit --no-fund; } && npm run build)
  echo "Building the Java server..."
  mvn -q install -DskipTests
fi

if lsof -nP -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Something is already running on port 8080. Open http://localhost:8080 or stop it first."
  exit 1
fi

echo "Starting OpeningCoach on http://localhost:8080 (Ctrl+C to stop)"
exec java -jar "$JAR"
