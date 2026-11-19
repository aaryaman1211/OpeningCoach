package com.openingcoach.core.pgn;

import java.util.List;
import java.util.Map;

/** One game as read from PGN: header tags plus the main-line moves in SAN (variations removed). */
public record PgnGame(Map<String, String> tags, List<String> sanMoves) {

    public PgnGame {
        tags = Map.copyOf(tags);
        sanMoves = List.copyOf(sanMoves);
    }

    public String tag(String name) {
        return tags.getOrDefault(name, "");
    }

    public String white() {
        return tag("White");
    }

    public String black() {
        return tag("Black");
    }

    public GameResult result() {
        return GameResult.parse(tag("Result"));
    }

    /** Elo from the header, or -1 if missing / not a number. */
    public int elo(boolean white) {
        try {
            return Integer.parseInt(tag(white ? "WhiteElo" : "BlackElo").trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Standard chess from the normal start position (no Chess960, no custom FEN). */
    public boolean isStandardStart() {
        String variant = tag("Variant").trim();
        boolean standardVariant = variant.isEmpty() || variant.equalsIgnoreCase("Standard")
                || variant.equalsIgnoreCase("chess");
        return standardVariant && tag("FEN").isEmpty() && !tag("SetUp").equals("1");
    }
}
