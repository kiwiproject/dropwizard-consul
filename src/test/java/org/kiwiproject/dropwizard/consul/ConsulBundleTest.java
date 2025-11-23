package org.kiwiproject.dropwizard.consul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.ConsulException;
import org.kiwiproject.consul.config.CacheConfig;
import org.kiwiproject.consul.config.ClientConfig;
import org.kiwiproject.net.LocalPortChecker;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

class ConsulBundleTest {

    private final ConsulFactory factory = spy(new ConsulFactory());
    private final Environment environment = mock(Environment.class);
    private final TestConfiguration config = new TestConfiguration();
    private ConsulBundle<TestConfiguration> bundle;

    class TestConfiguration extends Configuration implements ConsulConfiguration<TestConfiguration> {
        @Override
        public ConsulFactory getConsulFactory(TestConfiguration configuration) {
            return factory;
        }
    }

    @BeforeEach
    void setUp() {
        bundle =
            spy(
                new ConsulBundle<TestConfiguration>("test") {
                    @Override
                    public ConsulFactory getConsulFactory(TestConfiguration c) {
                        return c.getConsulFactory(c);
                    }
                });

        doNothing().when(bundle).setupEnvironment(factory, environment);
    }

    @Nested
    class Initialize {

        @Test
        void shouldReportWhenInitializationHasNotYetOccurred() {
            assertThat(bundle.didAttemptInitialize()).isFalse();
        }

        @Test
        void shouldReportInitializationSuccessAsFalse_WhenInitializationHasNotBeenAttemptedYet() {
            assertThat(bundle.didInitializeSucceed()).isFalse();
        }

        // As of consul-client 1.9.0, the Consul.Builder doesn't ping the
        // Consul agent, so we should not see ConsulException anymore. Despite
        // that, I kept this test but renamed it, since there should not be
        // a ConsulException anymore.
        @Test
        void shouldNotFail_WhenUnableToConnectToConsul() {
            var bootstrap = mock(Bootstrap.class);
            when(bootstrap.getConfigurationSourceProvider()).thenReturn(mock(ConfigurationSourceProvider.class));

            var openPort = new LocalPortChecker().findRandomOpenPort().orElseThrow();
            doReturn(openPort).when(bundle).getConsulAgentPort();
            assertThatCode(() -> bundle.initialize(bootstrap)).doesNotThrowAnyException();

            assertThat(bundle.didAttemptInitialize()).isTrue();
            assertThat(bundle.didInitializeSucceed()).isTrue();

            verify(bootstrap).getConfigurationSourceProvider();
            verify(bootstrap).setConfigurationSourceProvider(any());
            verifyNoMoreInteractions(bootstrap);
        }

        // This test is to validate the original behavior to not allow
        // ConsulException to "escape" if thrown. With the consul-client
        // 1.9.0 change that no longer eagerly pings Consul, this should
        // not happen anymore. Regardless, I added this test to validate
        // the original behavior. That is the reason for introducing the
        // buildConsulClient method, i.e., I needed to be able to cause
        // the Consul.Builder#build method to throw a ConsulException.
        @Test
        void shouldNotAllowConsulExceptionToEscape_IfConsulExceptionThrown() {
            var bootstrap = mock(Bootstrap.class);

            doThrow(new ConsulException("unexpected error")).when(bundle).buildConsulClient(any());

            assertThatCode(() -> bundle.initialize(bootstrap)).doesNotThrowAnyException();

            assertThat(bundle.didAttemptInitialize()).isTrue();
            assertThat(bundle.didInitializeSucceed()).isFalse();

            verify(bundle).buildConsulClient(any());
            verifyNoInteractions(bootstrap);
        }
    }

    @Test
    void testDefaultsToEnabled() {
        assertThat(factory.isEnabled()).isTrue();
    }

    @Test
    void testEnabled() {
        doReturn(true).when(factory).isEnabled();
        bundle.run(config, environment);
        verify(bundle, times(1)).setupEnvironment(factory, environment);
    }

    @Test
    void testNotEnabled() {
        doReturn(false).when(factory).isEnabled();
        bundle.run(config, environment);
        verify(bundle, times(0)).setupEnvironment(factory, environment);
    }

    @Test
    void testMissingServiceName() {
        factory.setServiceName(null);
        bundle.run(config, environment);
        assertThat(factory.getServiceName()).isEqualTo("test");
    }

    @Test
    void testPopulatedServiceName() {
        factory.setServiceName("test-service-name");
        bundle.run(config, environment);
        assertThat(factory.getServiceName()).isEqualTo("test-service-name");
    }

    @Test
    void shouldSetConsulClientConfigurationProperties() {
        var cacheConfig = CacheConfig.builder().withWatchDuration(Duration.of(20, ChronoUnit.SECONDS)).build();
        var clientConfig = new ClientConfig(cacheConfig);

        factory.setNetworkWriteTimeoutMillis(5_000L);
        factory.setNetworkReadTimeoutMillis(10_005L);
        factory.setClientConfig(clientConfig);

        bundle.run(config, environment);

        assertThat(factory.getNetworkWriteTimeoutMillis()).contains(5_000L);
        assertThat(factory.getNetworkReadTimeoutMillis()).contains(10_005L);
        assertThat(factory.getClientConfig()).hasValueSatisfying(theClientConfig ->
            assertThat(theClientConfig.getCacheConfig().getWatchDuration().toSeconds()).isEqualTo(20L));
    }

    @Test
    void testAclToken() {
        var token = "acl-token";
        factory.setAclToken(token);
        bundle.run(config, environment);
        assertThat(factory.getAclToken()).contains(token);
    }
}
