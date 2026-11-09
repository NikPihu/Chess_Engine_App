package com.gambyte.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Live updates over WebSocket using STOMP (a simple publish/subscribe protocol on top of
 * WebSocket).
 *
 * <ul>
 *   <li>Endpoint: {@code /ws}. The browser's session cookie authenticates the connection, so the
 *       logged-in user is known in every message handler.
 *   <li>Server → client: {@code /topic/game/{id}} (moves, clocks, results for players and
 *       spectators), {@code /topic/lobby} (seeks), {@code /user/queue/*} (private: match found,
 *       challenges, notifications).
 *   <li>Client → server: {@code /app/...} (moves, chat, heartbeats) handled by
 *       {@code @MessageMapping} methods.
 * </ul>
 *
 * The in-memory broker is enough for one server. To run several servers, switch to
 * {@code enableStompBrokerRelay} with RabbitMQ (or relay through Redis pub/sub) so messages reach
 * clients connected to any server.
 *
 * <p>Cross-site safety: only our own site may open the socket ({@code setAllowedOrigins}), and
 * the session cookie is SameSite=Lax, so other sites cannot ride on a user's login.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final GambyteProperties props;

  public WebSocketConfig(GambyteProperties props) {
    this.props = props;
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws").setAllowedOrigins(props.siteUrl());
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    ThreadPoolTaskScheduler heartbeat = new ThreadPoolTaskScheduler();
    heartbeat.setPoolSize(1);
    heartbeat.setThreadNamePrefix("ws-heartbeat-");
    heartbeat.initialize();
    registry
        .enableSimpleBroker("/topic", "/queue")
        .setHeartbeatValue(new long[] {10_000, 10_000}) // detect dead connections in ~20 s
        .setTaskScheduler(heartbeat);
    registry.setApplicationDestinationPrefixes("/app");
    registry.setUserDestinationPrefix("/user");
  }
}
