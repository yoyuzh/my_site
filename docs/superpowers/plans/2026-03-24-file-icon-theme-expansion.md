# File Icon Theme Expansion Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 扩展网盘文件图标体系，让常见文件类型都有稳定映射，并把新的图标主题发布到前端 OSS。

**Architecture:** 把当前散落在 `front/src/pages/Files.tsx` 和 `front/src/pages/files-upload.ts` 里的类型判断收口到共享模块，再通过一个小型 UI 组件统一渲染图标、配色和类型文案。这样列表、网格、详情栏和上传进度面板都能复用同一套规则，避免后续继续分叉。

**Tech Stack:** React 19、TypeScript、Vite 6、lucide-react、Node test runner、OSS 发布脚本

---

### Task 1: 锁定文件类型映射行为

**Files:**
- Modify: `front/src/pages/files-upload.test.ts`

- [ ] **Step 1: 写失败测试**
  覆盖 `xlsx`、`pptx`、`zip`、`mp4`、`mp3`、`fig`、`ttf`、`exe`、`epub`、`json` 等常见类型。

- [ ] **Step 2: 运行测试确认红灯**

Run: `npm run test -- src/pages/files-upload.test.ts`

Expected: 现有上传类型识别无法正确区分上述文件类型。

### Task 2: 收口共享文件类型模块

**Files:**
- Create: `front/src/lib/file-type.ts`
- Create: `front/src/lib/file-type.test.ts`

- [ ] **Step 1: 写失败测试**
  覆盖网盘文件元数据在 `contentType + extension` 组合下的分组、标签和优先级。

- [ ] **Step 2: 运行测试确认红灯**

Run: `npm run test -- src/lib/file-type.test.ts`

Expected: 共享模块尚不存在或无法满足测试期望。

- [ ] **Step 3: 写最小实现**
  提供统一的 `resolveFileType` / `resolveStoredFileType` 能力，并产出主题所需的标签与类型标识。

- [ ] **Step 4: 运行测试确认绿灯**

Run: `npm run test -- src/lib/file-type.test.ts src/pages/files-upload.test.ts`

Expected: 两组测试全部通过。

### Task 3: 统一前端图标主题与页面使用

**Files:**
- Create: `front/src/components/ui/FileTypeIcon.tsx`
- Modify: `front/src/pages/Files.tsx`
- Modify: `front/src/pages/files-upload.ts`

- [ ] **Step 1: 最小接入**
  让文件列表、网格卡片、详情栏、上传面板都使用统一图标主题组件或配置。

- [ ] **Step 2: 本地验证**

Run: `npm run lint`

Expected: TypeScript 校验通过。

- [ ] **Step 3: 构建验证**

Run: `npm run build`

Expected: Vite 生产构建成功。

### Task 4: 发布前端到 OSS

**Files:**
- Use existing script: `scripts/deploy-front-oss.mjs`

- [ ] **Step 1: 执行正式发布**

Run: `node scripts/deploy-front-oss.mjs`

Expected: `front/dist` 上传到配置好的 OSS 前缀并完成 SPA 别名文件更新。
