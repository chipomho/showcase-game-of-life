package au.chipomho.showcase.life.engine;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * One generation of Conway's Game of Life, packed one bit per cell: each row is a {@code long[]},
 * cell x in word {@code x / 64} at bit {@code x % 64}. Bits past the right edge are always zero.
 *
 * <p>A board never changes. {@link #next()} builds a new one, so a generation can be handed to
 * another thread, such as the one streaming it to a browser, without copying or locking. That
 * costs one allocation per step, a row of words per row of cells, which is small next to
 * encoding the board for the wire.
 *
 * <p>The next generation is worked out 64 cells at a time. For each word the eight neighbours
 * are lined up as eight shifted copies of the rows above, beside and below, carrying bits across
 * word boundaries and, on a torus, round the edges. Those eight one-bit inputs are added with
 * full and half adders into a three-bit count per cell; a count of eight wraps to zero, which
 * the rules treat the same way. A cell lives if the count is three, or two and it was alive.
 */
public final class Board {

    private final int width;
    private final int height;
    private final Topology topology;
    private final int words;
    private final long lastWordMask;
    private final long[] emptyRow;
    private final long[][] rows;

    private Board(int width, int height, Topology topology, long[][] rows) {
        this.width = width;
        this.height = height;
        this.topology = topology;
        this.words = (width + 63) >>> 6;
        var bitsInLastWord = width - (words - 1) * 64;
        this.lastWordMask = bitsInLastWord == 64 ? -1L : (1L << bitsInLastWord) - 1;
        this.emptyRow = new long[words];
        this.rows = rows;
    }

    public static Board of(int width, int height, Topology topology, Collection<Cell> live) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("A board needs at least one row and one column, not %d x %d"
                    .formatted(width, height));
        }
        Objects.requireNonNull(topology, "topology");
        var rows = new long[height][(width + 63) >>> 6];
        for (var cell : live) {
            if (cell.x() < 0 || cell.x() >= width || cell.y() < 0 || cell.y() >= height) {
                throw new IllegalArgumentException("%s is outside a %d x %d board".formatted(cell, width, height));
            }
            rows[cell.y()][cell.x() >>> 6] |= 1L << cell.x();
        }
        return new Board(width, height, topology, rows);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Topology topology() {
        return topology;
    }

    public boolean isAlive(int x, int y) {
        Objects.checkIndex(x, width);
        Objects.checkIndex(y, height);
        return (rows[y][x >>> 6] >>> x & 1) == 1;
    }

    public int population() {
        var count = 0;
        for (var row : rows) {
            for (var word : row) {
                count += Long.bitCount(word);
            }
        }
        return count;
    }

    public Board next() {
        var next = new long[height][words];
        for (var y = 0; y < height; y++) {
            var above = row(y - 1);
            var middle = rows[y];
            var below = row(y + 1);
            for (var w = 0; w < words; w++) {
                next[y][w] = step(above, middle, below, w);
            }
            next[y][words - 1] &= lastWordMask;
        }
        return new Board(width, height, topology, next);
    }

    /**
     * The board as bytes, a row at a time, {@code ceil(width / 8)} bytes per row, with cell x
     * of a row in byte {@code x / 8} at bit {@code x % 8}.
     */
    public byte[] toBytes() {
        var bytesPerRow = (width + 7) >>> 3;
        var bytes = new byte[bytesPerRow * height];
        for (var y = 0; y < height; y++) {
            for (var b = 0; b < bytesPerRow; b++) {
                bytes[y * bytesPerRow + b] = (byte) (rows[y][b >>> 3] >>> ((b & 7) * 8));
            }
        }
        return bytes;
    }

    private long step(long[] above, long[] middle, long[] below, int w) {
        long aw = west(above, w), a = above[w], ae = east(above, w);
        long mw = west(middle, w), m = middle[w], me = east(middle, w);
        long bw = west(below, w), b = below[w], be = east(below, w);

        long aSum = aw ^ a ^ ae, aCarry = (aw & a) | (ae & (aw ^ a));
        long mSum = mw ^ me, mCarry = mw & me;
        long bSum = bw ^ b ^ be, bCarry = (bw & b) | (be & (bw ^ b));

        long ones = aSum ^ mSum ^ bSum;
        long onesCarry = (aSum & mSum) | (bSum & (aSum ^ mSum));
        long carries = aCarry ^ mCarry ^ bCarry;
        long carriesCarry = (aCarry & mCarry) | (bCarry & (aCarry ^ mCarry));
        long twos = carries ^ onesCarry;
        long fours = carriesCarry ^ (carries & onesCarry);

        return twos & ~fours & (ones | m);
    }

    private long[] row(int y) {
        return switch (topology) {
            case Topology.Bounded _ -> y < 0 || y >= height ? emptyRow : rows[y];
            case Topology.Toroidal _ -> rows[Math.floorMod(y, height)];
        };
    }

    /**
     * Each cell's western neighbour: the word shifted up a bit, with the bit from the word before
     * moving in, or for the first word, whatever lies west of the left edge.
     */
    private long west(long[] row, int w) {
        var incoming = w > 0 ? row[w - 1] >>> 63 : beyondLeftEdge(row);
        return row[w] << 1 | incoming;
    }

    /**
     * Each cell's eastern neighbour: the word shifted down a bit, with the bit from the word after
     * moving in, or for the last cell of the row, whatever lies east of the right edge.
     */
    private long east(long[] row, int w) {
        return w < words - 1
                ? row[w] >>> 1 | row[w + 1] << 63
                : row[w] >>> 1 | beyondRightEdge(row) << (width - 1 & 63);
    }

    private long beyondLeftEdge(long[] row) {
        return switch (topology) {
            case Topology.Bounded _ -> 0;
            case Topology.Toroidal _ -> row[(width - 1) >>> 6] >>> (width - 1) & 1;
        };
    }

    private long beyondRightEdge(long[] row) {
        return switch (topology) {
            case Topology.Bounded _ -> 0;
            case Topology.Toroidal _ -> row[0] & 1;
        };
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Board board
                && width == board.width
                && height == board.height
                && topology.equals(board.topology)
                && Arrays.deepEquals(rows, board.rows);
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height, topology, Arrays.deepHashCode(rows));
    }

    /**
     * The board drawn with {@code O} for live cells and {@code .} for dead ones, a line per row.
     */
    @Override
    public String toString() {
        var text = new StringBuilder((width + 1) * height);
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                text.append(isAlive(x, y) ? 'O' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }
}
