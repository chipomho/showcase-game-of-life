package au.chipomho.showcase.life.http;

import java.io.IOException;
import java.util.Map;

/**
 * What a handler wants sent back. The router does the writing.
 */
public sealed interface Response {

    static Response json(Object body) {
        return new Json(200, "application/json", body, Map.of());
    }

    static Response problem(int status, String title, String detail) {
        return problem(status, title, detail, Map.of());
    }

    static Response problem(int status, String title, String detail, Map<String, String> headers) {
        return new Json(status, "application/problem+json", new Problem("about:blank", title, status, detail), headers);
    }

    /**
     * A body written as JSON.
     */
    record Json(int status, String contentType, Object body, Map<String, String> headers) implements Response {
    }

    /**
     * A file, sent as it is.
     */
    record File(byte[] content, String contentType) implements Response {
    }

    record Empty(int status, Map<String, String> headers) implements Response {
    }

    /**
     * Server-Sent Events: the writer sends events until it is done or the client goes away, and
     * the router calls {@code onClose} either way.
     */
    record EventStream(Writer writer, Runnable onClose) implements Response {
    }

    @FunctionalInterface
    interface Writer {
        void write(EventSink events) throws IOException;
    }

    @FunctionalInterface
    interface EventSink {
        void send(String event, Object data) throws IOException;
    }

    /**
     * An RFC 9457 problem detail.
     */
    record Problem(String type, String title, int status, String detail) {
    }
}
