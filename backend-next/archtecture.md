
# Architecture Guide

## 1. Purpose

This document defines the architectural baseline of this project.

Its purpose is to make sure that:

- humans and coding agents share the same understanding of the system structure
- new code follows stable module boundaries
- business rules are placed in the correct module
- cross-module dependencies remain controlled
- the project does not regress into a large, coupled service layer

If current implementation conflicts with this document, this document should be treated as the target architecture direction unless a newer architectural decision explicitly overrides it.

---

## 2. Core Architectural Style

This project is a **modular monolith** with **domain-oriented modules**.

It is **not** organized primarily by technical layers like:

- controller
- service
- repository
- entity

Instead, the system is organized first by **business domains**, and then each domain is internally divided into layers.

### Top-level principles

1. **Business rules must have a single owner**
   - A rule should be decided in exactly one module.
   - Other modules may consume the result, but should not duplicate the rule.

2. **Cross-module access must go through `api`**
   - No module may depend directly on another module's `internal` package.

3. **Internal layering must be respected**
   - `web` handles protocol adaptation
   - `application` handles use case orchestration
   - `domain` holds business truth
   - `infra` holds technical implementation

4. **Admin is governance, not bypass**
   - `ops.admin` must not bypass domain rules by directly mutating internal objects of core modules.

5. **Upload is ingress, not final business truth**
   - Upload handles content ingress and upload sessions.
   - Final workspace node creation belongs to `files.workspace`.
   - Final content registration belongs to `files.content`.

6. **Logical assets and physical content are separated**
   - Workspace/file tree semantics belong to `files.workspace`
   - Physical content/version/storage semantics belong to `files.content`

---

## 3. Package Structure

The project is organized under `com.yoyuzh` like this:

