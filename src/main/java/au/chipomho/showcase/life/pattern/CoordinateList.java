package au.chipomho.showcase.life.pattern;

import au.chipomho.showcase.life.engine.Cell;

import java.util.ArrayList;

/**
 * The original project's format: a JSON-style list of live cells as {@code [x, y]} pairs, for
 * example {@code [[5, 5], [6, 5], [7, 5]]}. The pattern is as wide and tall as its furthest cell.
 */
final class CoordinateList {

    private static final java.util.regex.Pattern WHOLE =
            java.util.regex.Pattern.compile("\\[\\[\\d{1,6},\\d{1,6}](,\\[\\d{1,6},\\d{1,6}])*]");
    private static final java.util.regex.Pattern PAIR = java.util.regex.Pattern.compile("\\[(\\d+),(\\d+)]");

    private CoordinateList() {
    }

    static Pattern parse(String text) {
        var compact = text.replaceAll("\\s+", "");
        if (!WHOLE.matcher(compact).matches()) {
            throw new PatternException("A coordinate list looks like [[x, y], [x, y], ...] with at least one cell");
        }
        var cells = new ArrayList<Cell>();
        var pairs = PAIR.matcher(compact);
        var width = 0;
        var height = 0;
        while (pairs.find()) {
            var cell = new Cell(Integer.parseInt(pairs.group(1)), Integer.parseInt(pairs.group(2)));
            cells.add(cell);
            width = Math.max(width, cell.x() + 1);
            height = Math.max(height, cell.y() + 1);
        }
        return new Pattern("Coordinate list", width, height, cells);
    }
}
