package org.kiwiproject.dropwizard.consul.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitz.consul.AgentClient;
import com.orbitz.consul.Consul;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

@DisplayName("MaintenanceTask")
class MaintenanceTaskTest {

    private MaintenanceTask task;
    private AgentClient agentClient;
    private StringWriter stringWriter;
    private PrintWriter writer;

    @BeforeEach
    void setUp() {
        agentClient = mock(AgentClient.class);

        var consul = mock(Consul.class);
        when(consul.agentClient()).thenReturn(agentClient);

        stringWriter = new StringWriter();
        writer = new PrintWriter(stringWriter);

        task = new MaintenanceTask(consul, "test-service");
    }

    @Test
    void shouldThrowIllegalArgumentException_WhenParametersDoesNotContain_EnableKey() {
        var parameters = Map.<String, List<String>>of();

        assertThatIllegalArgumentException()
            .isThrownBy(() -> task.execute(parameters, writer))
            .withMessage("Parameter \"enable\" not found");

        assertThat(stringWriter).asString().isEmpty();
    }

    @Test
    void shouldEnableMaintenanceMode_WithReason() {
        var parameters = Map.of(
            "enable", List.of("true"),
            "reason", List.of("Need to do some maintenance!")
        );

        task.execute(parameters, writer);

        verify(agentClient).toggleMaintenanceMode("test-service", true, "Need to do some maintenance!");

        assertTaskOutput();
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "True", "TRUE", "TruE" })
    void shouldEnableMaintenanceMode_WithoutAnyGivenReason(String value) {
        var parameters = Map.of(
            "enable", List.of(value)
        );

        task.execute(parameters, writer);

        verify(agentClient).toggleMaintenanceMode("test-service", true, "");

        assertTaskOutput();
    }

    /**
     * @implNote only "true" (case-insensitive) results in true; everything else is false
     */
    @ParameterizedTest
    @ValueSource(strings = { "false", "False", "foo", "bar", "yes", "t" })
    void shouldDisableMaintenanceMode(String value) {
        var parameters = Map.of(
            "enable", List.of(value)
        );

        task.execute(parameters, writer);

        verify(agentClient).toggleMaintenanceMode("test-service", false, "");

        assertTaskOutput();
    }

    @Test
    void shouldDisableMaintenanceMode_WithReason() {
        var parameters = Map.of(
            "enable", List.of("false"),
            "reason", List.of("Maintenance is complete")
        );

        task.execute(parameters, writer);

        verify(agentClient).toggleMaintenanceMode("test-service", false, "Maintenance is complete");

        assertTaskOutput();
    }

    private void assertTaskOutput() {
        assertThat(stringWriter).asString().isEqualToIgnoringNewLines("OK");
    }
}
