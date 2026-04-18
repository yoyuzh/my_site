# Backend-Next Rule Ownership Matrix

This document assigns each important rule to one final decision owner. Other modules may consume the decision result, but they must not silently reimplement the same rule.

## Rule Ownership Table

| Business rule | Final owner | Typical trigger | Result shape | Must not be decided by |
| --- | --- | --- | --- | --- |
| registration allowed or denied | `identity.access` | register request | allow or reject | controllers, admin aggregation layer |
| invite code validity | `identity.access` | before register | valid, invalid, consumed | controller private branches |
| session validity and revocation | `identity.access` | protected request, refresh, logout | active, expired, revoked | scattered service checks |
| admin capability under role policy | `identity.access` | admin entry, governance operation | allow or deny | hard-coded username lists |
| workspace node access | `files.workspace` with auth context | any workspace operation | allow or deny | controllers |
| path legality | `files.workspace` | create, move, copy, restore | valid or invalid | upload layer |
| same-directory duplicate-name rule | `files.workspace` | node write | allow or reject | sharing, transfer |
| formal workspace node creation | `files.workspace` | after content ingress completion | create or reject | upload layer |
| content version reuse | `files.content` | import, copy, share import | reuse or create new version | workspace |
| physical object deletion readiness | `files.content` | cleanup lifecycle | delete or retain | workspace |
| upload mode selection | `platform.storage` | create upload session | `PROXY`, `DIRECT_SINGLE`, `DIRECT_MULTIPART` | client, controller |
| upload size/object constraints | `platform.storage` with caller context | before upload | allow or reject | duplicated file/upload services |
| share view/download/import permission | `files.sharing` | share access | allow or reject | workspace, controllers |
| share expiry, password, quota exhaustion | `files.sharing` | any share action | usable or unusable | frontend cache |
| transfer join eligibility | `transfer` | join or receive transfer | allow or reject | signal infrastructure |
| offline transfer receivable state | `transfer` | download or import | receivable or not | upload ingress |
| async retry eligibility and idempotency | `platform.job` | failed job or duplicate submission | retry, ignore, fail | individual task handlers |
| system setting mutability at governance boundary | `ops.admin` plus auth policy | admin write operation | writable or read-only | DTO hints alone |

## Module Truth Table

| Module | Owns as truth | Must not own |
| --- | --- | --- |
| `identity.access` | account state, session state, login/register rules, role auth context, client session exclusivity | file path legality, share expiry detail, storage placement rules |
| `files.workspace` | workspace nodes, tree, recycle lifecycle, rename/move/copy/delete/restore, duplicate-name rule, path legality | physical content writes, share expiry policy, upload-mode choice, storage policy selection |
| `files.content` | content assets, versions, immutability, references, derivatives, preconditions for physical object deletion | directory semantics, share passwords, import destination path |
| `files.upload` | upload sessions, ingress process control, multipart completion conditions, pre-completion orchestration | final workspace truth, final content truth, client-chosen upload authority |
| `files.sharing` | share grants, share state, password/expiry/quota, view/download/import permissions, access logs | workspace mutation, storage placement, tree management |
| `transfer` | transfer sessions, online/offline state, pickup code, receiving eligibility, transfer item state | share semantics, workspace lifecycle, storage governance truth |
| `platform.job` | async job state machine, retry policy, lease, idempotency key, execution records | file/share/workspace business truth |
| `platform.storage` | storage policies, upload capability matrix, placement rules, object size constraints, migration rules | workspace legality, share permission, transfer receive condition |
| `ops.admin` | governance entry, admin orchestration, audit entry, settings entry | direct rule bypass over core objects |

## Review Questions

Before accepting a new class or flow, ask:

1. Which module is the final owner of this rule?
2. Did this code bypass another module's `api` and touch `internal` directly?
3. Is this code placed in the correct layer: `web`, `application`, `domain`, or `infra`?
4. Did `ops.admin` become a privileged shortcut again?
