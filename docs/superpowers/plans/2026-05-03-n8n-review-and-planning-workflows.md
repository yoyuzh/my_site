# N8N Review And Planning Workflows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在本机 `n8n` 中落地 `my_site` 的三条手动工作流：开发前计划设计、开发后 `git diff` 审核、开发后架构审核，并通过共享子流程复用上下文采集与双审查编排。

**Architecture:** 先创建两个共享子 workflow：`review-core-common` 负责统一采集 git、文档、相邻代码上下文，`ai-review-parallel` 负责并行调度 `Codex` 与 `Claude` 审查。再创建三个主 workflow：`plan-design`、`git-diff-review`、`architecture-review`。由于当前本机 `n8n` 未暴露 `Execute Command` 节点，命令执行统一通过 `SSH` 节点调用 `localhost`，因此需要先启用本机 `sshd` 并配置一个 `Localhost SSH` credential。

**Tech Stack:** n8n Workflow SDK, n8n core nodes (`Manual Trigger`, `Edit Fields`, `Code`, `SSH`, `Execute Sub-workflow`, `Merge`, `Read/Write File`, `Convert to File`), OpenAI node, Anthropic node, local `codex` CLI, local `claude` CLI.

---

## File Structure

### Create

- `docs/superpowers/specs/2026-05-03-n8n-review-and-planning-workflows-design.md`
  - 审查与计划工作流的功能设计和 contract。
- `docs/superpowers/plans/2026-05-03-n8n-review-and-planning-workflows.md`
  - 本实施计划。
- `docs/review-reports/`
  - 保存 `git diff` 和架构审核 Markdown 报告。
- `docs/plan-reports/`
  - 保存计划设计 Markdown 报告。

### Create in n8n

- Workflow: `my_site review-core-common`
  - 共享上下文采集子流程。
- Workflow: `my_site ai-review-parallel`
  - 共享双审查编排子流程。
- Workflow: `my_site git-diff-review`
  - 开发后改动审查主流程。
- Workflow: `my_site architecture-review`
  - 开发后架构边界主流程。
- Workflow: `my_site plan-design`
  - 开发前方案设计主流程。

### External prerequisites

- n8n project: `bUnP9eDhHAlOk5mI`
- Required credential: `Localhost SSH`
- Required credential: `OpenAI`
- Optional credential: `Anthropic`

## Task 1: Prepare Repo Artifacts And Local Prerequisites

**Files:**
- Create: `docs/review-reports/`
- Create: `docs/plan-reports/`
- Modify: `docs/superpowers/specs/2026-05-03-n8n-review-and-planning-workflows-design.md`
- Create: `docs/superpowers/plans/2026-05-03-n8n-review-and-planning-workflows.md`

- [ ] **Step 1: Create local report directories**

```bash
mkdir -p docs/review-reports docs/plan-reports
```

- [ ] **Step 2: Record the runtime prerequisite explicitly**

```text
Local command execution in this n8n environment currently depends on:
- enabling macOS Remote Login / sshd on localhost
- creating an n8n SSH credential named "Localhost SSH"
```

- [ ] **Step 3: Keep the current design doc as the workflow contract source**

```text
The workflow build must follow:
- shared input schema from the spec
- shared reviewer JSON schema from the spec
- markdown report format from the spec
```

- [ ] **Step 4: Manual verification**

Run: `ls -la docs/review-reports docs/plan-reports`  
Expected: both directories exist

## Task 2: Build Shared Workflow `my_site review-core-common`

**Files:**
- Create in n8n: `my_site review-core-common`
- Reference: `AGENTS.md`
- Reference: `backend-next/archtecture.md`
- Reference: `backend-next/api-reference.md`
- Reference: `docs/backend-next/module-dependency-whitelist.md`
- Reference: `docs/backend-next/directory-responsibilities.md`
- Reference: `docs/backend-next/rule-ownership-matrix.md`

- [ ] **Step 1: Create an `Execute Workflow Trigger` input contract**

```json
{
  "repo_path": "/Users/mac/Documents/my_site",
  "mode": "working_tree",
  "commit_range": "",
  "scope": "full",
  "adjacent_context_lines": 120,
  "include_docs": true
}
```

- [ ] **Step 2: Add a `Code` node that assembles a single SSH command**

