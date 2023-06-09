package org.kiwiproject.dropwizard.consul.core;

import static java.util.Objects.nonNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.dropwizard.util.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.ConsulException;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

class ConsulServiceListenerTest {

    private final ConsulAdvertiser advertiser = mock(ConsulAdvertiser.class);
    private ScheduledExecutorService scheduler;

    @BeforeEach
    public void setUp() {
        scheduler = Executors.newScheduledThreadPool(1);
    }

    @AfterEach
    public void tearDown() {
        if (nonNull(scheduler)) {
            scheduler.shutdownNow();
        }
    }

    @Test
    void testRegister() {
        var listener = new ConsulServiceListener(
            advertiser, Optional.of(Duration.milliseconds(1)), Optional.of(scheduler));

        when(advertiser.register(any(), anyInt(), anyInt(), anyCollection()))
            .thenThrow(new ConsulException("Cannot connect to Consul"))
            .thenReturn(true);

        var hosts = Set.of("192.168.1.22");
        listener.register("http", 0, 0, hosts);

        verify(advertiser, timeout(100).atLeast(1)).register("http", 0, 0, hosts);
    }
}
