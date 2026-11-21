terraform {
  required_version = ">= 1.9.0, < 2.0.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.80"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.7"
    }
  }

  # Provision the state account/container separately, then pass backend config
  # at init time. State contains generated SQL bootstrap credentials.
  backend "azurerm" {}
}

provider "azurerm" {
  features {}
}

provider "random" {}

data "azurerm_client_config" "current" {}
