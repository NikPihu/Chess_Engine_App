-- V7: premium subscriptions and the payment-provider events we have processed.
CREATE TABLE subscriptions (
    id            VARCHAR(40)  PRIMARY KEY,           -- provider's subscription id (sub_...)
    user_id       BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider      VARCHAR(16)  NOT NULL,              -- razorpay
    plan          VARCHAR(10)  NOT NULL,              -- monthly | yearly
    status        VARCHAR(16)  NOT NULL,              -- created | active | cancelled | halted | completed
    current_end   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX subscriptions_user_idx ON subscriptions (user_id);

-- Webhooks can be delivered more than once; the event id makes processing idempotent.
CREATE TABLE payment_events (
    event_id     VARCHAR(64)  PRIMARY KEY,
    type         VARCHAR(48)  NOT NULL,
    received_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
