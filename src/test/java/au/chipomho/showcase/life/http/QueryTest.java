package au.chipomho.showcase.life.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class QueryTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "&", "&&"})
    void anAbsentOrEmptyQueryHasNoParameters(String raw) {
        assertThat(Query.parse(raw).parameters()).isEmpty();
    }

    @Test
    void decodesAsFormEncodingKeepsTheFirstOfARepeatAndTreatsANameAloneAsEmpty() {
        assertThat(Query.parse("pattern=r%2Dpentomino&width=80&width=90&step").parameters())
                .isEqualTo(Map.of("pattern", "r-pentomino", "width", "80", "step", ""));
    }

    @Test
    void readsWholeNumbersWithinLimits() {
        var query = Query.parse("width=80&speed=30");

        assertThat(query.integer("width", 120, 1, 1000)).isEqualTo(80);
        assertThat(query.integer("height", 120, 1, 1000)).isEqualTo(120);
        assertThat(query.optionalInteger("speed", 1, 30)).isEqualTo(OptionalInt.of(30));
        assertThat(query.optionalInteger("generations", 1, 10)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"width=0", "width=1001", "width=wide", "width="})
    void refusesAnythingElse(String raw) {
        assertThatExceptionOfType(ProblemException.class)
                .isThrownBy(() -> Query.parse(raw).integer("width", 120, 1, 1000))
                .withMessage("width must be a whole number from 1 to 1000")
                .satisfies(problem -> assertThat(problem.status()).isEqualTo(400));
    }

    @Test
    void refusesBrokenPercentEncoding() {
        assertThatExceptionOfType(ProblemException.class)
                .isThrownBy(() -> Query.parse("width=%zz"))
                .withMessage("The query string is not valid URL encoding");
    }
}
