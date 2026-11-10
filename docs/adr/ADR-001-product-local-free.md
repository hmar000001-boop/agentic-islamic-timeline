# ADR-001 — Free, local-only application

- Status: accepted
- Date: 2026-11-06

## Decision

The initial Play package is free. No advertising, billing, IAP, subscriptions, donation flow, user account, proprietary backend, analytics or tracking SDK is part of the product. No app `INTERNET` permission is present in this baseline. Play AI Delivery is a separate conditional integration and is not enabled in this delivery.

## Consequences

All user data, agent state, retrieval and model inference remain local. Distribution network access is not an application data plane. A future paid sibling application would use a distinct application ID and an explicit versioned user-controlled transfer format.
