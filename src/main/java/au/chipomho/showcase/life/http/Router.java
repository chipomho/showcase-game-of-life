package au.chipomho.showcase.life.http;

import au.chipomho.showcase.life.json.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Matches each request to a route by method and path, where a path segment written
 * {@code {name}} matches anything and is passed to the handler. It answers CORS preflights and
 * echoes allowed origins, and every failure is a problem detail: 404 for an unknown path, 405 for
 * a method the path doesn't take, 413 for a body over {@value #MAX_BODY_BYTES} bytes, whatever
 * status a handler's {@link ProblemException} carries, and 500 for anything else.
 */
public final class Router implements HttpHandler {

    static final int MAX_BODY_BYTES = 1 << 20;
    private static final System.Logger LOG = System.getLogger(Router.class.getName());

    private final List<Route> routes;
    private final Set<String> allowedOrigins;

    public Router(List<Route> routes, Set<String> allowedOrigins) {
        this.routes = List.copyOf(routes);
        this.allowedOrigins = Set.copyOf(allowedOrigins);
    }

    @FunctionalInterface
    public interface Handler {
        Response handle(Request request);
    }

    public record Route(String method, String path, Handler handler) {

        Optional<Map<String, String>> match(String requestPath) {
            var expected = path.split("/", -1);
            var actual = requestPath.split("/", -1);
            if (expected.length != actual.length) {
                return Optional.empty();
            }
            var parameters = new HashMap<String, String>();
            for (var i = 0; i < expected.length; i++) {
                if (expected[i].startsWith("{")) {
                    parameters.put(expected[i].substring(1, expected[i].length() - 1), actual[i]);
                } else if (!expected[i].equals(actual[i])) {
                    return Optional.empty();
                }
            }
            return Optional.of(parameters);
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            var headers = exchange.getResponseHeaders();
            headers.set("Vary", "Origin");
            headers.set("X-Content-Type-Options", "nosniff");
            var origin = exchange.getRequestHeaders().getFirst("Origin");
            if (origin != null && allowedOrigins.contains(origin)) {
                headers.set("Access-Control-Allow-Origin", origin);
            }
            write(exchange, respond(exchange));
        }
    }

    private Response respond(HttpExchange exchange) throws IOException {
        var path = exchange.getRequestURI().getPath();
        var method = exchange.getRequestMethod();
        var matching = routes.stream().filter(route -> route.match(path).isPresent()).toList();
        if (matching.isEmpty()) {
            return Response.problem(404, "Not Found", "There is nothing at " + path);
        }
        var methods = new TreeSet<String>();
        matching.forEach(route -> methods.add(route.method()));
        methods.add("OPTIONS");
        var allowed = String.join(", ", methods);
        if (method.equals("OPTIONS")) {
            return new Response.Empty(204, Map.of(
                    "Allow", allowed,
                    "Access-Control-Allow-Methods", allowed,
                    "Access-Control-Allow-Headers", "Content-Type",
                    "Access-Control-Max-Age", "600"));
        }
        var route = matching.stream().filter(candidate -> candidate.method().equals(method)).findFirst();
        if (route.isEmpty()) {
            return Response.problem(405, "Method Not Allowed", method + " is not supported here; use " + allowed,
                    Map.of("Allow", allowed));
        }
        try {
            var body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                throw new ProblemException(413, "Content Too Large", "A request body can be at most 1 MiB");
            }
            var request = new Request(route.get().match(path).orElseThrow(),
                    Query.parse(exchange.getRequestURI().getRawQuery()), new String(body, StandardCharsets.UTF_8));
            return route.get().handler().handle(request);
        } catch (ProblemException e) {
            return Response.problem(e.status(), e.title(), e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.ERROR, "Request failed", e);
            return Response.problem(500, "Internal Server Error", "The request could not be completed");
        }
    }

    private static void write(HttpExchange exchange, Response response) throws IOException {
        var headers = exchange.getResponseHeaders();
        switch (response) {
            case Response.Json json -> {
                json.headers().forEach(headers::set);
                send(exchange, json.status(), json.contentType(), Json.write(json.body()).getBytes(StandardCharsets.UTF_8));
            }
            case Response.File file -> {
                headers.set("Cache-Control", "no-cache");
                send(exchange, 200, file.contentType(), file.content());
            }
            case Response.Empty empty -> {
                empty.headers().forEach(headers::set);
                exchange.sendResponseHeaders(empty.status(), -1);
            }
            case Response.EventStream stream -> {
                try {
                    headers.set("Content-Type", "text/event-stream");
                    headers.set("Cache-Control", "no-cache");
                    exchange.sendResponseHeaders(200, 0);
                    var out = exchange.getResponseBody();
                    stream.writer().write((event, data) -> {
                        out.write(("event: " + event + "\ndata: " + Json.write(data) + "\n\n").getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    });
                } finally {
                    stream.onClose().run();
                }
            }
        }
    }

    /**
     * A HEAD request gets the headers without the body.
     */
    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        if (exchange.getRequestMethod().equals("HEAD")) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
}
