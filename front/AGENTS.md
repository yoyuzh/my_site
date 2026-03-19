# Frontend AGENTS

This directory is a Vite + React + TypeScript frontend. Follow the current split between pages, shared state/helpers, auth context, and reusable UI.

## Frontend layout

- `src/pages`: route-level screens and page-scoped state modules.
- `src/lib`: API helpers, cache helpers, schedule utilities, shared types, and test files.
- `src/auth`: authentication context/provider.
- `src/components/layout`: page shell/layout components.
- `src/components/ui`: reusable UI primitives.
- `src/index.css`: global styles.

## Real frontend commands

Run these from `front/`:

- `npm run dev`
- `npm run build`
- `npm run preview`
- `npm run clean`
- `npm run lint`
- `npm run test`

Run this from the repository root for OSS publishing:

- `node scripts/deploy-front-oss.mjs`
- `node scripts/deploy-front-oss.mjs --dry-run`
- `node scripts/deploy-front-oss.mjs --skip-build`

Important:

- `npm run lint` is the current TypeScript check because it runs `tsc --noEmit`.
- There is no separate ESLint script.
- There is no separate `typecheck` script beyond `npm run lint`.
- OSS publishing uses `scripts/deploy-front-oss.mjs`, which reads credentials from environment variables or `.env.oss.local`.

## Frontend rules

- Keep route behavior in `src/pages` and shared non-UI logic in `src/lib`.
- Add or update tests next to the state/helper module they exercise, following the existing `*.test.ts` pattern.
- Preserve the current Vite alias usage: `@/*` resolves from the `front/` directory root.
- If a change depends on backend API behavior, verify the proxy expectations in `vite.config.ts` before hardcoding URLs.
- Use the existing `npm run build`, `npm run test`, and `npm run lint` commands for validation; do not invent a separate frontend verification command.
- For release work, let the deployer agent publish `front/dist` through `scripts/deploy-front-oss.mjs` instead of manual object uploads.
