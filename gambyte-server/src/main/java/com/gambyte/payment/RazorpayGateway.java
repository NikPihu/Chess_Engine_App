package com.gambyte.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.gambyte.common.ApiException;
import com.gambyte.config.GambyteProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Razorpay (India: UPI, cards, netbanking) through its REST API.
 *
 * <ul>
 *   <li>Subscriptions: {@code POST /v1/subscriptions} with a plan created in the Razorpay
 *       dashboard; the browser then opens Razorpay Checkout with the subscription id.
 *   <li>Checkout signature: HMAC-SHA256 of {@code payment_id + "|" + subscription_id} with the key
 *       secret.
 *   <li>Webhook signature: HMAC-SHA256 of the raw body with the webhook secret, in the
 *       {@code X-Razorpay-Signature} header.
 * </ul>
 *
 * Test mode works with test keys and Razorpay's test cards/UPI ids; nothing here changes for live
 * mode except the keys (live mode needs KYC on the Razorpay account).
 */
@Component
public class RazorpayGateway implements PaymentGateway {

  private final GambyteProperties.Razorpay config;
  private final RestClient http;

  public RazorpayGateway(GambyteProperties props) {
    this.config = props.payment().razorpay();
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(5));
    factory.setReadTimeout(Duration.ofSeconds(15));
    this.http = RestClient.builder()
        .baseUrl("https://api.razorpay.com/v1")
        .requestFactory(factory)
        .defaultHeaders(h -> {
          if (config.enabled()) h.setBasicAuth(config.keyId(), config.keySecret());
        })
        .build();
  }

  @Override
  public String name() {
    return "razorpay";
  }

  @Override
  public boolean configured() {
    return config.enabled();
  }

  @Override
  public Checkout createSubscription(long userId, String plan) {
    requireConfigured();
    String planId = "yearly".equals(plan) ? config.planYearly() : config.planMonthly();
    if (planId == null || planId.isBlank()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "plan_missing", "This plan is not set up yet");
    }
    try {
      JsonNode sub = http.post().uri("/subscriptions")
          .body(Map.of(
              "plan_id", planId,
              "total_count", "yearly".equals(plan) ? 10 : 120, // renew for up to 10 years
              "customer_notify", 1,
              "notes", Map.of("gambyte_user_id", String.valueOf(userId), "plan", plan)))
          .retrieve().body(JsonNode.class);
      return new Checkout(name(), config.keyId(), sub.get("id").asText());
    } catch (RestClientException e) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "payment_provider_error", "Could not start the payment, try again");
    }
  }

  @Override
  public boolean verifyCheckout(String paymentId, String subscriptionId, String signature) {
    return config.enabled() && constantTimeEquals(hmac(paymentId + "|" + subscriptionId, config.keySecret()), signature);
  }

  @Override
  public boolean verifyWebhook(String rawBody, String signature) {
    String secret = config.webhookSecret();
    return secret != null && !secret.isBlank() && signature != null
        && constantTimeEquals(hmac(rawBody, secret), signature);
  }

  @Override
  public SubscriptionState fetch(String subscriptionId) {
    requireConfigured();
    JsonNode sub = http.get().uri("/subscriptions/{id}", subscriptionId).retrieve().body(JsonNode.class);
    JsonNode end = sub.get("current_end");
    Instant currentEnd = end == null || end.isNull() ? null : Instant.ofEpochSecond(end.asLong());
    return new SubscriptionState(subscriptionId, sub.get("status").asText(), currentEnd);
  }

  @Override
  public void cancel(String subscriptionId) {
    requireConfigured();
    http.post().uri("/subscriptions/{id}/cancel", subscriptionId)
        .body(Map.of("cancel_at_cycle_end", 1)).retrieve().toBodilessEntity();
  }

  private void requireConfigured() {
    if (!config.enabled()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "payments_off", "Payments are not set up on this server");
    }
  }

  static String hmac(String data, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** Compares without leaking timing information (prevents guessing a signature byte by byte). */
  private static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) return false;
    return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }
}
