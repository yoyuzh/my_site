# Files Upload AGENTS

## Responsibility

- Own upload sessions, ingress process control, multipart completion conditions, and pre-completion orchestration.

## Prohibitions

- Do not own final `WorkspaceNode` truth.
- Do not own final `ContentAsset` truth.
- Do not let the client decide upload mode authority.
