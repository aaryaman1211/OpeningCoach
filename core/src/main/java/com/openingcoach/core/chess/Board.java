package com.openingcoach.core.chess;

/**
 * Minimal legal-move-aware chess board, used only to replay games written in SAN
 * and to compute a position key for every ply. It is infrastructure for the
 * assessed algorithms (trie, Bloom filter, selection, coverage), not one of them.
 *
 * Squares are numbered 0..63 with a1 = 0, h1 = 7, a8 = 56, h8 = 63.
 * Pieces are stored as signed ints: positive = white, negative = black.
 */
public final class Board {

    public static final int EMPTY = 0, PAWN = 1, KNIGHT = 2, BISHOP = 3, ROOK = 4, QUEEN = 5, KING = 6;

    static final int WK_CASTLE = 1, WQ_CASTLE = 2, BK_CASTLE = 4, BQ_CASTLE = 8;

    private static final int[][] KNIGHT_STEPS = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
    private static final int[][] KING_STEPS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final int[][] ROOK_DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] BISHOP_DIRS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private final int[] squares = new int[64];
    private boolean whiteToMove = true;
    private int castling;
    private int epSquare = -1;
    private int halfmoveClock;
    private int fullmoveNumber = 1;

    private Board() {
    }

    public static Board startPosition() {
        return fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
    }

    public static Board fromFen(String fen) {
        if (fen == null || fen.isBlank()) {
            throw new IllegalArgumentException("FEN is empty");
        }
        String[] parts = fen.trim().split("\\s+");
        if (parts.length < 4) {
            throw new IllegalArgumentException("FEN needs at least 4 fields: " + fen);
        }
        Board b = new Board();
        String[] ranks = parts[0].split("/");
        if (ranks.length != 8) {
            throw new IllegalArgumentException("FEN placement must have 8 ranks: " + fen);
        }
        for (int i = 0; i < 8; i++) {
            int rank = 7 - i;
            int file = 0;
            for (char c : ranks[i].toCharArray()) {
                if (Character.isDigit(c)) {
                    file += c - '0';
                } else {
                    if (file > 7) {
                        throw new IllegalArgumentException("FEN rank too long: " + ranks[i]);
                    }
                    b.squares[rank * 8 + file] = pieceFromFenChar(c);
                    file++;
                }
            }
            if (file != 8) {
                throw new IllegalArgumentException("FEN rank does not have 8 files: " + ranks[i]);
            }
        }
        b.whiteToMove = parts[1].equals("w");
        b.castling = 0;
        if (!parts[2].equals("-")) {
            for (char c : parts[2].toCharArray()) {
                switch (c) {
                    case 'K' -> b.castling |= WK_CASTLE;
                    case 'Q' -> b.castling |= WQ_CASTLE;
                    case 'k' -> b.castling |= BK_CASTLE;
                    case 'q' -> b.castling |= BQ_CASTLE;
                    default -> throw new IllegalArgumentException("Bad castling field: " + parts[2]);
                }
            }
        }
        b.epSquare = parts[3].equals("-") ? -1 : Squares.parse(parts[3]);
        if (parts.length >= 6) {
            b.halfmoveClock = Integer.parseInt(parts[4]);
            b.fullmoveNumber = Integer.parseInt(parts[5]);
        }
        return b;
    }

    public Board copy() {
        Board b = new Board();
        System.arraycopy(squares, 0, b.squares, 0, 64);
        b.whiteToMove = whiteToMove;
        b.castling = castling;
        b.epSquare = epSquare;
        b.halfmoveClock = halfmoveClock;
        b.fullmoveNumber = fullmoveNumber;
        return b;
    }

    public int pieceAt(int square) {
        return squares[square];
    }

    public boolean whiteToMove() {
        return whiteToMove;
    }

    int castlingRights() {
        return castling;
    }

    int epSquare() {
        return epSquare;
    }

    /** Zobrist key of the current position (placement, side to move, castling, capturable en passant). */
    public long key() {
        return Zobrist.hash(this);
    }

    /**
     * Plays one move written in Standard Algebraic Notation.
     *
     * @return the move in normalised SAN (check/annotation symbols removed)
     * @throws IllegalMoveException if the SAN is malformed, ambiguous, or illegal here
     */
    public String applySan(String rawSan) {
        String san = Squares.cleanSan(rawSan);
        if (san.isEmpty()) {
            throw new IllegalMoveException("Empty move", rawSan);
        }
        if (san.equals("O-O") || san.equals("O-O-O")) {
            castle(san.equals("O-O"), rawSan);
            return san;
        }

        int promotion = EMPTY;
        int eq = san.indexOf('=');
        String body = san;
        if (eq >= 0) {
            if (eq + 1 >= san.length()) {
                throw new IllegalMoveException("Missing promotion piece", rawSan);
            }
            promotion = pieceTypeFromLetter(san.charAt(eq + 1), rawSan);
            body = san.substring(0, eq);
        } else if (san.length() >= 3 && Character.isLowerCase(san.charAt(0))
                && "QRBN".indexOf(san.charAt(san.length() - 1)) >= 0) {
            promotion = pieceTypeFromLetter(san.charAt(san.length() - 1), rawSan);
            body = san.substring(0, san.length() - 1);
        }
        if (body.length() < 2) {
            throw new IllegalMoveException("Move too short", rawSan);
        }
        int to = Squares.parseOrMinus(body.substring(body.length() - 2));
        if (to < 0) {
            throw new IllegalMoveException("Bad target square", rawSan);
        }
        String prefix = body.substring(0, body.length() - 2);
        boolean capture = prefix.indexOf('x') >= 0;
        prefix = prefix.replace("x", "");

        int type;
        String disambiguation;
        if (!prefix.isEmpty() && "NBRQK".indexOf(prefix.charAt(0)) >= 0) {
            type = pieceTypeFromLetter(prefix.charAt(0), rawSan);
            disambiguation = prefix.substring(1);
        } else {
            type = PAWN;
            disambiguation = prefix;
        }
        int disFile = -1, disRank = -1;
        for (char c : disambiguation.toCharArray()) {
            if (c >= 'a' && c <= 'h') {
                disFile = c - 'a';
            } else if (c >= '1' && c <= '8') {
                disRank = c - '1';
            } else {
                throw new IllegalMoveException("Bad disambiguation '" + disambiguation + "'", rawSan);
            }
        }
        if (type == PAWN && capture && disFile < 0) {
            throw new IllegalMoveException("Pawn capture must name the source file", rawSan);
        }
        if (type != PAWN && promotion != EMPTY) {
            throw new IllegalMoveException("Only pawns promote", rawSan);
        }

        int sign = whiteToMove ? 1 : -1;
        int found = -1;
        int matches = 0;
        for (int from = 0; from < 64; from++) {
            if (squares[from] != sign * type) {
                continue;
            }
            if (disFile >= 0 && Squares.file(from) != disFile) {
                continue;
            }
            if (disRank >= 0 && Squares.rank(from) != disRank) {
                continue;
            }
            boolean reachable = type == PAWN ? pawnCanMove(from, to, capture) : pieceCanReach(from, to, type);
            if (!reachable || !leavesKingSafe(from, to, promotion)) {
                continue;
            }
            found = from;
            matches++;
        }
        if (matches == 0) {
            throw new IllegalMoveException("No legal piece can make this move", rawSan);
        }
        if (matches > 1) {
            throw new IllegalMoveException("Ambiguous move", rawSan);
        }
        if (type == PAWN) {
            int lastRank = whiteToMove ? 7 : 0;
            if (Squares.rank(to) == lastRank && promotion == EMPTY) {
                throw new IllegalMoveException("Pawn reaching last rank must promote", rawSan);
            }
            if (Squares.rank(to) != lastRank && promotion != EMPTY) {
                throw new IllegalMoveException("Promotion only on the last rank", rawSan);
            }
            if (promotion == KING || promotion == PAWN) {
                throw new IllegalMoveException("Cannot promote to that piece", rawSan);
            }
        }
        makeMove(found, to, promotion);
        return san;
    }

    // ------------------------------------------------------------------ move rules

    private boolean pawnCanMove(int from, int to, boolean capture) {
        int dir = whiteToMove ? 1 : -1;
        int df = Squares.file(to) - Squares.file(from);
        int dr = Squares.rank(to) - Squares.rank(from);
        if (!capture) {
            if (df != 0 || squares[to] != EMPTY) {
                return false;
            }
            if (dr == dir) {
                return true;
            }
            int startRank = whiteToMove ? 1 : 6;
            return dr == 2 * dir && Squares.rank(from) == startRank && squares[from + 8 * dir] == EMPTY;
        }
        if (Math.abs(df) != 1 || dr != dir) {
            return false;
        }
        int target = squares[to];
        boolean enemyThere = whiteToMove ? target < 0 : target > 0;
        return enemyThere || to == epSquare;
    }

    private boolean pieceCanReach(int from, int to, int type) {
        int target = squares[to];
        if (target != EMPTY && (target > 0) == whiteToMove) {
            return false; // own piece on target
        }
        int df = Squares.file(to) - Squares.file(from);
        int dr = Squares.rank(to) - Squares.rank(from);
        int adf = Math.abs(df), adr = Math.abs(dr);
        return switch (type) {
            case KNIGHT -> (adf == 1 && adr == 2) || (adf == 2 && adr == 1);
            case KING -> Math.max(adf, adr) == 1;
            case ROOK -> (df == 0 || dr == 0) && (adf + adr > 0) && pathClear(from, to);
            case BISHOP -> adf == adr && adf > 0 && pathClear(from, to);
            case QUEEN -> ((df == 0 || dr == 0) || adf == adr) && (adf + adr > 0) && pathClear(from, to);
            default -> false;
        };
    }

    private boolean pathClear(int from, int to) {
        int sf = Integer.signum(Squares.file(to) - Squares.file(from));
        int sr = Integer.signum(Squares.rank(to) - Squares.rank(from));
        int f = Squares.file(from) + sf, r = Squares.rank(from) + sr;
        while (f != Squares.file(to) || r != Squares.rank(to)) {
            if (squares[r * 8 + f] != EMPTY) {
                return false;
            }
            f += sf;
            r += sr;
        }
        return true;
    }

    private boolean leavesKingSafe(int from, int to, int promotion) {
        Board trial = copy();
        trial.makeMove(from, to, promotion == EMPTY && Math.abs(squares[from]) == PAWN
                && (Squares.rank(to) == 0 || Squares.rank(to) == 7) ? QUEEN : promotion);
        // after makeMove the side to move flipped; check the mover's king
        int king = trial.findKing(whiteToMove);
        return king >= 0 && !trial.isAttacked(king, !whiteToMove);
    }

    private void castle(boolean kingside, String rawSan) {
        int base = whiteToMove ? 0 : 56;
        int right = whiteToMove ? (kingside ? WK_CASTLE : WQ_CASTLE) : (kingside ? BK_CASTLE : BQ_CASTLE);
        int sign = whiteToMove ? 1 : -1;
        if ((castling & right) == 0) {
            throw new IllegalMoveException("Castling right already lost", rawSan);
        }
        int kingFrom = base + 4;
        int rookFrom = base + (kingside ? 7 : 0);
        if (squares[kingFrom] != sign * KING || squares[rookFrom] != sign * ROOK) {
            throw new IllegalMoveException("King or rook not on its starting square", rawSan);
        }
        int[] mustBeEmpty = kingside ? new int[]{base + 5, base + 6} : new int[]{base + 1, base + 2, base + 3};
        for (int s : mustBeEmpty) {
            if (squares[s] != EMPTY) {
                throw new IllegalMoveException("Pieces between king and rook", rawSan);
            }
        }
        int[] kingPath = kingside ? new int[]{base + 4, base + 5, base + 6} : new int[]{base + 4, base + 3, base + 2};
        for (int s : kingPath) {
            if (isAttacked(s, !whiteToMove)) {
                throw new IllegalMoveException("Cannot castle out of, through, or into check", rawSan);
            }
        }
        int kingTo = base + (kingside ? 6 : 2);
        int rookTo = base + (kingside ? 5 : 3);
        squares[kingTo] = squares[kingFrom];
        squares[kingFrom] = EMPTY;
        squares[rookTo] = squares[rookFrom];
        squares[rookFrom] = EMPTY;
        castling &= whiteToMove ? ~(WK_CASTLE | WQ_CASTLE) : ~(BK_CASTLE | BQ_CASTLE);
        epSquare = -1;
        halfmoveClock++;
        finishTurn();
    }

    private void makeMove(int from, int to, int promotion) {
        int piece = squares[from];
        int captured = squares[to];
        boolean pawn = Math.abs(piece) == PAWN;
        int sign = piece > 0 ? 1 : -1;

        if (pawn && to == epSquare && captured == EMPTY) {
            squares[to - 8 * sign] = EMPTY; // en passant capture
        }
        squares[to] = promotion != EMPTY ? sign * promotion : piece;
        squares[from] = EMPTY;

        if (piece == KING) {
            castling &= ~(WK_CASTLE | WQ_CASTLE);
        } else if (piece == -KING) {
            castling &= ~(BK_CASTLE | BQ_CASTLE);
        }
        for (int s : new int[]{from, to}) {
            switch (s) {
                case 0 -> castling &= ~WQ_CASTLE;
                case 7 -> castling &= ~WK_CASTLE;
                case 56 -> castling &= ~BQ_CASTLE;
                case 63 -> castling &= ~BK_CASTLE;
                default -> { }
            }
        }
        epSquare = pawn && Math.abs(to - from) == 16 ? (from + to) / 2 : -1;
        halfmoveClock = (pawn || captured != EMPTY) ? 0 : halfmoveClock + 1;
        finishTurn();
    }

    private void finishTurn() {
        if (!whiteToMove) {
            fullmoveNumber++;
        }
        whiteToMove = !whiteToMove;
    }

    int findKing(boolean white) {
        int target = white ? KING : -KING;
        for (int s = 0; s < 64; s++) {
            if (squares[s] == target) {
                return s;
            }
        }
        return -1;
    }

    /** True if {@code square} is attacked by a piece of the given colour. */
    public boolean isAttacked(int square, boolean byWhite) {
        int sign = byWhite ? 1 : -1;
        int tf = Squares.file(square), tr = Squares.rank(square);

        int pawnRank = tr - sign; // attacking pawn stands one rank "behind" the target
        for (int df : new int[]{-1, 1}) {
            if (onBoard(tf + df, pawnRank) && squares[pawnRank * 8 + tf + df] == sign * PAWN) {
                return true;
            }
        }
        for (int[] st : KNIGHT_STEPS) {
            int f = tf + st[0], r = tr + st[1];
            if (onBoard(f, r) && squares[r * 8 + f] == sign * KNIGHT) {
                return true;
            }
        }
        for (int[] st : KING_STEPS) {
            int f = tf + st[0], r = tr + st[1];
            if (onBoard(f, r) && squares[r * 8 + f] == sign * KING) {
                return true;
            }
        }
        if (slidingAttack(tf, tr, ROOK_DIRS, sign * ROOK, sign * QUEEN)) {
            return true;
        }
        return slidingAttack(tf, tr, BISHOP_DIRS, sign * BISHOP, sign * QUEEN);
    }

    private boolean slidingAttack(int tf, int tr, int[][] dirs, int slider, int queen) {
        for (int[] d : dirs) {
            int f = tf + d[0], r = tr + d[1];
            while (onBoard(f, r)) {
                int p = squares[r * 8 + f];
                if (p != EMPTY) {
                    if (p == slider || p == queen) {
                        return true;
                    }
                    break;
                }
                f += d[0];
                r += d[1];
            }
        }
        return false;
    }

    private static boolean onBoard(int f, int r) {
        return f >= 0 && f < 8 && r >= 0 && r < 8;
    }

    // ------------------------------------------------------------------ FEN output

    public String toFen() {
        StringBuilder sb = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                int p = squares[rank * 8 + file];
                if (p == EMPTY) {
                    empty++;
                } else {
                    if (empty > 0) {
                        sb.append(empty);
                        empty = 0;
                    }
                    sb.append(fenChar(p));
                }
            }
            if (empty > 0) {
                sb.append(empty);
            }
            if (rank > 0) {
                sb.append('/');
            }
        }
        sb.append(whiteToMove ? " w " : " b ");
        String c = (((castling & WK_CASTLE) != 0) ? "K" : "") + (((castling & WQ_CASTLE) != 0) ? "Q" : "")
                + (((castling & BK_CASTLE) != 0) ? "k" : "") + (((castling & BQ_CASTLE) != 0) ? "q" : "");
        sb.append(c.isEmpty() ? "-" : c);
        sb.append(' ').append(epSquare < 0 ? "-" : Squares.name(epSquare));
        sb.append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber);
        return sb.toString();
    }

    private static char fenChar(int p) {
        char c = " pnbrqk".charAt(Math.abs(p));
        return p > 0 ? Character.toUpperCase(c) : c;
    }

    private static int pieceFromFenChar(char c) {
        int idx = "pnbrqk".indexOf(Character.toLowerCase(c));
        if (idx < 0) {
            throw new IllegalArgumentException("Bad FEN piece: " + c);
        }
        int type = idx + 1;
        return Character.isUpperCase(c) ? type : -type;
    }

    private static int pieceTypeFromLetter(char c, String rawSan) {
        return switch (c) {
            case 'N' -> KNIGHT;
            case 'B' -> BISHOP;
            case 'R' -> ROOK;
            case 'Q' -> QUEEN;
            case 'K' -> KING;
            default -> throw new IllegalMoveException("Unknown piece letter '" + c + "'", rawSan);
        };
    }
}
