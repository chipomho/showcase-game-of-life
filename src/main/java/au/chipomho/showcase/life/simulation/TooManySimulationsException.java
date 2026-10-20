package au.chipomho.showcase.life.simulation;

import java.io.Serial;

public final class TooManySimulationsException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    TooManySimulationsException(int capacity) {
        super("%d simulations are already running; try again in a few minutes".formatted(capacity));
    }
}
