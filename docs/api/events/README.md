# Canonical events

- [Premium payment event schema](premium-payment.schema.json) — versioned provider-independent event contract.
- [Webhook and event contract guidance](../README.md) — signature handling, acknowledgement, privacy and compatibility rules.

Provider-specific payloads are mapped into this schema only after signature verification and validation. Do not add payer details, payment tokens or raw provider payloads to the canonical event.
