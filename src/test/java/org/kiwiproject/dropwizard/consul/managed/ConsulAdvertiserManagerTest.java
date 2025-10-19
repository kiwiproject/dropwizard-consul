package org.kiwiproject.dropwizard.consul.managed;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.dropwizard.consul.core.ConsulAdvertiser;

import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

@DisplayName("ConsulAdvertiserManager")
class ConsulAdvertiserManagerTest {

    private ConsulAdvertiser advertiser;
    private ScheduledExecutorService scheduler;

    @BeforeEach
    void setUp() {
        advertiser = mock(ConsulAdvertiser.class);
        scheduler = mock(ScheduledExecutorService.class);
    }

    // Yes, this "test" is a bit silly. It's a "just in case" test.
    @Test
    void shouldAllowStartCallsButDoNothing() {
        var manager = new ConsulAdvertiserManager(advertiser, scheduler);

        manager.start();
        manager.start();
        manager.start();

        verifyNoInteractions(advertiser, scheduler);
    }

    @SuppressWarnings("removal")
    @Test
    void shouldDeregisterAdvertiserAndShutdownScheduler_WhenConstructedUsingDeprecatedConstructor() {
        var manager = new ConsulAdvertiserManager(advertiser, Optional.of(scheduler));

        manager.stop();

        verify(advertiser, only()).deregister();
        verify(scheduler, only()).shutdownNow();
    }

    @Test
    void shouldDeregisterAdvertiserAndShutdownScheduler_WhenSchedulerNotNull() {
        var manager = new ConsulAdvertiserManager(advertiser, scheduler);

        manager.stop();

        verify(advertiser, only()).deregister();
        verify(scheduler, only()).shutdownNow();
    }

    @Test
    void shouldDeregisterAdvertiserAndIgnoreScheduler_WhenConstrucedWithoutAScheduler() {
        var manager = new ConsulAdvertiserManager(advertiser);

        manager.stop();

        verify(advertiser, only()).deregister();
    }

    @Test
    void shouldDeregisterAdvertiserAndIgnoreScheduler_WhenSchedulerIsNull() {
        var manager = new ConsulAdvertiserManager(advertiser, (ScheduledExecutorService) null);

        manager.stop();

        verify(advertiser, only()).deregister();
    }
}
