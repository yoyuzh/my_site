# N8N Review And Planning Workflows Design

## Goal

为 `my_site` 设计一套本机 `n8n` 工作流体系，覆盖三个手动触发场景：

1. 开发前的项目计划设计
2. 开发后的 `git diff` 审核
3. 开发后的架构审核

这套体系必须能并行使用本机 `Codex` 与本机 `Claude` 进行审查，并把结果同时返回到 `n8n` 执行结果和本地 Markdown 报告中。

## Scope

In scope:

- `n8n` 内的 workflow 编排设计
- 本机 `Codex` 与 `Claude` 的调用分工
- `git diff` 采集模式
- 架构审查的基线文档与审查维度
- Markdown 报告格式
- 共享子流程与统一 JSON contract
- 人工确认点与失败处理策略

Out of scope:

- 自动 PR 评论发布
- 自动 IM 通知
- 自动上线发布
- 直接在本次设计中创建真实 `n8n` workflow
- 把 AI 审查器变成自动改代码执行器

## Confirmed Decisions

本设计基于本轮对话里已经确认的约束：

- `项目计划工作流` 只在开发前手动触发
- `git diff 审核流` 与 `架构审核流` 在开发后手动触发，并且你需要立即拿到结果
- 审查输入模式支持三种：`working tree diff`、`staged diff`、`commit range`
- 默认审查深度不是只看 diff，而是 `diff + 项目约束文档 + 相邻代码`
- `Codex` 与 `Claude` 采用并行双审，各自独立输出结论
- 结果同时保存在 `n8n` 执行结果和本地 Markdown 文件中
- `Codex` 调用支持 `CLI / OpenAI API` 双通道，默认优先本机 CLI，可切换为 API
- `架构审核流` 支持 `backend-only / frontend-only / full`

## Design Principles

### One orchestrator, multiple focused workflows

不把计划、diff 审核、架构审核塞进一个超长 workflow。每一条 workflow 只负责一个业务目标，但复用共享的上下文采集与 AI 审查 contract。

### Reviewers are read-only

`Codex` 与 `Claude` 在这些 workflow 中都是审查器，不是执行器。它们只能读取上下文、产出审查 JSON、生成结论，不能在自动化流程中直接改仓库文件。

### Evidence-first reviews

每次 diff 或架构审查都必须带上真实上下文：

- 目标 repo 路径
- 当前分支
- 目标 diff
- 改动文件列表
- 相邻实现代码
- 项目约束文档

不允许只喂一句“帮我 review 一下”。

### Shared contracts over ad hoc prompts

三条 workflow 统一复用同一套输入对象和同一套 AI 输出 JSON schema，避免后续维护时 prompt 漂移。

## Workflow Topology

推荐结构为 `3 条主 workflow + 2 条共享子 workflow`。

### Main workflows

1. `plan-design-workflow`
2. `git-diff-review-workflow`
3. `architecture-review-workflow`

### Shared sub-workflows

1. `review-core-common`
2. `ai-review-parallel`

## Shared Workflow 1: `review-core-common`

### Purpose

统一采集审查上下文，并输出给上游 workflow 使用的标准化 JSON。

### Inputs

```json
{
  "repo_path": "/Users/mac/Documents/my_site",
  "mode": "working_tree|staged|commit_range",
  "commit_range": "main..HEAD",
  "scope": "general|frontend|backend|full",
  "adjacent_context_lines": 120,
  "include_docs": true
}
```

### Behavior

1. 校验 `repo_path` 是否存在且是 git 仓库
2. 获取当前分支名、HEAD commit、工作区状态
3. 根据 `mode` 获取目标 diff
4. 提取改动文件列表
5. 为每个改动文件读取有限的相邻上下文
6. 读取仓库级约束文档
7. 产出统一上下文对象

### Required repository docs

始终读取：

