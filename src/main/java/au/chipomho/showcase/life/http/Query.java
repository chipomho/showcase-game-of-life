package au.chipomho.showcase.life.http;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Query string parameters, decoded as {@code application/x-www-form-urlencoded} (so {@code +} is
 * a space). When a name is repeated the first value wins; a name without {@code =} has the empty
 * string as its value.
 */
public record Query(Map<String, String> parameters) {

    public Query {
        parameters = Map.copyOf(parameters);
    }

    public static Query parse(String rawQuery) {
        if (rawQuery == null) {
            return new Query(Map.of());
        }
        var parameters = new LinkedHashMap<String, String>();
        for (var pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            var equals = pair.indexOf('=');
            var name = equals < 0 ? pair : pair.substring(0, equals);
            var value = equals < 0 ? "" : pair.substring(equals + 1);
            parameters.putIfAbsent(decode(name), decode(value));
        }
        return new Query(parameters);
    }

    public Optional<String> get(String name) {
        return Optional.ofNullable(parameters.get(name));
    }

    /**
     * A whole number from min to max, or the default when the parameter is absent.
     */
    public int integer(String name, int defaultValue, int min, int max) {
        return optionalInteger(name, min, max).orElse(defaultValue);
    }

    public OptionalInt optionalInteger(String name, int min, int max) {
        var text = parameters.get(name);
        if (text == null) {
            return OptionalInt.empty();
        }
        try {
            var value = Integer.parseInt(text);
            if (value >= min && value <= max) {
                return OptionalInt.of(value);
            }
        } catch (NumberFormatException e) {
            // reported below, the same as a number out of range
        }
        throw ProblemException.badRequest("%s must be a whole number from %d to %d".formatted(name, min, max));
    }

    private static String decode(String text) {
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw ProblemException.badRequest("The query string is not valid URL encoding");
        }
    }
}
