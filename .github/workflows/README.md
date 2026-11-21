# CI/CD location

This assessment uses **Azure Pipelines**, not GitHub Actions. The pipeline entry point is [`azure-pipelines.yml`](../../azure-pipelines.yml), with the reusable deployment-stage template in [`pipelines/templates/deploy-stage.yml`](../../pipelines/templates/deploy-stage.yml).

This directory contains no GitHub Actions workflows; the README is retained only to make the chosen CI/CD location explicit.
