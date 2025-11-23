package org.kiwiproject.dropwizard.consul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.dropwizard.util.Duration;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    void shouldBuildConsulInstance() {
        var consulFactory = new ConsulFactory();
        consulFactory.setServicePing(false);

        var consul = consulFactory.build();
        assertThat(consul).isNotNull();
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
