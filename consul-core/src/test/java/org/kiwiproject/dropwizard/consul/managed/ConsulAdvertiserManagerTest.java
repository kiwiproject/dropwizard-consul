package org.kiwiproject.dropwizard.consul.managed;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Test;
import org.kiwiproject.dropwizard.consul.core.ConsulAdvertiser;
import org.kiwiproject.dropwizard.consul.managed.ConsulAdvertiserManager;

public class ConsulAdvertiserManagerTest {

  private final ConsulAdvertiser advertiser = mock(ConsulAdvertiser.class);
  private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
  private final ConsulAdvertiserManager manager =
      new ConsulAdvertiserManager(advertiser, Optional.of(scheduler));

  @Test
  public void testStop() throws Exception {
    manager.stop();
    verify(advertiser).deregister();
    verify(scheduler).shutdownNow();
  }
}
