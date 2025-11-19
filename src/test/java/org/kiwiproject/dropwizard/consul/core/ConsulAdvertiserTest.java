package org.kiwiproject.dropwizard.consul.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.dropwizard.core.setup.Environment;
import io.dropwizard.jetty.MutableServletContextHandler;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.collect.KiwiLists;
import org.kiwiproject.consul.AgentClient;
import org.kiwiproject.consul.Consul;
import org.kiwiproject.consul.ConsulException;
import org.kiwiproject.consul.model.agent.ImmutableRegCheck;
import org.kiwiproject.consul.model.agent.ImmutableRegistration;
import org.kiwiproject.dropwizard.consul.ConsulFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Stream;

class ConsulAdvertiserTest {

    private static final String SERVICE_ID = "test";
    private static final String SERVICE_NAME = "test-service";
    private static final String SECOND_SUBNET_IP = "192.168.2.99";
    private static final String FIRST_SUBNET_IP = "192.168.1.53";
    private static final String THIRD_SUBNET_IP = "192.168.3.32";
    private static final String DEFAULT_HEALTH_CHECK_PATH = "healthcheck";

    private final Consul consul = mock(Consul.class);
    private final AgentClient agent = mock(AgentClient.class);
    private final Environment environment = mock(Environment.class);
    private final MutableServletContextHandler handler = mock(MutableServletContextHandler.class);
    private final Supplier<String> supplierMock = mock();
    private ConsulAdvertiser advertiser;
    private ConsulFactory factory;
    private String healthCheckUrl;

    @BeforeEach
    void setUp() {
        when(consul.agentClient()).thenReturn(agent);
        when(environment.getAdminContext()).thenReturn(handler);
        when(handler.getContextPath()).thenReturn("admin");
        when(supplierMock.get()).thenReturn(null);
        factory = new ConsulFactory();
        factory.setServiceName(SERVICE_NAME);
        factory.setServiceSubnet("192.168.2.0/24");
        factory.setServiceAddressSupplier(supplierMock);
        factory.setHealthCheckPath(DEFAULT_HEALTH_CHECK_PATH);
        advertiser = new ConsulAdvertiser(environment, factory, consul, SERVICE_ID);
        healthCheckUrl = "http://127.0.0.1:8081/admin/" + DEFAULT_HEALTH_CHECK_PATH;
    }

