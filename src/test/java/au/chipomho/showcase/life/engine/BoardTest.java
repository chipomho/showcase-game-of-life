package au.chipomho.showcase.life.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BoardTest {

    @Test
    void neverChangesWhenTheNextGenerationIsWorkedOut() {
        var blinker = Drawn.board(Topology.BOUNDED, """
                ...
                OOO
                ...""");
        var before = blinker.toString();

        var next = blinker.next();

        assertThat(blinker.toString()).isEqualTo(before);
        assertThat(next).isNotSameAs(blinker).isNotEqualTo(blinker);
    }

    @Test
    void describesItself() {
        var board = Board.of(70, 3, Topology.TOROIDAL, List.of(new Cell(0, 0), new Cell(69, 2), new Cell(64, 1)));

        assertThat(board.width()).isEqualTo(70);
        assertThat(board.height()).isEqualTo(3);
        assertThat(board.topology()).isEqualTo(Topology.TOROIDAL);
        assertThat(board.population()).isEqualTo(3);
        assertThat(board.isAlive(64, 1)).isTrue();
        assertThat(board.isAlive(63, 1)).isFalse();
    }

    @Test
    void packsEightCellsToAByteARowAtATime() {
        var board = Board.of(10, 2, Topology.BOUNDED, List.of(new Cell(0, 0), new Cell(7, 0), new Cell(9, 0), new Cell(8, 1)));

        assertThat(board.toBytes()).containsExactly(0b1000_0001, 0b10, 0, 1);
    }

    @Test
    void equalBoardsHaveTheSameSizeEdgesAndCells() {
        var cells = List.of(new Cell(1, 1));
        var board = Board.of(3, 3, Topology.BOUNDED, cells);

        assertThat(board).isEqualTo(Board.of(3, 3, Topology.BOUNDED, cells))
                .hasSameHashCodeAs(Board.of(3, 3, Topology.BOUNDED, cells))
                .isNotEqualTo(Board.of(3, 3, Topology.TOROIDAL, cells))
                .isNotEqualTo(Board.of(4, 3, Topology.BOUNDED, cells))
                .isNotEqualTo(Board.of(3, 4, Topology.BOUNDED, cells))
                .isNotEqualTo(Board.of(3, 3, Topology.BOUNDED, List.of()))
                .isNotEqualTo("a board");
    }

    @Test
    void drawsItselfAsText() {
        assertThat(Board.of(3, 2, Topology.BOUNDED, List.of(new Cell(1, 0), new Cell(2, 1))))
                .hasToString(".O.\n..O\n");
    }

    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"0, 5", "5, 0"})
    void needsAtLeastOneCell(int width, int height) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Board.of(width, height, Topology.BOUNDED, List.of()))
                .withMessage("A board needs at least one row and one column, not %d x %d", width, height);
    }

    @ParameterizedTest(name = "{0},{1}")
    @CsvSource({"-1, 0", "4, 0", "0, -1", "0, 3"})
    void refusesCellsOffTheBoard(int x, int y) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Board.of(4, 3, Topology.BOUNDED, List.of(new Cell(x, y))))
                .withMessage("Cell[x=%d, y=%d] is outside a 4 x 3 board", x, y);
    }

    @Test
    void refusesToLookOffTheBoard() {
        var board = Board.of(4, 3, Topology.BOUNDED, List.of());

        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> board.isAlive(4, 0));
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> board.isAlive(0, 3));
    }

    @Test
    void namesItsTopologies() {
        assertThat(Topology.named("BOUNDED")).contains(Topology.BOUNDED);
        assertThat(Topology.named("TOROIDAL")).contains(Topology.TOROIDAL);
        assertThat(Topology.named("toroidal")).isEmpty();
    }
}
