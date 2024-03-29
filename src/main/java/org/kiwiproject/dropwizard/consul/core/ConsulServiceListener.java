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
import java.util.Set;
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
        int applicationPort = -1;
        int adminPort = -1;
        Set<String> hosts = new HashSet<>();

        for (var connector : server.getConnectors()) {
            var serverConnector = (ServerConnector) connector;
            hosts.add(serverConnector.getHost());
            if (APPLICATION_NAME.equals(connector.getName())) {
                applicationPort = serverConnector.getLocalPort();
                applicationScheme = getScheme(connector.getProtocols());
            } else if (ADMIN_NAME.equals(connector.getName())) {
                adminPort = serverConnector.getLocalPort();
            } else {
                applicationPort = serverConnector.getLocalPort();
                applicationScheme = getScheme(connector.getProtocols());
                adminPort = applicationPort;
            }
        }

        LOG.debug(
            "applicationScheme: {}, applicationPort: {}, adminPort: {}",
            applicationScheme,
            applicationPort,
            adminPort);

        register(applicationScheme, applicationPort, adminPort, hosts);
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
