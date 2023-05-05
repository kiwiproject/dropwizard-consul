package org.kiwiproject.dropwizard.consul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.google.common.collect.ImmutableList;
import io.dropwizard.util.Duration;

import org.junit.jupiter.api.Test;

class ConsulFactoryTest {

  @Test
  public void testEquality() {
    final ConsulFactory actual = createFullyPopulatedConsulFactory();
    final ConsulFactory expected = createFullyPopulatedConsulFactory();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testCorrectlyFormattedSubnet() {
    final ConsulFactory factory = createFullyPopulatedConsulFactory();
    factory.setServiceSubnet("192.168.3.0/24");
    assertThat(factory.getServiceSubnet()).isPresent().contains("192.168.3.0/24");
  }

  @Test
  public void testIncorrectlyFormattedSubnet() {
    final ConsulFactory factory = createFullyPopulatedConsulFactory();
    assertThatIllegalArgumentException().isThrownBy(() -> factory.setServiceSubnet("192.168.3.0/"));
  }

  @Test
  public void testNotEqual() {
    final ConsulFactory actual = createFullyPopulatedConsulFactory();
    final ConsulFactory expected = createFullyPopulatedConsulFactory();
    expected.setAdminPort(200);
    assertThat(actual).isNotEqualTo((expected));
  }

  @Test
  public void testHashCode() {
    final ConsulFactory actual = createFullyPopulatedConsulFactory();
    final ConsulFactory expected = createFullyPopulatedConsulFactory();
    assertThat(actual.hashCode()).hasSameHashCodeAs(expected);
  }

  @Test
  public void testMutatedHashCode() {
    final ConsulFactory actual = createFullyPopulatedConsulFactory();
    final ConsulFactory expected = createFullyPopulatedConsulFactory();
    expected.setAdminPort(200);
    assertThat(actual.hashCode()).isNotEqualTo(expected.hashCode());
  }

  @Test
  public void testSetServiceName() {
    ConsulFactory consulFactory = new ConsulFactory();
    String serviceName = "test-service";
    consulFactory.setServiceName(serviceName);
    assertThat(consulFactory.getServiceName()).isEqualTo(serviceName);
  }

  @SuppressWarnings("deprecation")
  @Test
  void shouldSetServiceName_WhenUsingDeprecated_SetSeviceName() {
    var consulFactory = new ConsulFactory();
    var serviceName = "test-service";
    consulFactory.setSeviceName(serviceName);
    assertThat(consulFactory.getServiceName()).isEqualTo(serviceName);
  }

  private ConsulFactory createFullyPopulatedConsulFactory() {
    final ConsulFactory consulFactory = new ConsulFactory();
    consulFactory.setServiceName("serviceName");
    consulFactory.setEnabled(true);
    consulFactory.setServicePort(1000);
    consulFactory.setAdminPort(2000);
    consulFactory.setServiceSubnet("192.168.1.0/24");
    consulFactory.setServiceAddress("localhost");
    consulFactory.setTags(ImmutableList.of("tag1", "tag2"));
    consulFactory.setRetryInterval(Duration.seconds(5));
    consulFactory.setCheckInterval(Duration.seconds(1));
    consulFactory.setAclToken("acl-token");
    consulFactory.setServicePing(false);
    return consulFactory;
  }
}
