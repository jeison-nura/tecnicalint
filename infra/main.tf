resource "random_string" "suffix" {
  length  = 6
  upper   = false
  special = false
  numeric = true
  keepers = {
    environment = var.environment
    country     = var.country
  }
}

locals {
  prefix         = "pay-${var.environment}-${var.country}-${random_string.suffix.result}"
  tags           = { system = "premium-payment-intake", environment = var.environment, country = var.country, managed_by = "terraform" }
  prod           = var.environment == "prod"
  sql_sku        = local.prod ? "S3" : "S0"
  sql_retention  = local.prod ? 35 : 7
  vnet_cidr      = "10.40.0.0/16"
  aca_subnet     = "10.40.0.0/23"
  private_subnet = "10.40.2.0/24"
  provider_secret = {
    mx = {
      key_vault_name = "QuetzalWebhookSecret"
      secret_name    = "quetzal-webhook-secret"
      env_name       = "QUETZAL_WEBHOOK_SECRET"
    }
    co = {
      key_vault_name = "AndinaWebhookSecret"
      secret_name    = "andina-webhook-secret"
      env_name       = "ANDINA_WEBHOOK_SECRET"
    }
    cl = {
      key_vault_name = "AustralWebhookSecret"
      secret_name    = "austral-webhook-secret"
      env_name       = "AUSTRAL_WEBHOOK_SECRET"
    }
  }[var.country]
}

resource "azurerm_resource_group" "main" {
  name     = "rg-${local.prefix}"
  location = var.location
  tags     = local.tags
}

resource "azurerm_log_analytics_workspace" "main" {
  name                = "${local.prefix}-logs"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "PerGB2018"
  retention_in_days   = local.prod ? 90 : 30
  daily_quota_gb      = local.prod ? 5 : 1
  tags                = local.tags
}

resource "azurerm_application_insights" "main" {
  name                 = "${local.prefix}-appi"
  location             = azurerm_resource_group.main.location
  resource_group_name  = azurerm_resource_group.main.name
  workspace_id         = azurerm_log_analytics_workspace.main.id
  application_type     = "web"
  daily_data_cap_in_gb = local.prod ? 5 : 1
  retention_in_days    = local.prod ? 90 : 30
  tags                 = local.tags
}

resource "azurerm_virtual_network" "main" {
  name                = "${local.prefix}-vnet"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  address_space       = [local.vnet_cidr]
  tags                = local.tags
}

resource "azurerm_subnet" "container_apps" {
  name                 = "snet-container-apps"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [local.aca_subnet]

  delegation {
    name = "container-apps"
    service_delegation {
      name    = "Microsoft.App/environments"
      actions = ["Microsoft.Network/virtualNetworks/subnets/join/action"]
    }
  }
}

resource "azurerm_subnet" "private_endpoints" {
  name                              = "snet-private-endpoints"
  resource_group_name               = azurerm_resource_group.main.name
  virtual_network_name              = azurerm_virtual_network.main.name
  address_prefixes                  = [local.private_subnet]
  private_endpoint_network_policies = "Disabled"
}

resource "azurerm_private_dns_zone" "sql" {
  name                = "privatelink.database.windows.net"
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.tags
}

resource "azurerm_private_dns_zone" "servicebus" {
  name                = "privatelink.servicebus.windows.net"
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.tags
}

resource "azurerm_private_dns_zone" "key_vault" {
  name                = "privatelink.vaultcore.azure.net"
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.tags
}

resource "azurerm_private_dns_zone_virtual_network_link" "sql" {
  name                  = "sql-link"
  resource_group_name   = azurerm_resource_group.main.name
  private_dns_zone_name = azurerm_private_dns_zone.sql.name
  virtual_network_id    = azurerm_virtual_network.main.id
  registration_enabled  = false
  tags                  = local.tags
}

resource "azurerm_private_dns_zone_virtual_network_link" "servicebus" {
  name                  = "servicebus-link"
  resource_group_name   = azurerm_resource_group.main.name
  private_dns_zone_name = azurerm_private_dns_zone.servicebus.name
  virtual_network_id    = azurerm_virtual_network.main.id
  registration_enabled  = false
  tags                  = local.tags
}

resource "azurerm_private_dns_zone_virtual_network_link" "key_vault" {
  name                  = "key-vault-link"
  resource_group_name   = azurerm_resource_group.main.name
  private_dns_zone_name = azurerm_private_dns_zone.key_vault.name
  virtual_network_id    = azurerm_virtual_network.main.id
  registration_enabled  = false
  tags                  = local.tags
}

