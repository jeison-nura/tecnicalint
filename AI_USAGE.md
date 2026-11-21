# AI usage disclosure

I used an AI assistant during this assessment. It helped draft and revise design, implementation, infrastructure, operations, and code-review materials. I am responsible for the submitted work and its correctness.

## Tools

| Tool | Where used | Purpose |
|---|---|---|
| OpenCode AI assistant (GPT-6 Luna; exact serving version unknown) | Parts A–F; repository files including `docs/`, `practical/`, `infra/`, pipeline YAML, `docs/operations/`, and `pr-review/REVIEW.md` | Explore repository context, propose and edit code/configuration/documentation, review provider and Salesforce contracts, identify risks, and explain work. |
| OpenCode shell and repository tools | Validation of practical code, Terraform, diffs and commits | Run tests/format/validation and inspect changes and status. These are execution tools, not additional AI models. |

## Validation

### What was exercised

- Practical Java: 20 automated tests passed and reported coverage was above the project threshold of 85%. The suite includes provider sample/signature vectors, invalid and stale signatures, malformed/oversized bodies, duplicate delivery, 32-way concurrent retry intake, out-of-order and conflicting state transitions (including 24 concurrent deliveries), amount-precision rejection, simulated persistence/broker failures, retry after lost broker acknowledgement, and an HTTP smoke test.
- Terraform: ran `terraform fmt` and `terraform validate` with Terraform 1.9.8 and AzureRM provider 4.81.0. This checks formatting/configuration, not a live deployment.
- Documentation/review: compared changes against the supplied scenario, provider contracts, Salesforce model/API constraints and incident evidence. Checked PR diff line references and ran `git diff --check`.

### Limits and residual risk

- The practical concurrency and failure tests use in-memory adapters/mocks. They do not establish SQL transaction/isolation behavior, cross-process races, real Salesforce/Service Bus behavior, production load capacity, or retry timing under network partitions.
- No load/stress test, live integration test, Azure `plan`/`apply`, drift detection, or Azure DevOps run against an organization was performed. No live cloud resources were deployed. Terraform validation cannot prove permissions, quotas, regional availability, cost, or that an eventual plan is drift-free.
- Coverage is not a correctness guarantee. Those untested behaviors need dedicated integration/load tests and controlled environment validation before production.

## What I discarded or corrected

- **Pipeline target selection:** removed queue-time country selection and pinned the six supported MX/CO environment stages in YAML. This prevents a run from silently targeting an unsupported country or drifting from the reviewed deployment matrix.
- **Country scope:** kept Chile out of the Part B implementation and CD stages until Austral is actually supported. This avoids representing an unimplemented provider path as deployable.
- **Terraform configuration:** corrected the private DNS zone association to the AzureRM-supported `private_dns_zone_ids` attribute and validated the configuration with `terraform validate`. This avoids shipping a plausible-looking but invalid provider argument.
- **Webhook acceptance/replay:** rejected early 2xx acknowledgement and blind DLQ replay. Early acknowledgement can lose a payment notification after a process crash; indiscriminate replay can repeat business effects or let an old capture overwrite a refund. The accepted design commits durable state/outbox before 2xx and replays through the idempotent, ordered path.
- **Incident arithmetic and scope:** refined the INC-0412 analysis to distinguish 4,312 DLQ messages from the 37,215 notifications received and 32,880 upserts. The 4,312 × 10 relationship explains the 43,120 Salesforce 400 responses, but not every received/upsert difference; that remainder must be reconciled rather than claimed as explained. Also treated 66% as reported integration-budget usage, not proof of available shared-org headroom.
- **SLO and dependency limits:** kept freshness targets as objectives and called out that Salesforce outages can last 90 minutes while the one-hour objective is stricter. This avoids promising an impossible outcome during a dependency outage while preserving the durability requirement.
- **Cost/deployment claims:** labeled cost ranges as manual indicative estimates, not Azure Pricing Calculator output, and stated that no Azure deployment or live Azure DevOps run occurred. This avoids implying measured cost or tested cloud behavior.
- **PR-214 example data:** treated the live-looking secret and payer fields in the fictional diff as security issues, not configuration to reuse; no literal value was copied into implementation deliverables.
- **AI disclosure revisions:** after review feedback, replaced the intent-based privacy statement with an explicit account that no automated secret/PII scan or sanitization was performed before the initial context was shared. Added preventive controls as recommendations, not as controls falsely claimed to have run, and documented the webhook architecture that had been missing from the first disclosure draft.

