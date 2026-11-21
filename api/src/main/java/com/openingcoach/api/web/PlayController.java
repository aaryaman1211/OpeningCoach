package com.openingcoach.api.web;

import com.openingcoach.api.service.ReferenceService;
import com.openingcoach.core.analysis.OpeningAnalyzer.LegalMove;
import com.openingcoach.core.chess.Board;
import com.openingcoach.core.chess.IllegalMoveException;
import com.openingcoach.core.chess.Squares;
import com.openingcoach.core.play.MoveCoach;
import com.openingcoach.core.play.Opponent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Play a game against one of "the greats". The server is stateless: every request carries the
 * moves so far. The opponent answers from the reference games (by win %) and calculates once
 * they run out; the coach grades the player's moves and gives hints instead of answers.
 */
@RestController
@RequestMapping("/api/play")
public class PlayController {

    private static final int MAX_PLIES = 600;

    public record Position(List<String> moves, String fen, String lastFrom, String lastTo, boolean check,
                           String status, String winner, List<LegalMove> legalMoves) {
    }

    public record MoveOut(String san, String from, String to) {
    }

    /** signature: the great's own game; while the moves follow it, they replay their famous moves. */
    public record OpponentRequest(List<String> moves, String level, List<String> signature, String signatureNote) {
    }

    public record OpponentReply(MoveOut move, String source, String note, Integer games, Double score, Double share,
                                Position position) {
    }

    public record ReviewRequest(List<String> moves, String move) {
    }

    public record ReviewReply(MoveCoach.Review review, Position position) {
    }

    private final ReferenceService reference;

    public PlayController(ReferenceService reference) {
        this.reference = reference;
    }

    @GetMapping("/position")
    public Position position(@RequestParam(defaultValue = "") String moves) {
        List<String> list = moves.isBlank() ? List.of()
                : Arrays.stream(moves.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return describe(replay(list));
    }

    @PostMapping("/opponent")
    public OpponentReply opponent(@RequestBody OpponentRequest req) {
        Game g = replay(req.moves() == null ? List.of() : req.moves());
        Board b = g.board();
        if (!status(g).equals("playing")) {
            throw new IllegalArgumentException("The game is already over");
        }
        Opponent.Level level = parseLevel(req.level());

        // the great's own move, while the game is still following their famous game
        List<String> sig = req.signature();
        int ply = g.sans().size();
        if (sig != null && sig.size() > ply && sig.subList(0, ply).equals(g.sans())) {
            Board.Move m = b.legalMoves().stream().filter(x -> x.san().equals(Squares.cleanSan(sig.get(ply)))).findFirst().orElse(null);
            if (m != null) {
                return reply(g, m, "signature", req.signatureNote() == null ? "Their move from the famous game." : req.signatureNote(),
                        null, null, null);
            }
        }
        Opponent.Choice c = new Opponent(reference.book(), new Random()).choose(b, level);
        return reply(g, c.move(), c.source(), c.note(), c.games(), c.score(), c.share());
    }

    @PostMapping("/review")
    public ReviewReply review(@RequestBody ReviewRequest req) {
        Game g = replay(req.moves() == null ? List.of() : req.moves());
        String clean = Squares.cleanSan(req.move() == null ? "" : req.move());
        Board.Move m = g.board().legalMoves().stream().filter(x -> x.san().equals(clean)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Illegal move: " + req.move()));
        MoveCoach.Review r = new MoveCoach(reference.book()).review(g.board(), m, g.sans().size());
        return new ReviewReply(r, describe(g.plus(m)));
    }

    // ------------------------------------------------------------------ helpers

    private OpponentReply reply(Game g, Board.Move m, String source, String note, Integer games, Double score, Double share) {
        return new OpponentReply(new MoveOut(m.san(), m.fromSquare(), m.toSquare()), source, note, games, score, share,
                describe(g.plus(m)));
    }

    private static Opponent.Level parseLevel(String level) {
        try {
            return Opponent.Level.valueOf((level == null ? "medium" : level).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Level must be easy, medium or hard");
        }
    }

    /** The moves replayed from the start, with every position's key for the repetition rule. */
    record Game(List<String> sans, Board board, Map<Long, Integer> seen, int lastFrom, int lastTo) {
        Game plus(Board.Move m) {
            Board next = board.after(m);
            List<String> s = new ArrayList<>(sans);
            s.add(m.san());
            Map<Long, Integer> counts = new HashMap<>(seen);
            counts.merge(next.key(), 1, Integer::sum);
            return new Game(s, next, counts, m.from(), m.to());
        }
    }

    static Game replay(List<String> moves) {
        if (moves.size() > MAX_PLIES) {
            throw new IllegalArgumentException("At most " + MAX_PLIES + " plies");
        }
        Board b = Board.startPosition();
        Map<Long, Integer> seen = new HashMap<>();
        seen.merge(b.key(), 1, Integer::sum);
        List<String> clean = new ArrayList<>();
        for (int i = 0; i < moves.size(); i++) {
            try {
                clean.add(b.applySan(moves.get(i)));
            } catch (IllegalMoveException e) {
                throw new IllegalArgumentException("Illegal move at ply " + (i + 1) + ": " + e.getMessage());
            }
            seen.merge(b.key(), 1, Integer::sum);
        }
        return new Game(clean, b, seen, b.lastMoveFrom(), b.lastMoveTo());
    }

    static String status(Game g) {
        Board b = g.board();
        if (b.legalMoves().isEmpty()) {
            return b.inCheck() ? "checkmate" : "stalemate";
        }
        if (b.halfmoveClock() >= 100) {
            return "fifty-move rule";
        }
        if (g.seen().getOrDefault(b.key(), 0) >= 3) {
            return "repetition";
        }
        return "playing";
    }

    static Position describe(Game g) {
        Board b = g.board();
        String status = status(g);
        List<LegalMove> legal = new ArrayList<>();
        if (status.equals("playing")) {
            for (Board.Move m : b.legalMoves()) {
                legal.add(new LegalMove(m.san(), m.fromSquare(), m.toSquare(),
                        m.promotion() == Board.EMPTY ? null : String.valueOf("  nbrqk".charAt(m.promotion()))));
            }
        }
        String winner = status.equals("checkmate") ? (b.whiteToMove() ? "black" : "white") : null;
        return new Position(g.sans(), b.toFen(), g.lastFrom() < 0 ? null : Squares.name(g.lastFrom()),
                g.lastTo() < 0 ? null : Squares.name(g.lastTo()), b.inCheck(), status, winner, legal);
    }
}
