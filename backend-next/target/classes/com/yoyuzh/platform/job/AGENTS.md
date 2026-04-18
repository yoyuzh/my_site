# Platform Job AGENTS

## Responsibility

- Own async job state machines, retry policy, leases, idempotency keys, and execution records.

## Prohibitions

- Do not own file, share, workspace, or transfer business truth.
- Other modules may depend only on `platform.job.api`.
