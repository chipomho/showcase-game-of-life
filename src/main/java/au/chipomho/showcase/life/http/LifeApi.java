package au.chipomho.showcase.life.http;

import au.chipomho.showcase.life.engine.Topology;
import au.chipomho.showcase.life.pattern.Pattern;
import au.chipomho.showcase.life.pattern.PatternCatalogue;
import au.chipomho.showcase.life.pattern.PatternException;
import au.chipomho.showcase.life.simulation.Simulation;
import au.chipomho.showcase.life.simulation.Simulations;
import au.chipomho.showcase.life.simulation.TooManySimulationsException;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The page, its runtime configuration and the API.
 *
 * <p>A simulation is created from a bundled pattern or from one sent in the body, and then
 * streamed: {@code GET /v1/simulations/{id}/events} sends its current generation and then each
 * next one as a Server-Sent Event, at up to {@value #MAX_SPEED} generations a second. Closing the
 * stream pauses the simulation where it is; opening a new one carries on from there, and takes
 * over from any stream still open on it.
 */
public final class LifeApi {

    static final int MAX_SIZE = 1000;
    static final int MAX_SPEED = 30;

    private final PatternCatalogue catalogue;
    private final Simulations simulations;
    private final String apiBase;
    private final Map<String, Response.File> files;

    public LifeApi(PatternCatalogue catalogue, Simulations simulations, String apiBase) throws IOException {
        this.catalogue = catalogue;
        this.simulations = simulations;
        this.apiBase = apiBase;
        this.files = Map.of(
                "/", file("index.html", "text/html; charset=utf-8"),
                "/app.js", file("app.js", "text/javascript; charset=utf-8"),
                "/app.css", file("app.css", "text/css; charset=utf-8"));
    }

    public List<Router.Route> routes() {
        return List.of(
                new Router.Route("GET", "/", _ -> files.get("/")),
                new Router.Route("GET", "/app.js", _ -> files.get("/app.js")),
                new Router.Route("GET", "/app.css", _ -> files.get("/app.css")),
                new Router.Route("GET", "/config.json", _ -> Response.json(new Config(apiBase))),
                new Router.Route("GET", "/health", _ -> Response.json(new Health("UP"))),
                new Router.Route("GET", "/v1/patterns", _ -> Response.json(patterns())),
                new Router.Route("POST", "/v1/simulations", this::start),
                new Router.Route("GET", "/v1/simulations/{id}", this::show),
                new Router.Route("DELETE", "/v1/simulations/{id}", this::remove),
                new Router.Route("GET", "/v1/simulations/{id}/events", this::events));
    }

    private List<PatternSummary> patterns() {
        return catalogue.all().entrySet().stream()
                .map(entry -> new PatternSummary(entry.getKey(), entry.getValue().name(),
                        entry.getValue().width(), entry.getValue().height()))
                .toList();
    }

    /**
     * A bundled pattern named by {@code pattern}, or one in the body as RLE or a coordinate
     * list, placed in the middle of a new board.
     */
    private Response start(Request request) {
        var width = request.query().integer("width", 120, 1, MAX_SIZE);
        var height = request.query().integer("height", 80, 1, MAX_SIZE);
        var topologyName = request.query().get("topology").orElse(Topology.TOROIDAL.name());
        var topology = Topology.named(topologyName)
                .orElseThrow(() -> ProblemException.badRequest("topology is BOUNDED or TOROIDAL, not " + topologyName));
        try {
            var simulation = simulations.start(pattern(request).centredOn(width, height, topology));
            return new Response.Json(201, "application/json", view(simulation),
                    Map.of("Location", "/v1/simulations/" + simulation.id()));
        } catch (PatternException e) {
            throw ProblemException.badRequest(e.getMessage());
        } catch (TooManySimulationsException e) {
            throw new ProblemException(503, "Service Unavailable", e.getMessage());
        }
    }

    private Pattern pattern(Request request) {
        var id = request.query().get("pattern");
        if (id.isPresent()) {
            return catalogue.find(id.get()).orElseThrow(() -> ProblemException.badRequest(
                    "'%s' is not a bundled pattern; GET /v1/patterns lists them".formatted(id.get())));
        }
        if (request.body().isBlank()) {
            throw ProblemException.badRequest("Name a bundled pattern, or send one in RLE or as a coordinate list");
        }
        return Pattern.parse(request.body());
    }

    private Response show(Request request) {
        return Response.json(view(find(request)));
    }

    private Response remove(Request request) {
        if (!simulations.remove(request.path("id"))) {
            throw notFound(request);
        }
        return new Response.Empty(204, Map.of());
    }

    /**
     * {@code speed} is generations a second. The stream ends with an {@code end} event after
     * {@code generations} more, if given, or when a newer stream takes over; otherwise it runs
     * until the client leaves.
     */
    private Response events(Request request) {
        var simulation = find(request);
        var pause = Duration.ofMillis(1000 / request.query().integer("speed", 10, 1, MAX_SPEED));
        var limit = request.query().optionalInteger("generations", 1, Integer.MAX_VALUE);
        var stream = simulation.takeStream();
        return new Response.EventStream(events -> {
            var snapshot = simulation.current();
            events.send("generation", generation(snapshot));
            try {
                for (var sent = 0; limit.isEmpty() || sent < limit.getAsInt(); sent++) {
                    Thread.sleep(pause);
                    var next = simulation.advance(stream);
                    if (next.isEmpty()) {
                        events.send("end", new End(snapshot.generation(), "replaced"));
                        return;
                    }
                    snapshot = next.get();
                    events.send("generation", generation(snapshot));
                }
                events.send("end", new End(snapshot.generation(), "limit"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, () -> simulations.endStream(simulation, stream));
    }

    private Simulation find(Request request) {
        return simulations.find(request.path("id")).orElseThrow(() -> notFound(request));
    }

    private static ProblemException notFound(Request request) {
        return ProblemException.notFound("There is no simulation %s; it may have been idle long enough to be removed"
                .formatted(request.path("id")));
    }

    private static SimulationView view(Simulation simulation) {
        var snapshot = simulation.current();
        var board = snapshot.board();
        return new SimulationView(simulation.id(), board.width(), board.height(), board.topology().name(),
                snapshot.generation(), board.population(), encode(snapshot));
    }

    private static GenerationEvent generation(Simulation.Snapshot snapshot) {
        return new GenerationEvent(snapshot.generation(), snapshot.board().population(), encode(snapshot));
    }

    private static String encode(Simulation.Snapshot snapshot) {
        return Base64.getEncoder().encodeToString(snapshot.board().toBytes());
    }

    private static Response.File file(String name, String contentType) throws IOException {
        try (InputStream in = LifeApi.class.getResourceAsStream("/web/" + name)) {
            return new Response.File(Objects.requireNonNull(in, name).readAllBytes(), contentType);
        }
    }

    /**
     * @param apiBase where the page should send its API requests; empty means its own origin
     */
    public record Config(String apiBase) {
    }

    public record Health(String status) {
    }

    public record PatternSummary(String id, String name, int width, int height) {
    }

    /**
     * @param cells the board, a row at a time with {@code ceil(width / 8)} bytes per row and cell x
     *              in byte {@code x / 8} at bit {@code x % 8}, base64-encoded
     */
    public record SimulationView(String id, int width, int height, String topology, long generation, int population,
                                 String cells) {
    }

    public record GenerationEvent(long generation, int population, String cells) {
    }

    /**
     * @param reason {@code limit} when the requested number of generations has been sent,
     *               {@code replaced} when a newer stream has taken over
     */
    public record End(long generation, String reason) {
    }
}
