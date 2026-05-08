# Frontend AGENTS

This directory contains the active Vite + React frontend for `yoyuzh.xyz`.

## Frontend layout

- `src/pages`: route-level pages, including dashboard pages and admin pages under `src/pages/admin`.
- `src/components`: reusable UI and feature components, with file-manager work concentrated in folders such as `components/files`, `components/workspace`, `components/shares`, and `components/offline-downloads`.
- `src/lib`: shared client-side logic, state helpers, file helpers, and route/session utilities.
- `src/api`: HTTP client, request helpers, and API contract types.
- `src/hooks`: reusable hooks such as upload-queue and interaction hooks.
- `src/assets`: static assets and embedded viewer resources.

## Real frontend commands

Run these from `frontend/`:

- `npm run dev`
- `npm run build`
- `npm run preview`
- `npm run lint`

Important:

- `npm run lint` runs `tsc --noEmit`; there is no separate ESLint command.
- There is no checked-in frontend `test` script. Do not document or claim one unless the repo adds it.

## Frontend rules

- Keep API wiring aligned with the checked-in Axios + React Query client layer under `src/api` and nearby hooks/utilities.
- Prefer editing the active `frontend/` app only; do not drift into old `front/` references from stale docs or history.
- Treat `frontend/vite.config.ts` as the local proxy source of truth. The default backend target is `http://127.0.0.1:8080` unless `VITE_BACKEND_URL` overrides it.
- When a change touches file-manager behavior, verify whether the owning code lives in `src/pages/Files.tsx`, `src/components/files/**`, `src/components/workspace/**`, or `src/hooks/useUploadQueue.ts` before adding new abstractions.
- If frontend verification is requested, use the real repo commands above and state clearly when no automated frontend test command exists.
