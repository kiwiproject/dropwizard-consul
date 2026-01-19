package org.kiwiproject.dropwizard.consul.core;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.core.setup.Environment;
import jakarta.ws.rs.core.UriBuilder;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.jspecify.annotations.Nullable;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class ConsulAdvertiser {

    private static final Logger LOG = LoggerFactory.getLogger(ConsulAdvertiser.class);
    private static final String LOCALHOST = "127.0.0.1";
    private static final String DEFAULT_HEALTH_CHECK_PATH = "healthcheck";

    // Matches four dot-separated numeric groups (e.g., 192.168.0.1), intended as a quick
    // format check, not full IPv4 validation.
    private static final String IPV4_ADDRESS = "(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})";
    private static final Pattern IPV4_ADDRESS_PATTERN = Pattern.compile(IPV4_ADDRESS);

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
    private final AtomicReference<Boolean> healthCheckSkipTlsVerify = new AtomicReference<>();

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

        configuration
            .getHealthCheckSkipTlsVerify()
            .ifPresent(newHealthCheckSkipTlsVerify -> {
                if (newHealthCheckSkipTlsVerify) {
                    LOG.warn("Certificate validation is disabled for the health check (tls_skip_verify=true)");
                } else {
                    LOG.info("Certificate validation is enabled for the health check (tls_skip_verify=false)");
                }

                healthCheckSkipTlsVerify.set(newHealthCheckSkipTlsVerify);
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

    /**
     * Register the service with Consul, where the application and admin ports
     * use the same scheme.
     *
     * @param scheme          Scheme the application and admin servers are listening on
     * @param applicationPort Port the service is listening on
     * @param adminPort       Port the admin server is listening on
     * @return true if successfully registered, otherwise false (e.g., if already registered)
     * @throws ConsulException if registration fails
     */
    public boolean register(String scheme, int applicationPort, int adminPort) {
        return register(scheme, applicationPort, scheme, adminPort);
    }

    /**
     * Register the service with Consul, where the application and admin ports
     * use the same scheme.
     *
     * @param scheme          Scheme the application and admin servers are listening on
     * @param applicationPort Port the service is listening on
     * @param adminPort       Port the admin server is listening on
     * @param hosts           Hosts that the application is listening on (can be host name or IP address);
     *                        may be null
     * @return true if successfully registered, otherwise false (e.g., if already registered)
     * @throws ConsulException if registration fails
     */
    public boolean register(String scheme,
                            int applicationPort,
                            int adminPort,
                            @Nullable Collection<String> hosts) {
        return register(scheme, applicationPort, scheme, adminPort, hosts);
    }

    /**
     * Register the service with Consul.
     *
     * @param applicationScheme Scheme the server is listening on
     * @param applicationPort   Port the service is listening on
     * @param adminScheme       Scheme the admin server is listening on
     * @param adminPort         Port the admin server is listening on
     * @return true if successfully registered, otherwise false (e.g., if already registered)
     * @throws ConsulException if registration fails
     */
    public boolean register(String applicationScheme,
                            int applicationPort,
                            String adminScheme,
                            int adminPort) {
        return register(applicationScheme, applicationPort, adminScheme, adminPort, Set.of());
    }

    /**
     * Register the service with Consul.
     *
     * @param applicationScheme Scheme the server is listening on
     * @param applicationPort   Port the service is listening on
     * @param adminScheme       Scheme the admin server is listening on
     * @param adminPort         Port the admin server is listening on
     * @param hosts             Hosts that the application is listening on (can be host name or IP address);
     *                          may be null
     * @return true if successfully registered, otherwise false (e.g., if already registered)
     * @throws ConsulException if registration fails
     */
    public boolean register(String applicationScheme,
                            int applicationPort,
                            String adminScheme,
                            int adminPort,
                            @Nullable Collection<String> hosts) {

        var serviceName = configuration.getServiceName();
        checkState(isNotBlank(serviceName),
            "serviceName must not be blank; make sure it is set (e.g., in ConsulFactory) before calling register");

        var agentClient = consul.agentClient();
        if (agentClient.isRegistered(serviceId)) {
            LOG.info("Service ({}) [{}] already registered", serviceName, serviceId);
            return false;
        }

        // If we haven't set the servicePort via the configuration file already,
        // set it from the listening applicationPort.
        servicePort.compareAndSet(null, applicationPort);
        serviceAdminPort.compareAndSet(null, adminPort);
        healthCheckPath.compareAndSet(null, DEFAULT_HEALTH_CHECK_PATH);

        var serviceAddressOpt = getServiceAddress(hosts);
        var serviceAddressOrNull = serviceAddressOpt.orElse(null);
        var healthCheckUrl = getHealthCheckUrl(adminScheme, serviceAddressOrNull);

        LOG.info(
            "Registering service {} [id: {}] with address {} on port {}" +
                " (admin port {}) with health check path '{}' (URL: {}) and interval of {}s",
            serviceName,
            serviceId,
            isNull(serviceAddressOrNull) ? "[agent-default]" : serviceAddressOrNull,
            servicePort.get(),
            serviceAdminPort.get(),
            healthCheckPath.get(),
            healthCheckUrl,
            configuration.getCheckInterval().toSeconds());

        var registrationCheck = ImmutableRegCheck.builder()
            .http(healthCheckUrl)
            .tlsSkipVerify(Optional.ofNullable(healthCheckSkipTlsVerify.get()))
            .interval(String.format("%ds", configuration.getCheckInterval().toSeconds()))
            .deregisterCriticalServiceAfter(
                String.format("%dm", configuration.getDeregisterInterval().toMinutes()))
            .build();

        var registrationBuilder = ImmutableRegistration.builder()
            .name(serviceName)
            .port(servicePort.get())
            .check(registrationCheck)
            .id(serviceId);

        // If we have set the serviceAddress, add it to the registration.
        serviceAddressOpt.ifPresent(registrationBuilder::address);

        // If we have tags, add them to the registration.
        if (nonNull(tags.get())) {
            registrationBuilder.tags(tags.get());
        }

        // If we have service meta, add them to the registration.
        if (nonNull(serviceMeta.get())) {
            registrationBuilder.meta(serviceMeta.get());
        }

        registrationBuilder.putMeta("scheme", applicationScheme);
        registrationBuilder.putMeta("applicationScheme", applicationScheme);
        registrationBuilder.putMeta("adminScheme", adminScheme);

        agentClient.register(registrationBuilder.build());
        return true;
    }

    /**
     * Returns the service address from best provided options. The order of precedence is as follows:
     * serviceAddress, if provided, then the subnet resolution, lastly the supplier. If none of the
     * above is provided or matched, Optional.empty() is returned.
     * <p>
     * Note that subnet resolution only takes place for hosts that are IP addresses.
     *
     * @param hosts the List of hosts the application is listening on (host names or IPs); may be null
     * @return Optional of the host to register as the service address or empty otherwise
     */
    @VisibleForTesting
    Optional<String> getServiceAddress(@Nullable Collection<String> hosts) {
        var address = serviceAddress.get();
        if (nonNull(address)) {
            return Optional.of(address);
        }

        var subnet = serviceSubnet.get();
        if (nonNull(hosts) && !hosts.isEmpty() && nonNull(subnet)) {
            Optional<String> ip = findFirstEligibleIpBySubnet(hosts, subnet);
            if (ip.isPresent()) {
                return ip;
            }
        }

        var addressSupplier = serviceAddressSupplier.get();
        if (nonNull(addressSupplier)) {
            try {
                return Optional.ofNullable(addressSupplier.get());
            } catch (Exception ex) {
                LOG.debug("Service address supplier threw an exception.", ex);
            }
        }

        return Optional.empty();
    }

    /**
     * Returns the service address from the list of hosts. It iterates through the list and finds the
     * first host that matched the subnet. If none is found, an empty Optional is returned.
     * <p>
     * Note that this method can return a value when a host is an IP (v4) address.
     *
     * @param hosts the List of hosts the application is listening on.
     * @return Optional of the host to register as the service address or empty otherwise
     */
    @VisibleForTesting
    static Optional<String> findFirstEligibleIpBySubnet(Collection<String> hosts, String subnet) {
        var subnetUtils = new SubnetUtils(subnet);
        var subnetInfo = subnetUtils.getInfo();
        return hosts.stream()
            .filter(IPV4_ADDRESS_PATTERN.asPredicate())
            .filter(host -> isInRangeSafe(host, subnetInfo))
            .findFirst();
    }

    @VisibleForTesting
    static boolean isInRangeSafe(String host, SubnetInfo subnetInfo) {
        try {
            return subnetInfo.isInRange(host);
        } catch (Exception e) {
            LOG.debug("Ignoring {} caught on non-IPv4 host '{}': {}",
                e.getClass().getName(), host, e.getMessage());
            return false;
        }
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
     * Return the health check URL for the service.
     *
     * @param scheme         the scheme to use for the health check URL
     * @param serviceAddress the service address, or null
     * @return health check URL
     * @apiNote This method is called by this class in {@link #register(String, int, String, int, Collection)}.
     * Overriding it in a subclass will therefore <strong>have no effect</strong> if you are using
     * {@link org.kiwiproject.dropwizard.consul.ConsulBundle ConsulBundle} for registration, since at present
     * there is no way to tell {@code ConsulBundle} to use a specific {@link ConsulAdvertiser} subclass.
     * In a future release, we may address this differently, for example, by providing a pluggable
     * mechanism to build the health check URL. If you must change the way the health check URL
     * is built, the only option currently is to subclass this class, and not use {@code ConsulBundle}
     * but rather create your own bundle. One way would be to copy {@code ConsulBundle}, modify the
     * code to instantiate the custom {@link ConsulAdvertiser} subclass, and then add the custom
     * bundle to your {@link io.dropwizard.core.Application Application}.
     */
    protected String getHealthCheckUrl(String scheme, @Nullable String serviceAddress) {
        var uriBuilder = UriBuilder.fromPath(environment.getAdminContext().getContextPath())
            .path(healthCheckPath.get())
            .scheme(scheme)
            .host(isNotBlank(serviceAddress) ? serviceAddress : LOCALHOST)
            .port(serviceAdminPort.get());
        return uriBuilder.build().toString();
    }
}
