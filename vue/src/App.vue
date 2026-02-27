<script setup lang="ts">
import {
  ArrowUpBold,
  Delete,
  Document,
  EditPen,
  FolderOpened,
  House,
  Monitor,
  Plus,
  Right,
  School,
  Trophy,
} from '@element-plus/icons-vue'
import { computed, nextTick, onMounted, onUnmounted, ref, watch, type Component } from 'vue'

type SectionId = 'overview' | 'explorer' | 'games' | 'school'
type ExplorerItemKind = 'folder' | 'file'
type GameId = 'race' | 't_race'

interface ExplorerItem {
  id: string
  parentId: string | null
  kind: ExplorerItemKind
  name: string
}

interface GameOption {
  id: GameId
  label: string
  subtitle: string
  path: string
}

interface SectionNavItem {
  id: SectionId
  title: string
  subtitle: string
  icon: Component
}

interface ExplorerFolderTreeItem {
  id: string
  name: string
  level: number
  hasChildren: boolean
  expanded: boolean
}

const text = {
  skip: '跳到主要内容',
  loginTitle: 'Workspace Login',
  loginSubtitle: '输入账号后进入工作区视图。',
  username: '用户名',
  usernamePlaceholder: '输入用户名…',
  password: '密码',
  passwordPlaceholder: '输入密码…',
  loginButton: '进入工作区',
  welcome: '欢迎回来',
}

const username = ref('')
const password = ref('')
const loginError = ref('')
const statusMessage = ref('')
const isLoggedIn = ref(false)
const activeSection = ref<SectionId>('overview')
const selectedGameId = ref<GameId | null>(null)
const isGameFullscreen = ref(false)
const explorerCurrentFolderId = ref('root')
const explorerSelectedItemId = ref<string | null>(null)
const expandedFolderIds = ref(new Set<string>(['root']))
const nextExplorerId = ref(1000)
const gamePlayerRef = ref<HTMLElement | null>(null)
const loginRef = ref<HTMLElement | null>(null)
const workspaceRef = ref<HTMLElement | null>(null)
const sidebarRef = ref<HTMLElement | null>(null)
const sidebarIndicatorStyle = ref({
  transform: 'translateY(0px)',
  height: '0px',
  opacity: '0',
})
const sidebarIndicatorJelly = ref(false)
let sidebarJellyTimer: ReturnType<typeof setTimeout> | null = null
let glowFrameId: number | null = null
let latestPointer: { x: number; y: number } | null = null
let latestPointerTarget: 'workspace' | 'login' | null = null
const lightTargetSelector =
  '.nav-item, .ghost-btn, .primary-btn, .icon-btn, .game-card, .file-main, .folder-item, .topbar, .sidebar, .panel, .hero-card, .metric-card, .explorer-toolbar, .folder-list, .file-list, .file-card, .game-player, .study-card, .path-segment, .status'
const loginLightTargetSelector = '.login-card, .login-form button, .login-input-shell, .login-card h1'

const navItems: SectionNavItem[] = [
  { id: 'overview', title: '总览', subtitle: '项目入口与状态', icon: House },
  { id: 'explorer', title: '文件', subtitle: '管理目录与文件', icon: FolderOpened },
  { id: 'games', title: '游戏', subtitle: '启动内置小游戏', icon: Trophy },
  { id: 'school', title: '学习', subtitle: '课程与路线图', icon: School },
]

const explorerItems = ref<ExplorerItem[]>([
  { id: 'root', parentId: null, kind: 'folder', name: 'Workspace' },
  { id: 'f-projects', parentId: 'root', kind: 'folder', name: 'Projects' },
  { id: 'f-docs', parentId: 'root', kind: 'folder', name: 'Docs' },
  { id: 'f-media', parentId: 'root', kind: 'folder', name: 'Assets' },
  { id: 'f-ui', parentId: 'f-projects', kind: 'folder', name: 'UI-Experiments' },
  { id: 'file-readme', parentId: 'root', kind: 'file', name: 'Readme.txt' },
  { id: 'file-plan', parentId: 'f-docs', kind: 'file', name: 'Roadmap.md' },
  { id: 'file-shot', parentId: 'f-media', kind: 'file', name: 'Preview.png' },
])

