package au.chipomho.showcase.life.http;

import java.io.Serial;

/**
 * A request that can't be served as asked; the router answers it with a problem detail.
 */
public final class ProblemException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int status;
    private final String title;

    public ProblemException(int status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public static ProblemException badRequest(String detail) {
        return new ProblemException(400, "Bad Request", detail);
    }

    public static ProblemException notFound(String detail) {
        return new ProblemException(404, "Not Found", detail);
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }
}
