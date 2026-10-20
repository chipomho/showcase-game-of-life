package au.chipomho.showcase.life.engine;

import java.util.Optional;

/**
 * What lies beyond the edge of the board: nothing, or the opposite edge.
 */
public sealed interface Topology {

    Topology BOUNDED = new Bounded();
    Topology TOROIDAL = new Toroidal();

    String name();

    static Optional<Topology> named(String name) {
        return BOUNDED.name().equals(name) ? Optional.of(BOUNDED)
                : TOROIDAL.name().equals(name) ? Optional.of(TOROIDAL)
                : Optional.empty();
    }

    /**
     * Cells beyond the edge are always dead.
     */
    record Bounded() implements Topology {

        @Override
        public String name() {
            return "BOUNDED";
        }
    }

    /**
     * The edges join up: leave on the right and come back on the left, likewise top and bottom.
     */
    record Toroidal() implements Topology {

        @Override
        public String name() {
            return "TOROIDAL";
        }
    }
}
