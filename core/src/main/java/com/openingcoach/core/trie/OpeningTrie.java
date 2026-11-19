package com.openingcoach.core.trie;

import com.openingcoach.core.Stats;
import com.openingcoach.core.pgn.GameResult;
import com.openingcoach.core.pgn.ReplayedGame;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Trie of opening move sequences (Unit 4: tries).
 *
 * <p>The alphabet is the set of SAN moves; each root-to-node path is one opening line, and each
 * node stores how many games went through that line and how they ended. Inserting a game of
 * L plies costs O(L) expected time (one hash-map step per ply); looking up a line of length
 * L costs O(L) regardless of how many games are stored. A linear scan over all games — the
 * baseline in the benchmarks — costs O(G * L).
 */
public final class OpeningTrie {

    /** One node = one opening line (the moves on the path from the root). */
    public static final class Node {
        private final String san;
        private final int ply;
        private final Node parent;
        private final long positionKey;
        private final Map<String, Node> children = new HashMap<>();
        private int whiteWins, draws, blackWins;

        private Node(String san, int ply, Node parent, long positionKey) {
            this.san = san;
            this.ply = ply;
            this.parent = parent;
            this.positionKey = positionKey;
        }

        /** The move that leads to this node; null for the root. */
        public String san() {
            return san;
        }

        /** Depth in plies (root = 0). */
        public int ply() {
            return ply;
        }

        public Node parent() {
            return parent;
        }

        /** Zobrist key of the position reached after this node's move. */
        public long positionKey() {
            return positionKey;
        }

        public Stats stats() {
            return new Stats(whiteWins, draws, blackWins);
        }

        public int games() {
            return whiteWins + draws + blackWins;
        }

        public Node child(String san) {
            return children.get(san);
        }

        public Collection<Node> children() {
            return Collections.unmodifiableCollection(children.values());
        }

        /** Moves from the root to this node. */
        public List<String> path() {
            Deque<String> moves = new ArrayDeque<>();
            for (Node n = this; n.parent != null; n = n.parent) {
                moves.addFirst(n.san);
            }
            return new ArrayList<>(moves);
        }

        /** True if White played the move into this node (odd ply). */
        public boolean whiteMoved() {
            return ply % 2 == 1;
        }

        private void record(GameResult r) {
            switch (r) {
                case WHITE_WIN -> whiteWins++;
                case DRAW -> draws++;
                case BLACK_WIN -> blackWins++;
                case UNFINISHED -> throw new IllegalArgumentException("Unfinished games are not inserted");
            }
        }
    }

    private final Node root;
    private final int maxPly;
    private int nodeCount = 1;
    private int gameCount;

    public OpeningTrie(long startKey, int maxPly) {
        if (maxPly <= 0) {
            throw new IllegalArgumentException("maxPly must be positive");
        }
        this.root = new Node(null, 0, null, startKey);
        this.maxPly = maxPly;
    }

    public Node root() {
        return root;
    }

    public int maxPly() {
        return maxPly;
    }

    public int nodeCount() {
        return nodeCount;
    }

    public int gameCount() {
        return gameCount;
    }

    /** Inserts the first {@code maxPly} plies of a replayed game. O(min(L, maxPly)). */
    public void insert(ReplayedGame game) {
        insert(game.sanMoves(), game.keys(), game.result());
    }

    public void insert(List<String> sans, long[] keys, GameResult result) {
        if (result == GameResult.UNFINISHED) {
            throw new IllegalArgumentException("Unfinished games are not inserted");
        }
        if (keys.length < sans.size() + 1) {
            throw new IllegalArgumentException("Need one key per ply plus the start key");
        }
        Node cur = root;
        cur.record(result);
        int plies = Math.min(maxPly, sans.size());
        for (int i = 0; i < plies; i++) {
            String san = sans.get(i);
            Node next = cur.children.get(san);
            if (next == null) {
                next = new Node(san, i + 1, cur, keys[i + 1]);
                cur.children.put(san, next);
                nodeCount++;
            }
            next.record(result);
            cur = next;
        }
        gameCount++;
    }

    /** Follows a move path from the root; returns null if no stored game played that line. O(L). */
    public Node find(List<String> path) {
        Node cur = root;
        for (String san : path) {
            cur = cur.children.get(san);
            if (cur == null) {
                return null;
            }
        }
        return cur;
    }

    /** Visits every node in depth-first order (iterative, so deep tries cannot overflow the stack). */
    public void forEachNode(Consumer<Node> visitor) {
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Node n = stack.pop();
            visitor.accept(n);
            for (Node c : n.children.values()) {
                stack.push(c);
            }
        }
    }

    /**
     * Baseline used in tests and benchmarks: counts results of games whose first moves equal
     * {@code path} by scanning every game. O(G * L).
     */
    public static Stats linearScan(List<ReplayedGame> games, List<String> path) {
        Stats s = Stats.EMPTY;
        outer:
        for (ReplayedGame g : games) {
            if (g.sanMoves().size() < path.size()) {
                continue;
            }
            for (int i = 0; i < path.size(); i++) {
                if (!g.sanMoves().get(i).equals(path.get(i))) {
                    continue outer;
                }
            }
            s = s.plus(g.result());
        }
        return s;
    }
}
