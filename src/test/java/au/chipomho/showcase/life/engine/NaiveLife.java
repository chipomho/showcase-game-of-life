package au.chipomho.showcase.life.engine;

/**
 * The rules written as plainly as possible, to check the bit-packed engine against: a grid of
 * booleans and a loop over each cell's eight neighbours. On a torus a neighbour's coordinates
 * wrap, so on a board one or two cells across a cell can be its own neighbour, more than once,
 * exactly as the wrapped shifts in {@link Board} count it.
 */
final class NaiveLife {

    private NaiveLife() {
    }

    static boolean[][] next(boolean[][] cells, boolean toroidal) {
        var height = cells.length;
        var width = cells[0].length;
        var next = new boolean[height][width];
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var neighbours = 0;
                for (var dy = -1; dy <= 1; dy++) {
                    for (var dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) {
                            continue;
                        }
                        var nx = x + dx;
                        var ny = y + dy;
                        if (toroidal) {
                            nx = Math.floorMod(nx, width);
                            ny = Math.floorMod(ny, height);
                        } else if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                            continue;
                        }
                        if (cells[ny][nx]) {
                            neighbours++;
                        }
                    }
                }
                next[y][x] = neighbours == 3 || neighbours == 2 && cells[y][x];
            }
        }
        return next;
    }

    static boolean[][] cellsOf(Board board) {
        var cells = new boolean[board.height()][board.width()];
        for (var y = 0; y < board.height(); y++) {
            for (var x = 0; x < board.width(); x++) {
                cells[y][x] = board.isAlive(x, y);
            }
        }
        return cells;
    }
}
