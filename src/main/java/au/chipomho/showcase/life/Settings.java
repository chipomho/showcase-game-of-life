package au.chipomho.showcase.life;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuration from environment variables: {@code PORT} (default 8080), {@code ALLOWED_ORIGINS}
 * (comma-separated browser origins allowed to call the API) and {@code API_BASE_URL} (where the page
 * sends API requests; empty for its own origin).
 */
public record Settings(int port, Set<String> allowedOrigins, String apiBase) {

    static final String DEFAULT_ORIGIN = "https://game-of-life.showcases.chipomho.local";

    public Settings {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("PORT must be from 0 to 65535, got " + port);
        }
        allowedOrigins = Set.copyOf(allowedOrigins);
    }

    public static Settings fromEnvironment(Map<String, String> environment) {
        var origins = Arrays.stream(environment.getOrDefault("ALLOWED_ORIGINS", DEFAULT_ORIGIN).split(","))
                .map(String::strip)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toSet());
        return new Settings(parsePort(environment.getOrDefault("PORT", "8080")), origins,
                environment.getOrDefault("API_BASE_URL", "").strip());
    }

    private static int parsePort(String text) {
        try {
            return Integer.parseInt(text.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("PORT must be a number, got '%s'".formatted(text), e);
        }
    }
}
