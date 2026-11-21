variable "environment" {
  description = "Deployment stage."
  type        = string
  validation {
    condition     = contains(["dev", "test", "prod"], var.environment)
    error_message = "environment must be dev, test, or prod."
  }
}

variable "country" {
  description = "Country deployment code. Chile is supported by the target design; the practical app implements MX and CO."
  type        = string
  validation {
    condition     = contains(["mx", "co", "cl"], var.country)
    error_message = "country must be mx, co, or cl."
  }
}

variable "location" {
  description = "Azure region."
  type        = string
  default     = "eastus"
}

variable "container_image" {
  description = "Immutable container image URI published by the application release process."
  type        = string
}

variable "enable_intake" {
  description = "Create the Container App after its country-specific Key Vault secret has been provisioned."
  type        = bool
  default     = false
}

variable "worker_container_image" {
  description = "Immutable processor image published by its release process; not implemented by Part B."
  type        = string
  default     = ""
}

variable "enable_processor" {
  description = "Create the downstream payment processor after its implementation and image are available."
  type        = bool
  default     = false
  validation {
    condition     = !var.enable_processor || length(var.worker_container_image) > 0
    error_message = "worker_container_image must be set when enable_processor is true."
  }
}

variable "api_manager_egress_cidrs" {
  description = "API Manager outbound CIDR ranges allowed to call the intake endpoint."
  type        = list(string)
  validation {
    condition     = length(var.api_manager_egress_cidrs) > 0
    error_message = "At least one API Manager egress CIDR is required; an empty allow-list would expose the app."
  }
}

variable "sql_entra_admin_login" {
  description = "Microsoft Entra administrator display name for SQL bootstrap."
  type        = string
}

variable "sql_entra_admin_object_id" {
  description = "Object ID of the Microsoft Entra administrator group."
  type        = string
}
