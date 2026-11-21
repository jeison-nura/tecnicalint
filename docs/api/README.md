# API and event contracts

`webhooks.openapi.yaml` defines one shared `POST /webhooks/v1/payments` route for Quetzal Pagos (MX), Andina Pay (CO), and Austral Pagos (CL), exposed through API Manager. The provider-specific signature-header profile selects an adapter; it does not authenticate the request until HMAC verification succeeds. `events/premium-payment.schema.json` defines the provider-independent event. Chile is included in the route contract and overall design; its intake implementation is outside the Part B practical scope.

## Signature and acknowledgement requirements

- Preserve exact request-body bytes through API Manager and verify signatures before parsing or reserializing JSON.
- Quetzal signs `<unix timestamp>.<raw body>` with HMAC-SHA256 hex, allows a 300-second skew, and may send multiple signatures during rotation.
- Andina signs `<timestamp header>.<raw body>` with HMAC-SHA256 Base64 and expects HTTP 200 or 204 within five seconds.
- Return success only after durable acceptance or a durable duplicate determination. A temporary persistence failure must produce a retryable non-2xx response.
- Personal data and payment tokens may be present in source payloads; do not include them in canonical events, storage, logs, or telemetry.

## Compatibility and versioning

- Webhook paths and payload shapes are provider-owned contracts. Do not rename fields, change their meaning, or add requirements that providers do not send. Tolerate unknown provider fields where safe, while validating fields required for payment processing.
- The canonical event carries `schemaVersion: 1`. Additive optional fields may be introduced compatibly within a version. Do not remove or repurpose fields, tighten existing constraints, or change field types/enums in place.
- A breaking canonical change requires a new major schema version and a migration/consumer plan. During rollout, publish the old and new versions or use an explicitly versioned event stream until subscribers migrate.
- Schema changes require updated examples, producer/consumer contract tests, and review of privacy and data-retention impact.
