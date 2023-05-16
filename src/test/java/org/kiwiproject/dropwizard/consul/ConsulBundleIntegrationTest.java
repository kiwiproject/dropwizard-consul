package org.kiwiproject.dropwizard.consul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kiwiproject.dropwizard.consul.config.ConsulSubstitutor;
import org.mockito.ArgumentCaptor;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.dropwizard.Configuration;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.FileConfigurationSourceProvider;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;

@Testcontainers
public class ConsulBundleIntegrationTest {

    @Container
    public static final ConsulContainer CONSUL = ConsulTestcontainers.newConsulContainer();

    static class TestConfiguration extends Configuration implements ConsulConfiguration<TestConfiguration> {
        @Override
        public ConsulFactory getConsulFactory(TestConfiguration configuration) {
            return new ConsulFactory();
        }
    }

    static class TestConsulBundle extends ConsulBundle<TestConfiguration> {

        private final String consulAgentHost;
        private final int consulAgentPort;

        TestConsulBundle(String consulAgentHost, int consulAgentPort) {
            super("testApp");
            this.consulAgentHost = consulAgentHost;
            this.consulAgentPort = consulAgentPort;
        }

        @Override
        public ConsulFactory getConsulFactory(TestConfiguration configuration) {
            return configuration.getConsulFactory(configuration);
        }

        @Override
        public String getConsulAgentHost() {
            return consulAgentHost;
        }

        @Override
        public int getConsulAgentPort() {
            return consulAgentPort;
        }
    }

    private static String consulHost;
    private static int consulPort;

    @BeforeAll
    static void beforeAll() {
        var consulHostAndPort = ConsulTestcontainers.consulHostAndPort(CONSUL);
        consulHost = consulHostAndPort.getHost();
        consulPort = consulHostAndPort.getPort();
    }

    @Test
    void shouldInitializeBundle() {
        var consulBundle = new TestConsulBundle(consulHost, consulPort);

        var bootstrap = mock(Bootstrap.class);
        when(bootstrap.getConfigurationSourceProvider()).thenReturn(new FileConfigurationSourceProvider());

        consulBundle.initialize(bootstrap);

        assertThat(consulBundle.didAttemptInitialize()).isTrue();
        assertThat(consulBundle.didInitializeSucceed()).isTrue();
    }

    @Test
    void shouldInitializeBundle_WithConsulSubstitutor() {
        var consulBundle = new TestConsulBundle(consulHost, consulPort);

        var bootstrap = mock(Bootstrap.class);
        when(bootstrap.getConfigurationSourceProvider()).thenReturn(new FileConfigurationSourceProvider());

        consulBundle.initialize(bootstrap);

        var configSourceProviderCaptor = ArgumentCaptor.forClass(ConfigurationSourceProvider.class);
        verify(bootstrap).setConfigurationSourceProvider(configSourceProviderCaptor.capture());

        var configSourceProvider = configSourceProviderCaptor.getValue();
        assertThat(configSourceProvider).isInstanceOf(SubstitutingSourceProvider.class);

        var subSourceProvider = (SubstitutingSourceProvider) configSourceProvider;

        var substitutor = getFieldValue(subSourceProvider, "substitutor");
        assertThat(substitutor).isInstanceOf(ConsulSubstitutor.class);
    }

    private static Object getFieldValue(Object target, String fieldName) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to get field " + fieldName, e);
        }
    }
}
