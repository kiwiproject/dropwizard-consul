package org.kiwiproject.dropwizard.consul.config;

import com.orbitz.consul.Consul;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.UndefinedEnvironmentVariableException;

/**
 * A custom {@link EnvironmentVariableSubstitutor} using Consul KV as lookup source.
 */
public class ConsulSubstitutor extends EnvironmentVariableSubstitutor {

    public ConsulSubstitutor(Consul consul) {
        this(consul, true, false);
    }

    public ConsulSubstitutor(Consul consul, boolean strict) {
        this(consul, strict, false);
    }

    /**
     * Constructor
     *
     * @param consul                  Consul client
     * @param strict                  {@code true} if looking up undefined environment variables should throw a {@link
     *                                UndefinedEnvironmentVariableException}, {@code false} otherwise.
     * @param substitutionInVariables a flag whether substitution is done in variable names.
     * @see org.apache.commons.text.StringSubstitutor#setEnableSubstitutionInVariables(boolean)
     */
    public ConsulSubstitutor(Consul consul, boolean strict, boolean substitutionInVariables) {
        super(strict);
        this.setVariableResolver(new ConsulLookup(consul, strict));
        this.setEnableSubstitutionInVariables(substitutionInVariables);
    }
}
