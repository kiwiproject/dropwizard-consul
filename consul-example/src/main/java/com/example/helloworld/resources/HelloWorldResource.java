package com.example.helloworld.resources;

import com.codahale.metrics.annotation.Timed;
import com.example.helloworld.api.Saying;
import com.netflix.loadbalancer.Server;
import com.orbitz.consul.Consul;
import com.orbitz.consul.model.health.ServiceHealth;
import org.kiwiproject.dropwizard.consul.ribbon.RibbonJerseyClient;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class HelloWorldResource {
    private final Consul consul;
    private final RibbonJerseyClient client;
    private final String template;
    private final String defaultName;
    private final AtomicLong counter;

    public HelloWorldResource(Consul consul, RibbonJerseyClient client, String template, String defaultName) {
        this.consul = consul;
        this.client = client;
        this.template = template;
        this.defaultName = defaultName;
        this.counter = new AtomicLong();
    }

    @GET
    @Timed
    @Path("/hello-world")
    public Saying sayHello(@QueryParam("name") Optional<String> name) {
        var value = String.format(template, name.orElse(defaultName));
        return new Saying(counter.incrementAndGet(), value);
    }

    @GET
    @Timed
    @Path("/consul/{service}")
    public List<ServiceHealth> getHealthyServiceInstances(@PathParam("service") String service) {
        return consul.healthClient().getHealthyServiceInstances(service).getResponse();
    }

    @GET
    @Timed
    @Path("/available")
    public List<Server> getAvailableServers() {
        return client.getAvailableServers();
    }
}
