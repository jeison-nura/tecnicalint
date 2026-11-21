# Azure services and cost estimate

The figures below are **manually estimated order-of-magnitude ranges**, not a quote and not output copied from a pricing tool. Prices vary by region, agreement, currency, retention, redundancy, and actual usage. Recalculate the chosen SKUs and workload assumptions with Microsoft's official [Azure Pricing Calculator](https://azure.microsoft.com/pricing/calculator/) before approval. Infrastructure is not deployed for this assessment.

## Service choices

| Need | Selected service / tier | Why it fits | Alternatives considered |
|---|---|---|---|
| Webhook runtime | Azure Container Apps, Consumption profile; 0–3 replicas in dev/test, 2–20 in prod | Runs the Java 21 service with HTTP ingress, scale-to-zero outside production, managed identity, and VNet integration. An immutable image is supplied by the release process. | App Service is simpler for a single web app but less convenient for consumption scaling and VNet-integrated worker evolution. AKS adds cluster operations and cost without a workload requiring Kubernetes. |
| Transactional inbox, payment state, outbox | Azure SQL Database, General Purpose provisioned (S0 in non-prod, S3 starting point in prod) | Relational transactions and unique indexes support atomic inbox/state/outbox writes, ordering, and operational lookup. Private Endpoint and Entra-only authentication are configured. | Cosmos DB can scale horizontally but makes the multi-row transactional model and ad-hoc operational queries less direct. Storage queues alone cannot atomically coordinate payment state and outbox. |
| Durable event transport | Azure Service Bus Standard in non-prod; evaluate Premium for production isolation/availability | Topics fan out canonical events; subscriptions isolate processors/consumers; built-in dead-letter subqueues, duplicate detection, and delivery counts support replay operations. | Storage Queue is cheaper but lacks topic/subscription fan-out. Event Hubs is optimized for telemetry streams, not this command/event workflow and per-message DLQ handling. |
| Provider secrets | Azure Key Vault Standard with RBAC, purge protection, private endpoint | Central secret storage and managed-identity access; webhook secret values stay out of source control and Terraform variables/state. | App settings or pipeline variables increase duplication and exposure; HSM-backed Premium keys are unnecessary for shared-secret HMAC in this design. |
| Logs, metrics, traces | Log Analytics workspace and workspace-based Application Insights | Centralized platform logs, application telemetry, alerting, and retention controls. Daily ingestion caps limit accidental cost growth. | Self-managed ELK/Prometheus/Grafana needs operating capacity; Datadog is a valid enterprise alternative but is not assumed in this Azure baseline. |
| Private connectivity | VNet-integrated Container Apps environment, private endpoints and private DNS for SQL, Service Bus, and Key Vault | Keeps data-plane endpoints off the public network; API Manager remains the public ingress boundary and its egress ranges are allow-listed at the app. | Public endpoints with IP allowlists are easier to start but widen exposure and depend on stable egress ranges. |
| Terraform state | Existing Azure Storage Blob backend, Entra data-plane auth, separate key per environment/country | Provides locking and centralized state access. The storage account/container is bootstrapped separately to avoid a backend creation cycle. | Local state is unsafe for team pipelines; HCP Terraform is an alternative if Meridian adopts it. |

The baseline models independent environment/country stacks. In practice, shared regional services (for example, a Service Bus namespace or Log Analytics workspace) may be consolidated after reviewing isolation, throughput, and blast-radius requirements. Production must validate Service Bus Premium availability in the selected region and decide whether SQL zone redundancy/geo-recovery is required.

## Monthly cost range (USD, illustrative)

Assumptions: one environment/country stack in one US region, 30-day month; roughly 15,000 notifications/day average with 120,000/day month-end peak and 40 requests/second bursts; 0.5 vCPU / 1 GiB replicas, averaging one intake replica plus one processor replica in production and scale-to-zero in non-production; moderate SQL storage and 1–5 GB/day combined logs/telemetry; three private endpoints; no inter-region replication or unusually high egress. Production SKU sizing must be load-tested before commitment.

| Cost area | Non-production per country/environment | Production per country/environment |
|---|---:|---:|
| Container Apps compute | ~$0–$40 | ~$40–$180 |
| Azure SQL Database | ~$20–$80 | ~$100–$350 |
| Service Bus | ~$10–$50 | ~$50–$300 (Premium capacity, if selected) |
| Key Vault and secret operations | ~$1–$10 | ~$2–$20 |
| Log Analytics / Application Insights | ~$10–$80 | ~$30–$200 |
| Private endpoints, DNS, network data | ~$25–$80 | ~$25–$120 |
| **Indicative subtotal** | **~$70–$340/month** | **~$250–$1,170/month** |

These ranges deliberately include uncertainty, especially for SQL, Service Bus Premium, and log ingestion. A production rollout for all three countries could therefore be on the order of **$750–$3,500/month** before shared-service optimization. The estimate excludes the existing API Manager, Salesforce, CI agent minutes, state-storage bootstrap, support plans, tax, and data-transfer charges beyond the stated assumption. Log ingestion and always-on compute are the main levers to monitor.

## Well-Architected considerations

- **Reliability:** durable SQL transaction/outbox before acknowledging; Service Bus subscriptions and DLQ; production replicas start at two. Configure alerts on oldest outbox/DLQ age and test restore/replay. Do not claim the one-hour freshness SLO during a longer Salesforce outage.
- **Security:** TLS 1.2+, private data-plane endpoints, managed identities, RBAC scoped to topic/vault, Entra-only SQL authentication, restricted API ingress, protected Terraform state, and no provider secrets in Git. Apply environment approvals and workload identity federation in Azure Pipelines.
- **Cost optimization:** scale-to-zero outside prod, per-workspace daily ingestion caps, retention by environment, and start with SQL S0/S3 sizing. Review actual metrics and use the Pricing Calculator before promoting SKUs.
- **Operational excellence:** plan/apply are pipeline controlled, production requires approval, and infrastructure changes use Terraform state locking. Keep schema migrations and secret rotation auditable.
- **Performance efficiency:** validate against the 40/s burst and Salesforce limits with load tests; tune Container Apps scaling, Service Bus throughput, and SQL tier from measurements rather than the estimates above.
