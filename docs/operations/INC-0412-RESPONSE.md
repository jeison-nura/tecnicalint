# INC-0412 — response plan

**Severity:** SEV-2 (customer payment/policy impact)
**Incident window:** opened Mon 2026-11-30 08:12 Colombia time (UTC−5)
**Scope:** Andina / Colombia. Do not assume Mexico or Chile are affected.

## First 30 minutes

| Time | Action |
|---|---|
| 08:12–08:15 | Acknowledge the contact-center report, declare SEV-2, open an incident channel and assign incident commander, technical lead and communications lead. Tell Colombia operations that 37 reported cases are being investigated. Freeze all manual/bulk replay and preserve a timestamped snapshot of queue/DLQ counts and sanitized logs. |
| 08:15–08:20 | Compare country dashboards: CO has 4,312 DLQ items, zero active-queue depth, 43,120 Salesforce 400s, and 32,880 upserts; MX/CL do not show comparable impact. Signature failures are zero. The 43,120 400s equal 4,312 × 10 deliveries, strongly indicating a permanent error was retried to max delivery count. API Manager received 37,215 notifications while 32,880 payments were upserted; reconcile the difference against deduplicated inbox/status records (the 4,312 DLQ items do not alone explain all of it). Check current rolling integration and org-wide API usage before any Salesforce action; 66% means about 99,000 of the 150,000-call integration budget has been used, but shared org headroom must be checked separately. |
| 08:20–08:25 | Use the protected status lookup with the 37 customer cases and sample `correlationId`/transaction IDs. Inspect sanitized error categories, delivery counts, timestamps, DLQ reason and affected event states. Do not query raw webhook bodies or expose payer data in the incident channel. |
| 08:25–08:32 | Compare `transaccion.referencia` for failed events with Salesforce `Policy__c.Policy_Number__c` using read-only queries and the authoritative policy source. Salesforce expects an existing uppercase `POL-CO-#########` external ID. Incident samples include `000215560` and lowercase `pol-co-000198004`; both differ from that contract. Check whether the correct policy exists and whether v1 passed the provider reference through unchanged or Salesforce policy data changed. Ask the Salesforce owner to review the Saturday 22:00 release, but do not assume its cancellation validation rule is the cause: the observed error is a missing foreign-key external ID. |
| 08:32–08:42 | Contain the retry amplification: classify Salesforce `400 INVALID_FIELD`/missing-policy as permanent, stop immediate abandon/redelivery and quarantine each failed event once; continue processing valid CO messages. Keep the DLQ intact. Do not replay until mapping and policy existence are confirmed. Send the contact-center lead the known scope, safe next update time and process for matching cases to payment IDs. |

## Most likely cause and confirmation

There are two related failures:

1. **Primary data failure:** Salesforce cannot resolve the supplied `Policy_Number__c` external ID. The stored field contract is `POL-CO-<9 digits>` uppercase. The examples show a bare nine-digit reference and a lowercase prefixed reference. This points to a reference-format/mapping mismatch or missing policy records. Confirm against read-only `Policy__c` queries and the policy system for all 37 cases and a representative sample of DLQ records. Do not invent a prefix or normalize case until the provider contract and authoritative policy key confirm the correct transformation.
2. **Amplifier:** v1 abandons permanent 400s and redelivers immediately up to 10 times. The exact ratio (4,312 dead-lettered × 10 = 43,120 400 responses), zero active queue depth, and `MaxDeliveryCountExceeded` evidence explain the API burn and why no valid retry can fix these records.

The Salesforce release is temporally close (the first DLQ item appeared 1h41m after it), so compare before/after policy data and API behavior. Its new cancellation-reason validation does not match the reported foreign-key error, but verify it did not alter policy identifiers or migration data. The Friday v1.14.0 logging change is not evidence of a mapping change.

## Safe remediation and replay

1. Maintain the quarantine/no-hot-retry behavior for permanent 4xx. Keep the 4,312 DLQ messages and their audit metadata; do not purge, edit `Policy__c`, or requeue blindly.
2. Reconcile every impacted payment against Andina transaction/state history and the authoritative policy number. Correct the integration mapping only when verified; if a policy record is absent, route it to the policy-data owner. Add tests for bare, lowercase, malformed and valid references. Deploy the fix through the normal controlled pipeline.
3. Build a replay manifest keyed by country + provider transaction/payment key. For transactions with both `APROBADA` and `REVERSADA`, replay only the latest valid provider state (or ordered state transitions if the Salesforce flow requires them); never let an older approval overwrite a refund. Include the stable event ID and original correlation/audit reference.
4. Get Salesforce owner approval for API headroom. At 66% reported usage, inspect the actual rolling integration and org-wide totals; reserve capacity for live traffic. Canary one verified record, then a small batch. Upsert by unique `Payment_Key__c`; use batches ≤200 with `allOrNone=false`, low concurrency and measured throttling. Reconcile every success/failure before increasing rate. Idempotent upsert makes a lost acknowledgement retry safe.
5. Confirm the 37 cases in the status API and Salesforce with Colombia operations/contact center. Check that Salesforce automation—not the integration—reinstates eligible policies. Communicate resolution and retain an audited replay report without PII.

## Prevention

Alert on every country's DLQ growth, oldest message age, permanent 400 rate and rolling API usage; the existing Mexico-only DLQ alert left Colombia invisible. Classify nonretryable 400s after one attempt, use bounded backoff for transient 5xx/locks, and circuit-break/throttle at API budget limits. Contract-test Andina reference formats and verify referential integrity before deployment. Add dashboards/reconciliation by country, runbook-approved idempotent replay, and a Salesforce-release canary that verifies external-ID lookups and automation behavior before full rollout.
