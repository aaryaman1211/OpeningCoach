package com.openingcoach.core.pgn;

import java.util.List;

/**
 * A game replayed on a board up to some ply limit.
 *
 * @param sanMoves normalised SAN of each ply played (length = plies)
 * @param keys     position keys: keys[0] is the start position, keys[i] the position after ply i
 * @param result   final result of the whole game
 * @param source   the original PGN game
 */
public record ReplayedGame(List<String> sanMoves, long[] keys, GameResult result, PgnGame source) {

    public int plies() {
        return sanMoves.size();
    }
}
