output "resource_group_name" {
  value = azurerm_resource_group.main.name
}

output "container_app_fqdn" {
  value = try(azurerm_container_app.intake[0].ingress[0].fqdn, null)
}

output "processor_app_name" {
  value = try(azurerm_container_app.processor[0].name, null)
}

output "sql_server_fqdn" {
  value = azurerm_mssql_server.main.fully_qualified_domain_name
}

output "sql_database_name" {
  value = azurerm_mssql_database.main.name
}

output "service_bus_namespace" {
  value = azurerm_servicebus_namespace.main.name
}

output "key_vault_uri" {
  value = azurerm_key_vault.main.vault_uri
}
