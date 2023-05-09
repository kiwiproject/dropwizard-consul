package org.kiwiproject.dropwizard.consul.task;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Strings;
import com.orbitz.consul.Consul;
import io.dropwizard.servlets.tasks.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

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

        String reason;
        if (parameters.containsKey("reason")) {
            reason = Strings.nullToEmpty(parameters.get("reason").get(0));
        } else {
            reason = "";
        }

        var enable = Boolean.parseBoolean(parameters.get("enable").get(0));
        if (enable) {
            if (isNullOrEmpty(reason)) {
                LOG.warn("Enabling maintenance mode for service {} (no reason given)", serviceId);
            } else {
                LOG.warn("Enabling maintenance mode for service {} (reason: {})", serviceId, reason);
            }
        } else {
            LOG.warn("Disabling maintenance mode for service {}", serviceId);
        }

        consul.agentClient().toggleMaintenanceMode(serviceId, enable, reason);

        output.println("OK");
        output.flush();
    }
}
