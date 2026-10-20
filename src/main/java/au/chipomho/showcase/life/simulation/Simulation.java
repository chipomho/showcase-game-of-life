package au.chipomho.showcase.life.simulation;

import au.chipomho.showcase.life.engine.Board;

import java.time.Instant;
import java.util.Optional;

/**
 * One running game: its current generation and how many steps it has taken. Only the most
 * recently opened stream may move it on. A newer stream takes over from an older one, which is
 * what a viewer means when they reopen a stream to change speed, and it stops two browser tabs on
 * the same simulation from doubling its speed.
 */
public final class Simulation {

    private final String id;
    private Board board;
    private long generation;
    private Instant lastUsed;
    private long lastStream;
    private long currentStream;

    Simulation(String id, Board board, Instant now) {
        this.id = id;
        this.board = board;
        this.lastUsed = now;
    }

    public String id() {
        return id;
    }

    public synchronized Snapshot current() {
        return new Snapshot(generation, board);
    }

    /**
     * @return the new stream's token, which supersedes any earlier one
     */
    public synchronized long takeStream() {
        currentStream = ++lastStream;
        return currentStream;
    }

    /**
     * The next generation, or nothing if a newer stream has taken over from this one.
     */
    public synchronized Optional<Snapshot> advance(long stream) {
        if (stream != currentStream) {
            return Optional.empty();
        }
        board = board.next();
        generation++;
        return Optional.of(current());
    }

    synchronized void releaseStream(long stream, Instant now) {
        if (stream == currentStream) {
            currentStream = 0;
        }
        lastUsed = now;
    }

    synchronized void touch(Instant now) {
        lastUsed = now;
    }

    /**
     * Unused since before the given moment, and not being streamed.
     */
    synchronized boolean idleSince(Instant moment) {
        return currentStream == 0 && lastUsed.isBefore(moment);
    }

    public record Snapshot(long generation, Board board) {
    }
}
