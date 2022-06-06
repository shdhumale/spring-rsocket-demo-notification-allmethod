package io.pivotal.rsocketserver;


import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.annotation.ConnectMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;

import io.pivotal.rsocketserver.data.Notification;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Controller
public class RSocketController {

	static final String SERVER = "Server";
	static final String RESPONSE = "Response";
	static final String STREAM = "Stream";
	static final String CHANNEL = "Channel";
	

	private final List<RSocketRequester> CLIENTS = new ArrayList<>();
	Logger logger = LoggerFactory.getLogger(RSocketController.class);

	@PreDestroy
	void shutdown() {

		logger.info("Detaching all remaining clients...");
		CLIENTS.stream().forEach(requester -> requester.rsocket().dispose());
		logger.info("Shutting down.");
	}

	@ConnectMapping("shell-client")
	void connectShellClientAndAskForTelemetry(RSocketRequester requester,
			@Payload String client) {

		requester.rsocket()
		.onClose()
		.doFirst(() -> {
			// Add all new clients to a client list
			logger.info("Client: {} CONNECTED.", client);
			CLIENTS.add(requester);
		})
		.doOnError(error -> {
			// Warn when channels are closed by clients
			logger.warn("Channel to client {} CLOSED", client);
		})
		.doFinally(consumer -> {
			// Remove disconnected clients from the client list
			CLIENTS.remove(requester);
			logger.info("Client {} DISCONNECTED", client);
		})
		.subscribe();

		// Callback to client, confirming connection
		requester.route("client-status")
		.data("OPEN")
		.retrieveFlux(String.class)
		.doOnNext(s -> logger.info("Client: {} Free Memory: {}.", client, s))
		.subscribe();
	}

	/**
	 * This @MessageMapping is intended to be used "request --> response" style.
	 * For each Message received, a new Message is returned with ORIGIN=Server and INTERACTION=Request-Response.
	 *
	 * @param request
	 * @return Message
	 */
	@PreAuthorize("hasRole('USER')")
	@MessageMapping("request-response")
	Mono<Notification> requestResponse(final Notification request, @AuthenticationPrincipal UserDetails user) {
		logger.info("Received request-response request: {}", request.toString());
		logger.info("Request-response initiated by '{}' in the role '{}'", user.getUsername(), user.getAuthorities());
		// create a single Message and return it
		return Mono.just(new Notification(request.getDestination(), request.getSource(), "In response to: " + request.getText(), "Client Id:" + request.getClientid()));
	}

	/**
	 * This @MessageMapping is intended to be used "fire --> forget" style.
	 * When a new CommandRequest is received, nothing is returned (void)
	 *
	 * @param request
	 * @return
	 */
	@PreAuthorize("hasRole('USER')")
	@MessageMapping("fire-and-forget")
	public Mono<Void> fireAndForget(final Notification notification, @AuthenticationPrincipal UserDetails user) {
		logger.info("Received fire-and-forget request: {}", notification.toString());
		logger.info("Fire-And-Forget initiated by '{}' in the role '{}'", user.getUsername(), user.getAuthorities());    	
		return Mono.empty();
	}

	/**
	 * This @MessageMapping is intended to be used "subscribe --> stream" style.
	 * When a new request command is received, a new stream of events is started and returned to the client.
	 *
	 * @param request
	 * @return
	 */
	@PreAuthorize("hasRole('USER')")
	@MessageMapping("stream")
	Flux<Notification> stream(final Notification notification, @AuthenticationPrincipal UserDetails user) {
		logger.info("Received stream request: {}", notification.toString());
		logger.info("Stream initiated by '{}' in the role '{}'", user.getUsername(), user.getAuthorities());

		//        return Flux
		//                // create a new indexed Flux emitting one element every second
		//                .interval(Duration.ofSeconds(1))
		//                // create a Flux of new Messages using the indexed Flux
		//                .map(index -> new Message(SERVER, STREAM, index));


		return Flux
				.interval(Duration.ofSeconds(5))
				.map(i -> new Notification(notification.getDestination(), notification.getSource(), "In response to: " + notification.getText(), "Client Id:" + notification.getClientid()));

	}

	/**
	 * This @MessageMapping is intended to be used "stream <--> stream" style.
	 * The incoming stream contains the interval settings (in seconds) for the outgoing stream of messages.
	 *
	 * @param settings
	 * @return
	 */
	@PreAuthorize("hasRole('USER')")
	@MessageMapping("channel")
	Flux<Long> channel(final Flux<Notification> notifications, @AuthenticationPrincipal UserDetails user) {
		//    	logger.info("Received channel request...");
		//    	logger.info("Channel initiated by '{}' in the role '{}'", user.getUsername(), user.getAuthorities());
		//
		//        return settings
		//                .doOnNext(setting -> logger.info("Channel frequency setting is {} second(s).", setting.getSeconds()))
		//                .doOnCancel(() -> logger.warn("The client cancelled the channel."))
		//                .switchMap(setting -> Flux.interval(setting)
		//                        .map(index -> new Message(SERVER, CHANNEL, index)));

		final AtomicLong notificationCount = new AtomicLong(0);        
		return notifications.doOnNext(notification -> {
			logger.info("Received notification for channel: " + notification.toString());
			notificationCount.incrementAndGet();
		})
				.switchMap(notification -> Flux.interval(Duration.ofSeconds(10)).map(new Object() {
					private Function<Long, Long> numberOfMessages(AtomicLong notificationCount) {
						long count = notificationCount.get();
						logger.info("Return flux with count: " + count);
						return i -> count;
					}
				}.numberOfMessages(notificationCount))).log();
	}
}
