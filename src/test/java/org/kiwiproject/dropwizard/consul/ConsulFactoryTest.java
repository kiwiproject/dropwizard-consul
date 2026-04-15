package org.kiwiproject.dropwizard.consul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.google.common.net.HostAndPort;
import io.dropwizard.util.Duration;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.consul.Consul;

import java.util.List;

class ConsulFactoryTest {

    @Test
    void testEquality() {
        ConsulFactory actual = createFullyPopulatedConsulFactory();
        ConsulFactory expected = createFullyPopulatedConsulFactory();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void testCorrectlyFormattedSubnet() {
        ConsulFactory factory = createFullyPopulatedConsulFactory();
        factory.setServiceSubnet("192.168.3.0/24");
        assertThat(factory.getServiceSubnet()).contains("192.168.3.0/24");
    }

    @Test
    void testIncorrectlyFormattedSubnet() {
        ConsulFactory factory = createFullyPopulatedConsulFactory();
        assertThatIllegalArgumentException().isThrownBy(() -> factory.setServiceSubnet("192.168.3.0/"));
    }

    @Test
    void shouldAllowNullSubnet() {
        var factory = new ConsulFactory();
        factory.setServiceSubnet(null);
        assertThat(factory.getServiceSubnet()).isEmpty();
    }

    @Test
    void testNotEqual() {
        ConsulFactory actual = createFullyPopulatedConsulFactory();
        ConsulFactory expected = createFullyPopulatedConsulFactory();
        expected.setAdminPort(200);
        assertThat(actual).isNotEqualTo((expected));
    }

    @Test
    void testHashCode() {
        ConsulFactory actual = createFullyPopulatedConsulFactory();
        ConsulFactory expected = createFullyPopulatedConsulFactory();
        assertThat(actual.hashCode()).hasSameHashCodeAs(expected);
    }

    @Test
    void testMutatedHashCode() {
        ConsulFactory actual = createFullyPopulatedConsulFactory();
        ConsulFactory expected = createFullyPopulatedConsulFactory();
        expected.setAdminPort(200);
        assertThat(actual.hashCode()).isNotEqualTo(expected.hashCode());
    }

    @Test
    void testSetServiceName() {
        var consulFactory = new ConsulFactory();
        var serviceName = "test-service";
        consulFactory.setServiceName(serviceName);
        assertThat(consulFactory.getServiceName()).isEqualTo(serviceName);
    }

    @Test
    void shouldBeEnabledByDefault() {
        var consulFactory = new ConsulFactory();

        assertAll(
                () -> assertThat(consulFactory.isEnabled()).isTrue(),
                () -> assertThat(consulFactory.isDisabled()).isFalse()
        );
    }

    @Test
    void shouldAllowDisabling() {
        var consulFactory = new ConsulFactory();
        consulFactory.setEnabled(false);

        assertAll(
                () -> assertThat(consulFactory.isEnabled()).isFalse(),
                () -> assertThat(consulFactory.isDisabled()).isTrue()
        );
    }

    @Test
    void shouldAllowSkipTlsVerificationOnHealthCheck() {
        var consulFactory = new ConsulFactory();
        assertThat(consulFactory.getHealthCheckSkipTlsVerify()).isEmpty();

        consulFactory.setHealthCheckSkipTlsVerify(true);
        assertThat(consulFactory.getHealthCheckSkipTlsVerify()).contains(true);
    }

    @Test
    void shouldSetServiceId() {
        var consulFactory = new ConsulFactory();
        var serviceId = "test-service-" + System.currentTimeMillis();
        consulFactory.setServiceId(serviceId);
        assertThat(consulFactory.getServiceId()).contains(serviceId);
    }

    @Test
    void shouldSetServiceId_Null() {
        var consulFactory = new ConsulFactory();
        consulFactory.setServiceId(null);
        assertThat(consulFactory.getServiceId()).isEmpty();
    }

    @Test
    void shouldHaveServicePing_DefaultToFalse() {
        var consulFactory = new ConsulFactory();
        assertThat(consulFactory.isServicePing()).isFalse();
    }

    @Test
    void shouldHaveDefaultRetryInterval() {
        var consulFactory = new ConsulFactory();
        assertThat(consulFactory.getRetryInterval()).contains(Duration.seconds(1));
    }

    @Test
    void shouldHaveNullUnixDomainSocketPathByDefault() {
        var consulFactory = new ConsulFactory();
        assertThat(consulFactory.getUnixDomainSocketPath()).isNull();
    }

    @Test
    void shouldSetUnixDomainSocketPath() {
        var consulFactory = new ConsulFactory();
        consulFactory.setUnixDomainSocketPath("/tmp/consul.sock");
        assertThat(consulFactory.getUnixDomainSocketPath()).isEqualTo("/tmp/consul.sock");
    }

    @Nested
    class ConnectionMode {

        @Test
        void shouldUseHostAndPort_WhenUnixDomainSocketPathIsNotSet() {
            var factory = new ConsulFactory();
            factory.setServicePing(false);

            // No socket path configured: host/port mode is active, UDS is not
            assertThat(factory.getUnixDomainSocketPath()).isNull();
            assertThat(factory.getEndpoint())
                .isEqualTo(HostAndPort.fromParts(Consul.DEFAULT_HTTP_HOST, Consul.DEFAULT_HTTP_PORT));

            assertThat(factory.build()).isNotNull();
        }

        @Test
        void shouldUseUnixDomainSocket_WhenUnixDomainSocketPathIsSet() {
            var factory = new ConsulFactory();
            factory.setServicePing(false);
            factory.setUnixDomainSocketPath("/tmp/consul.sock");

            // Socket path is set and endpoint is at its default: UDS mode is active, TCP is not
            assertThat(factory.getUnixDomainSocketPath()).isEqualTo("/tmp/consul.sock");
            assertThat(factory.getEndpoint())
                .isEqualTo(HostAndPort.fromParts(Consul.DEFAULT_HTTP_HOST, Consul.DEFAULT_HTTP_PORT));

            assertThat(factory.build()).isNotNull();
        }
    }

    @Test
    void unixDomainSocketPath_ShouldAffectEquality() {
        var factory1 = createFullyPopulatedConsulFactory();
        var factory2 = createFullyPopulatedConsulFactory();
        factory2.setUnixDomainSocketPath("/tmp/consul.sock");
        assertThat(factory1).isNotEqualTo(factory2);
    }

    @Test
    void unixDomainSocketPath_ShouldAffectHashCode() {
        var factory1 = createFullyPopulatedConsulFactory();
        var factory2 = createFullyPopulatedConsulFactory();
        factory2.setUnixDomainSocketPath("/tmp/consul.sock");
        assertThat(factory1.hashCode()).isNotEqualTo(factory2.hashCode());
    }

    @Nested
    class PropertyValidation {

        private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

        private ConsulFactory factory;

        @BeforeEach
        void setUp() {
            factory = new ConsulFactory();
        }

        @Test
        void shouldRequireHostAndPort() {
            factory.setEndpoint(null);

            var violations = VALIDATOR.validateProperty(factory, "endpoint");
            assertThat(violations).hasSize(1);
        }

        @Test
        void shouldRequireCheckInterval() {
            factory.setCheckInterval(null);

            var violations = VALIDATOR.validateProperty(factory, "checkInterval");
            assertThat(violations).hasSize(1);
        }

        @Test
        void shouldAllowMinimumCheckInterval() {
            factory.setCheckInterval(Duration.seconds(1));

            var violations = VALIDATOR.validateProperty(factory, "checkInterval");
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(longs = {1, 100, 500, 999})
        void shouldRequireMinCheckInterval(long millis) {
            factory.setCheckInterval(Duration.milliseconds(millis));

            var violations = VALIDATOR.validateProperty(factory, "checkInterval");
            assertThat(violations).hasSize(1);
        }

        @Test
        void shouldRequireDeregisterInterval() {
            factory.setDeregisterInterval(null);

            var violations = VALIDATOR.validateProperty(factory, "deregisterInterval");
            assertThat(violations).hasSize(1);
        }

        @Test
        void shouldAllowMinimumDeregisterInterval() {
            factory.setDeregisterInterval(Duration.minutes(1));

            var violations = VALIDATOR.validateProperty(factory, "deregisterInterval");
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(longs = {1, 25, 50, 59})
        void shouldRequireMinDeregisterInterval(long seconds) {
            factory.setDeregisterInterval(Duration.seconds(seconds));

            var violations = VALIDATOR.validateProperty(factory, "deregisterInterval");
            assertThat(violations).hasSize(1);
        }

        @Test
        void shouldPassValidation_WhenSocketPathIsNotConfigured() {
            // unixDomainSocketPath is null by default; any endpoint is valid
            factory.setEndpoint(HostAndPort.fromParts("consul.example.com", 8500));

            var violations = VALIDATOR.validate(factory);
            assertThat(violations).isEmpty();
        }

        @Test
        void shouldAllowSocketPath_WhenEndpointIsDefault() {
            factory.setUnixDomainSocketPath("/tmp/consul.sock");

            var violations = VALIDATOR.validate(factory);
            assertThat(violations).isEmpty();
        }

        @Test
        void shouldRejectSocketPath_WhenNonDefaultEndpointIsAlsoConfigured() {
            factory.setUnixDomainSocketPath("/tmp/consul.sock");
            factory.setEndpoint(HostAndPort.fromParts("consul.example.com", 8500));

            var violations = VALIDATOR.validate(factory);
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage())
                .contains("unixDomainSocketPath")
                .contains("endpoint");
        }
    }

    private ConsulFactory createFullyPopulatedConsulFactory() {
        var consulFactory = new ConsulFactory();
        consulFactory.setServiceName("serviceName");
        consulFactory.setEnabled(true);
        consulFactory.setServicePort(1000);
        consulFactory.setAdminPort(2000);
        consulFactory.setServiceSubnet("192.168.1.0/24");
        consulFactory.setServiceAddress("localhost");
        consulFactory.setTags(List.of("tag1", "tag2"));
        consulFactory.setRetryInterval(Duration.seconds(5));
        consulFactory.setCheckInterval(Duration.seconds(1));
        consulFactory.setAclToken("acl-token");
        consulFactory.setServicePing(false);
        return consulFactory;
    }
}
