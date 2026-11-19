package com.openingcoach.core.pgn;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data-preparation commands (run once, not part of the app):
 * <pre>
 *   filter   in.pgn out.pgn minElo              keep games where both players are rated ≥ minElo
 *   demo     in.pgn out.pgn [perSide=450]       build the offline demo player file
 * </pre>
 * Games are copied byte-for-byte; only the demo command rewrites one player name.
 */
public final class PgnTools {

    private static final Pattern WHITE_ELO = Pattern.compile("\\[WhiteElo \"(\\d+)\"\\]");
    private static final Pattern BLACK_ELO = Pattern.compile("\\[BlackElo \"(\\d+)\"\\]");

    private PgnTools() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Usage: PgnTools filter <in.pgn> <out.pgn> <minElo> | demo <in.pgn> <out.pgn> [perSide]");
            System.exit(2);
        }
        Path in = Path.of(args[1]), out = Path.of(args[2]);
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        int written = switch (args[0]) {
            case "filter" -> {
                int min = Integer.parseInt(args[3]);
                yield copy(in, out, (h, m) -> elo(h, WHITE_ELO) >= min && elo(h, BLACK_ELO) >= min, null);
            }
            case "demo" -> demo(in, out, args.length > 3 ? Integer.parseInt(args[3]) : 450);
            default -> throw new IllegalArgumentException("Unknown command " + args[0]);
        };
        System.out.println("Wrote " + written + " games to " + out);
    }

    /**
     * Real club-level games (both players 1200-1650) with a consistent repertoire: as White,
     * games starting 1.e4; as Black, 1.e4 e5 / 1.e4 d5 / 1.d4 d5. That side's name becomes demo_player.
     */
    private static int demo(Path in, Path out, int perSide) throws IOException {
        int[] counts = new int[2];
        return copy(in, out, (h, m) -> {
            int w = elo(h, WHITE_ELO), b = elo(h, BLACK_ELO);
            return w >= 1200 && w <= 1650 && b >= 1200 && b <= 1650;
        }, (h, m) -> {
            if (counts[0] < perSide && m.startsWith("1. e4 ")) {
                counts[0]++;
                return h.replaceFirst("\\[White \"[^\"]*\"\\]", "[White \"demo_player\"]");
            }
            if (counts[1] < perSide && (m.startsWith("1. e4 e5 ") || m.startsWith("1. e4 d5 ") || m.startsWith("1. d4 d5 "))) {
                counts[1]++;
                return h.replaceFirst("\\[Black \"[^\"]*\"\\]", "[Black \"demo_player\"]");
            }
            return null;
        });
    }

    interface HeaderRewrite {
        /** New header text, or null to drop the game. */
        String apply(String header, String movetext);
    }

    private static int copy(Path in, Path out, BiPredicate<String, String> keep, HeaderRewrite rewrite) throws IOException {
        int written = 0;
        try (BufferedReader r = Files.newBufferedReader(in, StandardCharsets.UTF_8);
             BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            StringBuilder header = new StringBuilder(), moves = new StringBuilder();
            String line;
            while (true) {
                line = r.readLine();
                boolean newGame = line == null || (line.startsWith("[") && moves.length() > 0);
                if (newGame && header.length() > 0) {
                    String h = header.toString(), m = moves.toString().strip();
                    if (keep.test(h, m)) {
                        String out2 = rewrite == null ? h : rewrite.apply(h, m);
                        if (out2 != null) {
                            w.write(out2);
                            w.write("\n");
                            w.write(m);
                            w.write("\n\n");
                            written++;
                        }
                    }
                    header.setLength(0);
                    moves.setLength(0);
                }
                if (line == null) {
                    break;
                }
                if (line.startsWith("[")) {
                    header.append(line).append('\n');
                } else if (!line.isBlank()) {
                    moves.append(line).append('\n');
                }
            }
        }
        return written;
    }

    private static int elo(String header, Pattern p) {
        Matcher m = p.matcher(header);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }
}
