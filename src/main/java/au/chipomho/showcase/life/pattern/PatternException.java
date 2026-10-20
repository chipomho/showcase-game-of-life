package au.chipomho.showcase.life.pattern;

import java.io.Serial;

/**
 * A pattern that can't be read or can't be used as asked.
 */
public final class PatternException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PatternException(String message) {
        super(message);
    }
}
