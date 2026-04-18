# Overview APK Download Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在总览界面增加 Android APK 下载入口，并让前端 OSS 发布流程稳定上传最新调试 APK。

**Architecture:** 总览页使用固定的前端静态下载路径展示 APK 下载按钮，避免引入额外后端接口。OSS 发布仍沿用现有 `scripts/deploy-front-oss.mjs`，只额外把 `front/android/app/build/outputs/apk/debug/app-debug.apk` 上传到稳定对象 key，避免把 APK 混入 `front/dist` 进而被 Capacitor 再次打包进 Android 壳。

**Tech Stack:** React 19, TypeScript, Vite 6, Node.js ESM scripts

---

### Task 1: 总览页下载入口状态与链接

**Files:**
- Modify: `front/src/lib/app-shell.ts`
- Modify: `front/src/lib/app-shell.test.ts`
- Modify: `front/src/pages/overview-state.ts`
- Modify: `front/src/pages/overview-state.test.ts`

- [ ] **Step 1: Write the failing test**
- [ ] **Step 2: Run `cd front && npm run test` to verify the new state tests fail for the missing helpers**
- [ ] **Step 3: Add native-shell detection and APK download path helpers**
- [ ] **Step 4: Run `cd front && npm run test` to verify the helper tests pass**

### Task 2: 桌面端与移动端总览入口

**Files:**
- Modify: `front/src/pages/Overview.tsx`
- Modify: `front/src/mobile-pages/MobileOverview.tsx`

- [ ] **Step 1: Render the APK download entry only in web contexts where it is useful**
- [ ] **Step 2: Keep the current overview information architecture intact and reuse the shared helper**
- [ ] **Step 3: Run `cd front && npm run lint` to verify the React/TypeScript changes type-check**

### Task 3: OSS 发布时上传 APK

**Files:**
- Modify: `scripts/deploy-front-oss.mjs`
- Modify: `memory.md`
- Modify: `docs/architecture.md`

- [ ] **Step 1: Extend the existing OSS deploy script to upload the built APK to a stable key**
- [ ] **Step 2: Verify with `node scripts/deploy-front-oss.mjs --dry-run` if credentials are available, otherwise document the gap**
- [ ] **Step 3: Update project memory and architecture notes for the new APK distribution path**
