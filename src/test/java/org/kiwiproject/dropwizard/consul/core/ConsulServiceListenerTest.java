package org.kiwiproject.dropwizard.consul.core;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.dropwizard.util.Duration;
import org.awaitility.Durations;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.LocalConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.consul.ConsulException;
import org.kiwiproject.dropwizard.consul.core.ConsulServiceListener.RetryResult;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@DisplayName("ConsulServiceListener")
class ConsulServiceListenerTest {

    private ConsulServiceListener listener;
    private ConsulAdvertiser advertiser;
    private ScheduledExecutorService scheduler;

    @BeforeEach
    void setUp() {
        advertiser = mock(ConsulAdvertiser.class);

        // Return a serviceId (mainly so it's not null in log messages during failure tests)
        when(advertiser.getServiceId()).thenReturn("test-service-" + System.nanoTime());

        scheduler = Executors.newScheduledThreadPool(1);
        listener = new ConsulServiceListener(advertiser, Duration.milliseconds(1), scheduler);
    }

    @AfterEach
    void tearDown() {
        if (nonNull(scheduler)) {
            scheduler.shutdownNow();
        }
    }

    /**
     * These tests, written many years after the original implementation, document the
     * existing behavior of the {@link ConsulServiceListener#serverStarted} method.
     */
    @Nested
    class ServerStarted {

        private Server server;

        @BeforeEach
        void setUp() {
            server = mock(Server.class);
        }

        @Test
        void shouldDoNothing_ForDegenerateCase_WhenThereAreNoConnectors() {
            when(server.getConnectors()).thenReturn(new Connector[0]);

            listener.serverStarted(server);

            verifyNoInteractions(advertiser);
        }

        @Test
        void shouldDoNothing_ForDegenerateCase_WhenThereAreNoServerConnectors() {
            var localConnector = mock(LocalConnector.class);
            when(server.getConnectors()).thenReturn(new Connector[] { localConnector });

            listener.serverStarted(server);

            verifyNoInteractions(advertiser);
        }

        @Test
        void shouldDoNothing_WhenOnlyApplicationConnector() {
            var applicationConnector = mockServerConnector(
                "application", "server.acme.com", 9042, "http/1.1");

            when(server.getConnectors()).thenReturn(new Connector[] { applicationConnector });

            listener.serverStarted(server);

            verifyNoInteractions(advertiser);
        }

        @Test
        void shouldDoNothing_WhenOnlyAdminConnector() {
            var adminConnector = mockServerConnector(
                "admin", "server.acme.com", 9043, "http/1.1");

            when(server.getConnectors()).thenReturn(new Connector[] { adminConnector });

            listener.serverStarted(server);

            verifyNoInteractions(advertiser);
        }

        @Test
        void shouldRegister_Using_HttpConnectors() {
            var applicationConnector = mockServerConnector(
                "application", "server.acme.com", 9042, "http/1.1");

            var adminConnector = mockServerConnector(
                "admin", "server.acme.com", 9043, "http/1.1");

            when(server.getConnectors()).thenReturn(
                new Connector[] {applicationConnector, adminConnector});

            verifyRegistration("http", 9042, "http", 9043, "server.acme.com");
        }

        @Test
        void shouldRegister_Using_HttpsConnectors() {
            var applicationConnector = mockServerConnector(
                "application", "server.acme.com", 9042, "http/1.1", "ssl");

            var adminConnector = mockServerConnector(
                "admin", "server.acme.com", 9043, "http/1.1", "ssl");

            when(server.getConnectors()).thenReturn(
                new Connector[] {applicationConnector, adminConnector});

            verifyRegistration("https", 9042, "https", 9043, "server.acme.com");
        }

        @Test
        void shouldRegister_WithLastConnectorWinning_WhenMultipleApplicationConnectors() {
            var applicationConnector1 = mockServerConnector(
                "application", "server.acme.com", 9042, "http/1.1", "ssl");

            var applicationConnector2 = mockServerConnector(
                "application", "server.acme.com", 9084, "http/1.1");

            var adminConnector = mockServerConnector(
                "admin", "server.acme.com", 9043, "http/1.1", "ssl");

            when(server.getConnectors()).thenReturn(
                    new Connector[] {applicationConnector1, applicationConnector2, adminConnector});

            verifyRegistration("http", 9084, "https", 9043, "server.acme.com");
        }

        @Test
        void shouldRegister_WithLastAdminPortAndSchemeWinning_WhenMultipleAdminConnectors() {
            var applicationConnector = mockServerConnector(
                "application", "server.acme.com", 9042, "http/1.1", "ssl");

            var adminConnector1 = mockServerConnector(
                "admin", "server.acme.com", 9043, "http/1.1", "ssl");

            var adminConnector2 = mockServerConnector(
                "admin", "server.acme.com", 9143, "http/1.1");

            when(server.getConnectors()).thenReturn(
                    new Connector[] {applicationConnector, adminConnector1, adminConnector2});

            verifyRegistration("https", 9042, "http", 9143, "server.acme.com");
        }