const gameOptions: GameOption[] = [
  { id: 'race', label: 'Race', subtitle: '经典 JS13K 版本', path: '/race/index.html' },
  { id: 't_race', label: 'HTML Race', subtitle: '新版 HTML 版本', path: '/t_race/index.html' },
]

const currentUser = computed(() => username.value.trim() || 'Guest')

const explorerCurrentFolder = computed(
  () => explorerItems.value.find((item) => item.id === explorerCurrentFolderId.value) ?? explorerItems.value[0],
)

const explorerChildItems = computed(() =>
  explorerItems.value
    .filter((item) => item.parentId === explorerCurrentFolderId.value)
    .sort((a, b) => {
      if (a.kind !== b.kind) return a.kind === 'folder' ? -1 : 1
      return a.name.localeCompare(b.name)
    }),
)

const explorerPath = computed(() => {
  const result: ExplorerItem[] = []
  const itemMap = new Map(explorerItems.value.map((item) => [item.id, item]))
  let cursorId: string | null = explorerCurrentFolderId.value

  while (cursorId) {
    const current = itemMap.get(cursorId)
    if (!current) break
    result.unshift(current)
    cursorId = current.parentId
  }

  return result
})

const explorerFolders = computed(() =>
  explorerItems.value
    .filter((item) => item.kind === 'folder')
    .sort((a, b) => a.name.localeCompare(b.name)),
)

const explorerFolderChildrenMap = computed(() => {
  const folderMap = new Map<string, ExplorerItem[]>()
  for (const folder of explorerFolders.value) {
    folderMap.set(folder.id, [])
  }
  for (const item of explorerFolders.value) {
    if (!item.parentId) continue
    if (!folderMap.has(item.parentId)) continue
    folderMap.get(item.parentId)!.push(item)
  }
  for (const children of folderMap.values()) {
    children.sort((a, b) => a.name.localeCompare(b.name))
  }
  return folderMap
})

const explorerFolderTreeItems = computed<ExplorerFolderTreeItem[]>(() => {
  const root = explorerItems.value.find((item) => item.id === 'root' && item.kind === 'folder')
  if (!root) return []

  const result: ExplorerFolderTreeItem[] = []
  const walk = (folder: ExplorerItem, level: number) => {
    const children = explorerFolderChildrenMap.value.get(folder.id) ?? []
    const expanded = expandedFolderIds.value.has(folder.id)
    result.push({
      id: folder.id,
      name: folder.name,
      level,
      hasChildren: children.length > 0,
      expanded,
    })
    if (!expanded) return
    for (const child of children) {
      walk(child, level + 1)
    }
  }

  walk(root, 0)
  return result
})

const activeGame = computed(() => gameOptions.find((option) => option.id === selectedGameId.value) ?? null)

function setSection(nextSection: SectionId) {
  activeSection.value = nextSection
  statusMessage.value = `已切换到${navItems.find((item) => item.id === nextSection)?.title ?? ''}视图。`
}

function applyMouseLighting(clientX: number, clientY: number) {
  const workspace = workspaceRef.value
  if (!workspace) return

  const targets = workspace.querySelectorAll<HTMLElement>(lightTargetSelector)
  for (const target of targets) {
    const rect = target.getBoundingClientRect()
    target.style.setProperty('--lx', `${clientX - rect.left}px`)
    target.style.setProperty('--ly', `${clientY - rect.top}px`)
  }
}

