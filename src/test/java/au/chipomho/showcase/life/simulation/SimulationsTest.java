package au.chipomho.showcase.life.simulation;

import au.chipomho.showcase.life.MutableClock;
import au.chipomho.showcase.life.engine.Board;
import au.chipomho.showcase.life.engine.Cell;
import au.chipomho.showcase.life.engine.Topology;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SimulationsTest {

    private static final Board BLINKER = Board.of(5, 5, Topology.BOUNDED,
            List.of(new Cell(1, 2), new Cell(2, 2), new Cell(3, 2)));

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-21T00:00:00Z"));
    private final Simulations simulations = new Simulations(clock, Duration.ofMinutes(15), 3);

    @Test
    void startsSimulationsWithUnguessableIds() {
        var first = simulations.start(BLINKER);
        var second = simulations.start(BLINKER);

        assertThat(first.id()).matches("[0-9a-f]{24}").isNotEqualTo(second.id());
        assertThat(simulations.find(first.id())).containsSame(first);
        assertThat(first.current()).isEqualTo(new Simulation.Snapshot(0, BLINKER));
    }

    @Test
    void movesOnAGenerationAtATimeForTheStreamThatOwnsIt() {
        var simulation = simulations.start(BLINKER);
        var stream = simulation.takeStream();

        assertThat(simulation.advance(stream)).contains(new Simulation.Snapshot(1, BLINKER.next()));
        assertThat(simulation.advance(stream)).contains(new Simulation.Snapshot(2, BLINKER));
    }

    @Test
    void letsANewerStreamTakeOverFromAnOlderOne() {
        var simulation = simulations.start(BLINKER);
        var older = simulation.takeStream();
        var newer = simulation.takeStream();

        assertThat(simulation.advance(older)).isEmpty();
        assertThat(simulation.advance(newer)).map(Simulation.Snapshot::generation).contains(1L);

        simulations.endStream(simulation, older);
        assertThat(simulation.advance(newer)).isPresent();
        simulations.endStream(simulation, newer);
        assertThat(simulation.advance(newer)).isEmpty();
    }

    @Test
    void forgetsSimulationsLeftIdleButNotOnesStillBeingWatched() {
        var forgotten = simulations.start(BLINKER);
        var looked = simulations.start(BLINKER);
        var watched = simulations.start(BLINKER);
        watched.takeStream();

        clock.advance(Duration.ofMinutes(10));
        simulations.find(looked.id());
        clock.advance(Duration.ofMinutes(10));
        simulations.start(BLINKER);

        assertThat(simulations.find(forgotten.id())).isEmpty();
        assertThat(simulations.find(looked.id())).isPresent();
        assertThat(simulations.find(watched.id())).isPresent();
    }

    @Test
    void refusesMoreThanItsCapacity() {
        simulations.start(BLINKER);
        simulations.start(BLINKER);
        simulations.start(BLINKER);

        assertThatExceptionOfType(TooManySimulationsException.class)
                .isThrownBy(() -> simulations.start(BLINKER))
                .withMessage("3 simulations are already running; try again in a few minutes");
    }

    @Test
    void removesASimulationOnRequest() {
        var simulation = simulations.start(BLINKER);

        assertThat(simulations.remove(simulation.id())).isTrue();
        assertThat(simulations.remove(simulation.id())).isFalse();
        assertThat(simulations.find(simulation.id())).isEmpty();
    }
}