resource "azurerm_container_app_environment" "main" {
  name                           = "${local.prefix}-cae"
  location                       = azurerm_resource_group.main.location
  resource_group_name            = azurerm_resource_group.main.name
  log_analytics_workspace_id     = azurerm_log_analytics_workspace.main.id
  infrastructure_subnet_id       = azurerm_subnet.container_apps.id
  internal_load_balancer_enabled = false
  zone_redundancy_enabled        = local.prod
  tags                           = local.tags
}

resource "azurerm_key_vault" "main" {
  name                          = "kv${var.environment}${var.country}${random_string.suffix.result}"
  location                      = azurerm_resource_group.main.location
  resource_group_name           = azurerm_resource_group.main.name
  tenant_id                     = data.azurerm_client_config.current.tenant_id
  sku_name                      = "standard"
  rbac_authorization_enabled    = true
  soft_delete_retention_days    = local.prod ? 90 : 30
  purge_protection_enabled      = true
  public_network_access_enabled = false
  tags                          = local.tags
}

resource "azurerm_user_assigned_identity" "intake" {
  name                = "${local.prefix}-intake-id"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.tags
}

resource "azurerm_user_assigned_identity" "processor" {
  name                = "${local.prefix}-processor-id"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.tags
}

resource "azurerm_mssql_server" "main" {
  name                          = "sql-${local.prefix}"
  resource_group_name           = azurerm_resource_group.main.name
  location                      = azurerm_resource_group.main.location
  version                       = "12.0"
  administrator_login           = "sqlbootstrap"
  administrator_login_password  = random_password.sql_bootstrap.result
  minimum_tls_version           = "1.2"
  public_network_access_enabled = false
  azuread_administrator {
    login_username              = var.sql_entra_admin_login
    object_id                   = var.sql_entra_admin_object_id
    tenant_id                   = data.azurerm_client_config.current.tenant_id
    azuread_authentication_only = true
  }
  tags = local.tags
}

resource "azurerm_mssql_server_extended_auditing_policy" "main" {
  server_id              = azurerm_mssql_server.main.id
  enabled                = true
  log_monitoring_enabled = true
  retention_in_days      = local.prod ? 90 : 30
}

resource "random_password" "sql_bootstrap" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "azurerm_mssql_database" "main" {
  name           = "payments-${var.environment}-${var.country}"
  server_id      = azurerm_mssql_server.main.id
  sku_name       = local.sql_sku
  zone_redundant = false
  short_term_retention_policy {
    retention_days = local.sql_retention
  }
  tags = local.tags
}

resource "azurerm_servicebus_namespace" "main" {
  name                          = "${local.prefix}-sb"
  location                      = azurerm_resource_group.main.location
  resource_group_name           = azurerm_resource_group.main.name
  sku                           = "Standard"
  minimum_tls_version           = "1.2"
  local_auth_enabled            = false
  public_network_access_enabled = false
  tags                          = local.tags
}

resource "azurerm_servicebus_topic" "payments" {
  name                                    = "payment-events"
  namespace_id                            = azurerm_servicebus_namespace.main.id
  default_message_ttl                     = "P14D"
  requires_duplicate_detection            = true
  duplicate_detection_history_time_window = "PT10M"
  partitioning_enabled                    = true
}

resource "azurerm_servicebus_subscription" "consumers" {
  for_each = toset(["payment-processing", "finance", "notifications"])

  name                                 = each.key
  topic_id                             = azurerm_servicebus_topic.payments.id
  max_delivery_count                   = 10
  dead_lettering_on_message_expiration = true
  default_message_ttl                  = "P14D"
  lock_duration                        = "PT1M"
}

resource "azurerm_container_app" "intake" {
  count                        = var.enable_intake ? 1 : 0
  name                         = "${local.prefix}-intake"
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.intake.id]
  }
  dynamic "secret" {
    for_each = [local.provider_secret]
    content {
      name                = secret.value.secret_name
      identity            = azurerm_user_assigned_identity.intake.id
      key_vault_secret_id = "${azurerm_key_vault.main.vault_uri}secrets/${secret.value.key_vault_name}"
    }
  }
  ingress {
    external_enabled           = true
    target_port                = 8080
    transport                  = "http"
    allow_insecure_connections = false
    traffic_weight {
      percentage      = 100
      latest_revision = true
    }
    dynamic "ip_security_restriction" {
      for_each = var.api_manager_egress_cidrs
      content {
        name             = "apim-${ip_security_restriction.key}"
        description      = "Allow only API Manager egress."
        ip_address_range = ip_security_restriction.value
        action           = "Allow"
      }
    }
  }
  template {
    min_replicas = local.prod ? 2 : 0
    max_replicas = local.prod ? 20 : 3
    http_scale_rule {
      name                = "http-concurrency"
      concurrent_requests = "40"
    }
    container {
      name   = "payment-intake"
      image  = var.container_image
      cpu    = 0.5
      memory = "1Gi"
      dynamic "env" {
        for_each = [local.provider_secret]
        content {
          name        = env.value.env_name
          secret_name = env.value.secret_name
        }
      }
      env {
        name  = "AZURE_SQL_SERVER"
        value = "${azurerm_mssql_server.main.name}.database.windows.net"
      }
      env {
        name  = "AZURE_SQL_DATABASE"
        value = azurerm_mssql_database.main.name
      }
      env {
        name  = "AZURE_CLIENT_ID"
        value = azurerm_user_assigned_identity.intake.client_id
      }
      env {
        name  = "SERVICE_BUS_NAMESPACE"
        value = azurerm_servicebus_namespace.main.name
      }
      env {
        name  = "APPLICATIONINSIGHTS_CONNECTION_STRING"
        value = azurerm_application_insights.main.connection_string
      }
    }
  }
  depends_on = [
    azurerm_role_assignment.service_bus_sender,
    azurerm_role_assignment.key_vault_secrets_user
  ]
  tags = local.tags
}

