package com.gambyte.payment;

import com.gambyte.auth.AuthUser;
import com.gambyte.common.ApiException;
import com.gambyte.payment.PaymentGateway.Checkout;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Pricing, checkout, cancellation and the provider webhook. */
@RestController
@Tag(name = "Premium", description = "Subscriptions (Razorpay)")
public class PremiumController {

  private final PremiumService premium;
  private final PaymentGateway gateway;

  public PremiumController(PremiumService premium, PaymentGateway gateway) {
    this.premium = premium;
    this.gateway = gateway;
  }

  @GetMapping("/api/premium/plans")
  public Map<String, Object> plans() {
    return premium.plans();
  }

  public record SubscribeRequest(@NotBlank String plan) {}

  @Operation(summary = "Start a subscription; returns what Razorpay Checkout needs")
  @PostMapping("/api/premium/subscribe")
  public Checkout subscribe(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SubscribeRequest body) {
    return premium.subscribe(me.id(), body.plan());
  }

  public record ConfirmRequest(
      @NotBlank String razorpayPaymentId, @NotBlank String razorpaySubscriptionId, @NotBlank String razorpaySignature) {}

  @Operation(summary = "Confirm a completed checkout (signature-verified); premium starts now")
  @PostMapping("/api/premium/confirm")
  public Map<String, Instant> confirm(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ConfirmRequest body) {
    return Map.of("premiumUntil",
        premium.confirm(me.id(), body.razorpayPaymentId(), body.razorpaySubscriptionId(), body.razorpaySignature()));
  }

  @Operation(summary = "Cancel renewal; premium stays until the end of the paid period")
  @PostMapping("/api/premium/cancel")
  public ResponseEntity<Void> cancel(@AuthenticationPrincipal AuthUser me) {
    premium.cancel(me.id());
    return ResponseEntity.noContent().build();
  }

  /**
   * Razorpay webhook. Not logged in and not CSRF-protected; trust comes only from the HMAC
   * signature over the exact raw body, which is why the body is taken as a String.
   */
  @PostMapping("/api/payments/webhook")
  public ResponseEntity<Void> webhook(
      @RequestBody String rawBody,
      @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
      @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
    if (!gateway.verifyWebhook(rawBody, signature)) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "bad_signature", "Invalid signature");
    }
    premium.handleWebhook(eventId, rawBody);
    return ResponseEntity.ok().build();
  }
}
