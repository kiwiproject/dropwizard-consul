package org.kiwiproject.dropwizard.consul.core;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import io.dropwizard.core.setup.Environment;
import jakarta.ws.rs.core.UriBuilder;
import org.apache.commons.net.util.SubnetUtils;
import org.kiwiproject.consul.Consul;
import org.kiwiproject.consul.ConsulException;
import org.kiwiproject.consul.model.agent.ImmutableRegCheck;
import org.kiwiproject.consul.model.agent.ImmutableRegistration;
import org.kiwiproject.dropwizard.consul.ConsulFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class ConsulAdvertiser {

    private static final Logger LOG = LoggerFactory.getLogger(ConsulAdvertiser.class);
    private static final String LOCALHOST = "127.0.0.1";
    private static final String DEFAULT_HEALTH_CHECK_PATH = "healthcheck";

    private final AtomicReference<Integer> servicePort = new AtomicReference<>();
    private final AtomicReference<Integer> serviceAdminPort = new AtomicReference<>();
    private final AtomicReference<String> serviceAddress = new AtomicReference<>();
    private final AtomicReference<String> serviceSubnet = new AtomicReference<>();
    private final AtomicReference<Supplier<String>> serviceAddressSupplier = new AtomicReference<>();
    private final AtomicReference<String> aclToken = new AtomicReference<>();
    private final AtomicReference<Iterable<String>> tags = new AtomicReference<>();
    private final AtomicReference<Map<String, String>> serviceMeta = new AtomicReference<>();
    private final Environment environment;
    private final ConsulFactory configuration;
    private final Consul consul;
    private final String serviceId;
    private final AtomicReference<String> healthCheckPath = new AtomicReference<>();

    /**
     * Constructor
     *
     * @param environment   Dropwizard environment
     * @param configuration Consul configuration
     * @param consul        Consul client
     * @param serviceId     Consul service ID
     */
    public ConsulAdvertiser(Environment environment, ConsulFactory configuration, Consul consul, String serviceId) {
        this.environment = requireNonNull(environment, "environment == null");
        this.configuration = requireNonNull(configuration, "configuration == null");
        this.consul = requireNonNull(consul, "consul == null");
        this.serviceId = requireNonNull(serviceId, "serviceId == null");

        configuration
            .getServicePort()
            .ifPresent(
                port -> {
                    LOG.info("Using \"{}\" as servicePort from configuration file", port);
                    servicePort.set(port);
                });

        configuration
            .getAdminPort()
            .ifPresent(
                port -> {
                    LOG.info("Using \"{}\" as adminPort from configuration file", port);
                    serviceAdminPort.set(port);
                });

        configuration
            .getServiceAddress()
            .ifPresent(
                address -> {
                    LOG.info("Using \"{}\" as serviceAddress from configuration file", address);
                    serviceAddress.set(address);
                });

        configuration
            .getServiceSubnet()
            .ifPresent(
                subnet -> {
                    LOG.info("Using \"{}\" as serviceSubnet from configuration file", subnet);
                    serviceSubnet.set(subnet);
                });

        configuration
            .getServiceAddressSupplier()
            .ifPresent(
                supplier -> {
                    LOG.info("Using \"{}\" as serviceSupplier from configuration file", supplier);
                    serviceAddressSupplier.set(supplier);
                });

        configuration
            .getTags()
            .ifPresent(
                newTags -> {
                    LOG.info("Using \"{}\" as tags from the configuration file", newTags);
                    tags.set(newTags);
                });

        configuration
            .getAclToken()
            .ifPresent(
                token -> {
                    LOG.info("Using ACL token from the configuration file (value intentionally not shown)");
                    aclToken.set(token);
                });

        configuration
            .getServiceMeta()
            .ifPresent(
                newServiceMeta -> {
                    LOG.info(
                        "Using \"{}\" as serviceMeta from the configuration file", newServiceMeta);
                    serviceMeta.set(newServiceMeta);
                });

        configuration
            .getHealthCheckPath()
            .ifPresent(
                newHealthCheckPath -> {
                    LOG.info(
                        "Using \"{}\" as health check path from the configuration file",
                        newHealthCheckPath);
                    healthCheckPath.set(newHealthCheckPath);
                });
    }

    /**
     * Return the Service ID
     *
     * @return service ID
     */
    public String getServiceId() {
        return serviceId;
    }

    public boolean register(String applicationScheme, int applicationPort, int adminPort) {
        return register(applicationScheme, applicationPort, adminPort, null);
    }

    /**
     * Register the service with Consul
     *
     * @param applicationScheme Scheme the server is listening on
     * @param applicationPort   Port the service is listening on
     * @param adminPort         Port the admin server is listening on
     * @param ipAddresses       IP addresses that the application is listening on
     * @return true if successfully registered, otherwise false
     * @throws ConsulException When registration fails
     */
    public boolean register(String applicationScheme,
                            int applicationPort,
                            int adminPort,
                            Collection<String> ipAddresses) {

        var agentClient = consul.agentClient();
        if (agentClient.isRegistered(serviceId)) {
            LOG.info("Service ({}) [{}] already registered", configuration.getServiceName(), serviceId);
            return false;
        }

        // If we haven't set the servicePort via the configuration file already,
        // set it from the listening applicationPort.
        servicePort.compareAndSet(null, applicationPort);
        serviceAdminPort.compareAndSet(null, adminPort);
        healthCheckPath.compareAndSet(null, DEFAULT_HEALTH_CHECK_PATH);

        LOG.info(
            "Registering service ({}) [{}] on port {} (admin port {}) with a health check at {} with interval of {}s",
            configuration.getServiceName(),
            serviceId,
            servicePort.get(),
            serviceAdminPort.get(),
            healthCheckPath.get(),
            configuration.getCheckInterval().toSeconds());

        var registrationCheck = ImmutableRegCheck.builder()
                .http(getHealthCheckUrl(applicationScheme, ipAddresses))
                .interval(String.format("%ds", configuration.getCheckInterval().toSeconds()))
                .deregisterCriticalServiceAfter(
                    String.format("%dm", configuration.getDeregisterInterval().toMinutes()))
                .build();

        var registrationBuilder = ImmutableRegistration.builder()
                .port(servicePort.get())
                .check(registrationCheck)
                .id(serviceId);

        var serviceName = configuration.getServiceName();
        if (nonNull(serviceName)) {
            registrationBuilder.name(serviceName);
        }

        // If we have set the serviceAddress, add it to the registration.
        getServiceAddress(ipAddresses).ifPresent(registrationBuilder::address);

        // If we have tags, add them to the registration.
        if (nonNull(tags.get())) {
            registrationBuilder.tags(tags.get());
        }

        // If we have service meta, add them to the registration.
        if (nonNull(serviceMeta.get())) {
            registrationBuilder.meta(serviceMeta.get());
        }

        registrationBuilder.putMeta("scheme", applicationScheme);

        agentClient.register(registrationBuilder.build());
        return true;
    }

    /**
     * Returns the service address from best provided options. The order of precedence is as follows:
     * serviceAddress, if provided, then the subnet resolution, lastly the supplier. If none of the
     * above is provided or matched, Optional.empty() is returned.
     *
     * @param ipAddresses the List of ipAddresses the application is listening on.
     * @return Optional of the host to register as the service address or empty otherwise
     */
    private Optional<String> getServiceAddress(Collection<String> ipAddresses) {
        if (nonNull(serviceAddress.get())) {
            return Optional.of(serviceAddress.get());
        }

        if (nonNull(ipAddresses) && !ipAddresses.isEmpty() && nonNull(serviceSubnet.get())) {
            Optional<String> ip = findFirstEligibleIpBySubnet(ipAddresses);
            if (ip.isPresent()) {
                return ip;
            }
        }

        if (nonNull(serviceAddressSupplier.get())) {
            try {
                return Optional.ofNullable(serviceAddressSupplier.get().get());
            } catch (Exception ex) {
                LOG.debug("Service address supplier threw an exception.", ex);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the service address from the list of hosts. It iterates through the list and finds the
     * first host that matched the subnet. If none is found, an empty Optional is returned.
     *
     * @param ipAddresses the List of ipAddresses the application is listening on.
     * @return Optional of the host to register as the service address or empty otherwise
     */
    private Optional<String> findFirstEligibleIpBySubnet(Collection<String> ipAddresses) {
        var subnetUtils = new SubnetUtils(serviceSubnet.get());
        var subNetInfo = subnetUtils.getInfo();
        return ipAddresses.stream().filter(subNetInfo::isInRange).findFirst();
    }

    /**
     * Deregister a service from Consul
     */
    public void deregister() {
        var agentClient = consul.agentClient();
        try {
            if (!agentClient.isRegistered(serviceId)) {
                LOG.info("No service registered with ID \"{}\"", serviceId);
                return;
            }
        } catch (ConsulException e) {
            LOG.error("Failed to determine if service ID \"{}\" is registered", serviceId, e);
            return;
        }

        LOG.info("Deregistering service ID \"{}\"", serviceId);

        try {
            agentClient.deregister(serviceId);
        } catch (ConsulException e) {
            LOG.error("Failed to deregister service from Consul", e);
        }
    }

    /**
     * Return the health check URL for the service
     *
     * @param applicationScheme Scheme the server is listening on
     * @param hosts             the hosts to choose from
     * @return health check URL
     */
    protected String getHealthCheckUrl(String applicationScheme, Collection<String> hosts) {
        var uriBuilder = UriBuilder.fromPath(environment.getAdminContext().getContextPath())
            .path(healthCheckPath.get())
            .scheme(applicationScheme)
            .host(getServiceAddress(hosts).orElse(LOCALHOST))
            .port(serviceAdminPort.get());
        return uriBuilder.build().toString();
    }
}
