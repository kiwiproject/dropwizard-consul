package org.kiwiproject.dropwizard.consul;

import static java.util.Objects.isNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
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

    @Nullable private String serviceName;

    private boolean enabled = true;
    private Optional<String> serviceId = Optional.empty();
    private Optional<Integer> servicePort = Optional.empty();
    private Optional<Integer> adminPort = Optional.empty();
    private Optional<String> serviceAddress = Optional.empty();
    private Optional<String> serviceSubnet = Optional.empty();
    private Optional<Supplier<String>> serviceAddressSupplier = Optional.empty();
    private Optional<Iterable<String>> tags = Optional.empty();
    private Optional<String> aclToken = Optional.empty();
    private Optional<Map<String, String>> serviceMeta = Optional.empty();
    private boolean servicePing = true;

    @Nullable
    @MinDuration(value = 1, unit = TimeUnit.SECONDS)
    private Duration retryInterval;

    @NotNull
    @MinDuration(value = 1, unit = TimeUnit.SECONDS)
    private Duration checkInterval = Duration.seconds(1);

    @NotNull
    @MinDuration(value = 1, unit = TimeUnit.MINUTES)
    private Duration deregisterInterval = Duration.minutes(1);

    private Optional<String> healthCheckPath = Optional.empty();

    private Optional<Long> networkWriteTimeoutMillis = Optional.empty();

    private Optional<Long> networkReadTimeoutMillis = Optional.empty();

    private Optional<ClientConfig> clientConfig = Optional.empty();

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
        return serviceId;
    }

    @JsonProperty
    public void setServiceId(@Nullable String serviceId) {
        this.serviceId = Optional.ofNullable(serviceId);
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
        return tags;
    }

    @JsonProperty
    public void setTags(Iterable<String> tags) {
        this.tags = Optional.ofNullable(tags);
    }

    @JsonProperty
    public Optional<Integer> getServicePort() {
        return servicePort;
    }

    @JsonProperty
    public void setServicePort(Integer servicePort) {
        this.servicePort = Optional.ofNullable(servicePort);
    }

    @JsonProperty
    public Optional<Integer> getAdminPort() {
        return adminPort;
    }

    @JsonProperty
    public void setAdminPort(Integer adminPort) {
        this.adminPort = Optional.ofNullable(adminPort);
    }

    @JsonProperty
    public Optional<String> getServiceAddress() {
        return serviceAddress;
    }

    @JsonProperty
    public void setServiceAddress(String serviceAddress) {
        this.serviceAddress = Optional.ofNullable(serviceAddress);
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
        return aclToken;
    }

    @JsonProperty
    public void setAclToken(@Nullable String aclToken) {
        this.aclToken = Optional.ofNullable(aclToken);
    }

    @JsonProperty
    public Optional<Map<String, String>> getServiceMeta() {
        return serviceMeta;
    }

    @JsonProperty
    public void setServiceMeta(Map<String, String> serviceMeta) {
        this.serviceMeta = Optional.ofNullable(serviceMeta);
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
        return serviceSubnet;
    }

    public void setServiceSubnet(String serviceSubnet) {
        Preconditions.checkArgument(
            isValidCidrIp(serviceSubnet), "%s is not a valid Subnet in CIDR notation", serviceSubnet);
        this.serviceSubnet = Optional.ofNullable(serviceSubnet);
    }

    public void setServiceAddressSupplier(Supplier<String> serviceAddressSupplier) {
        this.serviceAddressSupplier = Optional.ofNullable(serviceAddressSupplier);
    }

    public Optional<Supplier<String>> getServiceAddressSupplier() {
        return serviceAddressSupplier;
    }

    public Optional<String> getHealthCheckPath() {
        return healthCheckPath;
    }

    public void setHealthCheckPath(String healthCheckPath) {
        this.healthCheckPath = Optional.ofNullable(healthCheckPath);
    }

    public Optional<Long> getNetworkWriteTimeoutMillis() {
        return networkWriteTimeoutMillis;
    }

    public void setNetworkWriteTimeoutMillis(Long networkTimeout) {
        this.networkWriteTimeoutMillis = Optional.ofNullable(networkTimeout);
    }

    public Optional<Long> getNetworkReadTimeoutMillis() {
        return networkReadTimeoutMillis;
    }

    public void setNetworkReadTimeoutMillis(Long networkReadTimeoutMillis) {
        this.networkReadTimeoutMillis = Optional.ofNullable(networkReadTimeoutMillis);
    }

    public Optional<ClientConfig> getClientConfig() {
        return clientConfig;
    }

    public void setClientConfig(ClientConfig clientConfig) {
        this.clientConfig = Optional.ofNullable(clientConfig);
    }

    @JsonIgnore
    public Consul build() {

        var consulBuilder = Consul.builder().withHostAndPort(endpoint).withPing(servicePing);

        // Setting the acl token here and a header, supplying an auth
        // header. This should cover both use cases: endpoint supports
        // legacy ?token query param and another case in which endpoint
        // requires an X-Consul-Token header.
        // @see https://www.consul.io/api/index.html#acls
        aclToken.ifPresent(token ->
            consulBuilder.withAclToken(token).withHeaders(Map.of(CONSUL_AUTH_HEADER_KEY, token)));

        networkWriteTimeoutMillis.ifPresent(consulBuilder::withWriteTimeoutMillis);
        networkReadTimeoutMillis.ifPresent(consulBuilder::withReadTimeoutMillis);
        clientConfig.ifPresent(consulBuilder::withClientConfiguration);

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
