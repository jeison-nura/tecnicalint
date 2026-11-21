# Review — PR-214

## Verdict

- [ ] Approve
- [ ] Approve with comments
- [x] Request changes

## Summary for the author

@dvaldes, thanks for adding the Chile provider path and isolating its signature verification and status mapping. I recommend changes before merge: the endpoint currently accepts unsigned requests, acknowledges work before it is durably recorded, and logs the full payload including personal data. The hard-coded production-looking webhook secret must also be removed and rotated. There are additional correctness gaps around event ordering, money representation and the tests, detailed below.

## Findings

| # | Severity | File:line | Finding | Why it matters in this scenario | Suggested fix |
|---|---|---|---|---|---|
| 1 | Blocker | `src/main/java/com/meridian/payments/cl/AustralWebhookController.java:193` | A missing signature is accepted; verification runs only when the header is non-null. | Anyone who can reach the endpoint can submit forged captured/refunded events. A forged `Captured` payment can trigger Salesforce automation that reinstates a suspended policy. | Reject absent or malformed signatures with 401; verify every request against the exact raw body using a constant-time comparison. Add tests for missing, invalid and valid signatures. |
| 2 | Blocker | `src/main/java/com/meridian/payments/cl/AustralWebhookController.java:199` | The entire deserialized notification is logged. | The payload contains payer name, RUT and email. This creates unnecessary exposure in centralized logs and conflicts with data-minimization requirements. | Remove payload logging. Log only sanitized metadata such as provider, event ID/correlation ID and outcome; ensure identifiers are appropriately protected. |
| 3 | Blocker | `src/main/java/com/meridian/payments/cl/AustralWebhookController.java:201-204` | The controller returns 2xx immediately after submitting an in-memory task, before any durable commit. | A process restart, executor rejection, or worker failure after the response loses an event permanently; Austral will not retry a 2xx. Its at-least-once delivery does not protect events Meridian acknowledged but never persisted. | Persist the authenticated event durably (inbox/queue) before returning 2xx, then process asynchronously from that durable store. Return non-2xx if persistence fails. |
| 4 | Blocker | `src/main/resources/application.yml:289-290` | A live-looking webhook secret is committed in application configuration. | Repository readers and build artifacts can obtain it and forge Austral notifications. A secret in source cannot be protected by deployment access controls. | Remove it from source, load it from a secret manager/environment injection, and rotate/revoke the exposed value. Never use a production secret in tests. |
| 5 | Major | `src/main/java/com/meridian/payments/cl/AustralPaymentService.java:78-92` | The service applies events in arrival order and does not compare `occurredAt` with the currently applied state. | Austral explicitly does not guarantee ordering. A delayed `PAYMENT_CONFIRMED` can overwrite a later `PAYMENT_REVERSED` and leave the payment captured, potentially causing a policy to be reinstated incorrectly. | Persist per-payment state/version and apply only a newer valid transition, using provider `occurredAt` plus a deterministic tie-breaker. Add out-of-order and reversal tests. |
| 6 | Major | `src/main/java/com/meridian/payments/cl/AustralNotification.java:15-21`; `src/main/java/com/meridian/payments/cl/AustralPaymentService.java:82-89` | `amount` is modeled as `double` and rounded to two decimal places; the provider sends an integer amount as a string and CLP has no minor unit. | Binary floating-point and a two-decimal conversion are the wrong representation for this provider contract and can cause amount discrepancies in Salesforce automation and reconciliation. | Model the wire value as a validated integer string/`BigDecimal` parsed exactly; map CLP as whole units without floating-point arithmetic. Test the documented `"45990"` example. |
| 7 | Major | `src/main/java/com/meridian/payments/cl/AustralPaymentService.java:98-104` | Unknown event types silently map to `Pending`. | A future or malformed event may be persisted as a valid payment state and affect Salesforce processing instead of being rejected or quarantined. | Accept only the three documented event types; reject/quarantine unsupported values without writing a payment. |
| 8 | Major | `src/test/java/com/meridian/payments/cl/AustralPaymentServiceTest.java:327-348` | The tests do not assert the business behavior: the happy-path test only checks the service object exists, and the error test passes because the service swallows exceptions. | CI can pass while no Salesforce write occurs or failures are acknowledged as success; the security, durability and ordering behaviors are also untested. | Assert exact Salesforce calls and persisted inbox/idempotency state; test failure propagation/retry, duplicate and concurrent delivery, event ordering, amount mapping and controller signature/ack behavior. |

## What looks good

- The implementation keeps the Chile-specific provider adapter separate from the shared domain.
- The status mapping covers the three event types documented by Austral.
- The author has added focused classes for the controller, verifier and service, which should make the required fixes and tests easier to isolate.
