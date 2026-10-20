package au.chipomho.showcase.life.http;

import au.chipomho.showcase.life.LogCapture;
import au.chipomho.showcase.life.TestClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.assertj.core.api.Assertions.assertThat;

class RouterTest {

    private static final String ALLOWED = "https://page.example";

    private static final CountDownLatch STREAM_CLOSED = new CountDownLatch(1);
    private static final CountDownLatch HUNG_UP = new CountDownLatch(1);

    private static HttpServer server;
    private static TestClient client;

    public record Item(String id, String colour) {
    }

    public record Echo(String body) {
    }

    @BeforeAll
    static void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", new Router(List.of(
                new Router.Route("GET", "/items/{id}", request ->
                        Response.json(new Item(request.path("id"), request.query().get("colour").orElse("plain")))),
                new Router.Route("DELETE", "/items/{id}", _ -> new Response.Empty(204, Map.of())),
                new Router.Route("POST", "/echo", request -> Response.json(new Echo(request.body()))),
                new Router.Route("GET", "/teapot", _ -> {
                    throw new ProblemException(418, "I'm a teapot", "Short and stout");
                }),
                new Router.Route("GET", "/broken", _ -> {
                    throw new IllegalStateException("boom");
                }),
                new Router.Route("GET", "/file", _ -> new Response.File("hello".getBytes(StandardCharsets.UTF_8), "text/plain")),
                new Router.Route("GET", "/stream", _ -> new Response.EventStream(events -> {
                    events.send("first", new Echo("one"));
                    events.send("second", new Echo("two"));
                }, STREAM_CLOSED::countDown)),
                new Router.Route("GET", "/endless", _ -> new Response.EventStream(events -> {
                    while (true) {
                        events.send("tick", new Echo("x".repeat(1000)));
                    }
                }, HUNG_UP::countDown))), Set.of(ALLOWED)));
        server.start();
        client = new TestClient(server.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        client.close();
        server.stop(0);
    }

    @Test
    void passesPathPartsAndQueryParametersToTheHandler() {
        var response = client.get("/items/42?colour=red");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).contains("application/json");
        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(response.body()).isEqualTo("{\"id\":\"42\",\"colour\":\"red\"}");
    }

    @Test
    void passesTheBodyToTheHandler() {
        assertThat(client.send("POST", "/echo", "x = 3, y = 3").body()).isEqualTo("{\"body\":\"x = 3, y = 3\"}");
    }

    @Test
    void answersAPathItDoesNotKnowWith404() {
        for (var path : List.of("/items", "/items/42/more", "/nothing")) {
            var response = client.get(path);

            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
            assertThat(response.body()).isEqualTo(
                    "{\"type\":\"about:blank\",\"title\":\"Not Found\",\"status\":404,\"detail\":\"There is nothing at " + path + "\"}");
        }
    }

    @Test
    void answersAMethodThePathDoesNotTakeWith405() {
        var response = client.send("PUT", "/items/42", "");

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.headers().firstValue("Allow")).contains("DELETE, GET, OPTIONS");
        assertThat(response.body()).isEqualTo("{\"type\":\"about:blank\",\"title\":\"Method Not Allowed\",\"status\":405,"
                + "\"detail\":\"PUT is not supported here; use DELETE, GET, OPTIONS\"}");
    }

    @Test
    void sendsNoBodyForHead() {
        var response = client.send("HEAD", "/items/42", "");

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.body()).isEmpty();
    }

    @Test
    void answersAnEmptyResponse() {
        var response = client.send("DELETE", "/items/42", "");

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
    }

    @Test
    void answersPreflightsAndEchoesOnlyAllowedOrigins() {
        var preflight = client.send("OPTIONS", "/items/42", "", "Origin", ALLOWED, "Access-Control-Request-Method", "DELETE");
        var elsewhere = client.get("/items/42", "Origin", "https://elsewhere.example");

        assertThat(preflight.statusCode()).isEqualTo(204);
        assertThat(preflight.headers().map())
                .containsEntry("Access-Control-Allow-Origin", List.of(ALLOWED))
                .containsEntry("Access-Control-Allow-Methods", List.of("DELETE, GET, OPTIONS"))
                .containsEntry("Access-Control-Allow-Headers", List.of("Content-Type"))
                .containsEntry("Access-Control-Max-Age", List.of("600"))
                .containsEntry("Vary", List.of("Origin"));
        assertThat(elsewhere.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    void refusesABodyOverOneMebibyte() {
        var response = client.send("POST", "/echo", "x".repeat(Router.MAX_BODY_BYTES + 1));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"detail\":\"A request body can be at most 1 MiB\"");
    }

    @Test
    void reportsAHandlersProblemWithItsOwnStatus() {
        var response = client.get("/teapot");

        assertThat(response.statusCode()).isEqualTo(418);
        assertThat(response.body()).isEqualTo(
                "{\"type\":\"about:blank\",\"title\":\"I'm a teapot\",\"status\":418,\"detail\":\"Short and stout\"}");
    }

    @Test
    void turnsAnUnexpectedFailureIntoA500AndLogsIt() {
        try (var log = LogCapture.of(Router.class)) {
            var response = client.get("/broken");

            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(response.body()).contains("\"detail\":\"The request could not be completed\"");
            assertThat(log.records()).singleElement().satisfies(record -> {
                assertThat(record.getLevel()).isEqualTo(Level.SEVERE);
                assertThat(record.getThrown()).hasMessage("boom");
            });
        }
    }

    @Test
    void servesAFileAsItIs() {
        var response = client.get("/file");

        assertThat(response.body()).isEqualTo("hello");
        assertThat(response.headers().firstValue("Content-Type")).contains("text/plain");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-cache");
    }

    @Test
    void streamsEventsAndTidiesUpAfterwards() throws Exception {
        try (var events = client.events("/stream")) {
            assertThat(events.response().headers().firstValue("Content-Type")).contains("text/event-stream");
            assertThat(events.next()).isEqualTo(new TestClient.Event("first", "{\"body\":\"one\"}"));
            assertThat(events.next()).isEqualTo(new TestClient.Event("second", "{\"body\":\"two\"}"));
            assertThat(events.next()).isNull();
        }
        assertThat(STREAM_CLOSED.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void tidiesUpWhenTheClientHangsUp() throws Exception {
        var events = client.events("/endless");
        assertThat(events.next().name()).isEqualTo("tick");

        events.close();

        assertThat(HUNG_UP.await(10, TimeUnit.SECONDS)).isTrue();
    }
}