    @Test
    void testGetServiceId() {
        assertThat(advertiser.getServiceId()).isEqualTo(SERVICE_ID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void registerShouldThrowIllegalStateException_WhenServiceNameIsBlank(String serviceName) {
        factory.setServiceName(serviceName);
        assertThatIllegalStateException()
            .isThrownBy(() -> advertiser.register("http", 8080, "http", 8081))
            .withMessage("serviceName must not be blank; make sure it is set (e.g., in ConsulFactory) before calling register");
    }

    @Test
    void testRegister() {
        when(agent.isRegistered(SERVICE_ID)).thenReturn(false);
        registerAndEnsureRegistered(advertiser);

        var registration = ImmutableRegistration.builder()
            .port(8080)
            .check(
                ImmutableRegCheck.builder()
                    .http(healthCheckUrl)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name(SERVICE_NAME)
            .meta(standardMetaForHttp())
            .id(SERVICE_ID)
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
        advertiser = new ConsulAdvertiser(environment, factory, consul, SERVICE_ID);

        when(agent.isRegistered(SERVICE_ID)).thenReturn(false);
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
            .meta(standardMetaForHttp())
            .id(SERVICE_ID)
            .build();

        verify(agent).register(registration);
    }

    @Test
    void testRegisterWithSubnet() {
        when(agent.isRegistered(SERVICE_ID)).thenReturn(false);
        advertiser.register(
            "http", 8080, "http", 8081, List.of(FIRST_SUBNET_IP, SECOND_SUBNET_IP, THIRD_SUBNET_IP));

        var healthCheckUrlWithCorrectSubnet = "http://192.168.2.99:8081/admin/" + DEFAULT_HEALTH_CHECK_PATH;
        var registration = ImmutableRegistration.builder()
            .port(8080)
            .check(
                ImmutableRegCheck.builder()
                    .http(healthCheckUrlWithCorrectSubnet)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name(SERVICE_NAME)
            .address(SECOND_SUBNET_IP)
            .meta(standardMetaForHttp())
            .id(SERVICE_ID)
            .build();

        verify(agent).register(registration);
    }

    @Test
    void testRegisterWithSubnetNoEligibleIps() {
        when(agent.isRegistered(SERVICE_ID)).thenReturn(false);
        advertiser.register(
            "http", 8080, "http", 8081, List.of(FIRST_SUBNET_IP, "192.168.7.23", THIRD_SUBNET_IP));

        var registration = ImmutableRegistration.builder()
            .port(8080)
            .check(
                ImmutableRegCheck.builder()
                    .http(healthCheckUrl)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name(SERVICE_NAME)
            .meta(standardMetaForHttp())
            .id(SERVICE_ID)
            .build();

        verify(agent).register(registration);
    }

    @Test
    void testRegisterWithSupplier() {
        when(agent.isRegistered(SERVICE_ID)).thenReturn(false);
        when(supplierMock.get()).thenReturn("192.168.8.99");
        advertiser.register(
            "http", 8080, "http", 8081, List.of(FIRST_SUBNET_IP, "192.168.7.23", THIRD_SUBNET_IP));

        var healthCheckUrlWithCorrectSubnet = "http://192.168.8.99:8081/admin/healthcheck";
        var registration = ImmutableRegistration.builder()
            .port(8080)
            .check(
                ImmutableRegCheck.builder()
                    .http(healthCheckUrlWithCorrectSubnet)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name(SERVICE_NAME)
            .meta(standardMetaForHttp())
            .address("192.168.8.99")
            .id(SERVICE_ID)
            .build();

        verify(agent).register(registration);
    }

    @Test
    void testRegisterWithSupplierException() {
        when(agent.isRegistered(SERVICE_ID)).thenReturn(false);
        when(supplierMock.get()).thenThrow(new IllegalArgumentException());
        advertiser.register(
            "http", 8080, "http", 8081, List.of(FIRST_SUBNET_IP, "192.168.7.23", THIRD_SUBNET_IP));

        var registration = ImmutableRegistration.builder()
            .port(8080)
            .check(
                ImmutableRegCheck.builder()
                    .http(healthCheckUrl)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name(SERVICE_NAME)
            .meta(standardMetaForHttp())
            .id(SERVICE_ID)
            .build();

        verify(agent).register(registration);
    }

    @Test
    void testRegisterWithHttps() {
        when(agent.isRegistered(SERVICE_ID)).thenReturn(false);
        advertiser.register("https", 8080, "https", 8081);

        var httpsHealthCheckUrl = "https://127.0.0.1:8081/admin/healthcheck";
        var registration = ImmutableRegistration.builder()
            .port(8080)
            .check(
                ImmutableRegCheck.builder()
                    .http(httpsHealthCheckUrl)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name(SERVICE_NAME)
            .meta(standardMetaForScheme("https"))
            .id(SERVICE_ID)
            .build();

        verify(agent).register(registration);
    }

    @Test
    void testRegisterAlreadyRegistered() {
        when(agent.isRegistered(anyString())).thenReturn(true);
        var didRegister = register(advertiser);
        assertThat(didRegister).isFalse();

        verify(agent, only()).isRegistered(SERVICE_ID);
    }

    @Test
    void testHostFromConfig() {
        factory.setServicePort(8888);
        factory.setServiceAddress("127.0.0.1");

        when(agent.isRegistered(anyString())).thenReturn(false);
        advertiser = new ConsulAdvertiser(environment, factory, consul, SERVICE_ID);
        registerAndEnsureRegistered(advertiser);

        var registration = ImmutableRegistration.builder()
            .id(SERVICE_ID)
            .port(8888)
            .address("127.0.0.1")
            .check(
                ImmutableRegCheck.builder()
                    .http(healthCheckUrl)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name(SERVICE_NAME)
            .meta(standardMetaForHttp())
            .build();

        verify(agent).register(registration);
    }

    @Test
    void testTagsFromConfig() {
        var tags = List.of("test", "second-test");
        factory.setTags(tags);

        when(agent.isRegistered(SERVICE_ID)).thenReturn(false);
        advertiser = new ConsulAdvertiser(environment, factory, consul, SERVICE_ID);
        registerAndEnsureRegistered(advertiser);

        var registration = ImmutableRegistration.builder()
            .tags(tags)
            .check(
                ImmutableRegCheck.builder()
                    .http(healthCheckUrl)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name(SERVICE_NAME)
            .meta(standardMetaForHttp())
            .port(8080)
            .id(SERVICE_ID)
            .build();

        verify(agent).register(registration);
    }

    @Test
    void testAclTokenFromConfig() {
        var aclToken = "acl-token";
        factory.setAclToken(aclToken);

        when(agent.isRegistered(SERVICE_ID)).thenReturn(false);
        advertiser = new ConsulAdvertiser(environment, factory, consul, SERVICE_ID);
        registerAndEnsureRegistered(advertiser);

        var registration = ImmutableRegistration.builder()
            .id(SERVICE_ID)
            .check(
                ImmutableRegCheck.builder()
                    .http(healthCheckUrl)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name(SERVICE_NAME)
            .port(8080)
            .meta(standardMetaForHttp())
            .id(SERVICE_ID)
            .build();

        verify(agent).register(registration);
    }

    @Test
    void testServiceMetaFromConfig() {
        var serviceMeta = Map.of("meta1-key", "meta1-value", "meta2-key", "meta2-value");
        factory.setServiceMeta(serviceMeta);

        when(agent.isRegistered(SERVICE_ID)).thenReturn(false);
        advertiser = new ConsulAdvertiser(environment, factory, consul, SERVICE_ID);
        registerAndEnsureRegistered(advertiser);

        var registration = ImmutableRegistration.builder()
            .meta(serviceMeta)
            .putMeta("scheme", "http")
            .putMeta("applicationScheme", "http")
            .putMeta("adminScheme", "http")
            .check(
                ImmutableRegCheck.builder()
                    .http(healthCheckUrl)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name(SERVICE_NAME)
            .port(8080)
            .id(SERVICE_ID)
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
        advertiser = new ConsulAdvertiser(environment, factory, consul, SERVICE_ID);
        registerAndEnsureRegistered(advertiser);

        var registration = ImmutableRegistration.builder()
            .id(SERVICE_ID)
            .port(8888)
            .address("127.0.0.1")
            .check(
                ImmutableRegCheck.builder()
                    .http(configuredHealthCheckUrl)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .build())
            .name(SERVICE_NAME)
            .meta(standardMetaForHttp())
            .build();

        verify(agent).register(registration);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testRegisterWithSkipTlsVerifyOnHealthCheck(boolean tlsSkipVerify) {
        factory.setHealthCheckSkipTlsVerify(tlsSkipVerify);
        advertiser = new ConsulAdvertiser(environment, factory, consul, SERVICE_ID);

        when(agent.isRegistered(SERVICE_ID)).thenReturn(false);
        registerAndEnsureRegistered(advertiser);

        var registration = ImmutableRegistration.builder()
            .port(8080)
            .check(
                ImmutableRegCheck.builder()
                    .http(healthCheckUrl)
                    .interval("1s")
                    .deregisterCriticalServiceAfter("1m")
                    .tlsSkipVerify(tlsSkipVerify)
                    .build())
            .name(SERVICE_NAME)
            .meta(standardMetaForHttp())
            .id(SERVICE_ID)
            .build();

        verify(agent).register(registration);
    }

    private static Map<String, String> standardMetaForHttp() {
        return standardMetaForScheme("http");
    }

    private static Map<String, String> standardMetaForScheme(String scheme) {
        return Map.of(
            "scheme", scheme,
            "applicationScheme", scheme,
            "adminScheme", scheme
        );
    }

    private static void registerAndEnsureRegistered(ConsulAdvertiser advertiser) {
        var didRegister = register(advertiser);
        assertThat(didRegister).isTrue();
    }

    private static boolean register(ConsulAdvertiser advertiser) {
        return advertiser.register("http", 8080, "http", 8081);
    }

    @Test
    void testDeregister() {
        when(agent.isRegistered(SERVICE_ID)).thenReturn(true);
        advertiser.deregister();
        verify(agent).deregister(SERVICE_ID);
    }

    @Test
    void testDeregisterNotRegistered() {
        when(agent.isRegistered(SERVICE_ID)).thenReturn(false);
        advertiser.deregister();
        verify(agent, only()).isRegistered(SERVICE_ID);
    }

    @Test
    void testDeregisterException() {
        when(agent.isRegistered(anyString())).thenReturn(true);
        doThrow(new ConsulException("error")).when(agent).deregister(anyString());
        advertiser.deregister();
        verify(agent).deregister(SERVICE_ID);
    }

    // This exists to test all paths in getServiceAddress
    @Nested
    class InternalGetServiceAddress {

        @Test
        void shouldReturnEmptyOptional_WhenNoServiceAddress_OrSubnet_OrSupplier() {
            factory = new ConsulFactory();
            advertiser = new ConsulAdvertiser(environment, factory, consul, SERVICE_ID);

            var serviceAddressOpt = advertiser.getServiceAddress(List.of());

            assertThat(serviceAddressOpt).isEmpty();
        }

        @Test
        void shouldReturnServiceAddress_WhenServiceAddressIsConfigured() {
            factory = new ConsulFactory();
            factory.setServiceAddress("test.acme.com");
            factory.setServiceSubnet("192.168.2.0/24");
            factory.setServiceAddressSupplier(() -> "192.168.2.10");
            advertiser = new ConsulAdvertiser(environment, factory, consul, SERVICE_ID);

            var serviceAddressOpt = advertiser.getServiceAddress(List.of());

            assertThat(serviceAddressOpt).contains("test.acme.com");
        }

        @Test
        void shouldReturnValueFromServiceAddressSupplier_WhenNoServiceAddress_OrSubnet() {
            factory = new ConsulFactory();
            factory.setServiceAddressSupplier(() -> "test.acme.com");
            advertiser = new ConsulAdvertiser(environment, factory, consul, SERVICE_ID);

            var serviceAddressOpt = advertiser.getServiceAddress(List.of());

            assertThat(serviceAddressOpt).contains("test.acme.com");
        }

        @ParameterizedTest
        @NullAndEmptySource
        void shouldReturnValueFromServiceAddressSupplier_WhenHostsIsNullOrEmpty(Collection<String> hosts) {
            factory = new ConsulFactory();
            factory.setServiceAddressSupplier(() -> "test.acme.com");
            advertiser = new ConsulAdvertiser(environment, factory, consul, SERVICE_ID);

            var serviceAddressOpt = advertiser.getServiceAddress(hosts);

            assertThat(serviceAddressOpt).contains("test.acme.com");
        }

        @Test
        void shouldReturnValueFromServiceAddressSupplier_WhenHostsNotEmpty_ButNoSubnet() {
            factory = new ConsulFactory();
            factory.setServiceAddressSupplier(() -> "test.acme.com");
            advertiser = new ConsulAdvertiser(environment, factory, consul, SERVICE_ID);

            var serviceAddressOpt = advertiser.getServiceAddress(List.of("192.168.2.10"));

            assertThat(serviceAddressOpt).contains("test.acme.com");
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "test.acme.com",
            "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
            "192.168.2.128",
            "192.168.2.142",
        })
        void shouldReturnValueFromServiceAddressSupplier_WhenHostsNotEmpty_ButHostNotIpOrInSubnet(String host) {
            factory = new ConsulFactory();
            factory.setServiceSubnet("192.168.2.0/26");
            factory.setServiceAddressSupplier(() -> "192.168.2.10");
            advertiser = new ConsulAdvertiser(environment, factory, consul, SERVICE_ID);

            var serviceAddressOpt = advertiser.getServiceAddress(List.of(host));

            assertThat(serviceAddressOpt).contains("192.168.2.10");
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "192.168.2.1",
            "192.168.2.42",
            "192.168.2.62"
        })
        void shouldReturnFirstHostInTheSubnet(String host) {
            factory = new ConsulFactory();
            factory.setServiceSubnet("192.168.2.0/26");
            factory.setServiceAddressSupplier(() -> "test.acme.com");
            advertiser = new ConsulAdvertiser(environment, factory, consul, SERVICE_ID);

            var serviceAddressOpt = advertiser.getServiceAddress(List.of(host));

            assertThat(serviceAddressOpt).contains(host);
        }

        @Test
        void shouldReturnEmptyOptional_WhenServiceAddressSupplierThrows() {
            factory = new ConsulFactory();
            when(supplierMock.get()).thenThrow(new RuntimeException("boom"));
            factory.setServiceAddressSupplier(supplierMock);
            advertiser = new ConsulAdvertiser(environment, factory, consul, SERVICE_ID);

            var serviceAddressOpt = advertiser.getServiceAddress(List.of());

            assertThat(serviceAddressOpt).isEmpty();
        }
    }

    // This exists mainly to test the non-IP code path that should never happen
    @Nested
    class InternalFindFirstEligibleIpBySubnet {

        private static final String SUBNET = "192.168.2.0/28";
        private static final SubnetInfo SUBNET_INFO = new SubnetUtils(SUBNET).getInfo();

        @ParameterizedTest
        @MethodSource("subnetIps")
        void shouldReturnFirstIpInSubnet_WhenInSubnet(String ip) {
            var hosts = List.of(
                "10.116.178.92",
                "10.116.178.93",
                ip,
                "192.168.3.67"
            );
            var ipOpt = ConsulAdvertiser.findFirstEligibleIpBySubnet(hosts, SUBNET);

            assertThat(ipOpt).contains(ip);
        }

        @Test
        void shouldReturnEmptyOptional_WhenNoIpsAreInSubnet() {
            var hosts = List.of(
                "10.116.178.92",
                "10.116.178.93",
                "10.116.178.94"
            );
            var ipOpt = ConsulAdvertiser.findFirstEligibleIpBySubnet(hosts, SUBNET);

            assertThat(ipOpt).isEmpty();
        }

        @ParameterizedTest
        @MethodSource("threeRandomSubnetIps")
        void shouldIgnoreNonIpAddresses(String ip) {
            var hosts = List.of(
                "test.acme.com",
                "localhost",
                ip
            );
            var ipOpt = ConsulAdvertiser.findFirstEligibleIpBySubnet(hosts, SUBNET);

            assertThat(ipOpt).contains(ip);
        }

        static Stream<Arguments> subnetIps() {
            return SUBNET_INFO.streamAddressStrings().map(Arguments::of);
        }

        static Stream<Arguments> threeRandomSubnetIps() {
            return KiwiLists.shuffledListOf(SUBNET_INFO.getAllAddresses())
                .stream()
                .map(Arguments::of)
                .limit(3);
        }
    }

    @Nested
    class InternalIsInRangeSafe {

        @ParameterizedTest
        @ValueSource(strings = {
            "localhost",
            "test.acme.com",
            "2001:0db8:85a3:0000:0000:8a2e:0370:7334"
        })
        void shouldReturnFalse_ForNonIpv4Addresses(String host) {
            var subnetInfo = new SubnetUtils("192.168.100.0/26").getInfo();

            assertThat(ConsulAdvertiser.isInRangeSafe(host, subnetInfo)).isFalse();
        }
    }

    @Nested
    class GetHealthCheckUrl {

        @Test
        void shouldUseProvidedServiceAddress() {
            factory.setAdminPort(62999);
            factory.setHealthCheckPath("health-check");

            advertiser = new ConsulAdvertiser(environment, factory, consul, SERVICE_ID);

            var url = advertiser.getHealthCheckUrl("https", "10.116.42.84");

            assertThat(url).isEqualTo("https://10.116.42.84:62999/admin/health-check");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = { " ", "\t" })
        void shouldFallBackToLoopbackAddress_WithoutServiceAddress(String serviceAddress) {
            factory.setAdminPort(61424);
            factory.setHealthCheckPath("get-health");

            advertiser = new ConsulAdvertiser(environment, factory, consul, SERVICE_ID);

            var url = advertiser.getHealthCheckUrl("https", serviceAddress);

            assertThat(url).isEqualTo("https://127.0.0.1:61424/admin/get-health");
        }

        @SuppressWarnings("removal")
        @Test
        void shouldUseProvidedServiceAddress_WhenUsingDeprecatedMethod() {
            factory.setAdminPort(9999);
            factory.setHealthCheckPath("health");
            factory.setServiceAddress("10.116.42.84");

            advertiser = new ConsulAdvertiser(environment, factory, consul, SERVICE_ID);

            var url = advertiser.getHealthCheckUrl("https", Set.of());

            assertThat(url).isEqualTo("https://10.116.42.84:9999/admin/health");
        }

        @SuppressWarnings("removal")
        @Test
        void shouldFallBackToLoopbackAddress_WhenUsingDeprecatedMethod_AndNoServiceAddressIsProvided() {
            factory.setAdminPort(9999);
            factory.setHealthCheckPath("is-healthy");

            advertiser = new ConsulAdvertiser(environment, factory, consul, SERVICE_ID);

            var url = advertiser.getHealthCheckUrl("https", Set.of());

            assertThat(url).isEqualTo("https://127.0.0.1:9999/admin/is-healthy");
        }
    }

    @Nested
    class RegisterMethodsUsingSameScheme {

        @ParameterizedTest
        @ValueSource(strings = { "http", "https" })
        void shouldPassSchemeAsApplicationAndAdminScheme_ToRegisterMethodWithAdminScheme(String scheme) {
            var advertiserSpy = spy(advertiser);
            doReturn(true)
                .when(advertiserSpy)
                .register(anyString(), anyInt(), anyString(), anyInt());

            var applicationPort = ThreadLocalRandom.current().nextInt(61000, 62000);
            var adminPort = applicationPort + 1;

            var registered = advertiserSpy.register(scheme, applicationPort, adminPort);

            assertThat(registered).isTrue();

            verify(advertiserSpy).register(scheme, applicationPort, scheme, adminPort);
        }

        @ParameterizedTest
        @ValueSource(strings = { "http", "https" })
        void shouldPassSchemeAsApplicationAndAdminScheme_ToRegisterWithAdminScheme_AndHosts(String scheme) {
            var advertiserSpy = spy(advertiser);
            doReturn(true)
                .when(advertiserSpy)
                .register(anyString(), anyInt(), anyString(), anyInt(), anyCollection());

            var applicationPort = ThreadLocalRandom.current().nextInt(9100, 9200);
            var adminPort = applicationPort + 1;
             var host = "server.example.com";

            var registered = advertiserSpy.register(scheme, applicationPort, adminPort, Set.of(host));

            assertThat(registered).isTrue();

            verify(advertiserSpy).register(scheme, applicationPort, adminPort, Set.of(host));
        }
    }
}
