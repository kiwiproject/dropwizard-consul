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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

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
        String adminScheme = null;
        var applicationPort = -1;
        var adminPort = -1;
        var hosts = new HashSet<String>();

        var connectors = server.getConnectors();
        var serverConnectors = Arrays.stream(connectors)
            .filter(ServerConnector.class::isInstance)
            .map(ServerConnector.class::cast)
            .toList();

        var applicationConnectorCount = 0;
        var adminConnectorCount = 0;
        var otherConnectorCount = 0;

        for (var serverConnector : serverConnectors) {
            var host = serverConnector.getHost();
            if (isNotBlank(host)) {
                hosts.add(host);
            }

            if (APPLICATION_NAME.equals(serverConnector.getName())) {
                applicationPort = serverConnector.getLocalPort();
                applicationScheme = getScheme(serverConnector);
                ++applicationConnectorCount;

            } else if (ADMIN_NAME.equals(serverConnector.getName())) {
                adminPort = serverConnector.getLocalPort();
                adminScheme = getScheme(serverConnector);
                ++adminConnectorCount;

            } else {
                applicationPort = serverConnector.getLocalPort();
                applicationScheme = getScheme(serverConnector);
                adminScheme = applicationScheme;
                adminPort = applicationPort;
                ++otherConnectorCount;
            }
        }

        logWarningsIfNecessary(applicationConnectorCount, adminConnectorCount, otherConnectorCount);

        var missingPorts = getMissingPorts(applicationPort, adminPort);

        if (!missingPorts.isEmpty()) {
            logMissingPortError(missingPorts);
            return;
        }

        LOG.debug(
            "Register with Consul using applicationScheme: {}, applicationPort: {}, adminPort: {}, hosts: {}",
            applicationScheme,
            applicationPort,
            adminPort,
            hosts);

        register(applicationScheme, applicationPort, adminScheme, adminPort, hosts);
    }

    private void logWarningsIfNecessary(int applicationConnectorCount,
                                        int adminConnectorCount,
                                        int otherConnectorCount) {

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
                    " and its scheme as the application and admin scheme" +
                    " unless specified in ConsulFactory configuration!");
        }
    }

    private static List<String> getMissingPorts(int applicationPort, int adminPort) {
        return Stream.of(
                missingPortOrNull(APPLICATION_NAME, applicationPort),
                missingPortOrNull(ADMIN_NAME, adminPort)
            )
            .filter(Objects::nonNull)
            .toList();
    }

    private static String missingPortOrNull(String name, int port) {
        return port == -1 ? name : null;
    }

    private static void logMissingPortError(List<String> missingPorts) {
        var joined = String.join(" and ", missingPorts);
        var plural = missingPorts.size() > 1 ? "s" : "";
        LOG.error(
            "Did not find {} port{}, so Consul registration cannot continue and will be skipped." +
                " Check your configuration to make sure Jetty connectors are configured correctly.",
            joined, plural
        );
    }

    /**
     * Return the protocol scheme from a list of protocols.
     *
     * @param serverConnector the server connector
     * @return protocol scheme
     */
    private static String getScheme(ServerConnector serverConnector) {
        var protocols = serverConnector.getProtocols();
        LOG.info("ServerConnector '{}' has protocols: {}", serverConnector.getName(), protocols);

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
    void register(String applicationScheme, int applicationPort, String adminScheme, int adminPort, Collection<String> hosts) {
        try {
            advertiser.register(applicationScheme, applicationPort, adminScheme, adminPort, hosts);
            if (hasScheduler()) {
                scheduler.shutdownNow();
            }
        } catch (ConsulException e) {
            var serviceId = advertiser.getServiceId();
            LOG.error("Failed to register service with ID {} in Consul (scheme: {}, hosts: {}, port:{}, admin port: {})",
                serviceId, applicationScheme, hosts, applicationPort, adminPort, e);

            var retryResult = determineRetryDecision();
            if (retryResult.shouldRetry()) {
                var retryIntervalMillis = retryResult.retryIntervalMillis();
                LOG.info("Will try to register service with ID {} (scheme: {}, hosts: {}, port:{}, admin port: {}) again in {} ({} ms)",
                    serviceId, applicationScheme, hosts, applicationPort, adminPort, retryInterval, retryIntervalMillis);
                scheduler.schedule(
                    () -> register(applicationScheme, applicationPort, adminScheme, adminPort, hosts),
                    retryIntervalMillis,
                    TimeUnit.MILLISECONDS
                );
            } else if (hasScheduler()) {
                LOG.info("Will not try to register service with ID {} again." +
                        " Ensure there is a valid retryInterval if you want retry behavior. (retryInterval: {})",
                    serviceId, retryInterval);
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
