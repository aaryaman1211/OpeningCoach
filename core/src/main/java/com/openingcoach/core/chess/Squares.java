package com.openingcoach.core.chess;

/** Square-index helpers and SAN clean-up. a1 = 0, h8 = 63. */
public final class Squares {

    private Squares() {
    }

    public static int file(int square) {
        return square & 7;
    }

    public static int rank(int square) {
        return square >> 3;
    }

    public static String name(int square) {
        return "" + (char) ('a' + file(square)) + (char) ('1' + rank(square));
    }

    public static int parse(String name) {
        int s = parseOrMinus(name);
        if (s < 0) {
            throw new IllegalArgumentException("Bad square: " + name);
        }
        return s;
    }

    static int parseOrMinus(String name) {
        if (name == null || name.length() != 2) {
            return -1;
        }
        char f = name.charAt(0), r = name.charAt(1);
        if (f < 'a' || f > 'h' || r < '1' || r > '8') {
            return -1;
        }
        return (r - '1') * 8 + (f - 'a');
    }

    /**
     * Removes check/mate marks and annotation glyphs, and normalises castling written with zeros.
     * "Nf3+" -> "Nf3", "e8=Q#" -> "e8=Q", "0-0-0" -> "O-O-O", "Bb5!?" -> "Bb5".
     */
    public static String cleanSan(String san) {
        if (san == null) {
            return "";
        }
        String s = san.trim();
        int end = s.length();
        while (end > 0 && "+#!?".indexOf(s.charAt(end - 1)) >= 0) {
            end--;
        }
        s = s.substring(0, end);
        if (s.equals("0-0")) {
            return "O-O";
        }
        if (s.equals("0-0-0")) {
            return "O-O-O";
        }
        return s;
    }
}
