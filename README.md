Dropwizard Consul Bundle
========================

_This README is a work in progress as we transition dropwizard-consul from smoketurner to kiwiproject._

Introduction
------------

A bundle for using [Consul](https://consul.io) in Dropwizard applications. Features:

* Integrated client-side load balancer based on [Ribbon](https://github.com/netflix/ribbon)
* Dropwizard health check that monitors reachablility of Consul
* The Dropwizard service is registered as a Consul service with a Consul-side health check querying the Dropwizard [health check](https://www.dropwizard.io/en/latest/manual/core.html#health-checks)
* Ability to resolve [configuration](https://www.dropwizard.io/en/latest/manual/core.html#configuration) properties from Consul's KV store
* Admin task to toggle Consul's [maintenance](https://www.consul.io/api/agent.html#enable-maintenance-mode) mode

Background
----------

This library was imported from [smoketurner/dropwizard-consul](https://github.com/smoketurner/dropwizard-consul), which
is now a [public archive](https://docs.github.com/en/repositories/archiving-a-github-repository/archiving-repositories)
and is no longer maintained by the original author.

Since we are still using this library in our services which use Dropwizard and Consul, we decided to import the original
repository and continue maintaining it for our own use, and anyone else who might want to use it.  We make no guarantees
whatsoever about how long we will maintain it, and also plan to make our own changes such as changing the base package
name to `org.kiwiproject` to be consistent with our other libraries.

All other [kiwiproject](https://github.com/kiwiproject/) projects are MIT-licensed. However, because the original
dropwizard-consul uses the Apache 2.0 license, we are keeping the Apache 2.0 license (otherwise to switch to MIT we would
have to gain consent of all contributors, which we do not want to do).

Another thing to note is that we _imported_ this repository from the original, so that it is a "disconnected fork". We
did not want a reference to the original repository since it is a public archive and no longer maintained. Thus, while
we maintain the history that this is a fork , it is completely disconnected and is now a standalone (normal) repository.

Dependency Info
---------------
```xml
<dependency>
    <groupId>com.smoketurner.dropwizard</groupId>
    <artifactId>consul-core</artifactId>
    <version>2.0.7-1</version>
</dependency>
<dependency>
    <groupId>com.smoketurner.dropwizard</groupId>
    <artifactId>consul-ribbon</artifactId>
    <version>2.0.7-1</version>
</dependency>
```

Usage
-----
Add a `ConsulBundle` to your [Application](https://javadoc.io/doc/io.dropwizard/dropwizard-project/latest/io/dropwizard/Application.html) class.

```java
@Override
public void initialize(Bootstrap<MyConfiguration> bootstrap) {
    // ...
    bootstrap.addBundle(new ConsulBundle<MyConfiguration>(getName()) {
        @Override
        public ConsulFactory getConsulFactory(MyConfiguration configuration) {
            return configuration.getConsulFactory();
        }
    });
}
```

The bundle also includes a `ConsulSubsitutor` to retrieve configuration values from the Consul KV store. You can define settings in your YAML configuration file:

```
template: ${helloworld/template:-Hello, %s!}
defaultName: ${helloworld/defaultName:-Stranger}
```

The setting with the path `helloworld/template` will be looked up in the KV store and will be replaced in the configuration file when the application is started. You can specify a default value after the `:-`. This currently does not support dynamically updating values in a running Dropwizard application.

Configuration
-------------
For configuring the Consul connection, there is a `ConsulFactory`:

```yaml
consul:
  # Optional properties
  # endpoint for consul (defaults to localhost:8500)
  endpoint: localhost:8500
  # service port
  servicePort: 8080
  # check interval frequency
  checkInterval: 1 second
```

Example Application
-------------------
This bundle includes a modified version of the `HelloWorldApplication` from Dropwizard's [Getting Started](https://www.dropwizard.io/1.3.12/docs/getting-started.html) documentation.

You can execute this application by first starting Consul on your local machine then running:

```
mvn clean package
java -jar consul-example/target/consul-example-2.0.7-4-SNAPSHOT.jar server consul-example/hello-world.yml
```

This will start the application on port `8080` (admin port `8180`). This application demonstrations the following Consul integration points:

- The application is registered as a service with Consul (with the [service port](https://www.consul.io/docs/agent/services.html) set to the applicationConnectors port in the configuration file.
- The application will lookup any variables in the configuration file from Consul upon startup (it defaults to connecting to a Consul agent running on `localhost:8500` for this functionality)
- The application exposes an additional HTTP endpoint for querying Consul for available healthy services:
```
curl -X GET localhost:8080/consul/hello-world -i
HTTP/1.1 200 OK
Date: Mon, 25 Jan 2016 03:42:10 GMT
Content-Type: application/json
Vary: Accept-Encoding
Content-Length: 870

[
    {
        "Node": {
            "Node": "mac",
            "Address": "192.168.1.100",
            "Datacenter": "dc1",
            "TaggedAddresses": {
                "wan": "192.168.1.100",
                "lan": "192.168.1.100"
            },
            "Meta": {
                "consul-network-segment": ""
            }
        },
        "Service": {
            "ID": "test123",
            "Service": "hello-world",
            "EnableTagOverride": false,
            "Tags": [],
            "Address": "",
            "Meta": {
                "scheme": "http"
            },
            "Port": 8080,
            "Weights": {
                "Passing": 1,
                "Warning": 1
            }
        },
        "Checks": [
            {
                "Node": "mac",
                "CheckID": "serfHealth",
                "Name": "Serf Health Status",
                "Status": "passing",
                "Notes": "",
                "Output": "Agent alive and reachable",
                "ServiceID": "",
                "ServiceName": "",
                "ServiceTags": []
            },
            {
                "Node": "mac",
                "CheckID": "service:test123",
                "Name": "Service 'hello-world' check",
                "Status": "passing",
                "Notes": "",
                "Output": "HTTP GET http:\/\/127.0.0.1:8180\/healthcheck: 200 OK Output: {\"consul\":{\"healthy\":true},\"deadlocks\":{\"healthy\":true}}",
                "ServiceID": "test123",
                "ServiceName": "hello-world",
                "ServiceTags": []
            }
        ]
    }
]
```
- The application will periodically checkin with Consul every second to notify the service check that it is still alive
- Upon shutdown, the application will deregister itself from Consul

Credits
-------
This library comes from the [dropwizard-consul](https://github.com/smoketurner/dropwizard-consul) library from
[Smoketurner](https://github.com/smoketurner/). The following credits are also retained from the original library.

> This bundle was inspired by an older bundle (Dropwizard 0.6.2) that [Chris Gray](https://github.com/chrisgray) created at https://github.com/chrisgray/dropwizard-consul. I also incorporated the configuration provider changes from https://github.com/remmelt/dropwizard-consul-config-provider

Support
-------
Please file bug reports and feature requests in [GitHub issues](https://github.com/kiwiproject/dropwizard-consul/issues).

License
-------
Copyright (c) 2020 Smoke Turner, LLC \
Copyright (c) 2023 Kiwi Project

This library is licensed under the Apache License, Version 2.0.

See http://www.apache.org/licenses/LICENSE-2.0.html or the [LICENSE](LICENSE) file in this repository for the full license text.
