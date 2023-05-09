package com.example.helloworld;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import org.kiwiproject.dropwizard.consul.ConsulFactory;
import org.kiwiproject.dropwizard.consul.ribbon.RibbonJerseyClientConfiguration;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class HelloWorldConfiguration extends Configuration {

    @NotEmpty private String template = "Hello, %s!";

    @NotEmpty private String defaultName = "Stranger";

    @NotNull
    @Valid
    public final ConsulFactory consul = new ConsulFactory();

    @NotNull
    @Valid
    public final RibbonJerseyClientConfiguration clientConfig = new RibbonJerseyClientConfiguration();

    @JsonProperty
    public String getTemplate() {
        return template;
    }

    @JsonProperty
    public void setTemplate(String template) {
        this.template = template;
    }

    @JsonProperty
    public String getDefaultName() {
        return defaultName;
    }

    @JsonProperty
    public void setDefaultName(String name) {
        this.defaultName = name;
    }

    @JsonProperty
    public ConsulFactory getConsulFactory() {
        return consul;
    }

    @JsonProperty
    public RibbonJerseyClientConfiguration getClientConfig() {
        return clientConfig;
    }
}
