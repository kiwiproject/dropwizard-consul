package com.smoketurner.dropwizard.consul.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitz.consul.ConsulException;
import io.dropwizard.util.Duration;
import io.dropwizard.util.Sets;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ConsulServiceListenerTest {

  private final ConsulAdvertiser advertiser = mock(ConsulAdvertiser.class);
  private ScheduledExecutorService scheduler;

  @Before
  public void setUp() {
    scheduler = Executors.newScheduledThreadPool(1);
  }

  @After
  public void tearDown() {
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
  }

  @Test
  public void testRegister() {
    final ConsulServiceListener listener =
        new ConsulServiceListener(
            advertiser, Optional.of(Duration.milliseconds(1)), Optional.of(scheduler));

    when(advertiser.register(any(), anyInt(), anyInt(), anyCollection()))
        .thenThrow(new ConsulException("Cannot connect to Consul"))
        .thenReturn(true);

    Collection<String> hosts = Sets.of("192.168.1.22");
    listener.register("http", 0, 0, hosts);

    verify(advertiser, timeout(100).atLeast(1)).register("http", 0, 0, hosts);
  }
}
