package au.chipomho.showcase.life.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnownPatternsTest {

    @ParameterizedTest
    @ValueSource(strings = {"BOUNDED", "TOROIDAL"})
    void aBlinkerFlipsBetweenTwoPhases(String topologyName) {
        var topology = Topology.named(topologyName).orElseThrow();
        var horizontal = Drawn.board(topology, """
                .....
                .....
                .OOO.
                .....
                .....""");

        assertThat(horizontal.next().toString()).isEqualTo(Drawn.text("""
                .....
                ..O..
                ..O..
                ..O..
                ....."""));
        assertThat(horizontal.next().next()).isEqualTo(horizontal);
    }

    @Test
    void aBlockNeverChanges() {
        var block = Drawn.board(Topology.BOUNDED, """
                ....
                .OO.
                .OO.
                ....""");

        assertThat(Drawn.after(block, 10)).isEqualTo(block);
    }

    @Test
    void aGliderMovesOneCellDiagonallyEveryFourGenerations() {
        var glider = Drawn.board(Topology.BOUNDED, """
                .O.....
                ..O....
                OOO....
                .......
                .......
                .......""");

        assertThat(Drawn.after(glider, 4).toString()).isEqualTo(Drawn.text("""
                .......
                ..O....
                ...O...
                .OOO...
                .......
                ......."""));
        assertThat(Drawn.after(glider, 8).toString()).isEqualTo(Drawn.text("""
                .......
                .......
                ...O...
                ....O..
                ..OOO..
                ......."""));
    }

    @Test
    void aGliderDiesAgainstAHardEdge() {
        var glider = Drawn.board(Topology.BOUNDED, """
                .O..
                ..O.
                OOO.
                ....""");

        assertThat(Drawn.after(glider, 12).population()).isLessThan(5);
    }

    /**
     * A glider crosses a 70 x 70 torus, a width with a ragged last word, in 280 generations and
     * arrives back where it started.
     */
    @Test
    void aGliderWrapsRoundATorusAndComesHome() {
        var start = Board.of(70, 70, Topology.TOROIDAL,
                List.of(new Cell(1, 0), new Cell(2, 1), new Cell(0, 2), new Cell(1, 2), new Cell(2, 2)));

        var halfWay = Drawn.after(start, 140);
        var home = Drawn.after(halfWay, 140);

        assertThat(halfWay.population()).isEqualTo(5);
        assertThat(halfWay.isAlive(36, 35)).isTrue();
        assertThat(home).isEqualTo(start);
    }

    @Test
    void aPulsarRepeatsEveryThreeGenerations() {
        var pulsar = Drawn.board(Topology.BOUNDED, """
                .................
                .................
                ....OOO...OOO....
                .................
                ..O....O.O....O..
                ..O....O.O....O..
                ..O....O.O....O..
                ....OOO...OOO....
                .................
                ....OOO...OOO....
                ..O....O.O....O..
                ..O....O.O....O..
                ..O....O.O....O..
                .................
                ....OOO...OOO....
                .................
                .................""");

        assertThat(pulsar.population()).isEqualTo(48);
        assertThat(pulsar.next()).isNotEqualTo(pulsar);
        assertThat(Drawn.after(pulsar, 3)).isEqualTo(pulsar);
    }

    /**
     * After 30 generations the Gosper gun is back as it started, plus the glider it has fired.
     */
    @Test
    void theGosperGunFiresAGliderEveryThirtyGenerations() {
        var rows = List.of(
                "........................O...........",
                "......................O.O...........",
                "............OO......OO............OO",
                "...........O...O....OO............OO",
                "OO........O.....O...OO..............",
                "OO........O...O.OO....O.O...........",
                "..........O.....O.......O...........",
                "...........O...O....................",
                "............OO......................");
        var gun = new ArrayList<Cell>();
        for (var y = 0; y < rows.size(); y++) {
            for (var x = 0; x < rows.get(y).length(); x++) {
                if (rows.get(y).charAt(x) == 'O') {
                    gun.add(new Cell(x + 2, y + 2));
                }
            }
        }
        var start = Board.of(80, 60, Topology.BOUNDED, gun);

        var later = Drawn.after(start, 30);

        assertThat(start.population()).isEqualTo(36);
        assertThat(later.population()).isEqualTo(41);
        assertThat(gun).allMatch(cell -> later.isAlive(cell.x(), cell.y()));
    }
}
