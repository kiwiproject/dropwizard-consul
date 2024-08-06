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
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.ConsulException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@DisplayName("ConsulServiceListener")
class ConsulServiceListenerTest {

    private ConsulServiceListener listener;
    private ConsulAdvertiser advertiser;
    private ScheduledExecutorService scheduler;

    @BeforeEach
    public void setUp() {
        advertiser = mock(ConsulAdvertiser.class);
        scheduler = Executors.newScheduledThreadPool(1);
        listener = new ConsulServiceListener(
            advertiser, Optional.of(Duration.milliseconds(1)), Optional.of(scheduler));
    }

    @AfterEach
    public void tearDown() {
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
        public void setUp() {
            server = mock(Server.class);
        }

        @Test
        void shouldDoNothing_ForDegenerateCase_WhenThereAreNoServerConnectors() {
            when(server.getConnectors()).thenReturn(new Connector[0]);

            verifyRegistration(null, -1, -1);
        }

        @Test
        void shouldRegister_Using_HttpConnectors() {
            var applicationConnector = mockServerConnector(
                "application", "server.acme.com", 9042, "http/1.1");

            var adminConnector = mockServerConnector(
                "admin", "server.acme.com", 9043, "http/1.1");

            when(server.getConnectors()).thenReturn(
                new Connector[] {applicationConnector, adminConnector});

            verifyRegistration("http", 9042, 9043, "server.acme.com");
        }

        @Test
        void shouldRegister_Using_HttpsConnectors() {
            var applicationConnector = mockServerConnector(
                "application", "server.acme.com", 9042, "http/1.1", "ssl");

            var adminConnector = mockServerConnector(
                "admin", "server.acme.com", 9043, "http/1.1", "ssl");

            when(server.getConnectors()).thenReturn(
                new Connector[] {applicationConnector, adminConnector});

            verifyRegistration("https", 9042, 9043, "server.acme.com");
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

            verifyRegistration("http", 9084, 9043, "server.acme.com");
        }

        @Test
        void shouldRegister_WithLastAdminPortWinning_AndApplicationSchemeWinning_WhenMultipleAdminConnectors() {
            var applicationConnector = mockServerConnector(
                "application", "server.acme.com", 9042, "http/1.1", "ssl");

            var adminConnector1 = mockServerConnector(
                "admin", "server.acme.com", 9043, "http/1.1", "ssl");

            var adminConnector2 = mockServerConnector(
                "admin", "server.acme.com", 9143, "http/1.1");

            when(server.getConnectors()).thenReturn(
                    new Connector[] {applicationConnector, adminConnector1, adminConnector2});

            verifyRegistration("https", 9042, 9143, "server.acme.com");
        }

        @Test
        void shouldRegister_WithLastPortWinning_AndLastApplicationSchemeWinning_WhenMultipleConnectors() {
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

            verifyRegistration("http", 9142, 9143, "server.acme.com");
        }

        @Test
        void shouldRegister_UsingSomeOtherConnector() {
            var connector = mockServerConnector("simple-app", "simple.acme.com", 8042, "http/1.1");

            when(server.getConnectors()).thenReturn(new Connector[] { connector });

            verifyRegistration("http", 8042, 8042, "simple.acme.com");
        }

        @Test
        void shouldRegister_WithLastConnectorWinning_WhenMultipleOtherConnectors() {
            var connector1 = mockServerConnector("connector-1", "simple.acme.com", 7042, "http/1.1", "ssl");
            var connector2 = mockServerConnector("connector-2", "simple.acme.com", 7142, "http/1.1");

            when(server.getConnectors()).thenReturn(new Connector[] { connector1, connector2 });

            verifyRegistration("http", 7142, 7142, "simple.acme.com");
        }

        private void verifyRegistration(String scheme, int applicationPort, int adminPort, String... hosts) {
            listener.serverStarted(server);

            verify(advertiser).register(scheme, applicationPort, adminPort, Set.of(hosts));
        }
    }

    private static ServerConnector mockServerConnector(String name, String host, int port, String... protocols) {
        var connector = mock(ServerConnector.class);
        when(connector.getName()).thenReturn(name);
        when(connector.getHost()).thenReturn(host);
        when(connector.getLocalPort()).thenReturn(port);
        when(connector.getProtocols()).thenReturn(List.of(protocols));
        return connector;
    }

    @Test
    void shouldRegister() {
        when(advertiser.register(any(), anyInt(), anyInt(), anyCollection()))
            .thenThrow(new ConsulException("Cannot connect to Consul"))
            .thenReturn(true);

        var hosts = Set.of("192.168.1.22");
        listener.register("http", 0, 0, hosts);

        verify(advertiser, timeout(100).atLeast(1)).register("http", 0, 0, hosts);
    }
}
