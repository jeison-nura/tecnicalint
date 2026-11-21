# Azure infrastructure

This directory uses **Terraform** with `hashicorp/azurerm` 4.x. The configuration provisions one isolated country/environment stack: Azure Container Apps for intake and the separately gated processor, Azure SQL Database, Service Bus, Key Vault, private networking, and Azure Monitor/Application Insights. It does not deploy anything during validation.

## Layout and parameters

- `versions.tf`: Terraform/provider constraints and the remote Azure Blob state backend.
- `variables.tf`: environment, country, region, image, API Manager source ranges, and Entra SQL bootstrap administrator.
- `main.tf`: resources, managed identity/RBAC, private endpoints, and dead-letter-capable Service Bus subscriptions.
- `outputs.tf`: runtime endpoints and resource names.

Deploy separately for `dev`, `test`, and `prod`, and for the countries that need isolation (`mx`, `co`, `cl`). The Part B application handles Mexico and Colombia; Chile is represented in the target infrastructure design, not implemented by the practical component.

Create the remote state storage account and `tfstate` container in a protected bootstrap step before `terraform init`. The Azure Pipelines variable groups provide `TFSTATE_RESOURCE_GROUP`, `TFSTATE_STORAGE_ACCOUNT`, and `TFSTATE_CONTAINER`; the pipeline generates a different state key per environment and country. The state can contain a generated SQL bootstrap password: enable blob versioning/soft delete, restrict the state data-plane role, and never commit state files.

Provider webhook secret **values** are not Terraform inputs and are not created or stored in state. The first apply leaves `enable_intake=false` (the default) and provisions the platform resources. Create the country-specific Key Vault secret through an approved secret-management process (`QuetzalWebhookSecret`, `AndinaWebhookSecret`, or `AustralWebhookSecret`), then set `ENABLE_INTAKE=true` in the environment variable group and rerun the pipeline. A user-assigned managed identity resolves the Key Vault reference and sends to the Service Bus topic. The processor app is separately gated by `ENABLE_PROCESSOR` and an externally built `WORKER_CONTAINER_IMAGE`; it receives from the `payment-processing` subscription using its own identity. This worker is part of the target design, not the Part B implementation. The generated SQL bootstrap credential is state-backed and must be protected; the workloads are intended to use managed identity and least-privileged contained SQL principals provisioned by reviewed database migrations.

## Validation (no deployment)

Validated locally with Terraform CLI 1.9.8 and AzureRM provider 4.81.0; no Azure deployment was performed. The Azure Pipeline repeats:

```bash
terraform fmt -check -recursive infra
terraform -chdir=infra init -backend=false -input=false
terraform -chdir=infra validate
```

Run those commands locally before opening a PR. Neither validation nor this assessment deploys Azure resources. CD is defined in `azure-pipelines.yml`; it is not executed here. The pipeline uses the Azure DevOps `TerraformInstaller@1` task; install its Terraform extension in the organization.

## Pipeline setup and production controls

Create Azure DevOps environments `dev-mx`, `dev-co`, `test-mx`, `test-co`, `prod-mx`, and `prod-co`; configure approvals/checks for both `test-*` and `prod-*` environments. Create Azure Resource Manager service connections using **workload identity federation**, and grant each pipeline identity only the deployment permissions needed for its environment/resource group. Configure variable groups named `payment-intake-<environment>-<country>` (six groups for dev/test/prod × mx/co) with `AZURE_SERVICE_CONNECTION`, `AZURE_LOCATION`, `CONTAINER_IMAGE`, `ENABLE_INTAKE` (`false` until the Key Vault secret is provisioned), `WORKER_CONTAINER_IMAGE`, `ENABLE_PROCESSOR` (`false` until processor code/image exist), `APIM_EGRESS_CIDRS_JSON`, `SQL_ENTRA_ADMIN_LOGIN`, `SQL_ENTRA_ADMIN_OBJECT_ID`, `TFSTATE_RESOURCE_GROUP`, `TFSTATE_STORAGE_ACCOUNT`, and `TFSTATE_CONTAINER`. Protect the state backend and restrict its data-plane role separately.

The Terraform creates private endpoints and DNS links for SQL, Service Bus, and Key Vault. Container App ingress is restricted to the configured API Manager egress CIDRs. The API Manager itself is assumed to be an existing enterprise resource. Review subnet sizing, API Manager routing, SQL workload identity grants, backup/restore, and production Service Bus tier before deployment.

## CI/CD gates

- Every PR and `main` update runs Terraform formatting and validation without Azure credentials; this catches malformed HCL and formatting drift early.
- Checkov scans Terraform on each run and fails on insecure IaC findings; any exception should be narrowly documented and reviewed, not globally suppressed.
- `mvn clean verify` runs practical tests and enforces the 85% line-coverage floor, protecting webhook signature, retry, and idempotency behavior.
- Only successful `main` builds proceed through dev → test → prod. Test and production use Azure DevOps environment approvals/checks and federated service connections; no Azure client secret is stored in the pipeline.
- The pipeline explicitly deploys only `mx` and `co`, the countries supported by the current intake; the country cannot be chosen at queue time. Terraform itself accepts `cl` for a future reviewed extension, but Chile is not in CD until its application image supports Austral. Terraform state keys are separate by environment and country. The intake and processor remain disabled until their prerequisites (Key Vault secret and processor image) are ready.
