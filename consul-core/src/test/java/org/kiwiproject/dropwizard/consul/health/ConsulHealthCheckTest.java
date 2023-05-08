package org.kiwiproject.dropwizard.consul.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void testCheckHealthy() {
        var result = healthCheck.check();
        verify(agent).ping();
        assertThat(result.isHealthy()).isTrue();
    }

    @Test
    void testCheckUnhealthy() {
        doThrow(new ConsulException("error")).when(agent).ping();
        var result = healthCheck.check();
        verify(agent).ping();
        assertThat(result.isHealthy()).isFalse();
    }
}
