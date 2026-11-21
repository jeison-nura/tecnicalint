# Practical — Payment notification intake (MX, CO)

## Stack and scope

- Java 21
- Spring Boot 3.5.16
- Maven
- JUnit 5 via `spring-boot-starter-test`

The OpenAPI contract and overall design include Chile, but this practical implementation covers Quetzal (MX) and Andina (CO), as required by Part B.

## Build and tests

From the repository root, with JDK 21 and Maven installed:

```bash
mvn -f practical/pom.xml clean verify
```

`verify` runs the JUnit 5 suite, generates `practical/target/site/jacoco/index.html`, and fails if JaCoCo line coverage is below 85%. Tests use the supplied HMAC vectors and reconstruct the specified LF/no-final-newline fixture bytes if Git's Windows checkout conversion changed their line endings. Production requests are verified over untouched incoming bytes. An injected `Clock` makes timestamp checks deterministic.

## Run locally

Set only the supplied fictional test secrets:

```bash
export QUETZAL_WEBHOOK_SECRET='qz_test_whsec_4f8a1c2e9b7d'
export ANDINA_WEBHOOK_SECRET='andina_test_secret_7c3e19ab'
mvn -f practical/pom.xml spring-boot:run
```

On PowerShell, use `$env:QUETZAL_WEBHOOK_SECRET=...` and `$env:ANDINA_WEBHOOK_SECRET=...`. During Quetzal rotation, optionally set `QUETZAL_PREVIOUS_WEBHOOK_SECRET`.

The only endpoint is `POST /webhooks/v1/payments`. The provider adapter factory selects exactly one adapter from the existing signature-header profile:

- Quetzal: `Quetzal-Signature`
- Andina: `X-Andina-Timestamp` plus `X-Andina-Signature`

Header presence selects a verification strategy; it does not authenticate the sender. The selected adapter verifies HMAC over exact request bytes before parsing. Missing or ambiguous profiles are rejected.

HTTP 200 is returned after the inbox/outbox store accepts the event or confirms a duplicate. It does not wait for broker publication. Invalid signature/payload returns 401/400; missing secrets or store failure returns 503.

## Hexagonal / DDD structure

- `domain/model/`: provider-neutral provider/status types, canonical event, invariant-enforcing builder and ordered `PaymentState`.
- `application/usecase/`: webhook intake and outbox relay use cases; no HTTP, Spring or database code.
- `error/`: transport/application failures that are not domain invariants.
- `port/`: shared contracts used by the use cases and implemented by adapters.
- `domain/repository/`: `PaymentNotificationRepository`, the repository abstraction for inbox/outbox persistence.
- `domain/exception/`: domain integrity failures, such as reusing an event ID with conflicting business content.
- `adapter/http/`: the single Spring MVC controller and HTTP error translation.
- `adapter/provider/`: provider adapter factory and anti-corruption adapters. Security verifiers inherit shared HMAC/secret handling from `AbstractWebhookSignatureVerifier`; provider subclasses implement their header format and timestamp rules.
- `adapter/secrets/`: local environment-backed secret adapter.
- `adapter/persistence/`: in-memory repository stub; a SQL/JPA adapter can implement the same repository.
- `adapter/messaging/`: broker stub and scheduled outbox relay trigger.
- `src/test/`: vectors, adapter selection, mapping, concurrent duplicate intake, conflicting event IDs, out-of-order/regressive state transitions under concurrency, broker-ack-loss redelivery, malformed amount, stale timestamp and dependency-failure tests.

## Stubs and production boundary

- `WebhookSecretProviderPort` is backed locally by environment variables. Production must use Key Vault through managed identity; secrets are never logged.
- `PaymentNotificationRepository` is backed by an in-memory inbox/outbox stub. It demonstrates idempotency and the repository boundary but is **not durable across process restarts**.
- `CanonicalEventPublisherPort` is an in-memory broker stub. The relay retries pending records; downstream consumers deduplicate by stable `eventId`. Production must replace both stubs with transactional durable storage and a real broker.
- The in-memory repository serializes inbox/state/outbox acceptance only within this JVM. A production SQL repository must use a transaction, unique constraints and optimistic versioning or row locking to preserve that behavior across instances.
- The stub does not claim outbox rows across relay instances. A production SQL repository must claim with a lease/lock and still preserve at-least-once semantics; consumers must deduplicate because a broker acknowledgement can be lost after acceptance.
- Domain integrity errors live under `domain/exception`; invalid input/dependency failures live outside the domain; signature and secret errors belong to the security adapter. HTTP status mapping stays in `adapter/http`.
- No Salesforce, payment provider, Azure resource or real broker is called. Salesforce writing is outside Part B.

## Security and known gaps

- Payer data and payment tokens are not copied into the canonical event or logged.
- Amount precision is constrained to fit Salesforce `Currency(16,2)`; policy references are country-validated.
- The practical stub applies event-time ordering and prevents pending/refunded-state regressions in-process. Cross-instance ordering, production SQL/outbox durability, DLQ/replay and production observability remain target-design responsibilities.
- API Manager must enforce TLS, request-size limits and routing. No operations query API is implemented in Part B.

## Validation status

JDK 21 is available in the workspace. `mvn -f practical/pom.xml clean verify` passes 20 tests, including a real HTTP smoke test that submits a signed Quetzal webhook and its duplicate, and enforces the 85% JaCoCo line-coverage minimum; the measured line coverage is 89.9%. All seven supplied Quetzal/Andina HMAC vectors are covered by automated tests.
