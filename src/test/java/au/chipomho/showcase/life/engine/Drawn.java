package au.chipomho.showcase.life.engine;

import java.util.ArrayList;

/**
 * Boards drawn as text, {@code O} for a live cell and {@code .} for a dead one, the same way
 * {@link Board#toString()} draws them.
 */
final class Drawn {

    private Drawn() {
    }

    static Board board(Topology topology, String drawing) {
        var lines = drawing.strip().lines().map(String::strip).toList();
        var live = new ArrayList<Cell>();
        for (var y = 0; y < lines.size(); y++) {
            for (var x = 0; x < lines.get(y).length(); x++) {
                if (lines.get(y).charAt(x) == 'O') {
                    live.add(new Cell(x, y));
                }
            }
        }
        return Board.of(lines.getFirst().length(), lines.size(), topology, live);
    }

    static String text(String drawing) {
        return drawing.strip().lines().map(String::strip).reduce("", (text, line) -> text + line + "\n");
    }

    static Board after(Board board, int generations) {
        for (var i = 0; i < generations; i++) {
            board = board.next();
        }
        return board;
    }
}
