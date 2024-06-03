package org.kiwiproject.dropwizard.consul.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.dropwizard.core.setup.Environment;
import io.dropwizard.jetty.MutableServletContextHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.AgentClient;
import org.kiwiproject.consul.Consul;
import org.kiwiproject.consul.ConsulException;
import org.kiwiproject.consul.model.agent.ImmutableRegCheck;
import org.kiwiproject.consul.model.agent.ImmutableRegistration;
import org.kiwiproject.dropwizard.consul.ConsulFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

class ConsulAdvertiserTest {

    private static final String SECOND_SUBNET_IP = "192.168.2.99";
    private static final String FIRST_SUBNET_IP = "192.168.1.53";
    private static final String THIRD_SUBNET_IP = "192.168.3.32";
    private static final String DEFAULT_HEALTH_CHECK_PATH = "healthcheck";

    private final Consul consul = mock(Consul.class);
    private final AgentClient agent = mock(AgentClient.class);
    private final Environment environment = mock(Environment.class);
    private final MutableServletContextHandler handler = mock(MutableServletContextHandler.class);
    @SuppressWarnings("unchecked") private final Supplier<String> supplierMock = mock(Supplier.class);
    private final String serviceId = "test";
    private ConsulAdvertiser advertiser;
    private ConsulFactory factory;
    private String healthCheckUrl;

    @BeforeEach
    public void setUp() {
        when(consul.agentClient()).thenReturn(agent);
        when(environment.getAdminContext()).thenReturn(handler);
        when(handler.getContextPath()).thenReturn("admin");
        when(supplierMock.get()).thenReturn(null);
        factory = new ConsulFactory();
        factory.setServiceName("test");
        factory.setServiceSubnet("192.168.2.0/24");
        factory.setServiceAddressSupplier(supplierMock);
        factory.setHealthCheckPath(DEFAULT_HEALTH_CHECK_PATH);
        advertiser = new ConsulAdvertiser(environment, factory, consul, serviceId);
        healthCheckUrl = "http://127.0.0.1:8081/admin/" + DEFAULT_HEALTH_CHECK_PATH;
    }

    @Test
    void testGetServiceId() {
        assertThat(advertiser.getServiceId()).isEqualTo(serviceId);
    }

