package org.kiwiproject.dropwizard.consul.core;

import static java.util.Objects.requireNonNull;

import io.dropwizard.lifecycle.ServerLifecycleListener;
import io.dropwizard.util.Duration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.kiwiproject.consul.ConsulException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConsulServiceListener implements ServerLifecycleListener {

    private static final Logger LOG = LoggerFactory.getLogger(ConsulServiceListener.class);

    private static final String APPLICATION_NAME = "application";
    private static final String ADMIN_NAME = "admin";

    private final ConsulAdvertiser advertiser;
    private final Optional<Duration> retryInterval;
    private final Optional<ScheduledExecutorService> scheduler;

    /**
     * Constructor
     *
     * @param advertiser    Consul advertiser
     * @param retryInterval When specified, will retry if service registration fails
     * @param scheduler     When specified, will retry if service registration fails
     */
    public ConsulServiceListener(ConsulAdvertiser advertiser,
                                 Optional<Duration> retryInterval,
                                 Optional<ScheduledExecutorService> scheduler) {

        this.advertiser = requireNonNull(advertiser, "advertiser == null");
        this.retryInterval = requireNonNull(retryInterval, "retryInterval == null");
        this.scheduler = requireNonNull(scheduler, "scheduler == null");
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
            @SuppressWarnings("resource")
            var serverConnector = (ServerConnector) connector;

            hosts.add(serverConnector.getHost());

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
    void register(String applicationScheme, int applicationPort, int adminPort, Collection<String> hosts) {
        try {
            advertiser.register(applicationScheme, applicationPort, adminPort, hosts);
            scheduler.ifPresent(ScheduledExecutorService::shutdownNow);
        } catch (ConsulException e) {
            LOG.error("Failed to register service in Consul", e);

            retryInterval.ifPresent(interval ->
                scheduler.ifPresent(service -> {
                    LOG.info("Will try to register service again in {} seconds", interval.toSeconds());
                    service.schedule(
                        () -> register(applicationScheme, applicationPort, adminPort, hosts),
                        interval.toSeconds(),
                        TimeUnit.SECONDS);
                }));
        }
    }
}