function applyLoginLighting(clientX: number, clientY: number) {
  const login = loginRef.value
  if (!login) return
  login.classList.add('lighting-active')

  const targets = login.querySelectorAll<HTMLElement>(loginLightTargetSelector)
  for (const target of targets) {
    const rect = target.getBoundingClientRect()
    const x = `${clientX - rect.left}px`
    const y = `${clientY - rect.top}px`
    target.style.setProperty('--lx', x)
    target.style.setProperty('--ly', y)
    target.style.setProperty('--mx', x)
    target.style.setProperty('--my', y)
  }
}

function flushMouseLighting() {
  glowFrameId = null
  if (!latestPointer) return
  if (latestPointerTarget === 'workspace') {
    applyMouseLighting(latestPointer.x, latestPointer.y)
  } else if (latestPointerTarget === 'login') {
    applyLoginLighting(latestPointer.x, latestPointer.y)
  }
}

function onWorkspacePointerMove(event: PointerEvent) {
  latestPointerTarget = 'workspace'
  latestPointer = { x: event.clientX, y: event.clientY }
  if (glowFrameId !== null) return
  glowFrameId = requestAnimationFrame(flushMouseLighting)
}

function onWorkspacePointerLeave() {
  const workspace = workspaceRef.value
  if (!workspace) return
  const targets = workspace.querySelectorAll<HTMLElement>(lightTargetSelector)
  for (const target of targets) {
    target.style.setProperty('--lx', '-9999px')
    target.style.setProperty('--ly', '-9999px')
  }
  latestPointerTarget = null
}

function onLoginPointerMove(event: PointerEvent) {
  latestPointerTarget = 'login'
  latestPointer = { x: event.clientX, y: event.clientY }
  if (glowFrameId !== null) return
  glowFrameId = requestAnimationFrame(flushMouseLighting)
}

function onLoginPointerLeave() {
  const login = loginRef.value
  if (!login) return
  login.classList.remove('lighting-active')
  const targets = login.querySelectorAll<HTMLElement>(loginLightTargetSelector)
  for (const target of targets) {
    target.style.setProperty('--lx', '-9999px')
    target.style.setProperty('--ly', '-9999px')
    target.style.setProperty('--mx', '-9999px')
    target.style.setProperty('--my', '-9999px')
  }
  latestPointerTarget = null
}

function updateSidebarIndicator(triggerJelly = false) {
  const sidebar = sidebarRef.value
  if (!sidebar) return

  const activeButton = sidebar.querySelector<HTMLButtonElement>(`.nav-item[data-section-id="${activeSection.value}"]`)
  if (!activeButton) return

  sidebarIndicatorStyle.value = {
    transform: `translateY(${activeButton.offsetTop}px)`,
    height: `${activeButton.offsetHeight}px`,
    opacity: '1',
  }

  if (!triggerJelly) return

  sidebarIndicatorJelly.value = false
  requestAnimationFrame(() => {
    sidebarIndicatorJelly.value = true
  })

  if (sidebarJellyTimer) {
    clearTimeout(sidebarJellyTimer)
  }
  sidebarJellyTimer = setTimeout(() => {
    sidebarIndicatorJelly.value = false
  }, 560)
}

function submitLogin() {
  loginError.value = ''
  if (!username.value.trim()) {
    loginError.value = '请输入用户名。'
    return
  }
  if (!password.value.trim()) {
    loginError.value = '请输入密码。'
    return
  }

  isLoggedIn.value = true
  statusMessage.value = `${text.welcome}，${username.value.trim()}。`
}

function logout() {
  isLoggedIn.value = false
  password.value = ''
  selectedGameId.value = null
  activeSection.value = 'overview'
  statusMessage.value = '你已退出登录。'
}

function openFolder(folderId: string) {
  explorerCurrentFolderId.value = folderId
  explorerSelectedItemId.value = null
  expandFolderPath(folderId)
}

function explorerGoUp() {
  const current = explorerCurrentFolder.value
  if (!current?.parentId) return
  explorerCurrentFolderId.value = current.parentId
  explorerSelectedItemId.value = null
}

