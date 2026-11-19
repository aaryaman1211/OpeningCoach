package com.openingcoach.core.pgn;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Streaming PGN reader. Handles header tags, comments in braces, ';' line comments,
 * nested variations in parentheses, NAGs ($n), move numbers and result tokens.
 * Works on multi-GB files because it only holds one game in memory at a time.
 */
public final class PgnParser {

    private static final Pattern TAG = Pattern.compile("^\\[(\\w+)\\s+\"(.*)\"\\]\\s*$");
    private static final Pattern MOVE_NUMBER = Pattern.compile("^\\d+\\.+");

    private PgnParser() {
    }

    public static List<PgnGame> parseAll(String text) {
        List<PgnGame> games = new ArrayList<>();
        stream(new StringReader(text == null ? "" : text), games::add);
        return games;
    }

    /** Reads games one by one and hands each to {@code sink}. Returns the number of games read. */
    public static int stream(Reader reader, Consumer<PgnGame> sink) {
        int count = 0;
        Map<String, String> tags = new LinkedHashMap<>();
        StringBuilder movetext = new StringBuilder();
        boolean blankAfterTags = false;
        try (BufferedReader in = reader instanceof BufferedReader br ? br : new BufferedReader(reader)) {
            String line;
            while ((line = in.readLine()) != null) {
                String t = line.strip();
                if (t.startsWith("[") && t.endsWith("]") && TAG.matcher(t).matches()) {
                    if (movetext.length() > 0 || blankAfterTags) {
                        sink.accept(build(tags, movetext));
                        count++;
                        tags = new LinkedHashMap<>();
                        movetext.setLength(0);
                        blankAfterTags = false;
                    }
                    Matcher m = TAG.matcher(t);
                    if (m.matches()) {
                        tags.put(m.group(1), m.group(2).replace("\\\"", "\""));
                    }
                } else if (t.isEmpty()) {
                    if (movetext.length() > 0) {
                        sink.accept(build(tags, movetext));
                        count++;
                        tags = new LinkedHashMap<>();
                        movetext.setLength(0);
                        blankAfterTags = false;
                    } else if (!tags.isEmpty()) {
                        blankAfterTags = true;
                    }
                } else if (!t.startsWith("%")) { // '%' escape lines are ignored per the PGN spec
                    movetext.append(stripLineComment(t)).append(' ');
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (movetext.length() > 0 || !tags.isEmpty()) {
            sink.accept(build(tags, movetext));
            count++;
        }
        return count;
    }

    private static String stripLineComment(String line) {
        int depth = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth = Math.max(0, depth - 1);
            } else if (c == ';' && depth == 0) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static PgnGame build(Map<String, String> tags, StringBuilder movetext) {
        List<String> moves = new ArrayList<>();
        String resultToken = null;
        for (String token : tokenize(movetext)) {
            String tok = MOVE_NUMBER.matcher(token).replaceFirst("");
            if (tok.isEmpty() || tok.startsWith("$")) {
                continue;
            }
            if (tok.equals("1-0") || tok.equals("0-1") || tok.equals("1/2-1/2") || tok.equals("*")) {
                resultToken = tok;
                continue;
            }
            moves.add(tok);
        }
        Map<String, String> finalTags = new LinkedHashMap<>(tags);
        if (!finalTags.containsKey("Result") && resultToken != null) {
            finalTags.put("Result", resultToken);
        }
        return new PgnGame(finalTags, moves);
    }

    /** Splits movetext into tokens, dropping {comments} and (variations), including nested ones. */
    static List<String> tokenize(CharSequence movetext) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int variationDepth = 0;
        boolean inComment = false;
        for (int i = 0; i < movetext.length(); i++) {
            char c = movetext.charAt(i);
            if (inComment) {
                if (c == '}') {
                    inComment = false;
                }
                continue;
            }
            if (c == '{') {
                inComment = true;
                flush(cur, tokens, variationDepth);
                continue;
            }
            if (c == '(') {
                flush(cur, tokens, variationDepth);
                variationDepth++;
                continue;
            }
            if (c == ')') {
                flush(cur, tokens, variationDepth);
                variationDepth = Math.max(0, variationDepth - 1);
                continue;
            }
            if (Character.isWhitespace(c)) {
                flush(cur, tokens, variationDepth);
            } else {
                cur.append(c);
            }
        }
        flush(cur, tokens, variationDepth);
        return tokens;
    }

    private static void flush(StringBuilder cur, List<String> tokens, int variationDepth) {
        if (cur.length() > 0) {
            if (variationDepth == 0) {
                tokens.add(cur.toString());
            }
            cur.setLength(0);
        }
    }
}
