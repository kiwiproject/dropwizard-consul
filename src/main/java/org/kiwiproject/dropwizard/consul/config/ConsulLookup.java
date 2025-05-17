package org.kiwiproject.dropwizard.consul.config;

import static java.util.Objects.requireNonNull;

import io.dropwizard.configuration.UndefinedEnvironmentVariableException;
import org.apache.commons.text.lookup.StringLookup;
import org.jspecify.annotations.Nullable;
import org.kiwiproject.consul.Consul;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * A custom {@link org.apache.commons.text.lookup.StringLookup} implementation using Consul KV as
 * lookup source.
 */
public class ConsulLookup implements StringLookup {

    private static final Logger LOG = LoggerFactory.getLogger(ConsulLookup.class);

    private final boolean strict;
    private final Consul consul;

    /**
     * Create a new instance with strict behavior.
     *
     * @param consul Consul client
     */
    public ConsulLookup(Consul consul) {
        this(consul, true);
    }

    /**
     * Constructor
     *
     * @param consul Consul client
     * @param strict {@code true} if looking up undefined environment variables should throw a {@link
     *               UndefinedEnvironmentVariableException}, {@code false} otherwise.
     * @throws UndefinedEnvironmentVariableException if the environment variable doesn't exist and
     *                                               strict behavior is enabled.
     */
    public ConsulLookup(Consul consul, boolean strict) {
        this.consul = requireNonNull(consul);
        this.strict = strict;
    }

    /**
     * {@inheritDoc}
     *
     * @throws UndefinedEnvironmentVariableException if the environment variable doesn't exist and
     *                                               strict behavior is enabled.
     */
    @Nullable
    @Override
    public String lookup(String key) {
        try {
            Optional<String> value = consul.keyValueClient().getValueAsString(key);
            if (value.isPresent()) {
                return value.get();
            }
        } catch (Exception e) {
            LOG.warn("Unable to lookup key in consul", e);
        }

        if (strict) {
            throw new UndefinedEnvironmentVariableException(
                String.format(
                    "The variable with key '%s' is not found in the Consul KV store;"
                        + " could not substitute the expression '${%s}'.",
                    key, key));
        }
        return null;
    }
}
