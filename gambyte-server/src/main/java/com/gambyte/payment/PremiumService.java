package com.gambyte.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gambyte.common.ApiException;
import com.gambyte.payment.PaymentGateway.Checkout;
import com.gambyte.social.NotificationService;
import com.gambyte.user.domain.User;
import com.gambyte.user.service.UserService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Premium membership: starting a subscription, confirming the first payment, renewals and
 * cancellations from webhooks.
 *
 * <p>Premium is simply {@code users.premium_until}: every successful charge pushes it to the end
 * of the paid period. If renewals stop (cancelled, card failed), it lapses by itself; there is no
 * "downgrade" job that could fail.
 *
 * <p>Webhooks are idempotent: each provider event id is recorded in {@code payment_events} and an
 * event seen before is ignored, so retried deliveries never double-extend anything.
 */
@Service
public class PremiumService {

  private static final Logger log = LoggerFactory.getLogger(PremiumService.class);

  /** Shown on the pricing page (amounts in rupees; must match the Razorpay plans). */
  public record Plan(String id, String name, int priceInr, String period, List<String> perks) {}

  private static final List<String> PERKS = List.of(
      "Unlimited Game Reviews with coach explanations",
      "Ask the AI coach about your games",
      "Unlimited rated puzzles and Puzzle Rush",
      "Premium badge and extra board themes",
      "Support an independent chess site");

  public static final List<Plan> PLANS = List.of(
      new Plan("monthly", "Premium Monthly", 199, "month", PERKS),
      new Plan("yearly", "Premium Yearly", 1499, "year", PERKS));

  private final PaymentGateway gateway;
  private final JdbcTemplate jdbc;
  private final UserService users;
  private final NotificationService notifications;
  private final ObjectMapper json;

  public PremiumService(
      PaymentGateway gateway, JdbcTemplate jdbc, UserService users,
      NotificationService notifications, ObjectMapper json) {
    this.gateway = gateway;
    this.jdbc = jdbc;
    this.users = users;
    this.notifications = notifications;
    this.json = json;
  }

  public Map<String, Object> plans() {
    return Map.of("plans", PLANS, "paymentsEnabled", gateway.configured(), "provider", gateway.name());
  }

  @Transactional
  public Checkout subscribe(long userId, String plan) {
    if (PLANS.stream().noneMatch(p -> p.id().equals(plan))) throw ApiException.badRequest("bad_plan", "Unknown plan");
    Checkout checkout = gateway.createSubscription(userId, plan);
    jdbc.update("INSERT INTO subscriptions (id, user_id, provider, plan, status) VALUES (?, ?, ?, ?, 'created')",
        checkout.subscriptionId(), userId, gateway.name(), plan);
    return checkout;
  }

  /**
   * Called by the browser right after Checkout succeeds. The signature proves the payment came
   * from Razorpay for this subscription; premium starts immediately (the webhook confirms later).
   */
  @Transactional
  public Instant confirm(long userId, String paymentId, String subscriptionId, String signature) {
    if (!gateway.verifyCheckout(paymentId, subscriptionId, signature)) {
      throw ApiException.badRequest("bad_signature", "Payment could not be verified");
    }
    Map<String, Object> sub = subscription(subscriptionId);
    if (((Number) sub.get("user_id")).longValue() != userId) throw ApiException.forbidden("not_yours", "Not your subscription");
    var state = gateway.fetch(subscriptionId);
    Instant until = state.currentEnd() != null ? state.currentEnd()
        : Instant.now().plus("yearly".equals(sub.get("plan")) ? Duration.ofDays(366) : Duration.ofDays(31));
    activate(userId, subscriptionId, "active", until);
    return until;
  }

  @Transactional
  public void cancel(long userId) {
    List<String> ids = jdbc.queryForList(
        "SELECT id FROM subscriptions WHERE user_id = ? AND status = 'active'", String.class, userId);
    if (ids.isEmpty()) throw ApiException.badRequest("no_subscription", "You have no active subscription");
    for (String id : ids) {
      gateway.cancel(id);
      jdbc.update("UPDATE subscriptions SET status = 'cancelled', updated_at = now() WHERE id = ?", id);
    }
  }

  /** Handles a provider webhook (signature already verified by the caller). */
  @Transactional
  public void handleWebhook(String eventId, String rawBody) {
    JsonNode event;
    try {
      event = json.readTree(rawBody);
    } catch (Exception e) {
      throw ApiException.badRequest("bad_json", "Invalid webhook body");
    }
    String type = event.path("event").asText();
    String id = eventId != null && !eventId.isBlank() ? eventId
        : type + ":" + event.path("created_at").asText() + ":" + event.path("payload").hashCode();
    int fresh = jdbc.update("INSERT INTO payment_events (event_id, type) VALUES (?, ?) ON CONFLICT DO NOTHING", id, type);
    if (fresh == 0) return; // already processed

    JsonNode sub = event.path("payload").path("subscription").path("entity");
    String subId = sub.path("id").asText(null);
    if (subId == null) return;
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT user_id FROM subscriptions WHERE id = ?", subId);
    if (rows.isEmpty()) {
      log.warn("Webhook {} for unknown subscription {}", type, subId);
      return;
    }
    long userId = ((Number) rows.getFirst().get("user_id")).longValue();
    switch (type) {
      case "subscription.activated", "subscription.charged" -> {
        long end = sub.path("current_end").asLong(0);
        Instant until = end > 0 ? Instant.ofEpochSecond(end) : Instant.now().plus(Duration.ofDays(31));
        activate(userId, subId, "active", until);
      }
      case "subscription.cancelled", "subscription.halted", "subscription.completed" ->
          jdbc.update("UPDATE subscriptions SET status = ?, updated_at = now() WHERE id = ?",
              type.substring("subscription.".length()), subId);
      default -> log.debug("Ignoring webhook {}", type);
    }
  }

  private void activate(long userId, String subscriptionId, String status, Instant until) {
    jdbc.update("UPDATE subscriptions SET status = ?, current_end = ?, updated_at = now() WHERE id = ?",
        status, Timestamp.from(until), subscriptionId);
    User u = users.get(userId);
    boolean wasPremium = u.isPremium();
    if (u.getPremiumUntil() == null || u.getPremiumUntil().isBefore(until)) users.setPremiumUntil(userId, until);
    if (!wasPremium) {
      notifications.notify(userId, u.getUsername(), "premium", "Welcome to Gambyte Premium! Thank you for your support.", "/premium");
    }
  }

  private Map<String, Object> subscription(String id) {
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT user_id, plan FROM subscriptions WHERE id = ?", id);
    if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "subscription_not_found", "Unknown subscription");
    return rows.getFirst();
  }
}