        @Test
        void shouldRegister_WithLastPortWinning_AndLastSchemeWinning_WhenMultipleConnectors() {
            var applicationConnector1 = mockServerConnector(
                "application", "server.acme.com", 9042, "http/1.1", "ssl");

            var applicationConnector2 = mockServerConnector(
                "application", "server.acme.com", 9142, "http/1.1");

            var adminConnector1 = mockServerConnector(
                "admin", "server.acme.com", 9043, "http/1.1", "ssl");

            var adminConnector2 = mockServerConnector(
                "admin", "server.acme.com", 9143, "http/1.1");

            when(server.getConnectors()).thenReturn(
                    new Connector[] {applicationConnector1, applicationConnector2, adminConnector1, adminConnector2});

            verifyRegistration("http", 9142, "http", 9143, "server.acme.com");
        }

        @Test
        void shouldRegister_UsingSomeOtherConnector() {
            var connector = mockServerConnector("simple-app", "simple.acme.com", 8042, "http/1.1");

            when(server.getConnectors()).thenReturn(new Connector[] { connector });

            verifyRegistration("http", 8042, "http", 8042, "simple.acme.com");
        }

        @Test
        void shouldRegister_UsingServerConnectors_WhenThereAreOtherConnectorTypes() {
            var applicationConnector = mockServerConnector(
                "application", "server.acme.com", 9042, "http/1.1");

            var adminConnector = mockServerConnector(
                "admin", "server.acme.com", 9043, "http/1.1");

            var localConnector1 = mockLocalConnector("local-1");
            var localConnector2 = mockLocalConnector("local-2");
            var localConnector3 = mockLocalConnector("local-3");

            when(server.getConnectors()).thenReturn(
                new Connector[] { localConnector1, applicationConnector, localConnector2, adminConnector, localConnector3 });

            verifyRegistration("http", 9042, "http", 9043, "server.acme.com");
        }

        private static LocalConnector mockLocalConnector(String name) {
            var connector = mock(LocalConnector.class);
            when(connector.getName()).thenReturn(name);
            return connector;
        }

        @Test
        void shouldRegister_WithLastConnectorWinning_WhenMultipleOtherConnectors() {
            var connector1 = mockServerConnector("connector-1", "simple.acme.com", 7042, "http/1.1", "ssl");
            var connector2 = mockServerConnector("connector-2", "simple.acme.com", 7142, "http/1.1");

            when(server.getConnectors()).thenReturn(new Connector[] { connector1, connector2 });

            verifyRegistration("http", 7142, "http", 7142, "simple.acme.com");
        }

        @Test
        void shouldRegister_WhenServerConnector_ReturnsNullHost() {
            var connector1 = mockServerConnector("application", null, 61_532, "ssl");
            var connector2 = mockServerConnector("admin", null, 63_427, "ssl");

            when(server.getConnectors()).thenReturn(new Connector[] { connector1, connector2 });

            verifyRegistration("https", 61_532, "https", 63_427);
        }

        private void verifyRegistration(String applicationScheme,
                                        int applicationPort,
                                        String adminScheme,
                                        int adminPort,
                                        String... hosts) {

            listener.serverStarted(server);

            verify(advertiser).register(applicationScheme, applicationPort, adminScheme, adminPort, Set.of(hosts));
        }
    }

    private static ServerConnector mockServerConnector(String name,
                                                       @Nullable String host,
                                                       int port,
                                                       String... protocols) {
        var connector = mock(ServerConnector.class);
        when(connector.getName()).thenReturn(name);
        when(connector.getHost()).thenReturn(host);
        when(connector.getLocalPort()).thenReturn(port);
        when(connector.getProtocols()).thenReturn(List.of(protocols));
        return connector;
    }

    @Nested
    class InternalRegisterMethod {

        @Test
        void shouldNotUseScheduler_WhenSchedulerNotProvided_AndSuccessfullyRegisters() {
            listener = new ConsulServiceListener(advertiser, null, null);

            var hosts = Set.of("simple.acme.com");
            listener.register("https", 8765, "https", 9876, hosts);

            verify(advertiser).register("https", 8765, "https", 9876, hosts);
        }

