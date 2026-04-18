# Backend-Next Directory Responsibilities

This document defines what each directory in `backend-next/` is allowed to contain. If a class does not fit the owning directory contract, it belongs somewhere else.

## Top-Level Directories

| Directory | Must contain | Must not contain |
| --- | --- | --- |
| `boot` | Spring Boot startup, configuration, security filters, MVC wiring, exception mapping, OpenAPI wiring | business rules, business repositories, business state machines |
| `shared.kernel` | minimal shared exceptions, base value objects, domain event markers, auth context abstractions, non-business constants | business DTOs, business commands, module facades, file/share/transfer policy types |
| `infra` | broker, lock, cache, external client, tracing, retry, id generation, serialization, other pure technical infrastructure | business entity models, repository abstractions, final business decisions |
| `<module>/api` | public module contracts, command/query DTOs, facade interfaces | controllers, entities, ORM models, repository implementations |
| `<module>/internal.web` | controllers, request/response DTOs, web assemblers, protocol adapters | transaction orchestration, repository calls, business rule decisions |
| `<module>/internal.application` | use-case handlers, app services, transaction boundaries, event publishing, cross-module orchestration through APIs | HTTP details, persistence implementation details, long rule-heavy state machines that belong in domain |
| `<module>/internal.domain` | aggregates, entities, value objects, domain services, rules, state machines, domain events, repository abstractions | Spring MVC, ORM implementations, SDK clients, request/response DTOs |
| `<module>/internal.infra` | repository implementations, mapper/dao, entity mappings, cache implementations, OSS/S3/local storage adapters, MQ adapters, converters | controllers, final rule ownership, cross-module orchestration |

## Layer Decision Table

| Question | Correct directory |
| --- | --- |
| "This class receives HTTP input and returns HTTP output." | `internal.web` |
| "This class completes one business use case or transaction." | `internal.application` |
| "This class decides what is correct in the business." | `internal.domain` |
| "This class decides how to persist, publish, cache, or call an external system." | `internal.infra` |
| "This type is used by other modules as a stable contract." | `api` |

## Hard Prohibitions

| Rule ID | Prohibition |
| --- | --- |
| `DIR-001` | Controllers must not call repositories directly. |
| `DIR-002` | `internal.web` must not hold business state machines or final permission checks. |
| `DIR-003` | Repository abstractions live in module `internal.domain`, not in top-level `infra`. |
| `DIR-004` | `shared.kernel` must not hold business DTOs, commands, queries, or facades. |
| `DIR-005` | `ops.admin` must not bypass core module rules through direct `internal` access. |
| `DIR-006` | `files.upload` must not define final completion semantics for formal file creation. |
