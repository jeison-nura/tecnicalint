# ADR-0001: Asynchronous Salesforce integration with bulk upsert

- **Status:** Proposed
- **Date:** 2026-11-20

## Context

The integration must acknowledge accepted webhooks quickly and durably, make 95% of payments visible within five minutes and 99.5% within one hour, while Salesforce is approximately 97% available and the integration has a shared 150,000-call rolling 24-hour budget. Salesforce supports upsert by unique `Payment_Key__c`, and bulk upsert handles up to 200 records in one API call. Its integration user is limited to 25 concurrent requests. Incorrect payment status can trigger policy reinstatement or suspension, so ordering matters.

## Options considered

### Option 1 — Synchronous single-record write during webhook
- Pros: simple request flow; immediate Salesforce result.
- Cons: provider deadlines are 5–10 seconds, while Salesforce can be unavailable or take up to 8 seconds per call; couples provider retries to Salesforce failures; high call volume; weak peak resilience.

### Option 2 — Asynchronous single-record writes
- Pros: webhook decoupled from Salesforce availability; durable queue can absorb outages.
- Cons: up to 120,000 calls/day at peak before retries; wastes the API budget and adds avoidable latency.

### Option 3 — Asynchronous, coalesced bulk upsert (chosen)
- Pros: durable acceptance independent of Salesforce; coalesces state changes per payment; up to 200 records per API call; bounded worker concurrency and retry control.
- Cons: introduces queue, state store, outbox and operational replay complexity; updates are eventually consistent; batching must balance throughput and latency.

## Decision

Accept a webhook only after signature verification and a transaction commits its inbox record, current payment state and outbox intent. An outbox relay publishes to durable messaging. A worker coalesces eligible latest payment states and uses Salesforce composite upsert in batches of at most 200 records with `allOrNone=false`. Upsert by `Payment_Key__c` and enforce no more than 25 concurrent calls; begin with a lower configured cap and adaptive throttling. Route Salesforce through the API Manager and use cached OAuth client-credentials tokens.

Retry 401 after token refresh, transient 5xx/row-lock errors with bounded backoff, and pause on API budget exhaustion. Invalid data/missing policies are classified for operations and DLQ rather than retried indefinitely. Never write policy status.

## Consequences

- Positive: provider acknowledgements are fast and durable; Salesforce outages do not lose accepted notifications; bulk calls preserve API budget; idempotent upsert supports safe retry.
- Negative: Salesforce visibility is asynchronous; database/outbox/broker consistency and DLQ operations require monitoring and runbooks.
- Risks and how they are mitigated: batching may approach freshness target under backlog, so measure oldest-message age and tune flush/concurrency; Salesforce outage windows up to 90 minutes conflict with the one-hour freshness SLO, so explicitly report dependency breaches and negotiate the SLO. Incorrect ordering could trigger policy automation, so only the latest provider state is projected and conflicts without a provider sequence are quarantined.