- [AGENTS.md](/Users/mac/Documents/my_site/AGENTS.md)
- [backend-next/archtecture.md](/Users/mac/Documents/my_site/backend-next/archtecture.md)
- [backend-next/api-reference.md](/Users/mac/Documents/my_site/backend-next/api-reference.md)
- [module-dependency-whitelist.md](/Users/mac/Documents/my_site/docs/backend-next/module-dependency-whitelist.md)
- [directory-responsibilities.md](/Users/mac/Documents/my_site/docs/backend-next/directory-responsibilities.md)
- [rule-ownership-matrix.md](/Users/mac/Documents/my_site/docs/backend-next/rule-ownership-matrix.md)

按范围读取：

- `frontend` 或 `full` 时补充 `frontend/AGENTS.md`
- `backend` 或 `full` 时补充 `backend/AGENTS.md`
- 如果目标需求涉及计划设计，再读取相关已有 spec/plan 文档

### Output contract

```json
{
  "repo_path": "/Users/mac/Documents/my_site",
  "branch": "feature/example",
  "head_sha": "abc123",
  "mode": "working_tree",
  "scope": "full",
  "diff_text": "...",
  "changed_files": [
    {
      "path": "frontend/src/pages/Files.tsx",
      "status": "modified"
    }
  ],
  "adjacent_context": [
    {
      "path": "frontend/src/pages/Files.tsx",
      "content": "..."
    }
  ],
  "project_docs": [
    {
      "path": "backend-next/archtecture.md",
      "content": "..."
    }
  ]
}
```

### Failure behavior

- 如果 diff 为空，直接返回 `no_changes` 状态，而不是调用 AI
- 如果 `commit_range` 非法，返回结构化错误
- 如果文件数过多，先裁剪相邻上下文，不裁剪 diff 本体

## Shared Workflow 2: `ai-review-parallel`

### Purpose

对同一份标准化上下文并行调用 `Codex` 与 `Claude`，生成两份独立审查结论，再汇总冲突与共识。

### Inputs

```json
{
  "review_kind": "plan|diff|architecture",
  "context": {},
  "codex_mode": "cli|api|fallback",
  "claude_enabled": true
}
```

### Reviewer roles

#### Codex

定位为：

- 更贴近仓库约束与工程落地
- 更关注实现风险、测试缺口、结构合理性

调用模式：

- 首选本机 `Codex CLI`
- 失败时可切换为 `OpenAI API`

#### Claude

定位为：

- 第二审查人
- 输出独立视角，避免与 `Codex` 完全同构
- 侧重可读性、逻辑缺口、边界遗漏、潜在回归

调用模式：

- 本机 `claude -p --output-format json`

### Shared AI output schema

两边必须输出同一结构：

```json
{
  "reviewer": "codex|claude",
  "review_kind": "plan|diff|architecture",
  "summary": "一段简要结论",
  "blocking_findings": [
    {
      "title": "string",
      "severity": "high|medium|low",
      "path": "frontend/src/pages/Files.tsx",
      "line_hint": "L120-L150",
      "why": "string",
      "suggestion": "string"
    }
  ],
  "non_blocking_findings": [],
  "open_questions": [],
  "verification_gaps": [],
  "decision": "approve|needs_changes|reject",
  "confidence": 0.82
}
```

### Merge behavior

汇总节点不做“重新审查”，只做结构化合并：

- `consensus_blockers`
  - 两边都认为是严重问题
- `single_reviewer_blockers`
  - 只有一方给出严重问题
- `consensus_non_blockers`
  - 两边都提到的普通问题
- `reviewer_conflicts`
  - 一方通过、一方拒绝
- `final_recommendation`
  - 按规则得出 `approve|needs_changes|manual_judgement`

### Merge rule

推荐规则：

- 任一 reviewer 给出 `reject`，且存在 `high` 严重问题，则总结果至少是 `needs_changes`
- 两边都 `approve` 且无 `blocking_findings`，总结果为 `approve`
- 任何一边存在高置信度结构冲突，总结果为 `manual_judgement`

## Workflow A: `plan-design-workflow`

### Purpose

在开发前把需求转成结构化计划设计，避免直接开写。

### Trigger

手动触发。

### Inputs

