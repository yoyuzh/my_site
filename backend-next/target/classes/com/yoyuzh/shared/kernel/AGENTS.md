# Shared Kernel AGENTS

## Responsibility

- Minimal shared exceptions, base value objects, domain event markers, auth context abstractions, and non-business constants.

## Prohibitions

- No business DTOs, business commands, or business queries.
- No module-specific facades or business services.
- No types whose core semantics are `File`, `Share`, `Transfer`, `Workspace`, or `StoragePolicy`.