```js
const input = $input.first().json;
const payload = {
  repo_path: input.repo_path,
  mode: input.mode,
  commit_range: input.commit_range ?? '',
  scope: input.scope,
  adjacent_context_lines: input.adjacent_context_lines ?? 120,
  include_docs: input.include_docs !== false,
};

const encoded = Buffer.from(JSON.stringify(payload), 'utf8').toString('base64');
const command = [
  'python3 - <<\\'PY\\'',
  'import base64, json, os, subprocess, pathlib',
  `payload = json.loads(base64.b64decode("${encoded}").decode("utf-8"))`,
  'repo = pathlib.Path(payload["repo_path"])',
  'mode = payload["mode"]',
  'commit_range = payload.get("commit_range") or ""',
  'scope = payload["scope"]',
  'docs = [',
  '  "AGENTS.md",',
  '  "backend-next/archtecture.md",',
  '  "backend-next/api-reference.md",',
  '  "docs/backend-next/module-dependency-whitelist.md",',
  '  "docs/backend-next/directory-responsibilities.md",',
  '  "docs/backend-next/rule-ownership-matrix.md",',
  ']',
  'if scope in ("frontend", "full"): docs.append("frontend/AGENTS.md")',
  'if scope in ("backend", "full"): docs.append("backend/AGENTS.md")',
  'if mode == "working_tree":',
  '  diff_cmd = ["git", "-C", str(repo), "diff", "--", "."]',
  'elif mode == "staged":',
  '  diff_cmd = ["git", "-C", str(repo), "diff", "--cached", "--", "."]',
  'else:',
  '  diff_cmd = ["git", "-C", str(repo), "diff", commit_range, "--", "."]',
  'diff_text = subprocess.run(diff_cmd, text=True, capture_output=True, check=False).stdout',
  'branch = subprocess.run(["git", "-C", str(repo), "rev-parse", "--abbrev-ref", "HEAD"], text=True, capture_output=True, check=False).stdout.strip()',
  'head_sha = subprocess.run(["git", "-C", str(repo), "rev-parse", "HEAD"], text=True, capture_output=True, check=False).stdout.strip()',
  'status_lines = subprocess.run(["git", "-C", str(repo), "status", "--short"], text=True, capture_output=True, check=False).stdout.splitlines()',
  'changed_files = []',
  'for line in status_lines:',
  '  if not line.strip(): continue',
  '  changed_files.append({"status": line[:2].strip(), "path": line[3:].strip()})',
  'project_docs = []',
  'for rel in docs:',
  '  path = repo / rel',
  '  if path.exists(): project_docs.append({"path": rel, "content": path.read_text(encoding="utf-8")})',
  'print(json.dumps({"repo_path": str(repo), "branch": branch, "head_sha": head_sha, "mode": mode, "scope": scope, "diff_text": diff_text, "changed_files": changed_files, "project_docs": project_docs}, ensure_ascii=False))',
  'PY',
].join('\\n');

return [{ json: { ...input, command } }];
```

- [ ] **Step 3: Add an `SSH` node using credential `Localhost SSH`**

```text
resource: command
operation: execute
authentication: privateKey
command: {{ $json.command }}
cwd: /
```

- [ ] **Step 4: Parse SSH stdout into the shared JSON context**

```js
const stdout = $input.first().json.stdout ?? '';
return [{ json: JSON.parse(stdout) }];
```

- [ ] **Step 5: Validate the sub-workflow in n8n**

Expected:
- workflow saves successfully
- no node parameter validation errors

## Task 3: Build Shared Workflow `my_site ai-review-parallel`

**Files:**
- Create in n8n: `my_site ai-review-parallel`

- [ ] **Step 1: Create an `Execute Workflow Trigger` input contract**

```json
{
  "review_kind": "diff",
  "context_json": "{}",
  "codex_mode": "cli",
  "claude_enabled": true
}
```

- [ ] **Step 2: Add a `Code` node that creates reviewer prompts**

```js
const input = $input.first().json;
const context = typeof input.context_json === 'string' ? JSON.parse(input.context_json) : input.context_json;

const sharedInstructions = [
  `当前任务类型: ${input.review_kind}`,
  '你是只读审查器，不允许修改代码。',
  '请严格输出 JSON。',
  '必须输出字段: reviewer, review_kind, summary, blocking_findings, non_blocking_findings, open_questions, verification_gaps, decision, confidence。',
  '如果没有问题，也要返回空数组。',
  '',
  '上下文如下:',
  JSON.stringify(context),
].join('\\n');

return [{
  json: {
    review_kind: input.review_kind,
    codex_mode: input.codex_mode ?? 'cli',
    claude_enabled: input.claude_enabled !== false,
    codex_prompt: sharedInstructions + '\\nreviewer=codex',
    claude_prompt: sharedInstructions + '\\nreviewer=claude',
  },
}];
```

- [ ] **Step 3: Implement the local Claude branch with `SSH`**

```text
Call:
/Users/mac/.codex/skills/claude-code-delegate/scripts/run_claude_delegate.sh

Prompt file content:
- reviewer=claude
- output must be strict JSON
- no file edits
```

- [ ] **Step 4: Implement the local Codex branch with `SSH` and keep API as fallback**

```text
Primary:
codex exec -C /Users/mac/Documents/my_site --skip-git-repo-check --json -o <tmpfile> <prompt>

Fallback:
OpenAI Responses node with JSON schema output
```

