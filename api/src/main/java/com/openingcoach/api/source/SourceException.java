package com.openingcoach.api.source;

import org.springframework.http.HttpStatus;

/** A problem fetching games from Lichess or Chess.com, carrying the HTTP status to return. */
public class SourceException extends RuntimeException {

    private final HttpStatus status;

    public SourceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
