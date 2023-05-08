package org.kiwiproject.dropwizard.consul.managed;

import io.dropwizard.lifecycle.Managed;

import static java.util.Objects.requireNonNull;

import org.kiwiproject.dropwizard.consul.core.ConsulAdvertiser;

import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

public class ConsulAdvertiserManager implements Managed {

    private final ConsulAdvertiser advertiser;
    private final Optional<ScheduledExecutorService> scheduler;

    /**
     * Constructor
     *
     * @param advertiser Consul advertiser
     * @param scheduler  Optional retry scheduler
     */
    public ConsulAdvertiserManager(
        final ConsulAdvertiser advertiser, final Optional<ScheduledExecutorService> scheduler) {
        this.advertiser = requireNonNull(advertiser, "advertiser == null");
        this.scheduler = requireNonNull(scheduler, "scheduler == null");
    }

    @Override
    public void start() {
        // the advertiser is register as a Jetty startup listener
    }

    @Override
    public void stop() {
        advertiser.deregister();
        scheduler.ifPresent(ScheduledExecutorService::shutdownNow);
    }
}
