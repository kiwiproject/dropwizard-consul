package com.example.helloworld;

import com.example.helloworld.resources.HelloWorldResource;
import com.orbitz.consul.Consul;

import org.kiwiproject.dropwizard.consul.ConsulBundle;
import org.kiwiproject.dropwizard.consul.ConsulFactory;
import org.smoketurner.dropwizard.consul.ribbon.RibbonJerseyClient;
import org.smoketurner.dropwizard.consul.ribbon.RibbonJerseyClientBuilder;

import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class HelloWorldApplication extends Application<HelloWorldConfiguration> {

  public static void main(String[] args) throws Exception {
    new HelloWorldApplication().run(args);
  }

  @Override
  public String getName() {
    return "hello-world";
  }

  @Override
  public void initialize(Bootstrap<HelloWorldConfiguration> bootstrap) {

    bootstrap.setConfigurationSourceProvider(
        new SubstitutingSourceProvider(
            bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));

    bootstrap.addBundle(
        new ConsulBundle<HelloWorldConfiguration>(getName(), false, true) {
          @Override
          public ConsulFactory getConsulFactory(HelloWorldConfiguration configuration) {
            return configuration.getConsulFactory();
          }
        });
  }

  @Override
  public void run(HelloWorldConfiguration configuration, Environment environment) throws Exception {
    final Consul consul = configuration.getConsulFactory().build();
    final RibbonJerseyClient loadBalancingClient =
        new RibbonJerseyClientBuilder(environment, consul, configuration.getClient())
            .build("hello-world");

    final HelloWorldResource resource =
        new HelloWorldResource(
            consul,
            loadBalancingClient,
            configuration.getTemplate(),
            configuration.getDefaultName());
    environment.jersey().register(resource);
  }
}
