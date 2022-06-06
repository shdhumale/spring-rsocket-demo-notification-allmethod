package io.pivotal.rsocketserver;

import io.pivotal.rsocketserver.data.Message;
import io.rsocket.SocketAcceptor;
import io.rsocket.metadata.WellKnownMimeType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.rsocket.context.LocalRSocketServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder;
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.UUID;

@SpringBootTest
public class RSocketClientToSecuredServerITest {

    private static RSocketRequester requester;
    private static UsernamePasswordMetadata credentials;
    private static MimeType mimeType;


    @BeforeAll
    public static void setupOnce(@Autowired RSocketRequester.Builder builder,
                                 @LocalRSocketServerPort Integer port,
                                 @Autowired RSocketStrategies strategies) {

        SocketAcceptor responder = RSocketMessageHandler.responder(strategies, new ClientHandler());
        mimeType = MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.getString());

        // *******  The user 'test' is NOT in the required 'USER' role! **********
        credentials = new UsernamePasswordMetadata("test", "pass");

        requester = builder
                .setupRoute("shell-client")
                .setupData(UUID.randomUUID().toString())
                .setupMetadata(credentials, mimeType)
                .rsocketStrategies(b ->
                        b.encoder(new SimpleAuthenticationEncoder()))

                .rsocketConnector(connector -> connector.acceptor(responder))
                .connectTcp("localhost", port)
                .block();
    }

    @Test
    public void testFireAndForget() {
        // Send a fire-and-forget message
        Mono<Void> result = requester
                .route("fire-and-forget")
                .data(new Message("TEST", "Fire-And-Forget"))
                .retrieveMono(Void.class);

        // Assert that the user 'test' is DENIED access to the method.
        StepVerifier
                .create(result)
                .verifyErrorMessage("Denied");
    }

    @AfterAll
    public static void tearDownOnce() {
        requester.rsocket().dispose();
    }

    @Slf4j
    static class ClientHandler {

        @MessageMapping("client-status")
        public Flux<String> statusUpdate(String status) {
            log.info("Connection {}", status);
            return Flux.interval(Duration.ofSeconds(5)).map(index -> String.valueOf(Runtime.getRuntime().freeMemory()));
        }
    }
}