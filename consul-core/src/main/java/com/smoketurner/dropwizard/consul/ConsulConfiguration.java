package com.smoketurner.dropwizard.consul;

import io.dropwizard.Configuration;

@FunctionalInterface
public interface ConsulConfiguration<C extends Configuration> {
  ConsulFactory getConsulFactory(C configuration);
}