```text
com.yoyuzh
├── boot
├── shared
│   └── kernel
├── identity
│   └── access
│       ├── api
│       └── internal
├── files
│   ├── workspace
│   │   ├── api
│   │   └── internal
│   ├── content
│   │   ├── api
│   │   └── internal
│   ├── upload
│   │   ├── api
│   │   └── internal
│   ├── sharing
│   │   ├── api
│   │   └── internal
│   └── search
│       ├── api
│       └── internal
├── transfer
│   ├── api
│   └── internal
├── platform
│   ├── job
│   │   ├── api
│   │   └── internal
│   └── storage
│       ├── api
│       └── internal
├── ops
│   └── admin
│       ├── api
│       └── internal
├── app
│   └── android
│       ├── api
│       └── internal
└── infra
    ├── broker
    ├── lock
    ├── cache
    └── client
````

---

## 4. Meaning of Top-Level Packages

### `boot`

Application bootstrap and global framework wiring.

Allowed responsibilities:

* Spring Boot configuration
* security configuration
* OpenAPI / Swagger configuration
* global web configuration
* exception mapping
* filter/interceptor registration
* startup wiring for runtime components

Not allowed:

* core business rules
* module-specific domain logic
* direct ownership of business state transitions

---

### `shared.kernel`

Very small shared kernel.

Allowed responsibilities:

* base exceptions
* basic shared value abstractions
* domain event base abstractions
* minimal authentication context abstractions

Not allowed:

* business DTOs
* workspace/share/transfer-specific rules
* cross-domain application services
* generic dumping ground utilities

Rule of thumb:
if something has strong domain language such as `Workspace`, `Share`, `Transfer`, `ContentAsset`, `StoragePolicy`, it probably does **not** belong in `shared.kernel`.

---

### `infra`

Global technical infrastructure only.

Allowed responsibilities:

* message broker adapters
* distributed lock facilities
* cache facilities
* external client foundations
* generic technical utilities needed across modules

Not allowed:

* business repository abstractions
* business entities
* domain rules
* module-specific persistence implementations

If a class is business-specific, it belongs inside that module's `internal.infra`, not global `infra`.

---

## 5. Business Modules

### `identity.access`

Owns identity and access concerns.

Responsibilities:

* account lifecycle
* login / registration
* session lifecycle
* token / refresh flow
* role and authorization context
* account status and session validity rules

Should not own:

* workspace path rules
* share access policy details
* storage policy decisions

---

### `files.workspace`

Owns the logical workspace model.

Responsibilities:

* workspace tree
* workspace nodes
* directory hierarchy
* rename / move / copy / delete / restore
* recycle lifecycle
* path legality
* same-parent naming rules

Should not own:

* physical object write logic
* share expiry logic
* upload mode selection
* storage placement policy

---

### `files.content`

Owns physical content assets and versions.

Responsibilities:

* content assets
* content versions
* immutable content version rules
* reference relations
* derived assets
* physical cleanup preconditions

Should not own:

* workspace path semantics
* share password/access rules
* target import directory semantics

---

### `files.upload`

Owns upload ingress and upload process orchestration.

Responsibilities:

* upload sessions
* multipart/direct/proxy upload process control
* upload completion checks
* upload-side ingress orchestration

Should not own:

* final workspace node truth
* final content asset truth
* client authority to define upload strategy

`files.upload` is an ingress/use-case module, not the final owner of workspace or content truth.

---

### `files.sharing`

Owns share grants and public sharing rules.

Responsibilities:

* share grants
* share status
* password / expiry / quota / action permission
* view/download/import authorization
* share visit records

Should not own:

* workspace tree mutation
* physical content write/storage placement
* transfer session semantics

---

### `files.search`

Owns search capability over relevant file-related domains.

Responsibilities:

* search use cases
* indexing/query orchestration
* search result shaping
* file-related retrieval support

Should not become:

* a bypass module that queries everyone's internal tables directly without API boundaries
* a hidden second admin module

---

### `transfer`

Owns transfer sessions.

Responsibilities:

* online/offline transfer sessions
* transfer items
* pickup/access rules
* transfer session status transitions
* receiving/import coordination

Should not own:

* share semantics
* workspace lifecycle truth
* storage governance truth

---

### `platform.job`

Owns the unified async job platform.

Responsibilities:

* async job lifecycle
* retry policy
* lease/heartbeat/progress
* idempotency keys
* execution records
* worker/scheduler orchestration

Should not own:

* workspace rules
* sharing rules
* transfer business truth

It is a platform capability, not a business domain owner.

---

### `platform.storage`

Owns storage governance.

Responsibilities:

* storage policies
* upload capability matrix
* object placement rules
* storage constraints
* migration rules
* backend storage selection policy

Should not degrade into:

* simple OSS/S3 utility wrappers only

This module is about **governance and decision-making**, not only technical storage calls.

---

### `ops.admin`

Owns governance and admin orchestration.

Responsibilities:

* governance entry points
* admin-facing orchestration
* audit entry points
* system setting management entry points
* global operational actions through domain APIs

Must not:

* bypass core module boundaries
* directly manipulate another core module's internal state
* directly depend on core modules' `internal`

---

### `app.android`

Client-specific adaptation layer for Android-facing concerns.

Responsibilities:

* Android client adaptation
* client-facing DTO shaping if needed
* endpoint compatibility for Android use cases

Must not become:

* a place for Android-only business truth
* a bypass over normal domain APIs

---

## 6. Internal Layering Rules

Every core module should follow this internal shape:

```text
<module>
├── api
└── internal
    ├── web
    ├── application
    ├── domain
    └── infra
