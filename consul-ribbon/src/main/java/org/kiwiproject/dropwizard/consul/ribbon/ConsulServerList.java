package org.kiwiproject.dropwizard.consul.ribbon;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import com.orbitz.consul.Consul;
import com.orbitz.consul.model.health.ServiceHealth;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collection;
import java.util.List;

public class ConsulServerList implements ServerList<Server> {

    private final Consul consul;
    private final ConsulServiceDiscoverer serviceDiscoverer;

    /**
     * Constructor
     *
     * @param consul            Consul client
     * @param serviceDiscoverer Discoverer
     */
    public ConsulServerList(Consul consul, ConsulServiceDiscoverer serviceDiscoverer) {
        this.consul = requireNonNull(consul);
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
    }

    @Override
    public List<Server> getInitialListOfServers() {
        return buildServerList(serviceDiscoverer.discover(consul));
    }

    @Override
    public List<Server> getUpdatedListOfServers() {
        return buildServerList(serviceDiscoverer.discover(consul));
    }

    /**
     * Converts a list of {@link ServiceHealth} objects into {@link Server} objects
     *
     * @param services list of healthy service instances
     * @return list of server instances
     */
    private List<Server> buildServerList(Collection<ServiceHealth> services) {
        return services.stream().map(this::buildServer).collect(toList());
    }

    /**
     * Build a {@link Server} instance from a Consul {@link ServiceHealth} instance. If the service
     * has an address defined, use that as the server host, otherwise default to using the node
     * address.
     *
     * @param serviceHealth Consul service health record
     * @return Ribbon Server instance
     */
    private Server buildServer(ServiceHealth serviceHealth) {
        var service = serviceHealth.getService();
        @Nullable String scheme = service.getMeta().get("scheme");
        int port = service.getPort();

        String address;
        if (isNullOrEmpty(service.getAddress())) {
            address = serviceHealth.getNode().getAddress();
        } else {
            address = service.getAddress();
        }

        var server = new Server(scheme, address, port);
        server.setZone(serviceHealth.getNode().getDatacenter().orElse(Server.UNKNOWN_ZONE));
        server.setReadyToServe(true);

        return server;
    }
}
