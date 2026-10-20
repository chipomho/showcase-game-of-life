package au.chipomho.showcase.life.http;

import au.chipomho.showcase.life.LifeApplication;
import au.chipomho.showcase.life.Settings;
import au.chipomho.showcase.life.TestClient;
import au.chipomho.showcase.life.engine.Board;
import au.chipomho.showcase.life.engine.Topology;
import au.chipomho.showcase.life.pattern.PatternCatalogue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.time.Clock;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole application on a free port. Simulation bodies and events are compared byte for byte
 * with what the engine produces for the same board.
 */
class LifeApiTest {

    private static final String PAGE = "https://page.example";

    private static LifeApplication application;
    private static TestClient client;
    private static PatternCatalogue catalogue;

    @BeforeAll
    static void start() throws IOException {
        application = LifeApplication.start(new Settings(0, Set.of(PAGE), "https://api.example"), Clock.systemUTC());
        client = new TestClient(application.port());
        catalogue = PatternCatalogue.bundled();
    }

    @AfterAll
    static void stop() {
        client.close();
        application.close();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "/, text/html; charset=utf-8, <title>Game of Life</title>",
            "/app.js, text/javascript; charset=utf-8, new EventSource(",
            "/app.css, text/css; charset=utf-8, canvas {"
    })
    void servesThePage(String path, String contentType, String excerpt) {
        var response = client.get(path);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).contains(contentType);
        assertThat(response.body()).contains(excerpt);
    }

    @Test
    void tellsThePageWhereTheApiIs() {
        assertThat(client.get("/config.json").body()).isEqualTo("{\"apiBase\":\"https://api.example\"}");
        assertThat(client.get("/health").body()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    void listsTheBundledPatterns() {
        assertThat(client.get("/v1/patterns").body()).isEqualTo("""
                [{"id":"glider","name":"Glider","width":3,"height":3},\
                {"id":"gosper-glider-gun","name":"Gosper glider gun","width":36,"height":9},\
                {"id":"pulsar","name":"Pulsar","width":13,"height":13},\
                {"id":"r-pentomino","name":"R-pentomino","width":3,"height":3},\
                {"id":"chaos","name":"Chaos (the original's coordinate list)","width":99,"height":99}]""");
    }

    @Test
    void startsASimulationFromABundledPattern() {
        var response = client.send("POST", "/v1/simulations?pattern=glider&width=8&height=7&topology=BOUNDED", "");
        var id = idOf(response.body());
        var expected = glider(8, 7, Topology.BOUNDED);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Location")).contains("/v1/simulations/" + id);
        assertThat(response.body()).isEqualTo("{\"id\":\"%s\",\"width\":8,\"height\":7,\"topology\":\"BOUNDED\",\"generation\":0,"
                .formatted(id) + "\"population\":5,\"cells\":\"%s\"}".formatted(cells(expected)));
    }

    @Test
    void startsOnATorusOfOneHundredAndTwentyByEightyUnlessToldOtherwise() {
        var body = client.send("POST", "/v1/simulations?pattern=pulsar", "").body();

        assertThat(body).contains("\"width\":120,\"height\":80,\"topology\":\"TOROIDAL\",\"generation\":0,\"population\":48");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            x = 3, y = 3, rule = B3/S23\\nbob$2bo$3o! | 5
            [[0, 0], [1, 0], [2, 0]]               | 3
            """)
    void startsASimulationFromAPatternInTheBody(String pattern, int population) {
        var response = client.send("POST", "/v1/simulations?width=10&height=10", pattern.replace("\\n", "\n"),
                "Content-Type", "text/plain");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("\"population\":" + population);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            ?pattern=glider&width=0                  |                     | width must be a whole number from 1 to 1000
            ?pattern=glider&height=1001              |                     | height must be a whole number from 1 to 1000
            ?pattern=glider&topology=SPHERE          |                     | topology is BOUNDED or TOROIDAL, not SPHERE
            ?pattern=unicorn                         |                     | 'unicorn' is not a bundled pattern; GET /v1/patterns lists them
            ?width=10                                |                     | Name a bundled pattern, or send one in RLE or as a coordinate list
            ?width=10                                | x = 3                | Expected a header line like 'x = 3, y = 3', not 'x = 3'
            ?pattern=gosper-glider-gun&width=20      |                     | Gosper glider gun is 36 x 9 and does not fit on a 20 x 80 board
            """)
    void explainsWhyItCannotStartASimulation(String query, String body, String detail) {
        var response = client.send("POST", "/v1/simulations" + query, body == null ? "" : body);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).isEqualTo(problem("Bad Request", 400, detail));
    }

    @Test
    void showsAndRemovesASimulation() {
        var id = idOf(client.send("POST", "/v1/simulations?pattern=glider&width=8&height=7", "").body());

        assertThat(client.get("/v1/simulations/" + id).body()).contains("\"id\":\"" + id + "\"", "\"generation\":0");
        assertThat(client.send("DELETE", "/v1/simulations/" + id, "").statusCode()).isEqualTo(204);

        var gone = client.get("/v1/simulations/" + id);
        assertThat(gone.statusCode()).isEqualTo(404);
        assertThat(gone.body()).isEqualTo(problem("Not Found", 404,
                "There is no simulation %s; it may have been idle long enough to be removed".formatted(id)));
        assertThat(client.send("DELETE", "/v1/simulations/" + id, "").statusCode()).isEqualTo(404);
    }

    @Test
    void streamsGenerationsAndCarriesOnWhereTheLastStreamStopped() throws IOException {
        var id = idOf(client.send("POST", "/v1/simulations?pattern=glider&width=8&height=7&topology=TOROIDAL", "").body());
        var board = glider(8, 7, Topology.TOROIDAL);

        try (var events = client.events("/v1/simulations/%s/events?speed=30&generations=3".formatted(id))) {
            for (var generation = 0; generation <= 3; generation++) {
                assertThat(events.next()).isEqualTo(new TestClient.Event("generation", event(generation, board)));
                board = board.next();
            }
            assertThat(events.next()).isEqualTo(new TestClient.Event("end", "{\"generation\":3,\"reason\":\"limit\"}"));
            assertThat(events.next()).isNull();
        }
        try (var events = client.events("/v1/simulations/%s/events?speed=30&generations=1".formatted(id))) {
            assertThat(events.next().data()).startsWith("{\"generation\":3,");
            assertThat(events.next()).isEqualTo(new TestClient.Event("generation", event(4, board)));
        }
    }

    @Test
    void handsTheSimulationToTheNewestStream() throws IOException {
        var id = idOf(client.send("POST", "/v1/simulations?pattern=r-pentomino", "").body());

        try (var first = client.events("/v1/simulations/%s/events?speed=30".formatted(id), "Origin", PAGE)) {
            assertThat(first.response().headers().firstValue("Access-Control-Allow-Origin")).contains(PAGE);
            assertThat(first.next().name()).isEqualTo("generation");
            try (var second = client.events("/v1/simulations/%s/events?speed=30&generations=2".formatted(id))) {
                assertThat(second.next().name()).isEqualTo("generation");

                var last = first.next();
                while (last.name().equals("generation")) {
                    last = first.next();
                }
                assertThat(last.name()).isEqualTo("end");
                assertThat(last.data()).endsWith("\"reason\":\"replaced\"}");
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            speed=0        | speed must be a whole number from 1 to 30
            speed=31       | speed must be a whole number from 1 to 30
            generations=0  | generations must be a whole number from 1 to 2147483647
            """)
    void refusesAStreamItCannotRun(String query, String detail) {
        var id = idOf(client.send("POST", "/v1/simulations?pattern=glider", "").body());

        assertThat(client.get("/v1/simulations/%s/events?%s".formatted(id, query)).body())
                .isEqualTo(problem("Bad Request", 400, detail));
        assertThat(client.get("/v1/simulations/nope/events").statusCode()).isEqualTo(404);
    }

    @Test
    void stopsTakingSimulationsAtItsLimit() throws IOException {
        try (var crowded = LifeApplication.start(new Settings(0, Set.of(), ""), Clock.systemUTC());
             var crowdedClient = new TestClient(crowded.port())) {
            for (var i = 0; i < 100; i++) {
                assertThat(crowdedClient.send("POST", "/v1/simulations?pattern=glider&width=3&height=3", "").statusCode())
                        .isEqualTo(201);
            }

            var refused = crowdedClient.send("POST", "/v1/simulations?pattern=glider&width=3&height=3", "");

            assertThat(refused.statusCode()).isEqualTo(503);
            assertThat(refused.body()).isEqualTo(problem("Service Unavailable", 503,
                    "100 simulations are already running; try again in a few minutes"));
        }
    }

    private static Board glider(int width, int height, Topology topology) {
        return catalogue.find("glider").map(pattern -> pattern.centredOn(width, height, topology)).orElseThrow();
    }

    private static String event(long generation, Board board) {
        return "{\"generation\":%d,\"population\":%d,\"cells\":\"%s\"}".formatted(generation, board.population(), cells(board));
    }

    private static String cells(Board board) {
        return Base64.getEncoder().encodeToString(board.toBytes());
    }

    private static String idOf(String body) {
        var matcher = java.util.regex.Pattern.compile("\"id\":\"([0-9a-f]{24})\"").matcher(body);
        assertThat(matcher.find()).as(body).isTrue();
        return matcher.group(1);
    }

    private static String problem(String title, int status, String detail) {
        return "{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}".formatted(title, status, detail);
    }
}