## Webhook architecture and failure behavior

The assistant helped review and document the design; the architecture is specified in [`docs/design/SOLUTION_DESIGN.md`](docs/design/SOLUTION_DESIGN.md), with its MX/CO behavioral stub in [`practical/README.md`](practical/README.md). The intended production flow is:

1. API Manager terminates public HTTPS and applies edge limits. A common controller routes to exactly one provider adapter by its documented signature-header profile; the header selects a verifier but is not trusted as authentication.
2. The adapter verifies HMAC over the **exact raw request bytes** (and the provider timestamp window where specified), then parses and validates the payload and maps it to a provider-neutral event. Unsupported/malformed or unauthenticated requests are rejected; payer fields and raw body do not enter the canonical event.
3. One SQL transaction inserts the inbox/event identity, applies the payment state only if its provider event time is not stale, and inserts the outbox record. Unique constraints protect event and business payment identities; conflicting equal-time states are quarantined rather than guessed.
4. The provider receives 2xx only after that transaction commits or a durable duplicate is confirmed. If validation fails, return 4xx; if durable storage is unavailable, return retryable non-2xx. Thus a crash before commit cannot produce a success acknowledgement, while a crash after commit is recovered from the inbox/outbox.
5. An outbox relay publishes at least once to Service Bus. It retains pending work when publication fails; if the broker accepted a message but its acknowledgement was lost, the stable event/payment keys let consumers deduplicate. Salesforce processing is asynchronous, upserts by its unique payment key, retries transient failures with bounded backoff, and stops/throttles on API-budget exhaustion. Permanent data errors are quarantined/DLQ'd for authorized, reconciled replay through the same idempotent path.

The practical adapter is intentionally an in-memory stub: its locks and persistence do not provide cross-process SQL guarantees. Production implementation still needs transactional SQL, unique indexes, concurrency control/leases for relay claims, and integration/load testing. This distinction prevents a passing prototype test from being mistaken for production durability.

## Scope and review controls used

- I supplied task/repository context and asked for changes tied to explicit deliverables; edits were reviewed through focused diffs and repository references rather than accepting broad rewrites. Provider and Salesforce contract statements were checked against the supplied source files.
- I checked the final changes and paths, examined cited review line numbers, and ran available tests/validators. I corrected scope where the output could exceed the task (Chile in Part B, selectable deployment destinations) and did not treat generated tests as sufficient without behavior assertions.
- These are review steps, not a claim that automated secret scanning or a formal pre-submission data classification scan was run.

## Data shared with AI tools

The assistant received repository text and diffs for this fictional assessment. That included synthetic provider samples and the PR-214 example containing payer fields and a live-looking secret. **I did not perform an automated secret/PII scan or sanitize the repository context before the initial review.** I recognized those values during review, treated them as untrusted example data, and did not copy the literal secret or payer details into implementation deliverables or use the secret to access a service. I cannot independently prove from the repository alone that arbitrary supplied data is synthetic; the assessment identifies these fixtures as fictional.

For real work, the preventive control is to classify the source before sharing any context: use synthetic/minimized excerpts, redact identifiers and payloads, scan for secrets and PII, and share only with an organization-approved tool under the applicable retention/access policy. If sensitive data is found after sharing, stop further disclosure, notify Security/Privacy, and rotate/revoke exposed credentials as appropriate. This assessment did not include a claim that those production controls were executed.