function goToPathFolder(folderId: string) {
  explorerCurrentFolderId.value = folderId
  explorerSelectedItemId.value = null
  expandFolderPath(folderId)
}

function toggleFolderExpand(folderId: string) {
  const nextExpanded = new Set(expandedFolderIds.value)
  if (nextExpanded.has(folderId)) {
    if (folderId !== 'root') {
      nextExpanded.delete(folderId)
    }
  } else {
    nextExpanded.add(folderId)
  }
  expandedFolderIds.value = nextExpanded
}

function expandFolderPath(folderId: string) {
  const parentMap = new Map(explorerFolders.value.map((folder) => [folder.id, folder.parentId]))
  const nextExpanded = new Set(expandedFolderIds.value)

  let cursor: string | null = folderId
  while (cursor) {
    nextExpanded.add(cursor)
    cursor = parentMap.get(cursor) ?? null
  }

  nextExpanded.add('root')
  expandedFolderIds.value = nextExpanded
}

function nextName(baseName: string, parentId: string, kind: ExplorerItemKind) {
  const siblingNames = new Set(
    explorerItems.value
      .filter((item) => item.parentId === parentId && item.kind === kind)
      .map((item) => item.name),
  )
  if (!siblingNames.has(baseName)) return baseName

  let index = 2
  while (siblingNames.has(`${baseName} (${index})`)) {
    index += 1
  }
  return `${baseName} (${index})`
}

function createExplorerItem(kind: ExplorerItemKind) {
  const parentId = explorerCurrentFolderId.value
  const baseName = kind === 'folder' ? 'New Folder' : 'New File.txt'
  const nextItem: ExplorerItem = {
    id: `${kind}-${nextExplorerId.value++}`,
    parentId,
    kind,
    name: nextName(baseName, parentId, kind),
  }
  explorerItems.value.push(nextItem)
  explorerSelectedItemId.value = nextItem.id
  if (kind === 'folder') {
    expandFolderPath(parentId)
  }
  statusMessage.value = `已创建${kind === 'folder' ? '文件夹' : '文件'}：${nextItem.name}`
}

function renameExplorerItem(itemId: string) {
  const item = explorerItems.value.find((entry) => entry.id === itemId)
  if (!item) return

  const input = window.prompt('输入新名称', item.name)
  if (input === null) return
  const next = input.trim()
  if (!next) {
    statusMessage.value = '名称不能为空。'
    return
  }

  item.name = next
  statusMessage.value = `已重命名为：${item.name}`
}

function collectDescendantIds(rootId: string) {
  const removed = new Set<string>([rootId])
  const stack = [rootId]

  while (stack.length) {
    const current = stack.pop()!
    for (const item of explorerItems.value) {
      if (item.parentId !== current) continue
      if (removed.has(item.id)) continue
      removed.add(item.id)
      stack.push(item.id)
    }
  }

  return removed
}

function deleteExplorerItem(itemId: string) {
  const target = explorerItems.value.find((item) => item.id === itemId)
  if (!target || target.id === 'root') return

  const confirmed = window.confirm(
    target.kind === 'folder'
      ? `确定删除文件夹“${target.name}”及其内容？`
      : `确定删除文件“${target.name}”？`,
  )
  if (!confirmed) return

  const removedIds = collectDescendantIds(itemId)
  explorerItems.value = explorerItems.value.filter((item) => !removedIds.has(item.id))

  if (explorerSelectedItemId.value && removedIds.has(explorerSelectedItemId.value)) {
    explorerSelectedItemId.value = null
  }

  if (removedIds.has(explorerCurrentFolderId.value)) {
    explorerCurrentFolderId.value = target.parentId ?? 'root'
  }

  const nextExpanded = new Set(expandedFolderIds.value)
  for (const removedId of removedIds) {
    nextExpanded.delete(removedId)
  }
  nextExpanded.add('root')
  expandedFolderIds.value = nextExpanded

  statusMessage.value = `已删除：${target.name}`
}