- [ ] **Step 5: Merge both reviewer outputs**

```js
const left = $('Codex Result').item.json;
const right = $('Claude Result').item.json;
return [{
  json: {
    codex: left,
    claude: right,
    consensus_blockers: [],
    single_reviewer_blockers: [],
    reviewer_conflicts: [],
    final_recommendation: 'manual_judgement',
  },
}];
```

- [ ] **Step 6: Validate the sub-workflow in n8n**

Expected:
- workflow saves successfully
- OpenAI / Anthropic or SSH branches have no schema errors

## Task 4: Build Main Workflow `my_site git-diff-review`

**Files:**
- Create in n8n: `my_site git-diff-review`

- [ ] **Step 1: Use `Manual Trigger` plus a JSON `Set` node for review inputs**

```json
{
  "repo_path": "/Users/mac/Documents/my_site",
  "mode": "working_tree",
  "commit_range": "",
  "scope": "full",
  "adjacent_context_lines": 120,
  "include_docs": true,
  "codex_mode": "cli",
  "claude_enabled": true
}
```

- [ ] **Step 2: Call `my_site review-core-common`**

```text
Pass all review input fields into the shared context workflow.
```

- [ ] **Step 3: Call `my_site ai-review-parallel` with `review_kind=diff`**

```text
Pass:
- review_kind = diff
- context_json = JSON.stringify(context workflow output)
- codex_mode
- claude_enabled
```

- [ ] **Step 4: Generate Markdown review text**

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

- [ ] **Step 5: Write the report to `docs/review-reports/`**

```text
Filename pattern:
YYYY-MM-DD-HHmm-diff-review-<branch>.md
```

## Task 5: Build Main Workflow `my_site architecture-review`

**Files:**
- Create in n8n: `my_site architecture-review`

- [ ] **Step 1: Use `Manual Trigger` plus a JSON `Set` node for architecture inputs**

```json
{
  "repo_path": "/Users/mac/Documents/my_site",
  "mode": "working_tree",
  "commit_range": "",
  "architecture_scope": "full",
  "adjacent_context_lines": 120,
  "include_docs": true,
  "codex_mode": "cli",
  "claude_enabled": true
}
```

- [ ] **Step 2: Reuse `my_site review-core-common`**

```text
Map architecture_scope to review scope:
- backend-only -> backend
- frontend-only -> frontend
- full -> full
```

- [ ] **Step 3: Reuse `my_site ai-review-parallel` with `review_kind=architecture`**

```text
Reviewers must treat backend-next docs and AGENTS docs as the baseline.
```

- [ ] **Step 4: Generate Markdown architecture report**

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

- [ ] **Step 5: Write the report to `docs/review-reports/`**

```text
Filename pattern:
YYYY-MM-DD-HHmm-architecture-review-<scope>.md
```

## Task 6: Build Main Workflow `my_site plan-design`

**Files:**
- Create in n8n: `my_site plan-design`

- [ ] **Step 1: Use `Manual Trigger` plus a JSON `Set` node for planning inputs**

```json
{
  "repo_path": "/Users/mac/Documents/my_site",
  "request_text": "Describe the feature or change here",
  "scope": "full",
  "planning_depth": "normal",
  "codex_mode": "cli",
  "claude_enabled": true
}
```

- [ ] **Step 2: Build a planning context object**

```text
Include:
- request_text
- repo_path
- scope
- design doc constraints
- relevant AGENTS docs
```

- [ ] **Step 3: Reuse `my_site ai-review-parallel` with `review_kind=plan`**

```text
Codex generates the proposal.
Claude reviews it as a second reader.
```

- [ ] **Step 4: Generate Markdown plan report**

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

- [ ] **Step 5: Write the report to `docs/plan-reports/`**

```text
Filename pattern:
YYYY-MM-DD-HHmm-plan-<topic>.md
```

## Task 7: Validate And Handoff

**Files:**
- View in n8n: all five workflow drafts
- Verify locally: `docs/review-reports/`, `docs/plan-reports/`

- [ ] **Step 1: Confirm all workflows exist in project `bUnP9eDhHAlOk5mI`**

```text
Search workflows by names:
- my_site review-core-common
- my_site ai-review-parallel
- my_site git-diff-review
- my_site architecture-review
- my_site plan-design
```

- [ ] **Step 2: Confirm saved workflow caveats**

```text
Current blocker to full execution:
- localhost SSH is not reachable on port 22
- n8n must be given a working `Localhost SSH` credential
```

- [ ] **Step 3: State the successful build boundary clearly**

```text
Built now:
- workflow drafts and orchestration structure
- report directories
- repo-side design + implementation docs

Still required for first real execution:
- enable sshd
- add n8n SSH credential
- add n8n OpenAI credential
- optionally add n8n Anthropic credential
```
