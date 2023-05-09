package org.kiwiproject.dropwizard.consul.ribbon;

import static java.util.Objects.requireNonNull;

import com.orbitz.consul.Consul;
import com.orbitz.consul.model.health.ServiceHealth;

import java.util.Collection;

public class HealthyConsulServiceDiscoverer implements ConsulServiceDiscoverer {

    private final String service;

    /**
     * Constructor
     *
     * @param service Service name
     */
    public HealthyConsulServiceDiscoverer(String service) {
        this.service = requireNonNull(service);
    }

    @Override
    public Collection<ServiceHealth> discover(Consul consul) {
        return consul.healthClient().getHealthyServiceInstances(service).getResponse();
    }
}