function openExplorerItem(item: ExplorerItem) {
  explorerSelectedItemId.value = item.id
  if (item.kind === 'folder') {
    openFolder(item.id)
    statusMessage.value = `已进入文件夹：${item.name}`
    return
  }
  statusMessage.value = `已打开文件：${item.name}`
}

function selectGame(gameId: GameId) {
  selectedGameId.value = gameId
  statusMessage.value = `已启动游戏：${gameOptions.find((item) => item.id === gameId)?.label ?? ''}`
}

function backToGameChooser() {
  selectedGameId.value = null
  statusMessage.value = '已返回游戏列表。'
}

async function toggleGameFullscreen() {
  if (!gamePlayerRef.value) return
  try {
    if (document.fullscreenElement === gamePlayerRef.value) {
      await document.exitFullscreen()
      return
    }
    await gamePlayerRef.value.requestFullscreen()
  } catch {
    statusMessage.value = '当前浏览器不支持全屏或全屏被阻止。'
  }
}

function onFullscreenChange() {
  isGameFullscreen.value = document.fullscreenElement === gamePlayerRef.value
}

function onWindowResize() {
  updateSidebarIndicator(false)
}

watch(
  activeSection,
  async () => {
    await nextTick()
    updateSidebarIndicator(true)
  },
  { flush: 'post' },
)

watch(isLoggedIn, async (loggedIn) => {
  if (!loggedIn) return
  await nextTick()
  updateSidebarIndicator(false)
})

onMounted(() => {
  document.addEventListener('fullscreenchange', onFullscreenChange)
  window.addEventListener('resize', onWindowResize)
  nextTick(() => {
    updateSidebarIndicator(false)
  })
})

onUnmounted(() => {
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  window.removeEventListener('resize', onWindowResize)
  if (sidebarJellyTimer) {
    clearTimeout(sidebarJellyTimer)
  }
  if (glowFrameId !== null) {
    cancelAnimationFrame(glowFrameId)
  }
})
</script>

