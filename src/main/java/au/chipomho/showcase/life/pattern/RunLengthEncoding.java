package au.chipomho.showcase.life.pattern;

import au.chipomho.showcase.life.engine.Cell;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The RLE format most Life software reads and writes:
 *
 * <pre>
 * #N Glider
 * x = 3, y = 3, rule = B3/S23
 * bob$2bo$3o!
 * </pre>
 *
 * Lines starting with {@code #} come first; {@code #N} names the pattern. The header gives the
 * size and optionally the rule, which has to be Conway's. In the body {@code b} is a dead cell,
 * {@code o} a live one and {@code $} the end of a row, each optionally preceded by a repeat count,
 * and {@code !} ends the pattern. Line breaks in the body mean nothing.
 */
final class RunLengthEncoding {

    private static final java.util.regex.Pattern HEADER = java.util.regex.Pattern.compile(
            "x\\s*=\\s*(\\d{1,6})\\s*,\\s*y\\s*=\\s*(\\d{1,6})(?:\\s*,\\s*rule\\s*=\\s*(\\S+))?");
    private static final List<String> CONWAY = List.of("B3/S23", "23/3");

    private RunLengthEncoding() {
    }

    static Pattern parse(String text) {
        var name = "Unnamed pattern";
        var lines = text.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        var next = 0;
        while (next < lines.size() && lines.get(next).startsWith("#")) {
            if (lines.get(next).startsWith("#N")) {
                name = lines.get(next).substring(2).strip();
            }
            next++;
        }
        if (next == lines.size()) {
            throw new PatternException("An RLE pattern needs a header line like 'x = 3, y = 3'");
        }
        var header = HEADER.matcher(lines.get(next));
        if (!header.matches()) {
            throw new PatternException("Expected a header line like 'x = 3, y = 3', not '%s'".formatted(lines.get(next)));
        }
        var rule = header.group(3);
        if (rule != null && !CONWAY.contains(rule.toUpperCase(Locale.ROOT))) {
            throw new PatternException("Only Conway's rule, B3/S23, is supported, not " + rule);
        }
        var width = Integer.parseInt(header.group(1));
        var height = Integer.parseInt(header.group(2));
        return new Pattern(name, width, height, decode(String.join("", lines.subList(next + 1, lines.size())), width, height));
    }

    /**
     * Stops at the first live cell outside the declared size, so a huge run count can't make it
     * build a huge list first. Positions are clamped just past the edge, so they can't overflow.
     */
    private static List<Cell> decode(String body, int width, int height) {
        var cells = new ArrayList<Cell>();
        var x = 0;
        var y = 0;
        var count = 0;
        for (var i = 0; i < body.length(); i++) {
            var c = body.charAt(i);
            if (c >= '0' && c <= '9') {
                count = Math.min(count * 10 + (c - '0'), 10_000_000);
                continue;
            }
            var run = Math.max(count, 1);
            count = 0;
            switch (c) {
                case 'b' -> x = Math.min(x + run, width + 1);
                case 'o' -> {
                    if (x + run > width || y >= height) {
                        throw new PatternException("The pattern draws outside the %d x %d its header declares"
                                .formatted(width, height));
                    }
                    for (var k = 0; k < run; k++) {
                        cells.add(new Cell(x + k, y));
                    }
                    x += run;
                }
                case '$' -> {
                    y = Math.min(y + run, height + 1);
                    x = 0;
                }
                case '!' -> {
                    return cells;
                }
                default -> throw new PatternException("'%s' has no meaning in an RLE pattern".formatted(c));
            }
        }
        throw new PatternException("An RLE pattern ends with '!'");
    }
}
