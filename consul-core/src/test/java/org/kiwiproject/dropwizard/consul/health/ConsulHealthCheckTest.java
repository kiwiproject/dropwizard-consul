package org.kiwiproject.dropwizard.consul.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codahale.metrics.health.HealthCheck.Result;
import com.orbitz.consul.AgentClient;
import com.orbitz.consul.Consul;
import com.orbitz.consul.ConsulException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsulHealthCheckTest {

    private final Consul consul = mock(Consul.class);
    private final AgentClient agent = mock(AgentClient.class);
    private final ConsulHealthCheck healthCheck = new ConsulHealthCheck(consul);

    @BeforeEach
    public void setUp() {
        when(consul.agentClient()).thenReturn(agent);
    }

    @Test
    public void testCheckHealthy() throws Exception {
        final Result actual = healthCheck.check();
        verify(agent).ping();
        assertThat(actual.isHealthy()).isTrue();
    }

    @Test
    public void testCheckUnhealthy() throws Exception {
        doThrow(new ConsulException("error")).when(agent).ping();
        final Result actual = healthCheck.check();
        verify(agent).ping();
        assertThat(actual.isHealthy()).isFalse();
    }
}
