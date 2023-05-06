package org.kiwiproject.dropwizard.consul.managed;

import io.dropwizard.lifecycle.Managed;
import org.kiwiproject.dropwizard.consul.core.ConsulAdvertiser;

import java.util.Objects;
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
        this.advertiser = Objects.requireNonNull(advertiser, "advertiser == null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler == null");
    }

    @Override
    public void start() throws Exception {
        // the advertiser is register as a Jetty startup listener
    }

    @Override
    public void stop() throws Exception {
        advertiser.deregister();
        scheduler.ifPresent(ScheduledExecutorService::shutdownNow);
    }
}
