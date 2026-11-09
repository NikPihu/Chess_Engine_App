package com.gambyte.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.gambyte.config.GambyteProperties;
import org.junit.jupiter.api.Test;

class RazorpaySignatureTest {

  private static RazorpayGateway gateway() {
    var rp = new GambyteProperties.Razorpay("rzp_test_key", "key_secret", "webhook_secret", "plan_m", "plan_y");
    var props = new GambyteProperties("http://localhost", null, null, null, new GambyteProperties.Payment(rp), null);
    return new RazorpayGateway(props);
  }

  @Test
  void hmacMatchesTheKnownVector() {
    // RFC 4231-style check computed independently: HMAC-SHA256("key", "The quick brown fox jumps over the lazy dog")
    assertThat(RazorpayGateway.hmac("The quick brown fox jumps over the lazy dog", "key"))
        .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
  }

  @Test
  void checkoutSignatureIsVerified() {
    RazorpayGateway g = gateway();
    String good = RazorpayGateway.hmac("pay_1|sub_1", "key_secret");
    assertThat(g.verifyCheckout("pay_1", "sub_1", good)).isTrue();
    assertThat(g.verifyCheckout("pay_1", "sub_2", good)).isFalse();
    assertThat(g.verifyCheckout("pay_1", "sub_1", "tampered")).isFalse();
  }

  @Test
  void webhookSignatureCoversTheExactBody() {
    RazorpayGateway g = gateway();
    String body = "{\"event\":\"subscription.charged\"}";
    String sig = RazorpayGateway.hmac(body, "webhook_secret");
    assertThat(g.verifyWebhook(body, sig)).isTrue();
    assertThat(g.verifyWebhook(body + " ", sig)).isFalse();
    assertThat(g.verifyWebhook(body, null)).isFalse();
  }
}
