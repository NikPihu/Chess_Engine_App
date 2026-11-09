# ADR-0010 - Razorpay for Premium, behind a PaymentGateway interface

- **Status:** accepted
- **Date:** 2026-11-09

## Context
The owner is in India. Stripe is invite-only for Indian accounts. Users expect UPI.

## Decision
- **Razorpay Subscriptions + Checkout,** behind a `PaymentGateway` interface.
- **Signatures:** checkout and webhook signatures are verified with HMAC-SHA256.
- **Idempotent webhooks:** each processed event id is stored.
- **Premium** is a date (`users.premium_until`), extended by each successful charge.

## Consequences
- Card and UPI data never reach our servers.
- Premium cannot get stuck "on": it simply lapses when renewals stop.
- **Adding Stripe later** means one new `PaymentGateway` implementation.