```json
{
  "repo_path": "/Users/mac/Documents/my_site",
  "request_text": "用户需求原文",
  "scope": "frontend|backend|full",
  "planning_depth": "light|normal|deep",
  "codex_mode": "cli|api|fallback",
  "use_claude_second_review": true
}
```

### Steps

1. 标准化需求文本
2. 读取仓库约束文档
3. 读取与目标范围相关的已有 spec / plan
4. 让 `Codex` 产出设计草案
5. 可选让 `Claude` 审查这份设计草案
6. 汇总为统一 spec
7. 生成 Markdown 报告

### Output sections

计划设计报告必须包含：

- Background
- Goal
- Non-goals
- Constraints
- Candidate approaches
- Recommended approach
- File or module impact
- Validation plan
- Risks
- Open questions

### Manual gate

这条 workflow 的终点不是“开始编码”，而是“等你确认方案”。

## Workflow B: `git-diff-review-workflow`

### Purpose

在开发后，对当前改动做实现质量审查，而不是只看静态命令结果。

### Trigger

手动触发。

### Inputs

```json
{
  "repo_path": "/Users/mac/Documents/my_site",
  "mode": "working_tree|staged|commit_range",
  "commit_range": "main..HEAD",
  "scope": "general|frontend|backend|full",
  "codex_mode": "cli|api|fallback",
  "claude_enabled": true
}
```

### Steps

1. 调 `review-core-common`
2. 如果没有 diff，直接返回
3. 并行调用 `Codex` 与 `Claude`
4. 汇总审查结果
5. 生成 Markdown 报告
6. 在 `n8n` 执行结果中输出摘要

### Review focus

重点关注：

- 行为回归
- 明显 bug 风险
- 边界条件遗漏
- 错误处理缺口
- 不符合 repo 约束的实现
- 缺失验证命令

### Non-goals

不做这些事：

- 自动修改代码
- 自动提交
- 自动 push

## Workflow C: `architecture-review-workflow`

### Purpose

在开发后，针对架构边界与模块职责做专项审核，而不是泛化 code review。

### Trigger

手动触发。

### Inputs

```json
{
  "repo_path": "/Users/mac/Documents/my_site",
  "mode": "working_tree|staged|commit_range",
  "commit_range": "main..HEAD",
  "architecture_scope": "backend-only|frontend-only|full",
  "codex_mode": "cli|api|fallback",
  "claude_enabled": true
}
```

### Backend review baseline

后端架构审查严格依据以下文档：

- [backend-next/archtecture.md](/Users/mac/Documents/my_site/backend-next/archtecture.md)
- [backend-next/api-reference.md](/Users/mac/Documents/my_site/backend-next/api-reference.md)
- [module-dependency-whitelist.md](/Users/mac/Documents/my_site/docs/backend-next/module-dependency-whitelist.md)
- [directory-responsibilities.md](/Users/mac/Documents/my_site/docs/backend-next/directory-responsibilities.md)
- [rule-ownership-matrix.md](/Users/mac/Documents/my_site/docs/backend-next/rule-ownership-matrix.md)

必须重点检查：

- 是否跨模块绕过 `api` 访问 `internal`
- 是否把规则放错 `web/application/domain/infra`
- 是否让 `ops.admin` 变成 bypass
- 是否让 `files.upload` 拿走最终业务真相
- 是否违反 `route ownership` 与 `rule ownership`

### Frontend review baseline

前端架构审查不是凭空“谈架构”，而是检查既有目录职责是否被打穿。

重点检查：

- `src/pages` 是否承担过多共享逻辑
- `src/components` 是否混入页面特定编排
- `src/lib` 是否变成无边界工具堆
- `src/api` 是否承载 UI 逻辑
- 页面、组件、共享逻辑、请求契约的边界是否混乱

### Output focus

报告必须优先列出：

- 严重边界破坏
- 中等结构风险
- 可接受但值得重构的债务
- 当前审查未覆盖的盲区

## Prompt Contracts

### Diff review prompt contract

必须包含：

- 审查目标说明
- 只读约束
- 项目约束文档节选
- diff 文本
- 相邻代码上下文
- 输出 JSON schema

禁止包含：

- “直接帮我修”
- “如果你觉得可以就改代码”
- 任意执行型指令

