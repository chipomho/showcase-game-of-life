package au.chipomho.showcase.life;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LifeApplicationTest {

    /**
     * The build sets PORT=0 for tests, so main() binds a free port and logs which one. The server
     * then runs until the test JVM exits, when the shutdown hook main() registered stops it.
     */
    @Test
    void mainStartsTheServerFromTheEnvironment() throws IOException {
        String port;
        try (var log = LogCapture.of(LifeApplication.class)) {
            LifeApplication.main(new String[0]);
            port = (String) log.records().getFirst().getParameters()[0];
        }

        try (var client = new TestClient(Integer.parseInt(port))) {
            assertThat(client.get("/health").statusCode()).isEqualTo(200);
            assertThat(client.get("/config.json").body()).isEqualTo("{\"apiBase\":\"\"}");
        }
    }

    @Test
    void cutsOffOpenStreamsWhenItStops() throws IOException {
        var application = LifeApplication.start(new Settings(0, Set.of(), ""), Clock.systemUTC());
        try (var client = new TestClient(application.port())) {
            var created = client.send("POST", "/v1/simulations?pattern=glider", "").body();
            var id = created.substring(created.indexOf(":\"") + 2, created.indexOf("\","));
            try (var events = client.events("/v1/simulations/" + id + "/events?speed=1")) {
                assertThat(events.next().name()).isEqualTo("generation");

                application.close();

                assertThat(nextOrFailure(events)).isNull();
            }
        }
    }

    @Test
    void readsItsSettingsFromTheEnvironment() {
        assertThat(Settings.fromEnvironment(Map.of())).isEqualTo(
                new Settings(8080, Set.of("https://game-of-life.showcases.chipomho.local"), ""));
        assertThat(Settings.fromEnvironment(Map.of("PORT", " 9090 ",
                "ALLOWED_ORIGINS", "https://a.example, ,https://b.example ",
                "API_BASE_URL", " https://api.example ")))
                .isEqualTo(new Settings(9090, Set.of("https://a.example", "https://b.example"), "https://api.example"));
    }

    @Test
    void refusesAPortThatIsNotANumber() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Settings.fromEnvironment(Map.of("PORT", "http")))
                .withMessage("PORT must be a number, got 'http'");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 65536})
    void refusesAPortOutOfRange(int port) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Settings(port, Set.of(), ""))
                .withMessage("PORT must be from 0 to 65535, got " + port);
    }

    /**
     * A stream cut off mid-response may end cleanly or with a reset, depending on timing.
     */
    private static TestClient.Event nextOrFailure(TestClient.Events events) {
        try {
            return events.next();
        } catch (UncheckedIOException e) {
            return null;
        }
    }
}
