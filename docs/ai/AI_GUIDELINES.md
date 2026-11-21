# Responsible use of AI — Payments Integration

These guidelines apply to AI assistants used for engineering work and to any LLM feature embedded in Meridian services. Follow stricter company, customer, legal and regulatory rules where applicable.

## Everyday engineering use

**Allowed:** use an approved assistant to explain public APIs, brainstorm designs, draft tests or documentation, refactor code, and review de-identified logs or synthetic examples. Treat suggestions as untrusted proposals, not authority. Engineers remain accountable for changes and must understand and be able to explain them.

**Never submit:** real customer or employee personal data (including names, RUT/national IDs, email, policy/payment identifiers, or raw webhook bodies); card, bank or authentication data; provider payloads that contain live data; credentials, signing keys, tokens, connection strings, private certificates, or production configuration; confidential incident details or source code to an unapproved/public tool. Use synthetic fixtures or redact and minimize data first. Do not assume a tool is private because it is paid or has a “no training” setting—use only tools approved by Security/Privacy for the data classification involved.

**Prevent disclosure rather than relying on intent:** before sharing repository context, classify it and inspect the selected files, prompts and attachments. Prefer a minimal excerpt over a repository dump; replace identifiers and payload values with synthetic ones. Run the approved secret/PII scanners on the material to be shared, and stop if findings are unexplained. Use pre-commit/CI secret scanning as a backstop, not permission to send secrets to an assistant. If sensitive data is accidentally shared, stop, report it through Security/Privacy procedures and rotate exposed credentials where applicable.

**Keep work bounded and auditable:** state the task, allowed files, constraints and authoritative contracts in the prompt; ask the assistant to identify assumptions rather than invent requirements. Make small, reviewable changes. Compare the diff to the requested scope, check schemas/types and provider contracts, and remove unrelated or speculative changes. A reviewer should be able to trace each material change to a requirement and validation evidence; split an oversized AI-assisted change into focused PRs where practical.

**For architecture and webhook changes:** require a human-owned design before implementation: trust boundaries and data flow; provider-specific raw-byte signature verification; canonical mapping; durable inbox/state/outbox transaction and the exact point at which 2xx is safe; event/payment idempotency and ordering; timeout, retry, backoff, DLQ and replay behavior; downstream Salesforce limits; privacy and observability. Record material trade-offs in the design/ADR and test failure paths. An assistant must not silently change an API contract, acknowledge before durable acceptance, turn a transient retry into an unbounded loop, or claim an in-memory prototype is production-durable.

## Validation before merge

AI-generated code has the same review bar as any other contribution:

1. The author reads and understands every changed line and checks behavior against requirements, provider contracts, data classifications and threat models.
2. Run formatters, static analysis, dependency/license and security scans, and the full relevant test suite. Add tests for edge cases, failures, authorization, retries, idempotency, ordering and data handling; generated tests are not evidence unless they assert the intended behavior.
3. Review dependencies, generated files, migrations and configuration. Never accept secrets, unsafe logging, unbounded retries, or a new external data flow without explicit review.
4. Have a qualified human review the diff for scope, correctness, security, privacy and maintainability. Security, privacy, payment-state, policy-impact, and infrastructure changes require their normal specialist approvals. CI must pass; an AI review does not replace code review or production validation.

## Disclosure in pull requests

Disclose material AI assistance in the PR description: tool/provider if known, what it helped produce (for example, test scaffolding, a code section, or documentation), and how the result was validated. State whether generated code was substantially rewritten. Do not include prompts containing sensitive information. The human author owns the change, incident response and follow-up regardless of disclosure.

## When a Meridian service calls an LLM

An LLM must not be placed in the authoritative payment-processing path. For a proposed feature that summarizes dead-lettered notifications:

- **Purpose and data:** require a documented use case, privacy/security review, data-flow and retention assessment, and approved provider/model with contractual controls (no training on inputs, bounded retention, appropriate region and access controls). Send only the minimum necessary sanitized error metadata; never send raw webhook bodies, payer data, secrets, or payment tokens. Prefer deterministic templates if they meet the need.
- **Isolation and security:** authenticate and authorize the caller, enforce country/tenant boundaries, encrypt transport and storage, keep prompts/configuration versioned, and audit access. Treat DLQ text and any retrieved content as untrusted data, not instructions; defend against prompt injection and data exfiltration. Do not give the model tools or credentials that can access Salesforce, queues, or customer records.
- **Human control:** output is advisory, clearly labeled as AI-generated, and linked to authorized source event IDs/status records. An operator verifies it before action. The model must never determine payment status, reinstate/suspend a policy, modify Salesforce, or trigger DLQ replay. Keep replay and state transitions behind existing deterministic validation and explicit approval.
- **Reliability and evaluation:** set strict input/output size limits, timeouts, rate and spend limits; handle provider outage with a safe “summary unavailable” fallback. Validate output against a schema, escape rendered text, and guard against fabricated explanations. Before launch, test accuracy, omissions, hallucinations, prompt injection, bias, privacy leakage and country-specific cases using synthetic/de-identified fixtures. Monitor quality, latency, errors, cost and user feedback; retain a kill switch and rollback plan.

If these controls cannot be met, do not ship the LLM feature; use a deterministic, privacy-preserving operational report instead.
