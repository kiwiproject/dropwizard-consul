package org.kiwiproject.dropwizard.consul.core;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.lifecycle.ServerLifecycleListener;
import io.dropwizard.util.Duration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.jspecify.annotations.Nullable;
import org.kiwiproject.consul.ConsulException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Dropwizard {@link ServerLifecycleListener} that registers the application
 * with Consul when the Jetty {@link Server} has started.
 * <p>
 * A retry scheduler may be provided to retry Consul registration upon failure.
 * It will continue trying to register with Consul until registration succeeds,
 * or the application shuts down. Also note that the retry interval is a fixed
 * delay, i.e., if the delay is 1 second and Consul is unavailable, registration
 * will be retried every second, so be careful not to set this too low.
 */
public class ConsulServiceListener implements ServerLifecycleListener {

    private static final Logger LOG = LoggerFactory.getLogger(ConsulServiceListener.class);

    private static final String APPLICATION_NAME = "application";
    private static final String ADMIN_NAME = "admin";

    private final ConsulAdvertiser advertiser;
    private final Duration retryInterval;
    private final ScheduledExecutorService scheduler;

    /**
     * Create a new instance.
     * <p>
     * If the retry {@code scheduler} is provided, it will be automatically shut down
     * after successful registration with Consul.
     *
     * @param advertiser    Consul advertiser
     * @param retryInterval When specified, will retry if service registration fails
     * @param scheduler     When specified, will retry if service registration fails
     * @deprecated use {@link #ConsulServiceListener(ConsulAdvertiser, Duration, ScheduledExecutorService)}
     */
    @SuppressWarnings({ "DeprecatedIsStillUsed", "OptionalUsedAsFieldOrParameterType", "java:S1133" })
    @Deprecated(since = "1.3.0", forRemoval = true)
    public ConsulServiceListener(ConsulAdvertiser advertiser,
                                 Optional<Duration> retryInterval,
                                 Optional<ScheduledExecutorService> scheduler) {
        this.advertiser = requireNonNull(advertiser, "advertiser == null");
        this.retryInterval = requireNonNull(retryInterval, "retryInterval == null").orElse(null);
        this.scheduler = requireNonNull(scheduler, "scheduler == null").orElse(null);
    }

    /**
     * Create a new instance.
     * <p>
     * Note that both {@code retryInterval} and {@code scheduler} must be provided
     * for retry scheduling of failed registrations.
     * <p>
     * If the retry {@code scheduler} is provided, it will be automatically shut down
     * after successful registration with Consul.
     *
     * @param advertiser    Consul advertiser
     * @param retryInterval The retry interval to use if service registration fails
     * @param scheduler     The scheduler to use if service registration fails
     */
    public ConsulServiceListener(ConsulAdvertiser advertiser,
                                 @Nullable Duration retryInterval,
                                 @Nullable ScheduledExecutorService scheduler) {
        this.advertiser = requireNonNull(advertiser, "advertiser must not be null");
        this.retryInterval = retryInterval;
        this.scheduler = scheduler;
    }

    @Override
    public void serverStarted(Server server) {
        String applicationScheme = null;
        var applicationPort = -1;
        var adminPort = -1;
        var hosts = new HashSet<String>();

        var applicationConnectorCount = 0;
        var adminConnectorCount = 0;
        var otherConnectorCount = 0;

        for (var connector : server.getConnectors()) {
            var serverConnector = (ServerConnector) connector;

            var host = serverConnector.getHost();
            if (isNotBlank(host)) {
                hosts.add(host);
            }

            if (APPLICATION_NAME.equals(connector.getName())) {
                applicationPort = serverConnector.getLocalPort();
                applicationScheme = getScheme(connector.getProtocols());
                ++applicationConnectorCount;

            } else if (ADMIN_NAME.equals(connector.getName())) {
                adminPort = serverConnector.getLocalPort();
                ++adminConnectorCount;

            } else {
                applicationPort = serverConnector.getLocalPort();
                applicationScheme = getScheme(connector.getProtocols());
                adminPort = applicationPort;
                ++otherConnectorCount;
            }
        }

        logWarningsIfNecessary(server, applicationConnectorCount, adminConnectorCount, otherConnectorCount);

        LOG.debug(
            "Register with Consul using applicationScheme: {}, applicationPort: {}, adminPort: {}, hosts: {}",
            applicationScheme,
            applicationPort,
            adminPort,
            hosts);

        register(applicationScheme, applicationPort, adminPort, hosts);
    }

