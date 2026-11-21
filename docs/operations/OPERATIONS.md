# Payments integration — operations

## Service objectives (measured separately for MX, CO and CL)

Use rolling 30-day windows. The freshness clock starts when Meridian receives an authentic provider notification (`receivedAt`) and stops when its latest eligible payment state is successfully visible in Salesforce. Exclude malformed/unauthenticated traffic; count every accepted state change, not provider retry attempts.

| SLI | SLO per country | Measurement |
|---|---|---|
| Freshness | 95% of accepted payment-state notifications visible within 5 minutes; 99.5% within 1 hour (NFR2). | Histogram of Salesforce success time minus durable intake time; dashboard p95/99.5 and age of oldest eligible message. |
| Durability | Zero accepted notifications lost (NFR3). A `2xx` is allowed only after inbox, current state and outbox commit. | Reconcile accepted/acknowledged IDs against durable inbox/outbox and eventual Salesforce status; discrepancy must be zero. |
| Provider response (supporting SLI) | 99.9% of valid requests answered before provider timeout; use provider-specific deadlines: Andina 5s, Quetzal/Austral 10s. | HTTP latency and timeout/retry rate split by country/provider. This is an operational target, not a replacement for NFR2/NFR3. |

Freshness is an objective, not a promise during a Salesforce outage: Salesforce availability is approximately 97% and outages can last 5–90 minutes. Preserve accepted work, communicate dependency-caused SLO impact, and recover without sacrificing durability or exhausting the shared API budget.

## Traceability, telemetry and privacy

1. At intake, create a Meridian `correlationId`; persist it with provider, country, provider event/delivery IDs, transaction ID, timestamps and processing status in the inbox.
2. Propagate it as message metadata through the outbox, Service Bus, Salesforce worker and downstream event. Preserve `traceparent`/trace IDs where distributed tracing is enabled. Record Salesforce outcome against the same correlation in the operational status store.
3. FR7 lookup is through the authenticated, audited status API by provider event/delivery or transaction ID—not by searching raw logs. Return processing state, timestamps, correlation ID and a sanitized error category.

Structured logs contain timestamp, severity, environment, country, provider, correlation/trace ID, operation, duration, attempt, queue age and sanitized result/error category. Metrics use low-cardinality labels (`country`, `provider`, `result`, `error_category`): webhook accepted/rejected and latency; Salesforce successes/4xx/5xx and call budget; freshness histogram; active backlog/oldest age; outbox backlog; DLQ count/age; reconciliation gap.

Never log webhook bodies, signatures/secrets/tokens, payer name, national ID, email, card/payment credentials, or unredacted policy/payment identifiers. Do not put correlation IDs or event IDs in metric labels. Restrict status lookups by role and audit who searched; apply documented retention and deletion policy to operational metadata.

## Alerts and response

| Alert (each country unless global) | Threshold | Route |
|---|---|---|
| Accepted-vs-durable reconciliation gap | Any nonzero gap, or missing committed event | Page Payments on-call immediately (potential NFR3 breach); incident commander and data-store owner. |
| Freshness / queue age | Warning: p95 >3m for 10m or oldest eligible message >3m. Page: p95 >5m for 5m, oldest >15m, or any accepted item approaches 1h. | Payments on-call; include country and oldest age. Engage Salesforce on-call if dependency errors rise. |
| DLQ growth | Any new DLQ item creates a ticket/warning; page on >10 items in 5m, oldest >10m, or a sustained increase. Alert every country, not only Mexico. | Payments on-call; Salesforce/data owner for permanent 4xx; security on-call for suspicious content. |
| Salesforce error/budget | Page on sustained 5xx >5% for 10m, permanent 400 >1% for 5m, or `REQUEST_LIMIT_EXCEEDED`. Warn at 50% and page at 75% of the rolling 24h integration budget; include org-wide usage. | Payments on-call + Salesforce on-call; throttle/pause dispatch on budget exhaustion. |
| Intake / provider edge | Page on 5xx/timeout increase or latency approaching provider deadlines; alert on sudden signature-failure spikes. | Payments on-call; API Manager/network or security on-call as indicated. |

On-call declares severity, opens an incident channel, assigns incident commander/comms/technical lead, records timestamps and preserves sanitized evidence. For customer-impacting payment-state errors or possible loss, use SEV-2; notify contact-center and country operations through the incident commander. Never repair a policy by writing `Policy__c.Status__c`; Salesforce automation owns reinstatement/suspension.
