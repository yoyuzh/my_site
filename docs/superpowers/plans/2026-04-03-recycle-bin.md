# Recycle Bin Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为网盘增加回收站，删除后的文件或目录自动进入回收站，保留 10 天，可在保留期内恢复，并在网盘左侧目录侧栏底部提供入口。

**Architecture:** 后端把删除操作从“物理删除”改成“回收站软删除”，为 `StoredFile` 增加删除时间、原始路径/名称、回收站分组信息，并提供“列出回收站”“恢复”“清空过期项”能力。前端在 `Files` 页目录树侧栏底部增加回收站入口，并新增回收站页面/状态，复用现有文件卡片风格展示删除时间、原始位置和恢复操作。

**Tech Stack:** Spring Boot 3.3, JPA/Hibernate update schema, React 19, TypeScript, Vite

---

### Task 1: 后端回收站模型与服务

**Files:**
- Modify: `backend/src/main/java/com/yoyuzh/files/StoredFile.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/StoredFileRepository.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/FileService.java`
- Modify: `backend/src/main/java/com/yoyuzh/files/FileController.java`
- Create: `backend/src/main/java/com/yoyuzh/files/RecycleBinItemResponse.java`
- Create: `backend/src/main/java/com/yoyuzh/files/RestoreRecycleBinItemRequest.java`

- [ ] **Step 1: 写后端失败测试，覆盖删除进入回收站、回收站列表、恢复、10 天过期清理**
- [ ] **Step 2: 运行 `cd backend && mvn test` 确认新增测试先失败**
- [ ] **Step 3: 给 `StoredFile` 增加回收站字段，并让普通文件列表/最近文件默认排除已删除项**
- [ ] **Step 4: 把删除改成软删除，目录删除时按组放入回收站，恢复时按组恢复**
- [ ] **Step 5: 增加回收站列表、恢复接口和定时清理过期项**
- [ ] **Step 6: 运行 `cd backend && mvn test` 确认后端通过**

### Task 2: 前端回收站入口与页面

**Files:**
- Modify: `front/src/components/layout/Layout.tsx`
- Modify: `front/src/components/layout/Layout.test.ts`
- Modify: `front/src/App.tsx`
- Modify: `front/src/pages/Files.tsx`
- Create: `front/src/pages/RecycleBin.tsx`
- Create: `front/src/pages/recycle-bin-state.ts`
- Create: `front/src/pages/recycle-bin-state.test.ts`
- Modify: `front/src/lib/types.ts`
- Modify: `front/src/MobileApp.tsx`
- Modify: `front/src/mobile-pages/MobileFiles.tsx`

- [ ] **Step 1: 写前端失败测试，锁住侧栏底部入口和回收站状态变换**
- [ ] **Step 2: 运行 `cd front && npm run test` 确认前端新增测试先失败**
- [ ] **Step 3: 新增回收站页面和状态，接后端列表/恢复接口**
- [ ] **Step 4: 在桌面网盘左侧侧栏最下方加回收站入口，并把删除确认文案改为“移入回收站”**
- [ ] **Step 5: 给移动端补可访问的回收站入口和路由**
- [ ] **Step 6: 运行 `cd front && npm run test`、`cd front && npm run lint`、`cd front && npm run build`**

### Task 3: 文档与项目记忆

**Files:**
- Modify: `memory.md`
- Modify: `docs/architecture.md`
- Modify: `docs/api-reference.md`

- [ ] **Step 1: 记录回收站行为、保留周期、入口位置和恢复约束**
- [ ] **Step 2: 在交付前确认验证命令和已知限制写入文档**
