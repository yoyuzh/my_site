# Backend-Next Module Dependency Whitelist

This document defines which modules in `backend-next/` may depend on which other modules. The default rule is strict: business modules may depend only on `api` contracts of other modules, never on their `internal` packages.

## Allowed and Forbidden Dependencies

| Module | Allowed dependencies | Forbidden dependencies |
| --- | --- | --- |
| `boot` | `shared.kernel`, any module `api`, `infra.*` | any business module `internal.*` |
| `shared.kernel` | none or other `shared.kernel` types | any business module package, any module facade with business semantics |
| `infra` | `shared.kernel` | business entities, business repository abstractions, business rule services |
| `identity.access` | `shared.kernel`, `infra.*` | any other business module `internal.*` |
| `files.workspace` | `identity.access.api`, `files.content.api`, `platform.storage.api`, `shared.kernel`, `infra.*` | `files.content.internal.*`, `files.sharing.internal.*`, `transfer.internal.*`, `ops.admin.internal.*` |
| `files.content` | `platform.storage.api`, `shared.kernel`, `infra.*` | `files.workspace.internal.*`, `files.sharing.internal.*`, `transfer.internal.*` |
| `files.upload` | `files.workspace.api`, `files.content.api`, `platform.storage.api`, `platform.job.api`, `shared.kernel`, `infra.*` | `files.workspace.internal.*`, `files.content.internal.*` |
| `files.sharing` | `identity.access.api`, `files.workspace.api`, `files.content.api`, `platform.job.api`, `shared.kernel`, `infra.*` | `files.workspace.internal.*`, `files.content.internal.*`, `transfer.internal.*` |
| `files.search` | `files.workspace.api`, `files.content.api`, `files.sharing.api`, `shared.kernel`, `infra.*` | any other module `internal.*` |
| `transfer` | `identity.access.api`, `files.content.api`, `platform.storage.api`, `platform.job.api`, `shared.kernel`, `infra.*` | `files.sharing.internal.*`, `files.workspace.internal.*`, `ops.admin.internal.*` |
| `platform.job` | any module `api`, `shared.kernel`, `infra.*` | any module `internal.domain`, `internal.web` |
| `platform.storage` | `shared.kernel`, `infra.*` | any business module `internal.*` |
| `ops.admin` | any module `api`, `platform.job.api`, `platform.storage.api`, `identity.access.api`, `shared.kernel`, `infra.*` | all core business module `internal.*` |
| `app.android` | `shared.kernel`, `infra.*`, selected module `api` needed for release/app entry contracts | any core module `internal.*` |

## Enforced Dependency Rules

| Rule ID | Constraint | Why |
| --- | --- | --- |
| `DEP-001` | No module may import another module's `internal` package. | Prevents hidden coupling and duplicated rule implementations. |
| `DEP-002` | `ops.admin` may orchestrate only through other modules' `api`. | Admin is a governance entry, not a privileged bypass tunnel. |
| `DEP-003` | `platform.job` may schedule or run work for modules, but it does not own their business rules. | Keeps job infrastructure generic. |
| `DEP-004` | `shared.kernel` contains only low-volatility shared concepts. | Prevents a new `common` dumping ground. |
| `DEP-005` | `infra` contains only reusable technical capability. | Keeps module truth inside domain packages. |
| `DEP-006` | `files.upload` completes ingress only; final workspace node creation and content registration must go back through owning modules. | Upload is not the business source of truth. |

## Naming Red Flags

Any type with names like `File`, `Share`, `Transfer`, `Workspace`, or `StoragePolicy` is business-shaped by default. Such types must not be placed in `shared.kernel` or top-level `infra` unless their semantics are strictly technical and cross-domain.
