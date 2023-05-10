package org.kiwiproject.dropwizard.consul;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.HostAndPort;
import com.orbitz.consul.Consul;
import com.orbitz.consul.ConsulException;
import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;
import org.kiwiproject.dropwizard.consul.config.ConsulSubstitutor;
import org.kiwiproject.dropwizard.consul.core.ConsulAdvertiser;
import org.kiwiproject.dropwizard.consul.core.ConsulServiceListener;
import org.kiwiproject.dropwizard.consul.health.ConsulHealthCheck;
import org.kiwiproject.dropwizard.consul.managed.ConsulAdvertiserManager;
import org.kiwiproject.dropwizard.consul.task.MaintenanceTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Replace variables with values from Consul KV. By default, this only works with a Consul agent
 * running on localhost:8500 (the default) as there's no way to configure Consul in the initialize
 * methods. You may override {@link #getConsulAgentHost()} and {@link #getConsulAgentPort()} to
 * provide other defaults.
 *
 * @param <C> The configuration class for your Dropwizard Application.
 */
public abstract class ConsulBundle<C extends Configuration>
    implements ConfiguredBundle<C>, ConsulConfiguration<C> {

    private static final Logger LOG = LoggerFactory.getLogger(ConsulBundle.class);
    private static final String CONSUL_AUTH_HEADER_KEY = "X-Consul-Token";

    private final String defaultServiceName;
    private final boolean strict;
    private final boolean substitutionInVariables;

    /**
     * Constructor
     *
     * @param name Service Name
     */
    @SuppressWarnings("java:S5993")
    public ConsulBundle(String name) {
        this(name, false);
    }

    /**
     * @param name   Service Name
     * @param strict If true, the application fails fast if a key cannot be found in Consul KV
     */
    @SuppressWarnings("java:S5993")
    public ConsulBundle(String name, boolean strict) {
        this(name, strict, false);
    }

    /**
     * @param name                    Service Name
     * @param strict                  If true, the application fails fast if a key cannot be found in Consul KV
     * @param substitutionInVariables If true, substitution will be done within variable names.
     */
    @SuppressWarnings("java:S5993")
    public ConsulBundle(String name, boolean strict, boolean substitutionInVariables) {
        this.defaultServiceName = requireNonNull(name);
        this.strict = strict;
        this.substitutionInVariables = substitutionInVariables;
    }

    @Override
    public void initialize(Bootstrap<?> bootstrap) {
        // Replace variables with values from Consul KV. Please override
        // getConsulAgentHost() and getConsulAgentPort() if Consul is not
        // listening on the default localhost:8500.
        try {
            LOG.debug("Connecting to Consul at {}:{}", getConsulAgentHost(), getConsulAgentPort());

            var consulBuilder = Consul.builder()
                    .withHostAndPort(HostAndPort.fromParts(getConsulAgentHost(), getConsulAgentPort()));

            getConsulAclToken()
                .ifPresent(
                    token -> {
                        // setting both ACL token here and with header, supplying an
                        // auth header. This should cover both use cases: endpoint
                        // supports legacy ?token query param and other case
                        // in which endpoint requires an X-Consul-Token header.
                        // @see https://www.consul.io/api/index.html#acls

                        LOG.debug("Using Consul ACL token: {}", token);

                        consulBuilder
                            .withAclToken(token)
                            .withHeaders(Map.of(CONSUL_AUTH_HEADER_KEY, token));
                    });

            // using Consul as a configuration substitution provider
            bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(
                    bootstrap.getConfigurationSourceProvider(),
                    new ConsulSubstitutor(consulBuilder.build(), strict, substitutionInVariables)));

        } catch (ConsulException e) {
            LOG.warn(
                "Unable to query Consul running on {}:{}," + " disabling configuration substitution",
                getConsulAgentHost(),
                getConsulAgentPort(),
                e);
        }
    }

    @Override
    public void run(C configuration, Environment environment) {
        var consulFactory = getConsulFactory(configuration);
        if (consulFactory.isEnabled()) {
            runEnabled(consulFactory, environment);
        } else {
            LOG.warn("Consul bundle disabled.");
        }
    }

    protected void runEnabled(ConsulFactory consulFactory, Environment environment) {
        if (isNullOrEmpty(consulFactory.getServiceName())) {
            consulFactory.setServiceName(defaultServiceName);
        }
        setupEnvironment(consulFactory, environment);
    }

    protected void setupEnvironment(ConsulFactory consulFactory, Environment environment) {

        var consul = consulFactory.build();
        var serviceId = consulFactory.getServiceId().orElseGet(() -> UUID.randomUUID().toString());
        var advertiser = new ConsulAdvertiser(environment, consulFactory, consul, serviceId);

        Optional<Duration> retryInterval = consulFactory.getRetryInterval();
        Optional<ScheduledExecutorService> scheduler =
            retryInterval.map(i -> Executors.newScheduledThreadPool(1));

        // Register a Jetty listener to get the listening host and port
        environment
            .lifecycle()
            .addServerLifecycleListener(
                new ConsulServiceListener(advertiser, retryInterval, scheduler));

        // Register a ping healthcheck to the Consul agent
        environment.healthChecks().register("consul", new ConsulHealthCheck(consul));

        // Register a shutdown manager to deregister the service
        environment.lifecycle().manage(new ConsulAdvertiserManager(advertiser, scheduler));

        // Add an administrative task to toggle maintenance mode
        environment.admin().addTask(new MaintenanceTask(consul, serviceId));
    }

    /**
     * Override as necessary to provide an alternative Consul Agent Host. This is only required if
     * using Consul KV for configuration variable substitution.
     *
     * @return By default, "localhost"
     */
    @VisibleForTesting
    public String getConsulAgentHost() {
        return Consul.DEFAULT_HTTP_HOST;
    }

    /**
     * Override as necessary to provide an alternative Consul Agent Port. This is only required if
     * using Consul KV for configuration variable substitution.
     *
     * @return By default, 8500
     */
    @VisibleForTesting
    public int getConsulAgentPort() {
        return Consul.DEFAULT_HTTP_PORT;
    }

    /**
     * Override as necessary to provide an alternative ACL Token. This is only required if using
     * Consul KV for configuration variable substitution.
     *
     * @return By default, empty string (no ACL support)
     */
    @VisibleForTesting
    public Optional<String> getConsulAclToken() {
        return Optional.empty();
    }
}