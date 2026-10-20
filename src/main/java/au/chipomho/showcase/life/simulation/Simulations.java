package au.chipomho.showcase.life.simulation;

import au.chipomho.showcase.life.engine.Board;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The simulations in memory. Each is known only by a random id, is forgotten after it has sat
 * unused for a while, and there can only be so many at once.
 */
public final class Simulations {

    private final Clock clock;
    private final Duration idleLimit;
    private final int capacity;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Simulation> byId = new ConcurrentHashMap<>();

    public Simulations(Clock clock, Duration idleLimit, int capacity) {
        this.clock = clock;
        this.idleLimit = idleLimit;
        this.capacity = capacity;
    }

    /**
     * @throws TooManySimulationsException when the limit is reached even after forgetting idle ones
     */
    public synchronized Simulation start(Board board) {
        var now = clock.instant();
        byId.values().removeIf(simulation -> simulation.idleSince(now.minus(idleLimit)));
        if (byId.size() >= capacity) {
            throw new TooManySimulationsException(capacity);
        }
        var bytes = new byte[12];
        random.nextBytes(bytes);
        var simulation = new Simulation(HexFormat.of().formatHex(bytes), board, now);
        byId.put(simulation.id(), simulation);
        return simulation;
    }

    public Optional<Simulation> find(String id) {
        var simulation = Optional.ofNullable(byId.get(id));
        simulation.ifPresent(found -> found.touch(clock.instant()));
        return simulation;
    }

    public boolean remove(String id) {
        return byId.remove(id) != null;
    }

    /**
     * Marks a stream as finished and starts the simulation's idle time from now.
     */
    public void endStream(Simulation simulation, long stream) {
        simulation.releaseStream(stream, clock.instant());
    }
}
