package io.github.aiarchguard.scanner.cli;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

class TimedAnalysisRunner {

    <T> T run(Supplier<T> analysis, Duration timeout) {
        if (analysis == null || timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("analysis and a positive timeout are required");
        }
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "archguard-scan");
            thread.setDaemon(true);
            return thread;
        });
        Future<T> future = executor.submit(analysis::get);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new ScanTimeoutException();
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("scan wait was interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("scan execution failed", cause);
        } finally {
            executor.shutdownNow();
        }
    }
}
