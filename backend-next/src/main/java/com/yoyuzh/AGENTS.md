# Yoyuzh Package AGENTS

This directory is the root namespace for the target backend modules.

## Rules

- Only top-level modules or technical roots belong here: `boot`, `shared`, `infra`, domain modules, and app entry modules.
- Do not recreate the old mixed package layout under this root.
- Cross-module communication must happen through module `api` packages.
