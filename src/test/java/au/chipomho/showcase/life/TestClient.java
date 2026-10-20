package au.chipomho.showcase.life;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;

/**
 * Plain HTTP/1.1 calls against a server on localhost, including reading Server-Sent Events as
 * they arrive.
 */
public final class TestClient implements AutoCloseable {

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final URI base;

    public TestClient(int port) {
        this.base = URI.create("http://localhost:" + port);
    }

    public HttpResponse<String> get(String pathAndQuery, String... headers) {
        return send("GET", pathAndQuery, "", headers);
    }

    public HttpResponse<String> send(String method, String pathAndQuery, String body, String... headers) {
        return call(request(method, pathAndQuery, body, headers), BodyHandlers.ofString());
    }

    /**
     * Opens an event stream; close it to hang up.
     */
    public Events events(String pathAndQuery, String... headers) {
        var response = call(request("GET", pathAndQuery, "", headers), BodyHandlers.ofInputStream());
        return new Events(response);
    }

    private HttpRequest request(String method, String pathAndQuery, String body, String... headers) {
        var request = HttpRequest.newBuilder(base.resolve(pathAndQuery))
                .method(method, body.isEmpty() ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
        if (headers.length > 0) {
            request.headers(headers);
        }
        return request.build();
    }

    private <T> HttpResponse<T> call(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            return client.send(request, handler);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        client.shutdownNow();
    }

    public record Event(String name, String data) {
    }

    public static final class Events implements AutoCloseable {

        private final HttpResponse<InputStream> response;
        private final BufferedReader reader;

        Events(HttpResponse<InputStream> response) {
            this.response = response;
            this.reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
        }

        public HttpResponse<InputStream> response() {
            return response;
        }

        /**
         * The next event, or null when the stream has ended.
         */
        public Event next() {
            try {
                String name = null;
                String data = null;
                for (var line = reader.readLine(); line != null; line = reader.readLine()) {
                    if (line.startsWith("event: ")) {
                        name = line.substring(7);
                    } else if (line.startsWith("data: ")) {
                        data = line.substring(6);
                    } else if (line.isEmpty() && name != null) {
                        return new Event(name, data);
                    }
                }
                return null;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }
}
