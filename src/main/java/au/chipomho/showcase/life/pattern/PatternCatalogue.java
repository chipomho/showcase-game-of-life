package au.chipomho.showcase.life.pattern;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The patterns bundled with the application, read once when it starts so a broken file fails
 * straight away rather than on the first request for it.
 */
public final class PatternCatalogue {

    private final Map<String, Pattern> patterns = new LinkedHashMap<>();

    private PatternCatalogue() {
    }

    public static PatternCatalogue bundled() throws IOException {
        var catalogue = new PatternCatalogue();
        catalogue.add("glider", "glider.rle");
        catalogue.add("gosper-glider-gun", "gosper-glider-gun.rle");
        catalogue.add("pulsar", "pulsar.rle");
        catalogue.add("r-pentomino", "r-pentomino.rle");
        catalogue.add("chaos", "chaos.json", "Chaos (the original's coordinate list)");
        return catalogue;
    }

    /**
     * In the order they were added.
     */
    public Map<String, Pattern> all() {
        return Collections.unmodifiableMap(patterns);
    }

    public Optional<Pattern> find(String id) {
        return Optional.ofNullable(patterns.get(id));
    }

    private void add(String id, String resource) throws IOException {
        patterns.put(id, Pattern.parse(read(resource)));
    }

    private void add(String id, String resource, String name) throws IOException {
        var pattern = Pattern.parse(read(resource));
        patterns.put(id, new Pattern(name, pattern.width(), pattern.height(), pattern.cells()));
    }

    private static String read(String resource) throws IOException {
        try (InputStream in = PatternCatalogue.class.getResourceAsStream("/patterns/" + resource)) {
            return new String(Objects.requireNonNull(in, resource).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
