package com.gambyte.payment;

import java.time.Instant;

/**
 * What Gambyte needs from a payment provider. Razorpay implements it today; another provider
 * (e.g. Stripe) can be added as a second implementation without touching {@link PremiumService}.
 */
public interface PaymentGateway {

  /** A started checkout: what the browser needs to open the provider's payment window. */
  record Checkout(String provider, String keyId, String subscriptionId) {}

  /** A subscription's state as reported by the provider. */
  record SubscriptionState(String id, String status, Instant currentEnd) {}

  String name();

  boolean configured();

  /** Creates a recurring subscription for a plan ("monthly" / "yearly"). */
  Checkout createSubscription(long userId, String plan);

  /** Checks the signature the checkout window returned after payment. */
  boolean verifyCheckout(String paymentId, String subscriptionId, String signature);

  /** Checks a webhook's signature over the raw request body. */
  boolean verifyWebhook(String rawBody, String signature);

  SubscriptionState fetch(String subscriptionId);

  /** Cancels at the end of the paid period (the user keeps premium until then). */
  void cancel(String subscriptionId);
}
