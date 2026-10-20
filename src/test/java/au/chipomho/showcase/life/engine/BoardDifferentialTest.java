package au.chipomho.showcase.life.engine;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.SplittableRandom;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * The bit-packed engine and {@link NaiveLife} must agree, cell for cell and generation after
 * generation, on random boards of awkward sizes: one cell wide, one short of a word, exactly a
 * word, one past it, several words with a ragged end, and both topologies.
 */
class BoardDifferentialTest {

    private static final int[] WIDTHS = {1, 2, 3, 7, 8, 9, 63, 64, 65, 127, 128, 129, 200};
    private static final int[] HEIGHTS = {1, 2, 3, 17, 40};
    private static final int GENERATIONS = 25;

    static Stream<Arguments> randomBoards() {
        var cases = new ArrayList<Arguments>();
        var seed = 0L;
        for (var width : WIDTHS) {
            for (var height : HEIGHTS) {
                for (var topology : new Topology[] {Topology.BOUNDED, Topology.TOROIDAL}) {
                    cases.add(arguments(width, height, topology, seed++));
                }
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0} x {1} {2}, seed {3}")
    @MethodSource
    void randomBoards(int width, int height, Topology topology, long seed) {
        var random = new SplittableRandom(seed);
        var density = 0.15 + random.nextDouble() * 0.45;
        var live = new ArrayList<Cell>();
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                if (random.nextDouble() < density) {
                    live.add(new Cell(x, y));
                }
            }
        }
        var board = Board.of(width, height, topology, live);
        var expected = NaiveLife.cellsOf(board);

        for (var generation = 1; generation <= GENERATIONS; generation++) {
            board = board.next();
            expected = NaiveLife.next(expected, topology == Topology.TOROIDAL);

            assertThat(NaiveLife.cellsOf(board)).as("generation %d", generation).isDeepEqualTo(expected);
        }
    }
}
