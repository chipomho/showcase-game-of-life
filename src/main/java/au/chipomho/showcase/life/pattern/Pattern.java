package au.chipomho.showcase.life.pattern;

import au.chipomho.showcase.life.engine.Board;
import au.chipomho.showcase.life.engine.Cell;
import au.chipomho.showcase.life.engine.Topology;

import java.util.List;

/**
 * A starting arrangement of live cells within a width x height box.
 */
public record Pattern(String name, int width, int height, List<Cell> cells) {

    public Pattern {
        cells = List.copyOf(cells);
    }

    /**
     * The pattern in the middle of an otherwise empty board.
     */
    public Board centredOn(int boardWidth, int boardHeight, Topology topology) {
        if (width > boardWidth || height > boardHeight) {
            throw new PatternException("%s is %d x %d and does not fit on a %d x %d board"
                    .formatted(name, width, height, boardWidth, boardHeight));
        }
        var left = (boardWidth - width) / 2;
        var top = (boardHeight - height) / 2;
        return Board.of(boardWidth, boardHeight, topology,
                cells.stream().map(cell -> new Cell(cell.x() + left, cell.y() + top)).toList());
    }

    /**
     * Reads either format: a coordinate list if the text starts with {@code [}, otherwise RLE.
     */
    public static Pattern parse(String text) {
        return text.stripLeading().startsWith("[") ? CoordinateList.parse(text) : RunLengthEncoding.parse(text);
    }
}
