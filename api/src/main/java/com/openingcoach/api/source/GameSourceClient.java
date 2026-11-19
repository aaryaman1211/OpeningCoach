package com.openingcoach.api.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openingcoach.api.CoachProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads a player's public games as PGN from Lichess or Chess.com. Both APIs are public and
 * need no login; only finished public games are available, so the app cannot be used during a game.
 */
@Component
public class GameSourceClient {

    private static final Pattern GAME_SPLIT = Pattern.compile("(?=\\[Event )");
    private static final Pattern WHITE_TAG = Pattern.compile("\\[White \"([^\"]*)\"\\]");
    private static final Pattern BLACK_TAG = Pattern.compile("\\[Black \"([^\"]*)\"\\]");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper json = new ObjectMapper();
    private final String userAgent;

    public GameSourceClient(CoachProperties props) {
        this.userAgent = props.userAgent();
    }

    /** Lichess: GET /api/games/user/{name} streams PGN; filtered server-side by colour and speed. */
    public String lichess(String username, boolean white, int max, boolean includeBullet) {
        String perf = includeBullet ? "bullet,blitz,rapid,classical,correspondence" : "blitz,rapid,classical,correspondence";
        String url = "https://lichess.org/api/games/user/" + enc(username)
                + "?max=" + max + "&color=" + (white ? "white" : "black") + "&perfType=" + perf
                + "&clocks=false&evals=false&opening=false&literate=false";
        HttpResponse<String> res = get(url, "application/x-chess-pgn", Duration.ofSeconds(90));
        switch (res.statusCode()) {
            case 200 -> {
                return res.body();
            }
            case 404 -> throw new SourceException(HttpStatus.NOT_FOUND, "Lichess user '" + username + "' not found");
            case 429 -> throw new SourceException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Lichess is rate-limiting requests; wait a minute and try again");
            default -> throw new SourceException(HttpStatus.BAD_GATEWAY, "Lichess returned HTTP " + res.statusCode());
        }
    }

    /**
     * Chess.com: list monthly archives, then download each month's PGN from newest to oldest
     * until enough games with the requested colour are collected.
     */
    public String chessCom(String username, boolean white, int max, boolean includeBullet) {
        String name = username.toLowerCase();
        HttpResponse<String> res = get("https://api.chess.com/pub/player/" + enc(name) + "/games/archives",
                "application/json", Duration.ofSeconds(20));
        if (res.statusCode() == 404) {
            throw new SourceException(HttpStatus.NOT_FOUND, "Chess.com user '" + username + "' not found");
        }
        if (res.statusCode() != 200) {
            throw new SourceException(HttpStatus.BAD_GATEWAY, "Chess.com returned HTTP " + res.statusCode());
        }
        List<String> archives = new ArrayList<>();
        try {
            JsonNode node = json.readTree(res.body()).path("archives");
            node.forEach(n -> archives.add(n.asText()));
        } catch (IOException e) {
            throw new SourceException(HttpStatus.BAD_GATEWAY, "Unexpected Chess.com response");
        }
        StringBuilder out = new StringBuilder();
        int kept = 0;
        for (int i = archives.size() - 1; i >= 0 && kept < max; i--) {
            HttpResponse<String> month = get(archives.get(i) + "/pgn", "application/x-chess-pgn", Duration.ofSeconds(60));
            if (month.statusCode() != 200) {
                continue;
            }
            for (String game : GAME_SPLIT.split(month.body())) {
                if (kept >= max || game.isBlank()) {
                    continue;
                }
                String side = tag(game, white ? WHITE_TAG : BLACK_TAG);
                if (!side.equalsIgnoreCase(username)) {
                    continue;
                }
                if (!includeBullet && isBullet(game)) {
                    continue;
                }
                out.append(game.strip()).append("\n\n");
                kept++;
            }
        }
        return out.toString();
    }

    private static boolean isBullet(String game) {
        Matcher m = Pattern.compile("\\[TimeControl \"(\\d+)").matcher(game);
        return m.find() && Integer.parseInt(m.group(1)) < 180;
    }

    private static String tag(String game, Pattern p) {
        Matcher m = p.matcher(game);
        return m.find() ? m.group(1) : "";
    }

    private HttpResponse<String> get(String url, String accept, Duration timeout) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", userAgent)
                .header("Accept", accept)
                .timeout(timeout)
                .GET()
                .build();
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new SourceException(HttpStatus.BAD_GATEWAY, "Could not reach " + URI.create(url).getHost()
                    + " - check the internet connection, or upload a PGN file instead");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SourceException(HttpStatus.SERVICE_UNAVAILABLE, "Request interrupted");
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
