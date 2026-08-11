package com.example.loadtest.service;

import com.example.loadtest.config.AppProperties;

import java.util.function.Supplier;

/**
 * Central "database base layer" toggle. Every service operation routes through
 * {@link #execute} so the whole app switches between real Mongo access and a
 * fixed-latency simulated response via the single {@code app.use-db} flag.
 */
public abstract class BaseDataService {

    protected final AppProperties props;

    protected BaseDataService(AppProperties props) {
        this.props = props;
    }

    /**
     * Runs {@code dbCall} when {@code app.use-db=true}; otherwise sleeps for the
     * configured baseline latency and returns {@code simulated.get()} without
     * touching MongoDB.
     */
    protected <T> T execute(Supplier<T> dbCall, Supplier<T> simulated) {
        if (props.isUseDb()) {
            return dbCall.get();
        }
        sleepQuietly(props.getSimulatedWaitMs());
        return simulated.get();
    }

    protected void sleepQuietly(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
