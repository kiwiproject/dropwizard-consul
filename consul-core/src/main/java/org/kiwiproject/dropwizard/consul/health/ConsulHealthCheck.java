package org.kiwiproject.dropwizard.consul.health;

import static java.util.Objects.requireNonNull;

import com.codahale.metrics.health.HealthCheck;
import com.orbitz.consul.Consul;
import com.orbitz.consul.ConsulException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsulHealthCheck extends HealthCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsulHealthCheck.class);
    private final Consul consul;

    /**
     * Constructor
     *
     * @param consul Consul client
     */
    public ConsulHealthCheck(Consul consul) {
        this.consul = requireNonNull(consul);
    }

    @Override
    protected Result check() {
        try {
            consul.agentClient().ping();
            return Result.healthy();
        } catch (ConsulException e) {
            LOGGER.warn("Unable to ping consul", e);
        }
        return Result.unhealthy("Could not ping consul");
    }
}