    @Test
    void testRegister() {
        when(agent.isRegistered(serviceId)).thenReturn(false);
        registerAndEnsureRegistered(advertiser);

        var registration = ImmutableRegistration.builder()
            .port(8080)
            .check(
                ImmutableRegCheck.builder()
                    .http(healthCheckUrl)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name("test")
            .meta(Map.of("scheme", "http"))
            .id(serviceId)
            .build();

        verify(agent).register(registration);
    }

    /**
     * Added to verify that NullPointerException is not thrown when a healthCheckPath is not specified
     * on ConsulFactory.
     */
    @Test
    void testRegisterWhenHealthCheckPathNotSpecifiedOnFactory() {
        factory = new ConsulFactory();
        factory.setServiceName("test");
        factory.setServiceSubnet("192.168.2.0/24");
        factory.setServiceAddressSupplier(supplierMock);
        advertiser = new ConsulAdvertiser(environment, factory, consul, serviceId);

        when(agent.isRegistered(serviceId)).thenReturn(false);
        registerAndEnsureRegistered(advertiser);

        var registration = ImmutableRegistration.builder()
            .port(8080)
            .check(
                ImmutableRegCheck.builder()
                    .http(healthCheckUrl)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name("test")
            .meta(Map.of("scheme", "http"))
            .id(serviceId)
            .build();

        verify(agent).register(registration);
    }

    @Test
    void testRegisterWithSubnet() {
        when(agent.isRegistered(serviceId)).thenReturn(false);
        advertiser.register(
            "http", 8080, 8081, Arrays.asList(FIRST_SUBNET_IP, SECOND_SUBNET_IP, THIRD_SUBNET_IP));

        var healthCheckUrlWithCorrectSubnet = "http://192.168.2.99:8081/admin/" + DEFAULT_HEALTH_CHECK_PATH;
        var registration = ImmutableRegistration.builder()
            .port(8080)
            .check(
                ImmutableRegCheck.builder()
                    .http(healthCheckUrlWithCorrectSubnet)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name("test")
            .address(SECOND_SUBNET_IP)
            .meta(Map.of("scheme", "http"))
            .id(serviceId)
            .build();

        verify(agent).register(registration);
    }

    @Test
    void testRegisterWithSubnetNoEligibleIps() {
        when(agent.isRegistered(serviceId)).thenReturn(false);
        advertiser.register(
            "http", 8080, 8081, Arrays.asList(FIRST_SUBNET_IP, "192.168.7.23", THIRD_SUBNET_IP));

        var registration = ImmutableRegistration.builder()
            .port(8080)
            .check(
                ImmutableRegCheck.builder()
                    .http(healthCheckUrl)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name("test")
            .meta(Map.of("scheme", "http"))
            .id(serviceId)
            .build();

        verify(agent).register(registration);
    }

    @Test
    void testRegisterWithSupplier() {
        when(agent.isRegistered(serviceId)).thenReturn(false);
        when(supplierMock.get()).thenReturn("192.168.8.99");
        advertiser.register(
            "http", 8080, 8081, Arrays.asList(FIRST_SUBNET_IP, "192.168.7.23", THIRD_SUBNET_IP));

        var healthCheckUrlWithCorrectSubnet = "http://192.168.8.99:8081/admin/healthcheck";
        var registration = ImmutableRegistration.builder()
            .port(8080)
            .check(
                ImmutableRegCheck.builder()
                    .http(healthCheckUrlWithCorrectSubnet)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name("test")
            .meta(Map.of("scheme", "http"))
            .address("192.168.8.99")
            .id(serviceId)
            .build();

        verify(agent).register(registration);
    }

    @Test
    void testRegisterWithSupplierException() {
        when(agent.isRegistered(serviceId)).thenReturn(false);
        when(supplierMock.get()).thenThrow(new IllegalArgumentException());
        advertiser.register(
            "http", 8080, 8081, Arrays.asList(FIRST_SUBNET_IP, "192.168.7.23", THIRD_SUBNET_IP));

        var registration = ImmutableRegistration.builder()
            .port(8080)
            .check(
                ImmutableRegCheck.builder()
                    .http(healthCheckUrl)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name("test")
            .meta(Map.of("scheme", "http"))
            .id(serviceId)
            .build();

        verify(agent).register(registration);
    }

    @Test
    void testRegisterWithHttps() {
        when(agent.isRegistered(serviceId)).thenReturn(false);
        advertiser.register("https", 8080, 8081);

        var httpsHealthCheckUrl = "https://127.0.0.1:8081/admin/healthcheck";
        var registration = ImmutableRegistration.builder()
            .port(8080)
            .check(
                ImmutableRegCheck.builder()
                    .http(httpsHealthCheckUrl)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name("test")
            .meta(Map.of("scheme", "https"))
            .id(serviceId)
            .build();

        verify(agent).register(registration);
    }

    @Test
    void testRegisterAlreadyRegistered() {
        when(agent.isRegistered(anyString())).thenReturn(true);
        var didRegister = register(advertiser);
        assertThat(didRegister).isFalse();

        verify(agent, never())
            .register(anyInt(), anyString(), anyLong(), anyString(), anyString(), anyList(), anyMap());
    }

    @Test
    void testHostFromConfig() {
        factory.setServicePort(8888);
        factory.setServiceAddress("127.0.0.1");

        when(agent.isRegistered(anyString())).thenReturn(false);
        advertiser = new ConsulAdvertiser(environment, factory, consul, serviceId);
        registerAndEnsureRegistered(advertiser);

        var registration = ImmutableRegistration.builder()
            .id(serviceId)
            .port(8888)
            .address("127.0.0.1")
            .check(
                ImmutableRegCheck.builder()
                    .http(healthCheckUrl)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name("test")
            .meta(Map.of("scheme", "http"))
            .build();

        verify(agent).register(registration);
    }

    @Test
    void testTagsFromConfig() {
        var tags = List.of("test", "second-test");
        factory.setTags(tags);

        when(agent.isRegistered(serviceId)).thenReturn(false);
        advertiser = new ConsulAdvertiser(environment, factory, consul, serviceId);
        registerAndEnsureRegistered(advertiser);

        var registration = ImmutableRegistration.builder()
            .tags(tags)
            .check(
                ImmutableRegCheck.builder()
                    .http(healthCheckUrl)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name("test")
            .meta(Map.of("scheme", "http"))
            .port(8080)
            .id(serviceId)
            .build();

        verify(agent).register(registration);
    }

    @Test
    void testAclTokenFromConfig() {
        var aclToken = "acl-token";
        factory.setAclToken(aclToken);

        when(agent.isRegistered(serviceId)).thenReturn(false);
        advertiser = new ConsulAdvertiser(environment, factory, consul, serviceId);
        registerAndEnsureRegistered(advertiser);

        var registration = ImmutableRegistration.builder()
            .id(serviceId)
            .check(
                ImmutableRegCheck.builder()
                    .http(healthCheckUrl)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name("test")
            .port(8080)
            .meta(Map.of("scheme", "http"))
            .id(serviceId)
            .build();

        verify(agent).register(registration);
    }

    @Test
    void testServiceMetaFromConfig() {
        var serviceMeta = Map.of("meta1-key", "meta1-value", "meta2-key", "meta2-value");
        factory.setServiceMeta(serviceMeta);

        when(agent.isRegistered(serviceId)).thenReturn(false);
        advertiser = new ConsulAdvertiser(environment, factory, consul, serviceId);
        registerAndEnsureRegistered(advertiser);

        var registration = ImmutableRegistration.builder()
            .meta(serviceMeta)
            .putMeta("scheme", "http")
            .check(
                ImmutableRegCheck.builder()
                    .http(healthCheckUrl)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name("test")
            .port(8080)
                .id(serviceId)
                .build();

        verify(agent).register(registration);
    }

    @Test
    void testHealthCheckUrlFromConfig() {
        factory.setServicePort(8888);
        factory.setServiceAddress("127.0.0.1");
        factory.setHealthCheckPath("ping");
        var configuredHealthCheckUrl = "http://127.0.0.1:8081/admin/ping";

        when(agent.isRegistered(anyString())).thenReturn(false);
        advertiser = new ConsulAdvertiser(environment, factory, consul, serviceId);
        registerAndEnsureRegistered(advertiser);

        var registration = ImmutableRegistration.builder()
            .id(serviceId)
            .port(8888)
            .address("127.0.0.1")
            .check(
                ImmutableRegCheck.builder()
                    .http(configuredHealthCheckUrl)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name("test")
                .meta(Map.of("scheme", "http"))
                .build();

        verify(agent).register(registration);
    }

    private static void registerAndEnsureRegistered(ConsulAdvertiser advertiser) {
        var didRegister = register(advertiser);
        assertThat(didRegister).isTrue();
    }

    private static boolean register(ConsulAdvertiser advertiser) {
        return advertiser.register("http", 8080, 8081);
    }

    @Test
    void testDeregister() {
        when(agent.isRegistered(serviceId)).thenReturn(true);
        advertiser.deregister();
        verify(agent).deregister(serviceId);
    }

    @Test
    void testDeregisterNotRegistered() {
        when(agent.isRegistered(serviceId)).thenReturn(false);
        advertiser.deregister();
        verify(agent, never()).deregister(serviceId);
    }

    @Test
    void testDeregisterException() {
        when(agent.isRegistered(anyString())).thenReturn(true);
        doThrow(new ConsulException("error")).when(agent).deregister(anyString());
        advertiser.deregister();
        verify(agent).deregister(anyString());
    }
}
