package com.openingcoach.api.web;

import com.openingcoach.core.chess.Board;
import com.openingcoach.core.chess.IllegalMoveException;
import com.openingcoach.core.chess.Squares;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Replays a move list on the server's board and returns every intermediate position, so the
 * frontend can animate a line ply by ply without any chess logic of its own.
 */
@RestController
@RequestMapping("/api/board")
public class BoardController {

    public record Ply(String san, String from, String to, String fen) {
    }

    public record Replay(String startFen, List<Ply> plies) {
    }

    @GetMapping
    public Replay replay(@RequestParam(defaultValue = "") String moves) {
        List<String> sans = moves.isBlank() ? List.of()
                : Arrays.stream(moves.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (sans.size() > 400) {
            throw new IllegalArgumentException("At most 400 plies");
        }
        Board b = Board.startPosition();
        String start = b.toFen();
        List<Ply> plies = new ArrayList<>();
        for (int i = 0; i < sans.size(); i++) {
            try {
                String clean = b.applySan(sans.get(i));
                plies.add(new Ply(clean, Squares.name(b.lastMoveFrom()), Squares.name(b.lastMoveTo()), b.toFen()));
            } catch (IllegalMoveException e) {
                throw new IllegalArgumentException("Illegal move at ply " + (i + 1) + ": " + e.getMessage());
            }
        }
        return new Replay(start, plies);
    }
}