<template>
  <a class="skip-link" href="#main-content">{{ text.skip }}</a>

  <main
    v-if="!isLoggedIn"
    id="main-content"
    ref="loginRef"
    class="login-view"
    @pointermove="onLoginPointerMove"
    @pointerleave="onLoginPointerLeave"
  >
    <section class="login-card" aria-labelledby="login-title">
      <p class="eyebrow">Workspace Console</p>
      <h1 id="login-title">{{ text.loginTitle }}</h1>
      <p class="subtitle">{{ text.loginSubtitle }}</p>

      <form class="login-form" @submit.prevent="submitLogin">
        <label for="username">{{ text.username }}</label>
        <div class="login-input-shell">
          <input
            id="username"
            v-model="username"
            name="username"
            autocomplete="username"
            type="text"
            spellcheck="false"
            :placeholder="text.usernamePlaceholder"
          />
        </div>

        <label for="password">{{ text.password }}</label>
        <div class="login-input-shell">
          <input
            id="password"
            v-model="password"
            name="password"
            autocomplete="current-password"
            type="password"
            :placeholder="text.passwordPlaceholder"
          />
        </div>

        <p v-if="loginError" class="form-error" aria-live="polite">{{ loginError }}</p>
        <button type="submit">{{ text.loginButton }}</button>
      </form>
    </section>
  </main>

  <main
    v-else
    id="main-content"
    ref="workspaceRef"
    class="workspace-view"
    @pointermove="onWorkspacePointerMove"
    @pointerleave="onWorkspacePointerLeave"
  >
    <header class="topbar">
      <div>
        <p class="eyebrow">Workspace</p>
        <h1>Personal Command Center</h1>
      </div>
      <div class="topbar-actions">
        <p class="user-chip">{{ currentUser }}</p>
        <button type="button" class="ghost-btn" @click="logout">退出</button>
      </div>
    </header>

    <div class="workspace-layout">
      <aside ref="sidebarRef" class="sidebar" aria-label="section-navigation">
        <span
          aria-hidden="true"
          class="nav-active-indicator"
          :class="{ jelly: sidebarIndicatorJelly }"
          :style="sidebarIndicatorStyle"
        ></span>
        <button
          v-for="item in navItems"
          :key="item.id"
          type="button"
          class="nav-item"
          :data-section-id="item.id"
          :class="{ active: activeSection === item.id }"
          @click="setSection(item.id)"
        >
          <span class="nav-icon" aria-hidden="true">
            <component :is="item.icon" class="nav-icon-glyph" />
          </span>
          <span class="nav-copy">
            <strong>{{ item.title }}</strong>
            <small>{{ item.subtitle }}</small>
          </span>
        </button>
      </aside>

      <section class="panel" aria-live="polite">
        <div v-if="activeSection === 'overview'" class="panel-body overview-panel">
          <article class="hero-card">
            <h2>一眼进入高频任务</h2>
            <p>从这里切换到文件、游戏或学习模块。相比原先桌面拖拽窗口模式，这里改为稳定导航 + 单主工作区，减少操作成本。</p>
            <div class="hero-actions">
              <button type="button" class="primary-btn" @click="setSection('explorer')">打开文件管理</button>
              <button type="button" class="ghost-btn" @click="setSection('games')">打开游戏中心</button>
            </div>
          </article>

          <div class="metric-grid">
            <article class="metric-card">
              <p>Folders</p>
              <strong>{{ explorerFolders.length }}</strong>
            </article>
            <article class="metric-card">
              <p>Items In Current Folder</p>
              <strong>{{ explorerChildItems.length }}</strong>
            </article>
            <article class="metric-card">
              <p>Games</p>
              <strong>{{ gameOptions.length }}</strong>
            </article>
          </div>
        </div>

        <div v-else-if="activeSection === 'explorer'" class="panel-body explorer-panel">
          <header class="explorer-toolbar">
            <button
              type="button"
              class="icon-btn"
              :disabled="!explorerCurrentFolder?.parentId"
              aria-label="返回上级目录"
              @click="explorerGoUp"
            >
              <ArrowUpBold class="inline-icon" aria-hidden="true" />
            </button>

            <nav class="pathbar" aria-label="当前路径">
              <button
                v-for="(pathItem, index) in explorerPath"
                :key="pathItem.id"
                type="button"
                class="path-segment"
                @click="goToPathFolder(pathItem.id)"
              >
                <FolderOpened class="inline-icon" aria-hidden="true" />
                <span>{{ pathItem.name }}</span>
                <Right v-if="index < explorerPath.length - 1" class="path-arrow" aria-hidden="true" />
              </button>
            </nav>

            <div class="toolbar-actions">
              <button type="button" class="ghost-btn" @click="createExplorerItem('folder')">
                <Plus class="inline-icon" aria-hidden="true" />新建文件夹
              </button>
              <button type="button" class="ghost-btn" @click="createExplorerItem('file')">
                <Document class="inline-icon" aria-hidden="true" />新建文件
              </button>
            </div>
          </header>

          <div class="explorer-layout">
            <aside class="folder-list" aria-label="所有文件夹">
              <div
                v-for="folder in explorerFolderTreeItems"
                :key="folder.id"
                class="tree-row"
                :style="{ paddingLeft: `${8 + folder.level * 14}px` }"
              >
                <button
                  v-if="folder.hasChildren"
                  type="button"
                  class="tree-toggle"
                  :aria-label="folder.expanded ? '折叠文件夹' : '展开文件夹'"
                  @click="toggleFolderExpand(folder.id)"
                >
                  <Right class="tree-chevron" :class="{ expanded: folder.expanded }" aria-hidden="true" />
                </button>
                <span v-else class="tree-placeholder" aria-hidden="true"></span>

                <button
                  type="button"
                  class="folder-item"
                  :class="{ active: explorerCurrentFolderId === folder.id }"
                  @click="openFolder(folder.id)"
                >
                  <FolderOpened class="inline-icon" aria-hidden="true" />
                  <span>{{ folder.name }}</span>
                </button>
              </div>
            </aside>

            <div class="file-list" role="list">
              <article v-for="item in explorerChildItems" :key="item.id" class="file-card" role="listitem">
                <button
                  type="button"
                  class="file-main"
                  :class="{ selected: explorerSelectedItemId === item.id }"
                  @click="openExplorerItem(item)"
                >
                  <span class="file-icon" aria-hidden="true">
                    <FolderOpened v-if="item.kind === 'folder'" class="inline-icon" />
                    <Document v-else class="inline-icon" />
                  </span>
                  <span class="file-text">
                    <strong>{{ item.name }}</strong>
                    <small>{{ item.kind === 'folder' ? '文件夹' : '文件' }}</small>
                  </span>
                </button>
                <div class="file-actions">
                  <button
                    type="button"
                    class="icon-btn"
                    :aria-label="`重命名 ${item.name}`"
                    @click="renameExplorerItem(item.id)"
                  >
                    <EditPen class="inline-icon" aria-hidden="true" />
                  </button>
                  <button
                    type="button"
                    class="icon-btn danger"
                    :aria-label="`删除 ${item.name}`"
                    @click="deleteExplorerItem(item.id)"
                  >
                    <Delete class="inline-icon" aria-hidden="true" />
                  </button>
                </div>
              </article>

              <p v-if="!explorerChildItems.length" class="empty">当前目录为空。</p>
            </div>
          </div>
        </div>

        <div v-else-if="activeSection === 'games'" class="panel-body games-panel">
          <div v-if="!activeGame" class="game-grid">
            <button
              v-for="game in gameOptions"
              :key="game.id"
              type="button"
              class="game-card"
              @click="selectGame(game.id)"
            >
              <span class="game-icon" aria-hidden="true">
                <Trophy class="inline-icon" />
              </span>
              <strong>{{ game.label }}</strong>
              <small>{{ game.subtitle }}</small>
            </button>
          </div>

          <div v-else ref="gamePlayerRef" class="game-player">
            <header class="game-player-bar">
              <div class="game-title-wrap">
                <Monitor class="inline-icon" aria-hidden="true" />
                <strong>{{ activeGame.label }}</strong>
              </div>
              <div class="game-actions">
                <button
                  type="button"
                  class="ghost-btn"
                  :aria-label="isGameFullscreen ? '退出全屏' : '进入全屏'"
                  @click="toggleGameFullscreen"
                >
                  {{ isGameFullscreen ? '退出全屏' : '全屏' }}
                </button>
                <button type="button" class="ghost-btn" @click="backToGameChooser">返回列表</button>
              </div>
            </header>
            <iframe
              class="game-frame"
              :title="activeGame.label"
              :src="activeGame.path"
              loading="lazy"
              allow="fullscreen"
            />
          </div>
        </div>

        <div v-else class="panel-body school-panel">
          <article class="hero-card compact">
            <h2>学习路径</h2>
            <p>你可以把课程链接、阶段任务、周计划集中放在这里。</p>
          </article>
          <div class="study-grid">
            <article class="study-card">
              <h3>Frontend</h3>
              <p>Vue + TypeScript 组件拆分、状态设计、可访问性。</p>
            </article>
            <article class="study-card">
              <h3>Graphics</h3>
              <p>游戏渲染循环、碰撞检测、输入系统与性能优化。</p>
            </article>
            <article class="study-card">
              <h3>Deployment</h3>
              <p>构建产物、缓存策略、静态资源托管与监控。</p>
            </article>
          </div>
        </div>
      </section>
    </div>

    <p class="status" aria-live="polite">{{ statusMessage }}</p>
  </main>
</template>
