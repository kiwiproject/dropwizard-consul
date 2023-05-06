package org.kiwiproject.dropwizard.consul.ribbon;

import com.orbitz.consul.Consul;
import com.orbitz.consul.model.health.ServiceHealth;

import java.util.Collection;
import java.util.Objects;

public class HealthyConsulServiceDiscoverer implements ConsulServiceDiscoverer {

    private final String service;

    /**
     * Constructor
     *
     * @param service Service name
     */
    public HealthyConsulServiceDiscoverer(final String service) {
        this.service = Objects.requireNonNull(service);
    }

    @Override
    public Collection<ServiceHealth> discover(final Consul consul) {
        return consul.healthClient().getHealthyServiceInstances(service).getResponse();
    }
}
