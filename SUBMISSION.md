# Submission

> Reviewer entry point for the completed assessment.

## Candidate

| Field | Value |
|---|---|
| Candidate ID | `jeisonnoc@gmail.com` |
| Final commit SHA | Provided separately in the recruiter email |
| Date and time of submission (with time zone) | 2026-11-21, 4:15 PM (UTC−05:00) |

## Time spent

| Part | Approximate time |
|---|---|
| A. Integration and data design | ≈ 2 h |
| B. Integration component | ≈ 2 h |
| C. Azure IaC and CI/CD | ≈ 1 h 30 min |
| D. Operations | ≈ 45 min |
| E. Code review | ≈ 45 min |
| F. Responsible AI | ≈ 45 min |
| **Total** | **≈ 7 h 45 min** |

**Nota:** Son estimaciones aproximadas reconstruidas comparando las sesiones de trabajo y los commits; no son un registro de tiempo exacto.

## Stack

- Practical part (Part B): Java 21, Spring Boot 3.5.16, Maven, JUnit 5.
- Infrastructure as code: Terraform (`>= 1.9.0, < 2.0.0`); AzureRM provider `~> 4.80`.
- Pipeline: Azure Pipelines (`azure-pipelines.yml`).
- CI run link: Not run against a live Azure DevOps organization.

## Where to start reading

1. [`docs/design/SOLUTION_DESIGN.md`](docs/design/SOLUTION_DESIGN.md) — architecture, canonical event, ordering, idempotency, error handling and capacity.
2. [`practical/README.md`](practical/README.md) — implemented MX/CO webhook intake, test coverage and production boundaries.
3. [`docs/operations/INC-0412-RESPONSE.md`](docs/operations/INC-0412-RESPONSE.md) — incident diagnosis, containment and safe DLQ replay.

Infrastructure and pipeline details are in [`infra/README.md`](infra/README.md) and [`azure-pipelines.yml`](azure-pipelines.yml).

## Known gaps and what I would do next

1. Replace the practical in-memory persistence and broker stubs with transactional Azure SQL and Service Bus adapters. Add cross-instance concurrency, lease/lock, recovery and integration tests before production.
2. Implement the Salesforce worker and protected operational status/replay APIs; validate OAuth, API limits, record-level errors, and DLQ handling against a sandbox.
3. Run load and resilience tests, then perform a reviewed Terraform plan and deployment in a non-production Azure subscription. Validate Azure DevOps environments, approvals, variable groups, service connections, permissions, quotas and actual cost.
4. Before public submission, run secret/PII scanning over the repository and its history, review any findings (including fictional assessment fixtures), and confirm the repository visibility and recruiter-specific submission details.
