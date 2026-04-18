# Backend-Next Docs AGENTS

This directory stores the standalone backend rewrite constraints for the new `backend-next/` skeleton.

## Rules

- Keep these docs aligned with `backend-next/archtecture.md`, which is now the active startup architecture document.
- Keep these docs aligned with `backend-next/api-reference.md`, which is now the active startup API reference.
- Keep these docs independent from legacy `docs/architecture.md`; that file will be rewritten later.
- Keep these docs independent from legacy `docs/api-reference.md`; that file remains runtime-history reference only.
- Treat these files as the source of truth for the new backend skeleton under `backend-next/`.
- New sessions should read all three docs in this directory during startup for target-backend work.
- Update these docs before or with any future structural change to `backend-next/`.
- Do not document compatibility shortcuts that violate the module boundaries defined here.