```

### `api`

Public cross-module contract.

May contain:

* facade interfaces
* command/query APIs
* cross-module DTOs
* minimal contract types

Must not contain:

* controller implementations
* ORM entities
* repository implementations
* concrete application services

Other modules may depend on `api`.

Other modules must **not** depend on `internal`.

---

### `internal.web`

Protocol adaptation layer.

May contain:

* controllers
* request/response DTOs
* web assemblers
* API versioning adapters such as `v2`, `compat`

Must not contain:

* repository calls
* business rule ownership logic
* core state transition logic
* large orchestration flows

Rule:
`web` receives requests, delegates to `application`, and returns responses.

---

### `internal.application`

Use case orchestration layer.

May contain:

* use case services
* command/query handlers
* transaction boundaries
* orchestration across domain objects
* calls to other modules' `api`
* event publication
* async job submission

Must not contain:

* HTTP protocol concerns
* direct technical implementation details that belong to infra
* the final ownership of core business rules if they belong in domain

Rule:
`application` answers: **how is this business action completed?**

---

### `internal.domain`

Business truth layer.

May contain:

* aggregates/entities
* value objects
* domain services
* policies/specifications
* state machines
* domain events
* repository abstractions

Must not contain:

* Spring MVC concerns
* ORM implementations
* Redis templates
* OSS SDK calls
* controller DTOs
* external HTTP client details

Rule:
`domain` answers: **what is correct?**

---

### `internal.infra`

Technical implementation layer.

May contain:

* repository implementations
* mapper/DAO code
* persistence mappings
* cache implementations
* MQ publisher/subscriber implementation
* storage adapters
* external client adapters
* converters

Must not contain:

* core business truth ownership
* controller logic
* use case orchestration as primary responsibility

Rule:
`infra` answers: **how is it implemented technically?**

---

## 7. Dependency Rules

### Global rule

No module may depend directly on another module's `internal`.

Allowed:

* `xxx -> yyy.api`

Forbidden:

* `xxx -> yyy.internal`
* `xxx -> yyy.internal.application`
* `xxx -> yyy.internal.domain`
* `xxx -> yyy.internal.infra`

---

### Recommended allowed dependencies

#### `identity.access`

May depend on:

* `shared.kernel`
* `infra.*`

Should remain mostly foundational.

---

#### `files.workspace`

May depend on:

* `identity.access.api`
* `files.content.api`
* `platform.storage.api`
* `shared.kernel`
* `infra.*`

Must not depend on:

* `files.content.internal`
* `files.sharing.internal`
* `transfer.internal`
* `ops.admin.internal`

---

#### `files.content`

May depend on:

* `platform.storage.api`
* `shared.kernel`
* `infra.*`

Must not depend on:

* `files.workspace.internal`
* `files.sharing.internal`
* `transfer.internal`

---

#### `files.upload`

May depend on:

* `files.workspace.api`
* `files.content.api`
* `platform.storage.api`
* `platform.job.api`
* `shared.kernel`
* `infra.*`

Must not depend on:

* `files.workspace.internal`
* `files.content.internal`

---

#### `files.sharing`

May depend on:

* `identity.access.api`
* `files.workspace.api`
* `files.content.api`
* `platform.job.api`
* `shared.kernel`

Must not depend on:

* `files.workspace.internal`
* `files.content.internal`
* `transfer.internal`

---

#### `files.search`

May depend on:

* `files.workspace.api`
* `files.content.api`
* `files.sharing.api`
* `shared.kernel`
* `infra.*`

Must not depend on other modules' `internal`.

---

#### `transfer`

May depend on:

* `identity.access.api`
* `files.content.api`
* `platform.storage.api`
* `platform.job.api`
* `shared.kernel`

Must not depend on:

* `files.sharing.internal`
* `files.workspace.internal`
* `ops.admin.internal`

---

#### `platform.job`

May depend on:

* other modules' `api`
* `shared.kernel`
* `infra.*`

Must not depend on other modules' `internal.web` or `internal.domain`.

---

#### `platform.storage`

May depend on:

* `shared.kernel`
* `infra.*`

Must not depend on business modules' `internal`.

---

#### `ops.admin`

May depend on:

* `identity.access.api`
* `files.workspace.api`
* `files.content.api`
* `files.sharing.api`
* `transfer.api`
* `platform.job.api`
* `platform.storage.api`
* `shared.kernel`

Must not depend on any core module's `internal`.

---

## 8. Hard Prohibitions

These are hard red lines.

1. **No controller may directly depend on repository implementation**
2. **No module may directly use another module's `internal`**
3. **`ops.admin` must not directly mutate core domain internals**
4. **`shared.kernel` must not become a common dumping ground**
5. **`infra` must not contain business repository abstractions**
6. **`files.upload` must not bypass `files.workspace` and `files.content` to define final business truth**
7. **`domain` code must not depend on web/framework/ORM implementations**
8. **Search must not become a hidden backdoor bypassing all domain APIs**

---

## 9. Naming Guidance

These naming patterns are preferred.

### In `api`

* `WorkspaceCommandApi`
* `WorkspaceQueryApi`
* `SharingFacade`
* `StoragePolicyQueryApi`

### In `internal.web`

* `WorkspaceController`
* `CreateShareRequest`
* `TransferResponse`
* `UploadWebAssembler`

### In `internal.application`

* `CreateShareHandler`
* `MoveNodeAppService`
* `CompleteUploadHandler`
* `ImportTransferHandler`

### In `internal.domain`

* `WorkspaceNode`
* `ShareGrant`
* `TransferSession`
* `ContentAsset`
* `ContentVersion`
* `StoragePolicy`
* `WorkspaceDomainService`

### In `internal.infra`

* `JpaWorkspaceNodeRepository`
* `MybatisShareGrantRepository`
* `OssContentStorage`
* `RedisShareTokenCache`
* `MqJobPublisher`

---

## 10. Guidance for Coding Agents

When changing code in this repository, always follow this decision order:

1. **Which module owns this rule?**
2. **Is this change being made in the correct layer?**
3. **Am I crossing a module boundary correctly through `api`?**
4. **Am I accidentally turning admin/upload/search/shared into a bypass?**

### Before writing code, the agent should check:

* Does this feature belong to identity, workspace, content, upload, sharing, transfer, job, storage, or admin?
* Is there already an API contract that should be reused?
* Is the change introducing a dependency on another module's `internal`?
* Is a business rule being duplicated outside its owner module?

### The agent must prefer:

* small explicit APIs over hidden direct access
* domain-owned rules over controller/application shortcuts
* orchestration in `application`, not in `web`
* technology code in `infra`, not in `domain`

### The agent must avoid:

* adding cross-module direct repository usage
* placing business logic in controllers
* placing technical SDK details in domain
* adding business-specific utilities to `shared.kernel`
* turning `ops.admin` into a super-module

---

## 11. Runtime Architecture Direction

The runtime architecture should follow this pattern:

* `boot` starts the application and wires components
* HTTP requests enter through module `internal.web`
* use cases run in `internal.application`
* business truth resides in `internal.domain`
* persistence, cache, broker, storage clients, and external adapters live in `internal.infra` or global `infra`
* async jobs are unified through `platform.job`
* storage capability and placement decisions are unified through `platform.storage`

---

## 12. Architectural Review Checklist

Before merging architectural or module-affecting changes, verify:

* Is the owning module correct?
* Is the owning layer correct?
* Are cross-module calls using `api` only?
* Is a rule being duplicated?
* Is `ops.admin` bypassing boundaries?
* Is `shared.kernel` staying small?
* Is `files.upload` still only ingress?
* Is `platform.storage` still governance, not only wrappers?
* Is `platform.job` still unified, not fragmented?

---

## 13. Future Enforcement

This document is intended to be enforced by automated checks such as:

* ArchUnit rules
* package dependency rules
* review checklists
* agent instructions inside module `agent.md` files

If code and this document diverge, either:

* the code should be corrected, or
* this document should be explicitly updated through an architectural decision

Silent divergence is not acceptable.

