#!/usr/bin/env bash
# Downloads one month of the Lichess open database (CC0) and keeps games where both players
# are rated at least MIN_ELO. Output: data/reference/lichess-<month>-<minElo>plus.pgn
#
# Usage: scripts/fetch-reference.sh [month=2013-01] [minElo=1700]
# Needs: curl, zstd, and a built core module (mvn -q -pl core package -DskipTests).
# Later months are much larger (2013-01 is 18 MB compressed; recent months are tens of GB).
set -euo pipefail

MONTH="${1:-2013-01}"
MIN_ELO="${2:-1700}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RAW="$ROOT/data/raw"
OUT="$ROOT/data/reference/lichess-${MONTH}-${MIN_ELO}plus.pgn"
mkdir -p "$RAW" "$ROOT/data/reference"

ZST="$RAW/lichess_${MONTH}.pgn.zst"
PGN="$RAW/lichess_${MONTH}.pgn"
if [ ! -f "$ZST" ]; then
  echo "Downloading Lichess ${MONTH} database..."
  curl -fL -o "$ZST" "https://database.lichess.org/standard/lichess_db_standard_rated_${MONTH}.pgn.zst"
fi
[ -f "$PGN" ] || zstd -d -q "$ZST" -o "$PGN"

java -cp "$ROOT/core/target/classes" com.openingcoach.core.pgn.PgnTools filter "$PGN" "$OUT" "$MIN_ELO"
echo "Delete data/index/ so the server rebuilds the reference index on next start."
