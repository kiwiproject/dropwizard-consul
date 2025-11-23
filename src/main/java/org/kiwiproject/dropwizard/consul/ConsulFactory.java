package org.kiwiproject.dropwizard.consul;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.isNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.net.HostAndPort;
import io.dropwizard.util.Duration;
import io.dropwizard.validation.MinDuration;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.net.util.SubnetUtils;
import org.jspecify.annotations.Nullable;
import org.kiwiproject.consul.Consul;
import org.kiwiproject.consul.config.ClientConfig;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ConsulFactory {

    private static final String CONSUL_AUTH_HEADER_KEY = "X-Consul-Token";

    @NotNull
    private HostAndPort endpoint =
        HostAndPort.fromParts(Consul.DEFAULT_HTTP_HOST, Consul.DEFAULT_HTTP_PORT);
    private String serviceName;
    private boolean enabled = true;
    private String serviceId;
    private Integer servicePort;
    private Integer adminPort;
    private String serviceAddress;
    private String serviceSubnet;
    private Supplier<String> serviceAddressSupplier;
    private Iterable<String> tags;
    private String aclToken;
    private Map<String, String> serviceMeta;
    private boolean servicePing;

    @MinDuration(value = 1, unit = TimeUnit.SECONDS)
    private Duration retryInterval = Duration.seconds(1);

    @NotNull
    @MinDuration(value = 1, unit = TimeUnit.SECONDS)
    private Duration checkInterval = Duration.seconds(30);

    @NotNull
    @MinDuration(value = 1, unit = TimeUnit.MINUTES)
    private Duration deregisterInterval = Duration.minutes(1);

    private String healthCheckPath;
    private Boolean healthCheckSkipTlsVerify;
    private Long networkWriteTimeoutMillis;
    private Long networkReadTimeoutMillis;
    private ClientConfig clientConfig;

    @JsonProperty
    public boolean isEnabled() {
        return enabled;
    }

    @JsonProperty
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @JsonIgnore
    public boolean isDisabled() {
        return !isEnabled();
    }

    @JsonProperty
    public HostAndPort getEndpoint() {
        return endpoint;
    }

    @JsonProperty
    public void setEndpoint(HostAndPort endpoint) {
        this.endpoint = endpoint;
    }

    @JsonProperty
    public Optional<String> getServiceId() {
        return Optional.ofNullable(serviceId);
    }

    @JsonProperty
    public void setServiceId(@Nullable String serviceId) {
        this.serviceId = serviceId;
    }

    @Nullable
    @JsonProperty
    public String getServiceName() {
        return serviceName;
    }

    @JsonProperty
    public void setServiceName(@Nullable String serviceName) {
        this.serviceName = serviceName;
    }

    @JsonProperty
    public Optional<Iterable<String>> getTags() {
        return Optional.ofNullable(tags);
    }

    @JsonProperty
    public void setTags(@Nullable Iterable<String> tags) {
        this.tags = tags;
    }

    @JsonProperty
    public Optional<Integer> getServicePort() {
        return Optional.ofNullable(servicePort);
    }

    @JsonProperty
    public void setServicePort(@Nullable Integer servicePort) {
        this.servicePort = servicePort;
    }

    @JsonProperty
    public Optional<Integer> getAdminPort() {
        return Optional.ofNullable(adminPort);
    }

    @JsonProperty
    public void setAdminPort(@Nullable Integer adminPort) {
        this.adminPort = adminPort;
    }

    @JsonProperty
    public Optional<String> getServiceAddress() {
        return Optional.ofNullable(serviceAddress);
    }

    @JsonProperty
    public void setServiceAddress(@Nullable String serviceAddress) {
        this.serviceAddress = serviceAddress;
    }

    @JsonProperty
    public Optional<Duration> getRetryInterval() {
        return Optional.ofNullable(retryInterval);
    }

    @JsonProperty
    public void setRetryInterval(@Nullable Duration interval) {
        this.retryInterval = interval;
    }

    @JsonProperty
    public Duration getCheckInterval() {
        return checkInterval;
    }

    @JsonProperty
    public void setCheckInterval(Duration interval) {
        this.checkInterval = interval;
    }

    @JsonProperty
    public Duration getDeregisterInterval() {
        return deregisterInterval;
    }

    @JsonProperty
    public void setDeregisterInterval(Duration interval) {
        this.deregisterInterval = interval;
    }

    @JsonProperty
    public Optional<String> getAclToken() {
        return Optional.ofNullable(aclToken);
    }

    @JsonProperty
    public void setAclToken(@Nullable String aclToken) {
        this.aclToken = aclToken;
    }

    @JsonProperty
    public Optional<Map<String, String>> getServiceMeta() {
        return Optional.ofNullable(serviceMeta);
    }

    @JsonProperty
    public void setServiceMeta(@Nullable Map<String, String> serviceMeta) {
        this.serviceMeta = serviceMeta;
    }

    @JsonProperty
    public boolean isServicePing() {
        return servicePing;
    }

    @JsonProperty
    public void setServicePing(boolean servicePing) {
        this.servicePing = servicePing;
    }

    public Optional<String> getServiceSubnet() {
        return Optional.ofNullable(serviceSubnet);
    }

    public void setServiceSubnet(@Nullable String serviceSubnet) {
        checkArgument(isNull(serviceSubnet) || isValidCidrIp(serviceSubnet),
                "%s is not a valid Subnet in CIDR notation", serviceSubnet);
        this.serviceSubnet = serviceSubnet;
    }

    public void setServiceAddressSupplier(@Nullable Supplier<String> serviceAddressSupplier) {
        this.serviceAddressSupplier = serviceAddressSupplier;
    }

    public Optional<Supplier<String>> getServiceAddressSupplier() {
        return Optional.ofNullable(serviceAddressSupplier);
    }

    public Optional<String> getHealthCheckPath() {
        return Optional.ofNullable(healthCheckPath);
    }

    public void setHealthCheckPath(@Nullable String healthCheckPath) {
        this.healthCheckPath = healthCheckPath;
    }

    public Optional<Boolean> getHealthCheckSkipTlsVerify() {
        return Optional.ofNullable(healthCheckSkipTlsVerify);
    }

    public void setHealthCheckSkipTlsVerify(@Nullable Boolean tlsSkipVerify) {
        this.healthCheckSkipTlsVerify = tlsSkipVerify;
    }

    public Optional<Long> getNetworkWriteTimeoutMillis() {
        return Optional.ofNullable(networkWriteTimeoutMillis);
    }

    public void setNetworkWriteTimeoutMillis(@Nullable Long networkTimeout) {
        this.networkWriteTimeoutMillis = networkTimeout;
    }

    public Optional<Long> getNetworkReadTimeoutMillis() {
        return Optional.ofNullable(networkReadTimeoutMillis);
    }

    public void setNetworkReadTimeoutMillis(@Nullable Long networkReadTimeoutMillis) {
        this.networkReadTimeoutMillis = networkReadTimeoutMillis;
    }

    public Optional<ClientConfig> getClientConfig() {
        return Optional.ofNullable(clientConfig);
    }

    public void setClientConfig(@Nullable ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    @JsonIgnore
    public Consul build() {

        var consulBuilder = Consul.builder().withHostAndPort(endpoint).withPing(servicePing);

        // Setting the acl token here and a header, supplying an auth
        // header. This should cover both use cases: endpoint supports
        // legacy ?token query param and another case in which endpoint
        // requires an X-Consul-Token header.
        // @see https://www.consul.io/api/index.html#acls
        getAclToken().ifPresent(token ->
            consulBuilder.withAclToken(token).withHeaders(Map.of(CONSUL_AUTH_HEADER_KEY, token)));

        getNetworkWriteTimeoutMillis().ifPresent(consulBuilder::withWriteTimeoutMillis);
        getNetworkReadTimeoutMillis().ifPresent(consulBuilder::withReadTimeoutMillis);
        getClientConfig().ifPresent(consulBuilder::withClientConfiguration);

        return consulBuilder.build();
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            endpoint,
            serviceName,
            enabled,
            servicePort,
            adminPort,
            serviceAddress,
            tags,
            retryInterval,
            checkInterval,
            deregisterInterval,
            aclToken,
            serviceMeta,
            servicePing);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        var other = (ConsulFactory) obj;
        return Objects.equals(this.endpoint, other.endpoint)
            && Objects.equals(this.serviceName, other.serviceName)
            && Objects.equals(this.enabled, other.enabled)
            && Objects.equals(this.servicePort, other.servicePort)
            && Objects.equals(this.adminPort, other.adminPort)
            && Objects.equals(this.serviceAddress, other.serviceAddress)
            && Objects.equals(this.tags, other.tags)
            && Objects.equals(this.retryInterval, other.retryInterval)
            && Objects.equals(this.checkInterval, other.checkInterval)
            && Objects.equals(this.deregisterInterval, other.deregisterInterval)
            && Objects.equals(this.aclToken, other.aclToken)
            && Objects.equals(this.serviceMeta, other.serviceMeta)
            && Objects.equals(this.servicePing, other.servicePing);
    }

    private static boolean isValidCidrIp(String cidrIp) {
        boolean isValid = true;
        try {
            new SubnetUtils(cidrIp);
        } catch (IllegalArgumentException e) {
            isValid = false;
        }
        return isValid;
    }
}
