package org.kiwiproject.dropwizard.consul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Test;
import org.kiwiproject.dropwizard.consul.ConsulBundle;
import org.kiwiproject.dropwizard.consul.ConsulConfiguration;
import org.kiwiproject.dropwizard.consul.ConsulFactory;

public class ConsulBundleTest {

  private final ConsulFactory factory = spy(new ConsulFactory());
  private final Environment environment = mock(Environment.class);
  private final TestConfiguration config = new TestConfiguration();
  private ConsulBundle<TestConfiguration> bundle;

  class TestConfiguration extends Configuration implements ConsulConfiguration<TestConfiguration> {
    @Override
    public ConsulFactory getConsulFactory(TestConfiguration configuration) {
      return factory;
    }
  }

  @Before
  public void setUp() throws Exception {
    bundle =
        spy(
            new ConsulBundle<TestConfiguration>("test") {
              @Override
              public ConsulFactory getConsulFactory(TestConfiguration c) {
                return c.getConsulFactory(c);
              }
            });

    doNothing().when(bundle).setupEnvironment(factory, environment);
  }

  @Test
  public void testDefaultsToEnabled() throws Exception {
    assertThat(factory.isEnabled()).isTrue();
  }

  @Test
  public void testEnabled() throws Exception {
    doReturn(true).when(factory).isEnabled();
    bundle.run(config, environment);
    verify(bundle, times(1)).setupEnvironment(factory, environment);
  }

  @Test
  public void testNotEnabled() throws Exception {
    doReturn(false).when(factory).isEnabled();
    bundle.run(config, environment);
    verify(bundle, times(0)).setupEnvironment(factory, environment);
  }

  @Test
  public void testMissingServiceName() throws Exception {
    factory.setSeviceName(null);
    bundle.run(config, environment);
    assertThat(factory.getServiceName()).isEqualTo("test");
  }

  @Test
  public void testPopulatedServiceName() throws Exception {
    factory.setSeviceName("test-service-name");
    bundle.run(config, environment);
    assertThat(factory.getServiceName()).isEqualTo("test-service-name");
  }

  @Test
  public void testAclToken() throws Exception {
    String token = "acl-token";
    factory.setAclToken(token);
    bundle.run(config, environment);
    assertThat(factory.getAclToken().get()).isEqualTo(token);
  }
}
