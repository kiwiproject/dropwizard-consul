package org.kiwiproject.dropwizard.consul.ribbon;

import static java.util.Objects.requireNonNull;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ZoneAwareLoadBalancer;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.UriBuilder;
import java.io.Closeable;
import java.net.URI;
import java.util.List;
import java.util.Map;

public class RibbonJerseyClient implements Client, Closeable {
    private final ZoneAwareLoadBalancer<Server> loadBalancer;
    private final Client delegate;

    /**
     * Constructor
     *
     * @param loadBalancer Load Balancer
     * @param delegate     Jersey Client delegate
     */
    public RibbonJerseyClient(
        final ZoneAwareLoadBalancer<Server> loadBalancer, final Client delegate) {
        this.loadBalancer = requireNonNull(loadBalancer);
        this.delegate = requireNonNull(delegate);
    }

    /**
     * Constructor
     *
     * @param scheme       Communication scheme (usually http or https)
     * @param loadBalancer Load Balancer
     * @param delegate     Jersey Client delegate
     * @deprecated Use non-scheme constructor instead
     */
    @Deprecated(since = "0.5.0", forRemoval = true)
    public RibbonJerseyClient(
        @SuppressWarnings("unused") final String scheme,
        final ZoneAwareLoadBalancer<Server> loadBalancer,
        final Client delegate) {
        this.loadBalancer = requireNonNull(loadBalancer);
        this.delegate = requireNonNull(delegate);
    }

    /**
     * Return a list of available servers from this load balancing client
     *
     * @return a list of available servers
     */
    public List<Server> getAvailableServers() {
        return loadBalancer.getServerList(true);
    }

    /**
     * Fetch a server from the load balancer or throw an exception if none are available.
     *
     * @return a server
     * @throws IllegalStateException if no servers are available
     */
    private Server fetchServerOrThrow() {
        final Server server = loadBalancer.chooseServer();
        if (server == null) {
            throw new IllegalStateException("No available servers for " + loadBalancer.getName());
        }
        return server;
    }

    @Override
    public void close() {
        delegate.close();
        loadBalancer.shutdown();
    }

    @Override
    public Configuration getConfiguration() {
        return delegate.getConfiguration();
    }

    @Override
    public Client property(String name, Object value) {
        delegate.property(name, value);
        return this;
    }

    @Override
    public Client register(Class<?> componentClass) {
        delegate.register(componentClass);
        return this;
    }

    @Override
    public Client register(Class<?> componentClass, int priority) {
        delegate.register(componentClass, priority);
        return this;
    }

    @Override
    public Client register(Class<?> componentClass, Class<?>... contracts) {
        delegate.register(componentClass, contracts);
        return this;
    }

    @Override
    public Client register(Class<?> componentClass, Map<Class<?>, Integer> contracts) {
        delegate.register(componentClass, contracts);
        return this;
    }

    @Override
    public Client register(Object component) {
        delegate.register(component);
        return this;
    }

    @Override
    public Client register(Object component, int priority) {
        delegate.register(component, priority);
        return this;
    }

    @Override
    public Client register(Object component, Class<?>... contracts) {
        delegate.register(component, contracts);
        return this;
    }

    @Override
    public Client register(Object component, Map<Class<?>, Integer> contracts) {
        delegate.register(component, contracts);
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if there are no available servers
     */
    @Override
    public WebTarget target(String uri) {
        final Server server = fetchServerOrThrow();
        final UriBuilder builder = UriBuilder.fromUri(uri);
        builder.scheme(server.getScheme());
        builder.host(server.getHost());
        builder.port(server.getPort());
        return delegate.target(builder);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if there are no available servers
     */
    @Override
    public WebTarget target(URI uri) {
        final Server server = fetchServerOrThrow();
        final UriBuilder builder = UriBuilder.fromUri(uri);
        builder.scheme(server.getScheme());
        builder.host(server.getHost());
        builder.port(server.getPort());
        return delegate.target(builder);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if there are no available servers
     */
    @Override
    public WebTarget target(UriBuilder uriBuilder) {
        final Server server = fetchServerOrThrow();
        uriBuilder.scheme(server.getScheme());
        uriBuilder.host(server.getHost());
        uriBuilder.port(server.getPort());
        return delegate.target(uriBuilder);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if there are no available servers
     */
    @Override
    public WebTarget target(Link link) {
        final Server server = fetchServerOrThrow();
        final UriBuilder builder = UriBuilder.fromLink(link);
        builder.scheme(server.getScheme());
        builder.host(server.getHost());
        builder.port(server.getPort());
        return delegate.target(builder);
    }

    @Override
    public Builder invocation(Link link) {
        return delegate.invocation(link);
    }

    @Override
    public SSLContext getSslContext() {
        return delegate.getSslContext();
    }

    @Override
    public HostnameVerifier getHostnameVerifier() {
        return delegate.getHostnameVerifier();
    }
}
