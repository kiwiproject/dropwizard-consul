package org.kiwiproject.dropwizard.consul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.dropwizard.util.Duration;

import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

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
        assertThat(factory.getServiceSubnet()).isPresent().contains("192.168.3.0/24");
    }

    @Test
    void testIncorrectlyFormattedSubnet() {
        ConsulFactory factory = createFullyPopulatedConsulFactory();
        assertThatIllegalArgumentException().isThrownBy(() -> factory.setServiceSubnet("192.168.3.0/"));
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
