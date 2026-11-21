# ADR-0002: MuleSoft integration and Azure SQL state store

- **Status:** Proposed
- **Date:** 2026-11-20

## Context

Meridian already operates an enterprise integration platform with API Manager and Mule 4 runtimes. The service needs provider-specific webhook verification, durable idempotency and ordering state, an outbox, operational lookup, Salesforce bulk integration and replay. It must support a short webhook response deadline, bursts of 40 notifications/second, and safe handling of personal data.

## Options considered

### Option 1 — Mule 4 with Azure SQL (chosen)
- Pros: aligns with the existing integration runtime and API Manager; provider adapters and shared orchestration fit the integration platform; SQL unique constraints and transactions support inbox/outbox and lifecycle ordering; relational status data is easy to query for operations.
- Cons: Mule runtime and licensing/operational footprint may cost more than a small serverless service; SQL requires capacity management and schema migrations; transactional behavior across SQL and broker still requires an outbox.

### Option 2 — Java 21 service with Azure SQL
- Pros: broad library/testing ecosystem and explicit control over webhook crypto and concurrency; SQL provides the same transactional/idempotency capabilities.
- Cons: adds a runtime and operational stack alongside the existing Mule platform; less reuse of established integration operations; Part B would require justification in a separate implementation decision.

### Option 3 — Mule 4 with a NoSQL state store
- Pros: elastic partitioned throughput and flexible document storage.
- Cons: unique constraints and multi-record transactional coordination for inbox, current payment state and outbox are more complex; operational ad hoc lookup and relational reconciliation are less straightforward.

## Decision

Use Mule 4 for the shared integration application, with provider-specific verification/mapping modules and a relational Azure SQL store for inbox, payment lifecycle, outbox and operational status. Use durable messaging with a DLQ between intake/publication and asynchronous Salesforce processing. Keep broker and persistence behind clear application connectors/interfaces so the practical component can stub them.

## Consequences

- Positive: leverages the existing integration capability; relational transactions and uniqueness constraints make deduplication and state transitions explicit; operations gets indexed lookups without raw-log search.
- Negative: SQL and Mule introduce baseline cost and require HA, backup, migration and capacity decisions; the outbox relay and broker are additional components.
- Risks and how they are mitigated: storage/broker outages must fail closed (no success response before durable commit); add health/backlog alerts, tested recovery and capacity headroom. Avoid sensitive-data persistence and use managed identity/least privilege. If the assessment practical is implemented in Java, document that deviation and justification in this ADR before implementation.
