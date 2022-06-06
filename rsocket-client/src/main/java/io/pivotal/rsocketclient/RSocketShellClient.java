package io.pivotal.rsocketclient;


import java.time.Duration;
import java.util.UUID;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder;
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import io.pivotal.rsocketclient.data.Message;
import io.pivotal.rsocketclient.data.Notification;
import io.rsocket.SocketAcceptor;
import io.rsocket.metadata.WellKnownMimeType;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@ShellComponent
public class RSocketShellClient {

    private static final String CLIENT = "Client";
    private static final String SERVER = "Server";
    private static final String REQUEST = "Request";
    private static final String FIRE_AND_FORGET = "Fire-And-Forget";
    private static final String STREAM = "Stream";
    private static final String CLIENT_ID = UUID.randomUUID().toString();
    private static final MimeType SIMPLE_AUTH = MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.getString());
    
    
    private static Disposable disposable;

    private RSocketRequester rsocketRequester;
    private RSocketRequester.Builder rsocketRequesterBuilder;
    private RSocketStrategies rsocketStrategies;
    Logger logger = LoggerFactory.getLogger(RSocketShellClient.class);
    
    @Autowired
    public RSocketShellClient(RSocketRequester.Builder builder,
                              @Qualifier("rSocketStrategies") RSocketStrategies strategies) {
        this.rsocketRequesterBuilder = builder;
        this.rsocketStrategies = strategies;
        
    }

    @ShellMethod("Login with your username and password.")
    public void login(String username, String password) {
    	logger.info("Connecting using client ID: {} and username: {}", CLIENT_ID, username);
        SocketAcceptor responder = RSocketMessageHandler.responder(rsocketStrategies, new ClientHandler());
        UsernamePasswordMetadata user = new UsernamePasswordMetadata(username, password);
        this.rsocketRequester = rsocketRequesterBuilder
                .setupRoute("shell-client")
                .setupData(CLIENT_ID)
                .setupMetadata(user, SIMPLE_AUTH)
                .rsocketStrategies(builder ->
                        builder.encoder(new SimpleAuthenticationEncoder()))
                .rsocketConnector(connector -> connector.acceptor(responder))
                .connectTcp("localhost", 7000)
                .block();

        this.rsocketRequester.rsocket()
                .onClose()
                .doOnError(error -> logger.warn("Connection CLOSED"))
                .doFinally(consumer -> logger.info("Client DISCONNECTED"))
                .subscribe();
    }

    @PreDestroy
    @ShellMethod("Logout and close your connection")
    public void logout() {
        if (userIsLoggedIn()) {
            this.s();
            this.rsocketRequester.rsocket().dispose();
            logger.info("Logged out.");
        }
    }

    private boolean userIsLoggedIn() {
        if (null == this.rsocketRequester || this.rsocketRequester.rsocket().isDisposed()) {
        	logger.info("No connection. Did you login?");
            return false;
        }
        return true;
    }

    @ShellMethod("Send one request. One response will be printed.")
    public void requestResponse() throws InterruptedException {
        if (userIsLoggedIn()) {
        	logger.info("\nSending one request. Waiting for one response...");
            Notification objNotification = this.rsocketRequester
                    .route("request-response")
                    .data(new Notification(CLIENT, SERVER, "Test the Request-Response interaction model from client",CLIENT_ID))                
                    .retrieveMono(Notification.class)
                    .block();
            logger.info("\nResponse was: {}", objNotification);
        }
    }

    @ShellMethod("Send one request. No response will be returned.")
    public void fireAndForget() throws InterruptedException {
        if (userIsLoggedIn()) {
        	logger.info("\nFire-And-Forget. Sending one request. Expect no response (check server console log)...");
            this.rsocketRequester
                    .route("fire-and-forget")
                    .data(new Notification(CLIENT, SERVER, "Test the Fire-And-Forget interaction model",CLIENT_ID))
                    .send()
                    .block();
        }
    }

    @ShellMethod("Send one request. Many responses (stream) will be printed.")
    public void stream() {
        if (userIsLoggedIn()) {
        	logger.info("\n\n**** Request-Stream\n**** Send one request.\n**** Log responses.\n**** Type 's' to stop.");
            disposable = this.rsocketRequester
                    .route("stream")
                    .data(new Notification(CLIENT, SERVER, "Test the Request-Stream interaction model",CLIENT_ID))
                    .retrieveFlux(Notification.class)
                    .subscribe(notification -> logger.info("Response: {} \n(Type 's' to stop.)", notification));
        }
    }

    @ShellMethod("Stream some settings to the server. Stream of responses will be printed.")
    public void channel() {
        if (userIsLoggedIn()) {
        	logger.info("\n\n***** Channel (bi-directional streams)\n***** Asking for a stream of messages.\n***** Type 's' to stop.\n\n");

//            Mono<Duration> setting1 = Mono.just(Duration.ofSeconds(1));
//            Mono<Duration> setting2 = Mono.just(Duration.ofSeconds(3)).delayElement(Duration.ofSeconds(5));
//            Mono<Duration> setting3 = Mono.just(Duration.ofSeconds(5)).delayElement(Duration.ofSeconds(15));
        	Notification objnotification0 = new Notification(CLIENT, SERVER, "Test the Channel interaction model",CLIENT_ID);
        	Notification objnotification2 = new Notification(CLIENT, SERVER, "Test the Channel interaction model second",CLIENT_ID);
        	Notification objnotification3 = new Notification(CLIENT, SERVER, "Test the Channel interaction model third",CLIENT_ID);
        	
        	
            Mono<Notification> notification0 = Mono.just(objnotification0);        
            Mono<Notification> notification2 = Mono.just(objnotification2).delayElement(Duration.ofSeconds(2));
            Mono<Notification> notification5 = Mono.just(objnotification3).delayElement(Duration.ofSeconds(5));

            Flux<Notification> notifications = Flux.concat(notification0, notification2, notification5)
                    .doOnNext(d -> logger.info("\n\"Send notification for my-channel\"\n"));

            disposable = this.rsocketRequester
                    .route("channel")
                    .data(notifications)
                    .retrieveFlux(Long.class)
                    .subscribe(notification -> logger.info("Received: {} \n(Type 's' to stop.)", notification));
        }
    }

    @ShellMethod("Stops Streams or Channels.")
    public void s() {
        if (userIsLoggedIn() && null != disposable) {
        	logger.info("Stopping the current stream.");
            disposable.dispose();
            logger.info("Stream stopped.");
        }
    }
}

@Slf4j
class ClientHandler {

	 Logger logger = LoggerFactory.getLogger(ClientHandler.class);
	 
    @MessageMapping("client-status")
    public Flux<String> statusUpdate(String status) {
    	logger.info("Connection {}", status);
        return Flux.interval(Duration.ofSeconds(10)).map(index -> String.valueOf(Runtime.getRuntime().freeMemory()));
    }
}
