package org.kiwiproject.dropwizard.consul;

import io.dropwizard.core.Configuration;

@FunctionalInterface
public interface ConsulConfiguration<C extends Configuration> {
    ConsulFactory getConsulFactory(C configuration);
}