resource "azurerm_container_app" "processor" {
  count                        = var.enable_processor ? 1 : 0
  name                         = "${local.prefix}-processor"
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.processor.id]
  }
  template {
    min_replicas = local.prod ? 1 : 0
    max_replicas = local.prod ? 10 : 2
    container {
      name   = "payment-processor"
      image  = var.worker_container_image
      cpu    = 0.5
      memory = "1Gi"
      env {
        name  = "AZURE_CLIENT_ID"
        value = azurerm_user_assigned_identity.processor.client_id
      }
      env {
        name  = "AZURE_SQL_SERVER"
        value = "${azurerm_mssql_server.main.name}.database.windows.net"
      }
      env {
        name  = "AZURE_SQL_DATABASE"
        value = azurerm_mssql_database.main.name
      }
      env {
        name  = "SERVICE_BUS_NAMESPACE"
        value = azurerm_servicebus_namespace.main.name
      }
      env {
        name  = "SERVICE_BUS_TOPIC"
        value = azurerm_servicebus_topic.payments.name
      }
      env {
        name  = "SERVICE_BUS_SUBSCRIPTION"
        value = "payment-processing"
      }
    }
  }
  depends_on = [
    azurerm_role_assignment.service_bus_receiver
  ]
  tags = local.tags
}

resource "azurerm_private_endpoint" "sql" {
  name                = "${local.prefix}-sql-pe"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  subnet_id           = azurerm_subnet.private_endpoints.id
  private_service_connection {
    name                           = "${local.prefix}-sql-connection"
    private_connection_resource_id = azurerm_mssql_server.main.id
    subresource_names              = ["sqlServer"]
    is_manual_connection           = false
  }
  private_dns_zone_group {
    name                 = "sql"
    private_dns_zone_ids = [azurerm_private_dns_zone.sql.id]
  }
  tags = local.tags
}

resource "azurerm_private_endpoint" "servicebus" {
  name                = "${local.prefix}-sb-pe"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  subnet_id           = azurerm_subnet.private_endpoints.id
  private_service_connection {
    name                           = "${local.prefix}-sb-connection"
    private_connection_resource_id = azurerm_servicebus_namespace.main.id
    subresource_names              = ["namespace"]
    is_manual_connection           = false
  }
  private_dns_zone_group {
    name                 = "servicebus"
    private_dns_zone_ids = [azurerm_private_dns_zone.servicebus.id]
  }
  tags = local.tags
}

resource "azurerm_private_endpoint" "key_vault" {
  name                = "${local.prefix}-kv-pe"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  subnet_id           = azurerm_subnet.private_endpoints.id
  private_service_connection {
    name                           = "${local.prefix}-kv-connection"
    private_connection_resource_id = azurerm_key_vault.main.id
    subresource_names              = ["vault"]
    is_manual_connection           = false
  }
  private_dns_zone_group {
    name                 = "key-vault"
    private_dns_zone_ids = [azurerm_private_dns_zone.key_vault.id]
  }
  tags = local.tags
}

resource "azurerm_role_assignment" "service_bus_sender" {
  scope                = azurerm_servicebus_topic.payments.id
  role_definition_name = "Azure Service Bus Data Sender"
  principal_id         = azurerm_user_assigned_identity.intake.principal_id
  principal_type       = "ServicePrincipal"
}

resource "azurerm_role_assignment" "key_vault_secrets_user" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_user_assigned_identity.intake.principal_id
  principal_type       = "ServicePrincipal"
}

resource "azurerm_role_assignment" "service_bus_receiver" {
  scope                = azurerm_servicebus_subscription.consumers["payment-processing"].id
  role_definition_name = "Azure Service Bus Data Receiver"
  principal_id         = azurerm_user_assigned_identity.processor.principal_id
  principal_type       = "ServicePrincipal"
}
