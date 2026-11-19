package com.openingcoach.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings from application.properties (prefix "openingcoach").
 *
 * @param dataDir      folder holding reference/ and index/ (relative paths resolve against the working directory)
 * @param referencePgn PGN of strong players' games, used to build the index on first start
 * @param minElo       both players must be rated at least this to enter the reference book
 * @param maxPly       opening depth analysed, in plies (half-moves)
 * @param bloomFpp     target false-positive rate of the reference Bloom filter
 * @param maxGames     upper limit on games fetched per analysis
 * @param userAgent    sent to Lichess / Chess.com as the client name
 */
@ConfigurationProperties(prefix = "openingcoach")
public record CoachProperties(String dataDir, String referencePgn, int minElo, int maxPly, double bloomFpp,
                              int maxGames, String userAgent) {
}
