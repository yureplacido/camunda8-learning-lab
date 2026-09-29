# Domain

The lab models a simplified banking loan-origination process.

## Core flow
Customer → Loan Application → Credit Analysis → Fraud Analysis → Approval → Contract → Disbursement

## Purpose
The domain is intentionally simplified. Its value is in creating realistic workflow and distributed-systems problems:
- synchronous and asynchronous integrations;
- human and automated steps;
- retries and failures;
- correlation;
- idempotency;
- timeouts;
- compensation;
- observability.

The domain should remain simple enough that Camunda concepts remain the focus.
