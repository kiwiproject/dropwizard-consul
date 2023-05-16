package org.kiwiproject.dropwizard.consul;

import com.google.common.net.HostAndPort;

import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.utility.DockerImageName;

public class ConsulTestcontainers  {

    private ConsulTestcontainers() {
        // utility class
    }

    private static final int CONSUL_HTTP_PORT = 8500;

    public static ConsulContainer newConsulContainer() {
        return new ConsulContainer(consulDockerImageName());
    }

    public static DockerImageName consulDockerImageName() {
        return DockerImageName.parse("consul");
    }

    public static HostAndPort consulHostAndPort(ConsulContainer consul) {
        return HostAndPort.fromParts(consul.getHost(), consul.getMappedPort(CONSUL_HTTP_PORT));
    }
}