    private void logWarningsIfNecessary(Server server,
                                        int applicationConnectorCount,
                                        int adminConnectorCount,
                                        int otherConnectorCount) {

        if (server.getConnectors().length == 0) {
            LOG.error("There are NO connectors for the Server!" +
                      " Consul registration will fail or not work as expected!");

            return;  // the following conditionals cannot be true if we're here
        }

        if (applicationConnectorCount > 1 ) {
            LOG.warn("There is more than one application connector." +
                    " Only the last one's scheme and port will be registered with Consul" +
                    " unless specified in ConsulFactory configuration!");
        }

        if (adminConnectorCount > 1) {
            LOG.warn("There is more than one admin connector." +
                    " Only the last one's port will be registered with Consul" +
                    " unless specified in ConsulFactory configuration!");
        }

        if (otherConnectorCount > 0) {
            LOG.warn("There is an 'other' connector (not application or admin)." +
                    " Its port will be used as application and admin port," +
                    " and its scheme as application scheme" +
                    " unless specified in ConsulFactory configuration!");
        }
    }

    /**
     * Return the protocol scheme from a list of protocols.
     *
     * @param protocols Configured protocols
     * @return protocol scheme
     */
    private static String getScheme(List<String> protocols) {
        if (protocols.contains("ssl")) {
            return "https";
        }
        return "http";
    }

    /**
     * Register ports with Consul and retry if unavailable
     *
     * @param applicationScheme Application protocol scheme
     * @param applicationPort   Application port
     * @param adminPort         Administration port
     * @param hosts             the List of addresses the service is bound to.
     */
    @VisibleForTesting
    void register(String applicationScheme, int applicationPort, int adminPort, Collection<String> hosts) {
        try {
            advertiser.register(applicationScheme, applicationPort, adminPort, hosts);
            if (hasScheduler()) {
                scheduler.shutdownNow();
            }
        } catch (ConsulException e) {
            var serviceId = advertiser.getServiceId();
            LOG.error("Failed to register service with ID {} in Consul", serviceId, e);

            var retryResult = determineRetryDecision();
            if (retryResult.shouldRetry()) {
                LOG.info("Will try to register service with ID {} again in {} ({} ms)",
                    serviceId, retryInterval, retryResult.retryIntervalMillis());
                scheduler.schedule(
                    () -> register(applicationScheme, applicationPort, adminPort, hosts),
                    retryResult.retryIntervalMillis(),
                    TimeUnit.MILLISECONDS
                );
            } else if (hasScheduler()) {
                scheduler.shutdownNow();
            }
        }
    }

    @VisibleForTesting
    record RetryResult(boolean shouldRetry, long retryIntervalMillis) {
        RetryResult {
            var validInterval = shouldRetry && retryIntervalMillis > 0;
            checkArgument(validInterval || !shouldRetry, "retryIntervalMillis must be positive when shouldRetry=true");
        }

        static RetryResult ofIntervalMillis(long retryIntervalMillis) {
            checkArgument(retryIntervalMillis > 0, "retryIntervalMillis must be positive");
            return new RetryResult(true, retryIntervalMillis);
        }

        static RetryResult ofNoRetry() {
            return new RetryResult(false, -1);
        }
    }

    private RetryResult determineRetryDecision() {
        if (hasScheduler() && nonNull(retryInterval)) {
            var intervalMillis = retryInterval.toMilliseconds();
            if (intervalMillis > 0) {
                return RetryResult.ofIntervalMillis(intervalMillis);
            } else {
                LOG.warn("Configured retry interval is non-positive ({} ms); treating as no retry.", intervalMillis);
            }
        }

        return RetryResult.ofNoRetry();
    }

    private boolean hasScheduler() {
        return nonNull(scheduler);
    }
}
