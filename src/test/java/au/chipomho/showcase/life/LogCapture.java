package au.chipomho.showcase.life;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Collects what one class logs, and keeps it out of the test output, until closed.
 */
public final class LogCapture extends Handler implements AutoCloseable {

    private final Logger logger;
    private final List<LogRecord> records = new CopyOnWriteArrayList<>();

    private LogCapture(Logger logger) {
        this.logger = logger;
        logger.addHandler(this);
        logger.setUseParentHandlers(false);
    }

    public static LogCapture of(Class<?> type) {
        return new LogCapture(Logger.getLogger(type.getName()));
    }

    public List<LogRecord> records() {
        return List.copyOf(records);
    }

    @Override
    public void publish(LogRecord record) {
        records.add(record);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
        logger.removeHandler(this);
        logger.setUseParentHandlers(true);
    }
}
