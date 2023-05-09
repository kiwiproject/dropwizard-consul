package com.example.helloworld;

import com.example.helloworld.resources.HelloWorldResource;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.kiwiproject.dropwizard.consul.ConsulBundle;
import org.kiwiproject.dropwizard.consul.ConsulFactory;
import org.kiwiproject.dropwizard.consul.ribbon.RibbonJerseyClientBuilder;

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
            new ConsulBundle<>(getName(), false, true) {
                @Override
                public ConsulFactory getConsulFactory(HelloWorldConfiguration configuration) {
                    return configuration.getConsulFactory();
                }
            });
    }

    @Override
    public void run(HelloWorldConfiguration configuration, Environment environment) {
        var consul = configuration.getConsulFactory().build();
        var clientConfig = configuration.getClientConfig();
        var loadBalancingClient = new RibbonJerseyClientBuilder(environment, consul, clientConfig).build("hello-world");

        var resource = new HelloWorldResource(
            consul,
            loadBalancingClient,
            configuration.getTemplate(),
            configuration.getDefaultName());
        environment.jersey().register(resource);
    }
}
