#!/usr/bin/env bash
# Builds data/samples/demo_player.pgn from the Lichess 2013-01 database (CC0) for offline demos.
# Real club-level games (both players rated 1200-1650) with a consistent repertoire:
#   as White: games starting 1.e4
#   as Black: 1.e4 e5, 1.e4 d5 or 1.d4 d5
# That side's player name is replaced by "demo_player"; the moves are unchanged. These are many
# different real players' games presented as one demo player - say so when you show it.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PGN="$ROOT/data/raw/lichess_2013-01.pgn"
[ -f "$PGN" ] || "$ROOT/scripts/fetch-reference.sh" 2013-01 1700
java -cp "$ROOT/core/target/classes" com.openingcoach.core.pgn.PgnTools demo "$PGN" "$ROOT/data/samples/demo_player.pgn" 450
