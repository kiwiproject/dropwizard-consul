package org.kiwiproject.dropwizard.consul.task;

import static java.util.Objects.requireNonNull;

import com.orbitz.consul.Consul;
import io.dropwizard.servlets.tasks.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MaintenanceTask extends Task {

    private static final Logger LOG = LoggerFactory.getLogger(MaintenanceTask.class);

    private final Consul consul;
    private final String serviceId;

    /**
     * Constructor
     *
     * @param consul    Consul client
     * @param serviceId Service ID to toggle maintenance mode
     */
    public MaintenanceTask(Consul consul, String serviceId) {
        super("maintenance");
        this.consul = requireNonNull(consul);
        this.serviceId = requireNonNull(serviceId);
    }

    @Override
    public void execute(Map<String, List<String>> parameters, PrintWriter output) {

        if (!parameters.containsKey("enable")) {
            throw new IllegalArgumentException("Parameter \"enable\" not found");
        }

        List<String> reasons = parameters.getOrDefault("reason", List.of());
        var reason = reasons.stream().filter(Objects::nonNull).findFirst().orElse("");

        var enable = Boolean.parseBoolean(parameters.get("enable").get(0));

        var action = enable ? "Enabling" : "Disabling";
        var reasonForLogs = reason.isEmpty() ? "none given" : reason;
        LOG.warn("{} maintenance mode for service {} (reason: {})", action, serviceId, reasonForLogs);

        consul.agentClient().toggleMaintenanceMode(serviceId, enable, reason);

        output.println("OK");
        output.flush();
    }
}
