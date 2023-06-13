Dropwizard Consul Bundle
========================

[![build](https://github.com/kiwiproject/dropwizard-consul/actions/workflows/build.yml/badge.svg)](https://github.com/kiwiproject/dropwizard-consul/actions/workflows/build.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=kiwiproject_dropwizard-consul&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=kiwiproject_dropwizard-consul)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=kiwiproject_dropwizard-consul&metric=coverage)](https://sonarcloud.io/summary/new_code?id=kiwiproject_dropwizard-consul)
[![CodeQL](https://github.com/kiwiproject/dropwizard-consul/actions/workflows/codeql.yml/badge.svg)](https://github.com/kiwiproject/dropwizard-consul/actions/workflows/codeql.yml)
[![javadoc](https://javadoc.io/badge2/org.kiwiproject/dropwizard-consul/javadoc.svg)](https://javadoc.io/doc/org.kiwiproject/dropwizard-consul)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/org.kiwiproject/dropwizard-consul)](https://central.sonatype.com/artifact/org.kiwiproject/dropwizard-consul/)

---

🥝 _We have now transitioned dropwizard-consul from smoketurner to kiwiproject. It is released to Maven Central. See the [releases](https://github.com/kiwiproject/dropwizard-consul/releases) for more information._ 🥝

---

Introduction
------------

A bundle for using [Consul](https://consul.io) in Dropwizard applications. Features:

* Dropwizard health check that monitors reachability of Consul
* The Dropwizard service is registered as a Consul service with a Consul-side health check querying the
  Dropwizard [health check](https://www.dropwizard.io/en/latest/manual/core/#man-core-healthchecks)
* Ability to resolve [configuration](https://www.dropwizard.io/en/latest/manual/core/#configuration) properties from
  Consul's KV store
* Admin task to toggle Consul's [maintenance](https://www.consul.io/api/agent.html#enable-maintenance-mode) mode

Background
----------

This library was imported from [smoketurner/dropwizard-consul](https://github.com/smoketurner/dropwizard-consul), which
is now a [public archive](https://docs.github.com/en/repositories/archiving-a-github-repository/archiving-repositories)
and is no longer maintained by the original author.

Since we are still using this library in our services which use Dropwizard and Consul, we decided to import the original
repository and continue maintaining it for our own use, and anyone else who might want to use it. We make no guarantees
whatsoever about how long we will maintain it, and also plan to make our own changes such as changing the base package
name to `org.kiwiproject` to be consistent with our other libraries.

All other [kiwiproject](https://github.com/kiwiproject/) projects are MIT-licensed. However, because the original
dropwizard-consul uses the Apache 2.0 license, we are keeping the Apache 2.0 license (otherwise to switch to MIT we
would
have to gain consent of all contributors, which we do not want to do).

Another thing to note is that we _imported_ this repository from the original, so that it is a "disconnected fork". We
did not want a reference to the original repository since it is a public archive and no longer maintained. Thus, while
we maintain the history that this is a fork , it is completely disconnected and is now a standalone (normal) repository.

Dependency Info
---------------

In a Maven POM, use the following:

```xml

<dependencies>

    <dependency>
        <groupId>org.kiwiproject</groupId>
        <artifactId>dropwizard-consul</artifactId>
        <version>[current-version]</version>
    </dependency>

    <!-- additional dependencies... -->

</dependencies>
```

Use the same group, artifact and version if using other build tools like Gradle, SBT, Grape, etc.

Usage
-----
Add a `ConsulFactory` to your
[Configuration](https://javadoc.io/doc/io.dropwizard/dropwizard-project/latest/io/dropwizard/core/Configuration.html)
class.

```java
public class MyConfiguration extends Configuration {

    @NotNull
    @Valid
    @JsonProperty
    private final ConsulFactory consul = new ConsulFactory();

    public ConsulFactory getConsulFactory() {
        return consul;
    }
}
```

Add a `ConsulBundle` to
your [Application](https://javadoc.io/doc/io.dropwizard/dropwizard-project/latest/io/dropwizard/core/Application.html)
class.

```java
public class MyApplication extends Application<MyConfiguration> {

    @Override
    public void initialize(Bootstrap<MyConfiguration> bootstrap) {
        // Add the bundle
        bootstrap.addBundle(new ConsulBundle<MyConfiguration>(getName()) {
            @Override
            public ConsulFactory getConsulFactory(MyConfiguration configuration) {
                return configuration.getConsulFactory();
            }
        });
    }

    @Override
    public void run(MyConfiguration configuration, Environment environment) {
        // Build a Consul instance
        var consul = configuration.getConsulFactory().build();

        // ...
    }
}
```

The bundle also includes a `ConsulSubsitutor` to retrieve configuration values from the Consul KV store. You can define
settings in your YAML configuration file, for example:

```
template: ${helloworld/template:-Hello, %s!}
defaultName: ${helloworld/defaultName:-Stranger}
```

The setting with the path `helloworld/template` will be looked up in the Consul KV store and will be replaced in the
configuration file when the application is started. You can specify a default value after the `:-`. This currently does
not support dynamically updating values in a running Dropwizard application.

Configuration
-------------
For configuring the Consul connection, you can configure the `ConsulFactory` in your Dropwizard configuration file:

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

Migrating from smoketurner/dropwizard-consul
--------------------------------------------
To migrate an existing project from [smoketurner/dropwizard-consul](https://github.com/smoketurner/dropwizard-consul), you need
to:

1. Change the group in your build file (i.e. Maven POM, Gradle) from `com.smoketurner.dropwizard` to `org.kiwiproject`
2. Change the base package in your code from `com.smoketurner.dropwizard` to `org.kiwiproject.dropwizard`

In a Maven POM, you would change:

```xml
<dependency>
    <groupId>com.smoketurner.dropwizard</groupId>
    <artifactId>consul-core</artifactId>
    <version>[version]</version>
</dependency>
```

to

```xml
<dependency>
    <groupId>org.kiwiproject</groupId>
    <artifactId>dropwizard-consul</artifactId>
    <version>[current-version]</version>
</dependency>
```

The class names from the original `smoketurner/dropwizard-consul` library are the same, so for example importing
`ConsulBundle` changes from:

```java
import com.smoketurner.dropwizard.consul.ConsulBundle;
```

to

```java
import org.kiwiproject.dropwizard.consul.ConsulBundle;
```

Credits
-------
This library comes from the [dropwizard-consul](https://github.com/smoketurner/dropwizard-consul) library from
[Smoketurner](https://github.com/smoketurner/). The following credits are also retained from the original library.

> This bundle was inspired by an older bundle (Dropwizard 0.6.2) that [Chris Gray](https://github.com/chrisgray) created
> at https://github.com/chrisgray/dropwizard-consul. I also incorporated the configuration provider changes
> from https://github.com/remmelt/dropwizard-consul-config-provider

Support
-------
Please file bug reports and feature requests
in [GitHub issues](https://github.com/kiwiproject/dropwizard-consul/issues).

License
-------
Copyright (c) 2020 Smoke Turner, LLC \
Copyright (c) 2023 Kiwi Project

This library is licensed under the Apache License, Version 2.0.

See http://www.apache.org/licenses/LICENSE-2.0.html or the [LICENSE](LICENSE) file in this repository for the full
license text.
