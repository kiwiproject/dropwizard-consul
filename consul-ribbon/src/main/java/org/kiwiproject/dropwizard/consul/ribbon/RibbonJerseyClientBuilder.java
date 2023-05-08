package org.kiwiproject.dropwizard.consul.ribbon;

import static java.util.Objects.requireNonNull;

import com.google.common.primitives.Ints;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.WeightedResponseTimeRule;
import com.netflix.loadbalancer.ZoneAwareLoadBalancer;
import com.orbitz.consul.Consul;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;

import javax.ws.rs.client.Client;

public class RibbonJerseyClientBuilder {

    private final Environment environment;
    private final Consul consul;
    private final RibbonJerseyClientConfiguration configuration;

    /**
     * Constructor
     *
     * @param environment   Dropwizard environment
     * @param consul        Consul client
     * @param configuration Load balancer Configuration
     */
    public RibbonJerseyClientBuilder(
        final Environment environment,
        final Consul consul,
        final RibbonJerseyClientConfiguration configuration) {
        this.environment = requireNonNull(environment);
        this.consul = requireNonNull(consul);
        this.configuration = requireNonNull(configuration);
    }

    /**
     * Builds a new {@link RibbonJerseyClient} using service discovery by health
     *
     * @param name Service name
     * @return new RibbonJerseyClient
     */
    public RibbonJerseyClient build(final String name) {
        return build(name, new HealthyConsulServiceDiscoverer(name));
    }

    /**
     * Builds a new {@link RibbonJerseyClient} using the provided service discoverer
     *
     * @param name              Jersey client name
     * @param serviceDiscoverer Service discoverer
     * @return new RibbonJerseyClient
     */
    public RibbonJerseyClient build(
        final String name, final ConsulServiceDiscoverer serviceDiscoverer) {

        // create a new Jersey client
        var jerseyClient = new JerseyClientBuilder(environment).using(configuration).build(name);

        return build(name, jerseyClient, serviceDiscoverer);
    }

    /**
     * Builds a new {@link RibbonJerseyClient} using service discovery by health
     *
     * @param name         Service name
     * @param jerseyClient Jersey Client
     * @return new {@link RibbonJerseyClient}
     */
    public RibbonJerseyClient build(final String name, final Client jerseyClient) {
        return build(name, jerseyClient, new HealthyConsulServiceDiscoverer(name));
    }

    /**
     * Builds a new {@link RibbonJerseyClient} with an existing Jersey Client and service discoverer
     *
     * @param name              Client name
     * @param jerseyClient      Jersey Client
     * @param serviceDiscoverer Service discoverer
     * @return new RibbonJerseyClient
     */
    public RibbonJerseyClient build(
        final String name,
        final Client jerseyClient,
        final ConsulServiceDiscoverer serviceDiscoverer) {

        // dynamic server list that is refreshed from Consul
        var serverList = new ConsulServerList(consul, serviceDiscoverer);

        // build a new load balancer based on the configuration
        var clientConfig = new DefaultClientConfigImpl();
        clientConfig.set(CommonClientConfigKey.AppName, name);
        clientConfig.set(
            CommonClientConfigKey.ServerListRefreshInterval,
            Ints.checkedCast(configuration.getRefreshInterval().toMilliseconds()));

        ZoneAwareLoadBalancer<Server> loadBalancer =
            LoadBalancerBuilder.newBuilder()
                .withClientConfig(clientConfig)
                .withRule(new WeightedResponseTimeRule())
                .withDynamicServerList(serverList)
                .buildDynamicServerListLoadBalancer();

        var client = new RibbonJerseyClient(loadBalancer, jerseyClient);

        environment
            .lifecycle()
            .manage(
                new Managed() {
                    @Override
                    public void start() {
                        // nothing to start
                    }

                    @Override
                    public void stop() {
                        client.close();
                    }
                });
        return client;
    }
}