### Architecture review prompt contract

必须明确：

- 当前审查是架构边界审查，不是普通 code style review
- 结论必须对照 repo 文档
- 所有问题都要说明违反了哪条边界或职责

### Planning prompt contract

必须明确：

- 当前任务是产出设计方案，不是实现代码
- 需要给出 `candidate approaches`
- 必须标出 `non-goals` 和 `validation plan`

## Markdown Report Format

三类 workflow 统一写到本地 Markdown，文件名按时间戳生成。

### Directories

- `docs/review-reports/`
- `docs/plan-reports/`

### File naming

- `YYYY-MM-DD-HHmm-plan-<topic>.md`
- `YYYY-MM-DD-HHmm-diff-review-<branch>.md`
- `YYYY-MM-DD-HHmm-architecture-review-<scope>.md`

### Plan report template

```md
# Plan Design Report

## Request
## Constraints
## Candidate Approaches
## Recommended Approach
## Impacted Areas
## Validation Plan
## Risks
## Open Questions
```

### Diff review template

```md
# Diff Review Report

## Input Summary
## Codex Summary
## Claude Summary
## Consensus Blockers
## Single Reviewer Findings
## Verification Gaps
## Final Recommendation
```

### Architecture review template

```md
# Architecture Review Report

## Input Summary
## Scope
## Baseline Documents
## Consensus Violations
## Reviewer-Specific Findings
## Boundary Risk Assessment
## Final Recommendation
```

## N8N Node Design

### Common node pattern

三条主 workflow 都使用类似骨架：

1. `Manual Trigger` 或 `Form Trigger`
2. `Set` 标准化输入
3. `Execute Command` 采集 git 与文件上下文
4. `Code` 节点整理上下文对象
5. `Execute Command` 调本机 `Claude`
6. `Execute Command` 或 `HTTP Request` 调 `Codex`
7. `Code` 节点汇总 JSON
8. `Code` 节点生成 Markdown
9. `Write File` 落盘
10. 返回执行摘要

### Why not a single giant workflow

因为三种目标不同：

- `plan` 面向需求设计
- `diff review` 面向行为与实现
- `architecture review` 面向边界与职责

如果塞到一条 workflow 里，prompt、输出格式、人工确认点都会失焦。

## Failure And Recovery

### Claude failure

如果 `Claude` 调用失败：

- 标记 `claude_status: failed`
- 不阻止 `Codex` 结果返回
- 最终报告明确说明只完成单审

### Codex failure

如果 `Codex CLI` 失败：

- 若模式是 `fallback`，切 `OpenAI API`
- 若 API 也失败，返回 `codex_status: failed`
- 最终报告说明只完成单审

### Empty diff

对于 `git-diff-review` 和 `architecture-review`：

- 如果输入模式对应的 diff 为空，直接返回结构化提示
- 不进入 AI 审查

## Security And Operational Constraints

- 审查 workflow 只读仓库，不允许自动写业务代码
- AI prompt 里不传无关 secret
- 审查报告只落到本机 repo 内文档目录
- 不把 SSH 部署能力混入审查 workflow

## Recommended Build Order

推荐按下面顺序实现：

1. `review-core-common`
2. `ai-review-parallel`
3. `git-diff-review-workflow`
4. `architecture-review-workflow`
5. `plan-design-workflow`

理由：

- `git diff` 审核最容易先验证整个 AI 审查链路
- 架构审核复用同一套上下文与审查器
- 计划设计最后接入时，只需替换输入和报告模板

## Acceptance Criteria

当下面条件都满足时，这套设计算成立：

1. 你可以在开发前手动触发计划设计，并拿到结构化方案报告
2. 你可以在开发后手动选择 `working tree / staged / commit range` 做 diff 审核
3. 你可以在开发后手动选择 `backend-only / frontend-only / full` 做架构审核
4. `Codex` 与 `Claude` 能对同一输入并行独立输出结构化 JSON
5. workflow 最终能把摘要回显到 `n8n`
6. workflow 最终能把完整报告落到本机 Markdown 文件
