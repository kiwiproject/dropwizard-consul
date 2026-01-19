package org.kiwiproject.dropwizard.consul.managed;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import io.dropwizard.lifecycle.Managed;
import org.jspecify.annotations.Nullable;
import org.kiwiproject.dropwizard.consul.core.ConsulAdvertiser;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Dropwizard {@link Managed} component that coordinates the lifecycle of a {@link ConsulAdvertiser}.
 */
public class ConsulAdvertiserManager implements Managed {

    private final ConsulAdvertiser advertiser;
    private final ScheduledExecutorService scheduler;

    /**
     * Create a new instance with no scheduler. Only use this if you aren't using a retry scheduler.
     *
     * @param advertiser Consul advertiser
     */
    public ConsulAdvertiserManager(ConsulAdvertiser advertiser) {
        this(advertiser, null);
    }

    /**
     * Create a new instance.
     *
     * @param advertiser Consul advertiser
     * @param scheduler  Retry scheduler; may be null
     */
    public ConsulAdvertiserManager(ConsulAdvertiser advertiser, @Nullable ScheduledExecutorService scheduler) {
        this.advertiser = requireNonNull(advertiser, "advertiser must not be null");
        this.scheduler = scheduler;
    }

    /**
     * No-op startup hook.
     * <p>
     * The associated {@link ConsulAdvertiser} is expected to be registered as a Jetty startup listener and
     * perform its own initialization when the server starts.
     */
    @Override
    public void start() {
        // the advertiser is registered as a Jetty startup listener
    }

    /**
     * Deregisters the service from Consul and shuts down the retry scheduler if this instance contains one.
     */
    @Override
    public void stop() {
        advertiser.deregister();
        if (nonNull(scheduler)) {
            scheduler.shutdownNow();
        }
    }
}
