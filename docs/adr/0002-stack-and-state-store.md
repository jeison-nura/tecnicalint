# ADR-0002: Java 21 integration component and Azure SQL state store

- **Status:** Proposed
- **Date:** 2026-11-20

## Context

Meridian already operates an enterprise integration platform with API Manager and Mule 4 runtimes. The service needs provider-specific webhook verification, durable idempotency and ordering state, an outbox, operational lookup, Salesforce bulk integration and replay. It must support a short webhook response deadline, bursts of 40 notifications/second, and safe handling of personal data.

## Options considered

### Option 1 — Mule 4 with Azure SQL
- Pros: aligns with the existing integration runtime and API Manager; provider adapters and shared orchestration fit the integration platform; SQL unique constraints and transactions support inbox/outbox and lifecycle ordering; relational status data is easy to query for operations.
- Cons: Mule runtime and licensing/operational footprint may cost more than a small serverless service; SQL requires capacity management and schema migrations; transactional behavior across SQL and broker still requires an outbox.

### Option 2 — Java 21 Spring Boot service with Azure SQL (chosen for the practical component)
- Pros: accepted by the assessment; direct control over raw-byte signature verification, provider adapters and idempotency; JUnit tests can exercise the supplied signature vectors; SQL provides transactional inbox/outbox capabilities.
- Cons: adds a runtime and operational stack alongside the existing Mule platform; less reuse of established integration operations; requires Spring Boot patching and Java runtime support.

### Option 3 — Mule 4 with a NoSQL state store
- Pros: elastic partitioned throughput and flexible document storage.
- Cons: unique constraints and multi-record transactional coordination for inbox, current payment state and outbox are more complex; operational ad hoc lookup and relational reconciliation are less straightforward.

## Decision

Use Java 21 with Spring Boot 3 for the Part B practical intake. This is the assessment's explicitly accepted alternative and makes provider signature verification and automated tests straightforward to review. Keep provider adapters isolated and define storage/publication behind interfaces. In the target design, use relational Azure SQL for inbox, payment lifecycle, outbox and operational status, plus durable messaging with a DLQ. The practical project uses test stubs for those external dependencies as required; it does not claim the in-memory stub is production durable.

## Consequences

- Positive: practical code can prove raw-byte HMAC verification and duplicate handling with JUnit; relational transactions and uniqueness constraints make target deduplication and state transitions explicit.
- Negative: Java adds a runtime alongside the existing Mule platform; SQL and broker still require HA, backup, migrations and operational ownership.
- Risks and how they are mitigated: practical in-memory adapters are demonstrative stubs only and must be replaced by transactional persistence/outbox and durable messaging before production. Storage/broker failures must fail closed; avoid sensitive-data persistence and use managed identity/least privilege.
