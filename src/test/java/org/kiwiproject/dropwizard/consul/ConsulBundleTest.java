package org.kiwiproject.dropwizard.consul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.orbitz.consul.config.CacheConfig;
import com.orbitz.consul.config.ClientConfig;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
    public void setUp() {
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

        @Test
        void shouldNotAllowConsulExceptionToEscape_WhenUnableToConnecttoConsul() {
            var bootstrap = mock(Bootstrap.class);
            assertThatCode(() -> bundle.initialize(bootstrap)).doesNotThrowAnyException();

            assertThat(bundle.didAttemptInitialize()).isTrue();
            assertThat(bundle.didInitializeSucceed()).isFalse();

            verifyNoInteractions(bootstrap);  // should throw before trying to set up the ConfigurationSourceProvider
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
        assertThat(factory.getClientConfig()).hasValueSatisfying(theClientConfig -> {
            assertThat(theClientConfig.getCacheConfig().getWatchDuration().toSeconds()).isEqualTo(20L);
        });
    }

    @Test
    void testAclToken() {
        var token = "acl-token";
        factory.setAclToken(token);
        bundle.run(config, environment);
        assertThat(factory.getAclToken()).contains(token);
    }
}
