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
import { isRectVisible } from './lighting'
import { calculateRevealRadius, resolveInitialTheme, toggleTheme, type Theme } from './theme'

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

interface SectionMeta {
  eyebrow: string
  title: string
  description: string
  badge: string
}

interface LightingTarget {
  element: HTMLElement
  left: number
  top: number
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

const THEME_STORAGE_KEY = 'workspace-theme'
const THEME_REVEAL_DURATION_MS = 520
const THEME_APPLY_OFFSET_MS = 320
const THEME_CANVAS_COLORS: Record<Theme, string> = {
  light: '#f5f8fc',
  dark: '#070d16',
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
const theme = ref<Theme>('light')
const followsSystemTheme = ref(true)
const themeRevealActive = ref(false)
const themeRevealStyle = ref<Record<string, string>>({
  '--reveal-x': '50vw',
  '--reveal-y': '50vh',
  '--reveal-radius': '0px',
  '--reveal-color': THEME_CANVAS_COLORS.light,
})
let sidebarJellyTimer: ReturnType<typeof setTimeout> | null = null
let glowFrameId: number | null = null
let lightingTargetRefreshFrameId: number | null = null
let themeApplyTimer: ReturnType<typeof setTimeout> | null = null
let themeRevealCleanupTimer: ReturnType<typeof setTimeout> | null = null
let systemThemeMediaQuery: MediaQueryList | null = null
let systemThemeChangeHandler: ((event: MediaQueryListEvent) => void) | null = null
let latestPointer: { x: number; y: number } | null = null
let latestPointerTarget: 'workspace' | 'login' | null = null
let workspaceLightTargets: LightingTarget[] = []
let loginLightTargets: LightingTarget[] = []
let workspaceLightObserver: MutationObserver | null = null
let loginLightObserver: MutationObserver | null = null
const lightTargetSelector =
  '.nav-item, .ghost-btn, .primary-btn, .icon-btn, .game-card, .file-main, .folder-item, .path-segment'
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
const isDarkTheme = computed(() => theme.value === 'dark')
const themeToggleAriaLabel = computed(() => (isDarkTheme.value ? '切换为浅色主题' : '切换为深色主题'))
const sectionMeta = computed<SectionMeta>(() => {
  switch (activeSection.value) {
    case 'explorer':
      return {
        eyebrow: 'Explorer',
        title: '文件编排',
        description: '用更清晰的目录树和文件卡片处理工作区结构。',
        badge: `${explorerCurrentFolder.value?.name ?? 'Workspace'} · ${explorerChildItems.value.length} 项`,
      }
    case 'games':
      return {
        eyebrow: 'Arcade',
        title: '游戏中心',
        description: '切换到内置小游戏，支持沉浸式全屏与快速返回。',
        badge: activeGame.value ? `Now Playing · ${activeGame.value.label}` : `${gameOptions.length} 个可玩项目`,
      }
    case 'school':
      return {
        eyebrow: 'Learning',
        title: '学习路径',
        description: '把阶段任务、知识结构与部署能力集中到同一视图。',
        badge: '3 条核心进阶路线',
      }
    default:
      return {
        eyebrow: 'Overview',
        title: '总览驾驶舱',
        description: '把常用入口、当前状态与近期重点放在同一块主画布里。',
        badge: `${navItems.length} 个模块 · ${explorerFolders.value.length} 个目录`,
      }
  }
})
const overviewSignals = computed(() => [
  {
    label: '当前模块',
    value: sectionMeta.value.title,
    note: '主工作流状态',
  },
  {
    label: '路径深度',
    value: `${explorerPath.value.length} 层`,
    note: '当前文件位置',
  },
  {
    label: '主题模式',
    value: isDarkTheme.value ? 'Dark' : 'Light',
    note: followsSystemTheme.value ? '跟随系统' : '手动切换',
  },
])
const studyTracks = [
  {
    title: 'Frontend Systems',
    description: 'Vue + TypeScript 组件拆分、状态设计、可访问性。',
    meta: '组件化 · 可访问性 · 工程化',
  },
  {
    title: 'Graphics',
    description: '游戏渲染循环、碰撞检测、输入系统与性能优化。',
    meta: 'Canvas · 循环调度 · 性能',
  },
  {
    title: 'Deployment',
    description: '构建产物、缓存策略、静态资源托管与监控。',
    meta: '构建 · 托管 · 监控',
  },
]

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

function readStoredTheme(): Theme | null {
  try {
    const raw = localStorage.getItem(THEME_STORAGE_KEY)
    return raw === 'light' || raw === 'dark' ? raw : null
  } catch {
    return null
  }
}

function writeStoredTheme(nextTheme: Theme) {
  try {
    localStorage.setItem(THEME_STORAGE_KEY, nextTheme)
  } catch {
    // Ignore storage write errors (private mode, quota, etc.)
  }
}

function updateThemeMeta(nextTheme: Theme) {
  let meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')
  if (!meta) {
    meta = document.createElement('meta')
    meta.name = 'theme-color'
    document.head.appendChild(meta)
  }
  meta.content = THEME_CANVAS_COLORS[nextTheme]
}

function applyTheme(nextTheme: Theme) {
  theme.value = nextTheme
  document.documentElement.setAttribute('data-theme', nextTheme)
  updateThemeMeta(nextTheme)
  scheduleLightingTargetsRefresh()
}

function clearThemeTimers() {
  if (themeApplyTimer) {
    clearTimeout(themeApplyTimer)
    themeApplyTimer = null
  }
  if (themeRevealCleanupTimer) {
    clearTimeout(themeRevealCleanupTimer)
    themeRevealCleanupTimer = null
  }
}

function handleThemeToggle(event: MouseEvent) {
  const nextTheme = toggleTheme(theme.value)
  followsSystemTheme.value = false
  writeStoredTheme(nextTheme)

  const toggleButton = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (reduceMotion || !toggleButton) {
    themeRevealActive.value = false
    clearThemeTimers()
    applyTheme(nextTheme)
    return
  }

  clearThemeTimers()

  const rect = toggleButton.getBoundingClientRect()
  const originX = rect.left + rect.width / 2
  const originY = rect.top + rect.height / 2
  const radius = calculateRevealRadius(originX, originY, window.innerWidth, window.innerHeight)

  themeRevealStyle.value = {
    '--reveal-x': `${originX}px`,
    '--reveal-y': `${originY}px`,
    '--reveal-radius': `${radius}px`,
    '--reveal-color': THEME_CANVAS_COLORS[nextTheme],
  }

  themeRevealActive.value = false
  requestAnimationFrame(() => {
    themeRevealActive.value = true
  })

  themeApplyTimer = setTimeout(() => {
    applyTheme(nextTheme)
    themeApplyTimer = null
  }, THEME_APPLY_OFFSET_MS)

  themeRevealCleanupTimer = setTimeout(() => {
    themeRevealActive.value = false
    themeRevealCleanupTimer = null
  }, THEME_REVEAL_DURATION_MS)
}

function initializeTheme() {
  const storedTheme = readStoredTheme()
  followsSystemTheme.value = storedTheme === null
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
  applyTheme(resolveInitialTheme(storedTheme, prefersDark))
}

function buildLightingTargets(root: HTMLElement, selector: string) {
  const viewportWidth = window.innerWidth
  const viewportHeight = window.innerHeight
  const targets: LightingTarget[] = []
  for (const element of root.querySelectorAll<HTMLElement>(selector)) {
    const rect = element.getBoundingClientRect()
    if (!isRectVisible(rect, viewportWidth, viewportHeight)) continue
    const target: LightingTarget = {
      element,
      left: rect.left,
      top: rect.top,
    }
    element.style.setProperty('--target-left', `${target.left}px`)
    element.style.setProperty('--target-top', `${target.top}px`)
    targets.push(target)
  }
  return targets
}

function refreshLightingTargets() {
  lightingTargetRefreshFrameId = null
  workspaceLightTargets = workspaceRef.value ? buildLightingTargets(workspaceRef.value, lightTargetSelector) : []
  loginLightTargets = loginRef.value ? buildLightingTargets(loginRef.value, loginLightTargetSelector) : []
}

function scheduleLightingTargetsRefresh() {
  if (lightingTargetRefreshFrameId !== null) return
  lightingTargetRefreshFrameId = requestAnimationFrame(refreshLightingTargets)
}

function setContainerPointer(container: HTMLElement | null, clientX: number, clientY: number) {
  if (!container) return
  container.style.setProperty('--pointer-x', `${clientX}px`)
  container.style.setProperty('--pointer-y', `${clientY}px`)
}

function clearContainerPointer(container: HTMLElement | null) {
  if (!container) return
  container.style.setProperty('--pointer-x', '-9999px')
  container.style.setProperty('--pointer-y', '-9999px')
}

function reconnectLightingObservers() {
  if (workspaceLightObserver) {
    workspaceLightObserver.disconnect()
    workspaceLightObserver = null
  }
  if (loginLightObserver) {
    loginLightObserver.disconnect()
    loginLightObserver = null
  }

  if (workspaceRef.value) {
    workspaceLightObserver = new MutationObserver(scheduleLightingTargetsRefresh)
    workspaceLightObserver.observe(workspaceRef.value, { childList: true, subtree: true })
  }
  if (loginRef.value) {
    loginLightObserver = new MutationObserver(scheduleLightingTargetsRefresh)
    loginLightObserver.observe(loginRef.value, { childList: true, subtree: true })
  }
}

function applyMouseLighting(clientX: number, clientY: number) {
  setContainerPointer(workspaceRef.value, clientX, clientY)
}

function applyLoginLighting(clientX: number, clientY: number) {
  const login = loginRef.value
  if (login) {
    login.classList.add('lighting-active')
  }
  setContainerPointer(login, clientX, clientY)
}

function flushMouseLighting() {
  glowFrameId = null
  if (document.hidden) return
  if (!latestPointer) return

  if (latestPointerTarget === 'workspace') {
    applyMouseLighting(latestPointer.x, latestPointer.y)
  } else if (latestPointerTarget === 'login') {
    applyLoginLighting(latestPointer.x, latestPointer.y)
  }
}

function onWorkspacePointerMove(event: PointerEvent) {
  if (!workspaceLightTargets.length) {
    scheduleLightingTargetsRefresh()
  }
  latestPointerTarget = 'workspace'
  latestPointer = { x: event.clientX, y: event.clientY }
  if (glowFrameId !== null) return
  glowFrameId = requestAnimationFrame(flushMouseLighting)
}

function onWorkspacePointerLeave() {
  clearContainerPointer(workspaceRef.value)
  latestPointerTarget = null
}

function onLoginPointerMove(event: PointerEvent) {
  if (!loginLightTargets.length) {
    scheduleLightingTargetsRefresh()
  }
  latestPointerTarget = 'login'
  latestPointer = { x: event.clientX, y: event.clientY }
  if (glowFrameId !== null) return
  glowFrameId = requestAnimationFrame(flushMouseLighting)
}

function onLoginPointerLeave() {
  const login = loginRef.value
  if (!login) return
  login.classList.remove('lighting-active')
  clearContainerPointer(login)
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
  scheduleLightingTargetsRefresh()
}

function onAnyScroll() {
  scheduleLightingTargetsRefresh()
}

if (typeof window !== 'undefined') {
  initializeTheme()
}

watch(
  activeSection,
  async () => {
    await nextTick()
    updateSidebarIndicator(true)
    scheduleLightingTargetsRefresh()
  },
  { flush: 'post' },
)

watch(isLoggedIn, async (loggedIn) => {
  await nextTick()
  if (loggedIn) {
    updateSidebarIndicator(false)
  }
  refreshLightingTargets()
  reconnectLightingObservers()
  clearContainerPointer(workspaceRef.value)
  clearContainerPointer(loginRef.value)
})

onMounted(() => {
  document.addEventListener('fullscreenchange', onFullscreenChange)
  window.addEventListener('resize', onWindowResize)
  document.addEventListener('scroll', onAnyScroll, true)
  systemThemeMediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  systemThemeChangeHandler = (event) => {
    if (!followsSystemTheme.value) return
    applyTheme(event.matches ? 'dark' : 'light')
  }
  systemThemeMediaQuery.addEventListener('change', systemThemeChangeHandler)
  nextTick(() => {
    updateSidebarIndicator(false)
    refreshLightingTargets()
    reconnectLightingObservers()
    clearContainerPointer(workspaceRef.value)
    clearContainerPointer(loginRef.value)
  })
})

onUnmounted(() => {
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  window.removeEventListener('resize', onWindowResize)
  document.removeEventListener('scroll', onAnyScroll, true)
  if (systemThemeMediaQuery && systemThemeChangeHandler) {
    systemThemeMediaQuery.removeEventListener('change', systemThemeChangeHandler)
  }
  clearThemeTimers()
  if (sidebarJellyTimer) {
    clearTimeout(sidebarJellyTimer)
  }
  if (glowFrameId !== null) {
    cancelAnimationFrame(glowFrameId)
  }
  if (lightingTargetRefreshFrameId !== null) {
    cancelAnimationFrame(lightingTargetRefreshFrameId)
  }
  if (workspaceLightObserver) {
    workspaceLightObserver.disconnect()
  }
  if (loginLightObserver) {
    loginLightObserver.disconnect()
  }
})
</script>

<template>
  <a class="skip-link" href="#main-content">{{ text.skip }}</a>
  <div
    aria-hidden="true"
    class="theme-reveal-layer"
    :class="{ active: themeRevealActive }"
    :style="themeRevealStyle"
  ></div>

  <Transition name="view-swap" mode="out-in">
    <main
      v-if="!isLoggedIn"
      id="main-content"
      key="login"
      ref="loginRef"
      class="login-view"
      @pointermove="onLoginPointerMove"
      @pointerleave="onLoginPointerLeave"
    >
      <button
        type="button"
        class="theme-toggle theme-toggle-floating"
        :class="{ 'is-dark': isDarkTheme }"
        :aria-label="themeToggleAriaLabel"
        @click="handleThemeToggle"
      >
        <span class="theme-toggle-track" aria-hidden="true">
          <span class="theme-toggle-sun">浅</span>
          <span class="theme-toggle-moon">深</span>
          <span class="theme-toggle-thumb"></span>
        </span>
      </button>

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
      key="workspace"
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
        <button
          type="button"
          class="theme-toggle"
          :class="{ 'is-dark': isDarkTheme }"
          :aria-label="themeToggleAriaLabel"
          @click="handleThemeToggle"
        >
          <span class="theme-toggle-track" aria-hidden="true">
            <span class="theme-toggle-sun">浅</span>
            <span class="theme-toggle-moon">深</span>
            <span class="theme-toggle-thumb"></span>
          </span>
        </button>
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
        <div class="panel-body">
          <header class="panel-intro">
            <div>
              <p class="eyebrow">{{ sectionMeta.eyebrow }}</p>
              <h2>{{ sectionMeta.title }}</h2>
              <p>{{ sectionMeta.description }}</p>
            </div>
            <span class="panel-intro-badge">{{ sectionMeta.badge }}</span>
          </header>

          <div v-if="activeSection === 'overview'" class="panel-section overview-panel">
            <div class="overview-hero-grid">
              <article class="hero-card hero-card-featured">
                <div class="hero-copy">
                  <span class="hero-kicker">Workspace Flow</span>
                  <h3>一眼进入高频任务</h3>
                  <p>
                    从这里切换到文件、游戏或学习模块。界面改为稳定导航 + 单主工作区，并用更强的层次、光感与状态反馈降低切换成本。
                  </p>
                </div>
                <div class="hero-actions">
                  <button type="button" class="primary-btn" @click="setSection('explorer')">打开文件管理</button>
                  <button type="button" class="ghost-btn" @click="setSection('games')">打开游戏中心</button>
                </div>
                <div class="hero-stat-strip" aria-label="总览状态">
                  <div v-for="signal in overviewSignals" :key="signal.label" class="hero-stat">
                    <small>{{ signal.label }}</small>
                    <strong>{{ signal.value }}</strong>
                    <span>{{ signal.note }}</span>
                  </div>
                </div>
              </article>

              <aside class="insight-rail" aria-label="工作区重点">
                <article class="insight-card ambient">
                  <span class="insight-pill">Live Pulse</span>
                  <strong>主工作区在线</strong>
                  <p>导航、主题切换与局部高光已统一到同一套交互语言。</p>
                </article>
                <article class="insight-card">
                  <span class="insight-pill neutral">Focus</span>
                  <strong>{{ explorerCurrentFolder?.name ?? 'Workspace' }}</strong>
                  <p>当前目录可直接新建、重命名与回退，减少跳转路径。</p>
                </article>
              </aside>
            </div>

            <div class="metric-grid">
              <article class="metric-card">
                <p>Folders</p>
                <strong>{{ explorerFolders.length }}</strong>
                <span>工作区目录总数</span>
              </article>
              <article class="metric-card">
                <p>Items In Current Folder</p>
                <strong>{{ explorerChildItems.length }}</strong>
                <span>当前层级可操作项</span>
              </article>
              <article class="metric-card">
                <p>Games</p>
                <strong>{{ gameOptions.length }}</strong>
                <span>支持快速进入沉浸模式</span>
              </article>
            </div>
          </div>

          <div v-else-if="activeSection === 'explorer'" class="panel-section explorer-panel">
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

          <div v-else-if="activeSection === 'games'" class="panel-section games-panel">
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
              <span class="game-meta">Launch Experience</span>
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

          <div v-else class="panel-section school-panel">
          <article class="hero-card compact">
            <h3>学习路径</h3>
            <p>把课程链接、阶段任务和周计划集中到一个高可读性的学习面板里。</p>
          </article>
          <div class="study-grid">
            <article v-for="track in studyTracks" :key="track.title" class="study-card">
              <span class="study-meta">{{ track.meta }}</span>
              <h3>{{ track.title }}</h3>
              <p>{{ track.description }}</p>
            </article>
          </div>
          </div>
        </div>
      </section>
    </div>

    <p class="status" aria-live="polite">{{ statusMessage }}</p>
    </main>
  </Transition>
</template>