        @Test
        void shouldShutdownScheduler_AfterSuccessfulRegistration() {
            listener = new ConsulServiceListener(advertiser, Duration.milliseconds(10), scheduler);

            when(advertiser.register(anyString(), anyInt(), anyString(), anyInt(), anyCollection()))
                .thenThrow(new ConsulException("boom"))
                .thenReturn(true);

            var hosts = Set.of("simple.acme.com");
            listener.register("http", 8080, "http", 8081, hosts);

            verify(advertiser).register("http", 8080, "http", 8081, hosts);

            await().atMost(Durations.FIVE_SECONDS)
                .alias("scheduler should be shut down after successful registration")
                .until(scheduler::isShutdown);
        }

        @Test
        void shouldNotThrowException_WhenSchedulerNotProvided_AndRegistrationFails() {
            var ex = new ConsulException("Consul is not available at the moment, sorry");
            when(advertiser.register(anyString(), anyInt(), anyString(), anyInt(), anyCollection())).thenThrow(ex);

            listener = new ConsulServiceListener(advertiser, null, null);

            assertThatCode(() -> listener.register("https", 8765, "https", 9876, Set.of("simple.acme.com")))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowException_WhenRetryIntervalNotProvided_AndRegistrationFails() {
            var ex = new ConsulException("Consul is not available at the moment, sorry");
            when(advertiser.register(anyString(), anyInt(), anyString(), anyInt(), anyCollection())).thenThrow(ex);

            listener = new ConsulServiceListener(advertiser, null, scheduler);

            assertThatCode(() -> listener.register("https", 8765, "https", 9876, Set.of("simple.acme.com")))
                .doesNotThrowAnyException();

            assertThat(scheduler.isShutdown()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(longs = { -1, 0 })
        void shouldNotThrowException_WhenRetryIntervalIsNotPositive_AndRegistrationFails(long millis) {
            var ex = new ConsulException("Consul is not available at the moment, sorry");
            when(advertiser.register(anyString(), anyInt(), anyString(), anyInt(), anyCollection())).thenThrow(ex);

            listener = new ConsulServiceListener(advertiser, Duration.milliseconds(millis), scheduler);

            assertThatCode(() -> listener.register("https", 8765, "https", 9876, Set.of("simple.acme.com")))
                .doesNotThrowAnyException();

            assertThat(scheduler.isShutdown()).isTrue();
        }

        @Nested
        class RetryResultRecord {

            @ParameterizedTest
            @CsvSource(textBlock = """
                true, 1
                true, 10
                false, -10
                false, -1
                false, 0
                false, 10
                """)
            void shouldCreateNewInstanceForValidInput(boolean shouldRetry, long retryIntervalMillis) {
                var result = new RetryResult(shouldRetry, retryIntervalMillis);
                assertAll(
                    () -> assertThat(result.shouldRetry()).isEqualTo(shouldRetry),
                    () -> assertThat(result.retryIntervalMillis()).isEqualTo(retryIntervalMillis)
                );
            }

            @ParameterizedTest
            @CsvSource(textBlock = """
                true, -10
                true, -1
                true, 0
                """)
            void shouldRejectInvalidInputs(boolean shouldRetry, long retryIntervalMillis) {
                assertThatIllegalArgumentException()
                    .isThrownBy(() -> new RetryResult(shouldRetry, retryIntervalMillis))
                    .withMessage("retryIntervalMillis must be positive when shouldRetry=true");
            }

            @ParameterizedTest
            @ValueSource(longs = { 1, 5, 10, 50 })
            void shouldCreateFromInterval(long retryIntervalMillis) {
                var result = RetryResult.ofIntervalMillis(retryIntervalMillis);
                assertAll(
                    () -> assertThat(result.shouldRetry()).isTrue(),
                    () -> assertThat(result.retryIntervalMillis()).isEqualTo(retryIntervalMillis)
                );
            }

            @ParameterizedTest
            @ValueSource(longs = { -10, -5, -1, 0 })
            void shouldThrowIllegalArgument_ForNonPositiveInterval(long retryIntervalMillis) {
                assertThatIllegalArgumentException()
                    .isThrownBy(() -> RetryResult.ofIntervalMillis(retryIntervalMillis));
            }

            @Test
            void shouldCreateNoRetryInstance() {
                var result = RetryResult.ofNoRetry();
                assertAll(
                    () -> assertThat(result.shouldRetry()).isFalse(),
                    () -> assertThat(result.retryIntervalMillis()).isEqualTo(-1L)
                );
            }
        }
    }

    @Test
    void shouldRegister() {
        when(advertiser.register(anyString(), anyInt(), anyString(), anyInt(), anyCollection()))
            .thenThrow(new ConsulException("Cannot connect to Consul"))
            .thenReturn(true);

        var hosts = Set.of("192.168.1.22");
        listener.register("http", 0, "http", 0, hosts);

        verify(advertiser, timeout(100).atLeast(1)).register("http", 0, "http", 0, hosts);
    }
}
