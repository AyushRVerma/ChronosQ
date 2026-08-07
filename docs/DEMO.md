# ChronosQ Demonstration Guide

This guide provides a repeatable demonstration of ChronosQ's job
submission, distributed claiming, idempotency, monitoring and
worker-failure recovery.

## What this demonstration proves

The demonstration shows that:

- REST clients can submit background jobs
- Jobs are persisted in PostgreSQL
- Multiple workers share the same queue
- PostgreSQL prevents duplicate claiming
- Idempotency keys prevent duplicate jobs
- Metrics and health endpoints are available
- A surviving worker can recover work abandoned by another worker
- Load-test results are measured rather than estimated

## Requirements

Install:

- Docker
- Docker Compose
- PowerShell 7

Create the environment file:

```powershell
Copy-Item .env.example .env