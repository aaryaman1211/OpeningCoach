package com.openingcoach.core.chess;

/** Thrown when a SAN move cannot be parsed or is not legal in the current position. */
public class IllegalMoveException extends RuntimeException {

    private final String san;

    public IllegalMoveException(String reason, String san) {
        super(reason + ": '" + san + "'");
        this.san = san;
    }

    public String san() {
        return san;
    }
}
