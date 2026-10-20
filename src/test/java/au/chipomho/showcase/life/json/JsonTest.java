package au.chipomho.showcase.life.json;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class JsonTest {

    public enum Colour { RED }

    public record Point(int x, long y) {
    }

    public record Everything(String text, boolean flag, Integer count, Colour colour, Point point,
                             List<Point> points, String missing) {
    }

    public record Empty() {
    }

    public record Faulty(String value) {

        @Override
        public String value() {
            throw new UnsupportedOperationException("no");
        }
    }

    @Test
    void writesRecordComponentsInDeclarationOrderAndLeavesOutNulls() {
        var value = new Everything("hi", true, 3, Colour.RED, new Point(1, 2L),
                List.of(new Point(3, 4L), new Point(5, 6L)), null);

        assertThat(Json.write(value)).isEqualTo("""
                {"text":"hi","flag":true,"count":3,"colour":"RED","point":{"x":1,"y":2},\
                "points":[{"x":3,"y":4},{"x":5,"y":6}]}""");
    }

    @Test
    void writesEmptyThingsAndNullsOutsideRecords() {
        assertThat(Json.write(new Empty())).isEqualTo("{}");
        assertThat(Json.write(List.of())).isEqualTo("[]");
        assertThat(Json.write(null)).isEqualTo("null");
        assertThat(Json.write(Arrays.asList("a", null))).isEqualTo("[\"a\",null]");
        assertThat(Json.write(Set.of(false))).isEqualTo("[false]");
    }

    @Test
    void escapesQuotesBackslashesAndControlCharacters() {
        assertThat(Json.write("say \"hi\"")).isEqualTo("\"say \\\"hi\\\"\"");
        assertThat(Json.write("a\\b")).isEqualTo("\"a\\\\b\"");
        assertThat(Json.write("a\nb\rc\td\u0001e\u001f")).isEqualTo("\"a\\nb\\rc\\td\\u0001e\\u001f\"");
    }

    @Test
    void leavesEverythingElseAlone() {
        assertThat(Json.write("112.4°C café, ok")).isEqualTo("\"112.4°C café, ok\"");
    }

    @Test
    void refusesTypesItHasNoFormFor() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Json.write(1.5))
                .withMessage("No JSON form for java.lang.Double");
    }

    @Test
    void reportsAComponentThatCannotBeRead() {
        assertThatIllegalStateException()
                .isThrownBy(() -> Json.write(new Faulty("x")))
                .withMessage("Cannot read value of " + Faulty.class.getName());
    }
}
