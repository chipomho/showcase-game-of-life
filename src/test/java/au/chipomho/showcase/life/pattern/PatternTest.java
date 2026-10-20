package au.chipomho.showcase.life.pattern;

import au.chipomho.showcase.life.engine.Cell;
import au.chipomho.showcase.life.engine.Topology;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

class PatternTest {

    private static final List<Cell> GLIDER =
            List.of(new Cell(1, 0), new Cell(2, 1), new Cell(0, 2), new Cell(1, 2), new Cell(2, 2));

    @Test
    void readsRle() {
        var pattern = Pattern.parse("""
                #N Glider
                #C A comment that means nothing to the parser
                x = 3, y = 3, rule = B3/S23
                bob$2bo$3o!""");

        assertThat(pattern).isEqualTo(new Pattern("Glider", 3, 3, GLIDER));
    }

    @Test
    void readsABodySplitOverLinesWithRowSkipsAndIgnoresAnythingAfterTheEnd() {
        var pattern = Pattern.parse("""

                x=4,y=5
                o2$
                3bo
                $$o!  trailing notes are ignored""");

        assertThat(pattern).isEqualTo(new Pattern("Unnamed pattern", 4, 5,
                List.of(new Cell(0, 0), new Cell(3, 2), new Cell(0, 4))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"B3/S23", "b3/s23", "23/3"})
    void acceptsConwaysRuleInEitherNotation(String rule) {
        assertThat(Pattern.parse("x = 1, y = 1, rule = " + rule + "\no!").cells()).containsExactly(new Cell(0, 0));
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            #N Only a comment                          | An RLE pattern needs a header line like 'x = 3, y = 3'
            ``                                         | An RLE pattern needs a header line like 'x = 3, y = 3'
            x = 3\\nbo!                                | Expected a header line like 'x = 3, y = 3', not 'x = 3'
            x = 3, y = 3, rule = B36/S23\\nbo!         | Only Conway's rule, B3/S23, is supported, not B36/S23
            x = 3, y = 3\\nbox!                        | 'x' has no meaning in an RLE pattern
            x = 3, y = 3\\nbo$2bo                      | An RLE pattern ends with '!'
            x = 3, y = 3\\n2b2o!                       | The pattern draws outside the 3 x 3 its header declares
            x = 3, y = 3\\n3$o!                        | The pattern draws outside the 3 x 3 its header declares
            x = 3, y = 3\\n9999999999b9999999999bo!    | The pattern draws outside the 3 x 3 its header declares
            """)
    void explainsWhatIsWrongWithBadRle(String text, String message) {
        assertThatExceptionOfType(PatternException.class)
                .isThrownBy(() -> Pattern.parse(text.replace("\\n", "\n")))
                .withMessage(message);
    }

    @Test
    void readsTheOriginalCoordinateList() {
        var pattern = Pattern.parse("""
                  [[1, 0], [2,1],
                   [0,2],[1,2], [2, 2]]
                """);

        assertThat(pattern).isEqualTo(new Pattern("Coordinate list", 3, 3, GLIDER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[[1,2],]", "[[1,-2]]", "[[1,2,3]]", "[[a,b]]", "[[1,2]] and more"})
    void refusesAMalformedCoordinateList(String text) {
        assertThatExceptionOfType(PatternException.class)
                .isThrownBy(() -> Pattern.parse(text))
                .withMessage("A coordinate list looks like [[x, y], [x, y], ...] with at least one cell");
    }

    @Test
    void sitsInTheMiddleOfABoard() {
        var board = new Pattern("Glider", 3, 3, GLIDER).centredOn(8, 7, Topology.TOROIDAL);

        assertThat(board.toString()).isEqualTo("""
                ........
                ........
                ...O....
                ....O...
                ..OOO...
                ........
                ........
                """);
        assertThat(board.topology()).isEqualTo(Topology.TOROIDAL);
    }

    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"2, 10", "10, 2"})
    void refusesABoardTooSmallForIt(int width, int height) {
        assertThatExceptionOfType(PatternException.class)
                .isThrownBy(() -> new Pattern("Glider", 3, 3, GLIDER).centredOn(width, height, Topology.BOUNDED))
                .withMessage("Glider is 3 x 3 and does not fit on a %d x %d board", width, height);
    }

    @Test
    void bundlesTheClassicsAndTheOriginalsData() throws IOException {
        var catalogue = PatternCatalogue.bundled();

        assertThat(catalogue.all().keySet())
                .containsExactly("glider", "gosper-glider-gun", "pulsar", "r-pentomino", "chaos");
        assertThat(catalogue.all().values())
                .extracting(Pattern::name, Pattern::width, Pattern::height, pattern -> pattern.cells().size())
                .containsExactly(
                        tuple("Glider", 3, 3, 5),
                        tuple("Gosper glider gun", 36, 9, 36),
                        tuple("Pulsar", 13, 13, 48),
                        tuple("R-pentomino", 3, 3, 5),
                        tuple("Chaos (the original's coordinate list)", 99, 99, 291));
        assertThat(catalogue.find("glider")).map(Pattern::cells).contains(GLIDER);
        assertThat(catalogue.find("unicorn")).isEmpty();
    }
}
