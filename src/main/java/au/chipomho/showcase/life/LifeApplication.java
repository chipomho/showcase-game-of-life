package au.chipomho.showcase.life;

import au.chipomho.showcase.life.http.LifeApi;
import au.chipomho.showcase.life.http.Router;
import au.chipomho.showcase.life.pattern.PatternCatalogue;
import au.chipomho.showcase.life.simulation.Simulations;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The JDK's HTTP server with one virtual thread per request, which suits event streams that stay
 * open for as long as someone is watching.
 */
public final class LifeApplication implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(LifeApplication.class.getName());

    private final HttpServer server;
    private final ExecutorService executor;

    private LifeApplication(HttpServer server, ExecutorService executor) {
        this.server = server;
        this.executor = executor;
    }

    public static void main(String[] args) throws IOException {
        var application = start(Settings.fromEnvironment(System.getenv()), Clock.systemUTC());
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(application::close));
    }

    public static LifeApplication start(Settings settings, Clock clock) throws IOException {
        var simulations = new Simulations(clock, Duration.ofMinutes(15), 100);
        var api = new LifeApi(PatternCatalogue.bundled(), simulations, settings.apiBase());
        var server = HttpServer.create(new InetSocketAddress(settings.port()), 0);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", new Router(api.routes(), settings.allowedOrigins()));
        server.start();
        var application = new LifeApplication(server, executor);
        LOG.log(Level.INFO, "Listening on port {0}", String.valueOf(application.port()));
        return application;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    /**
     * Stops at once. Open event streams are cut off rather than waited for, since they would
     * otherwise run until their viewers left.
     */
    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
