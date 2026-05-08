import React, { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Loader2, RefreshCw } from 'lucide-react';
import { listFiles } from '../../lib/files';
import { getSession } from '../../lib/session';
import {
  buildWorkspaceFilesHref,
  getWorkspaceFolderAncestorPaths,
  getWorkspaceFolderName,
  getWorkspaceFolderPathFromSearchParams,
  normalizeWorkspaceFolderPath,
  WORKSPACE_FOLDER_TREE_REFRESH_EVENT,
  WORKSPACE_FOLDER_TREE_STORAGE_KEY,
} from '../../lib/workspace-folder-tree';
import WorkspaceFolderTreeNode, { type WorkspaceFolderTreeNodeState } from './WorkspaceFolderTreeNode';

const ROOT_PATH = '/';
const TREE_PAGE_SIZE = 200;
let cachedNodes: Record<string, WorkspaceFolderTreeNodeState> | null = null;
let cachedTreeUserId: number | null = null;

function createTreeNode(path: string): WorkspaceFolderTreeNodeState {
  return {
    path,
    name: getWorkspaceFolderName(path),
    childPaths: null,
    childrenStatus: 'unknown',
  };
}

function getCurrentTreeUserId() {
  return getSession()?.user.id ?? null;
}

function createRootLoadingNodes() {
  return {
    [ROOT_PATH]: {
      ...createTreeNode(ROOT_PATH),
      childrenStatus: 'loading' as const,
    },
  };
}

function getInitialCachedNodes() {
  const currentUserId = getCurrentTreeUserId();
  if (cachedTreeUserId !== currentUserId) {
    cachedNodes = null;
    cachedTreeUserId = currentUserId;
  }

  return cachedNodes ?? createRootLoadingNodes();
}

function getLogicalPath(path: string, filename: string) {
  if (path === '/') {
    return `/${filename}`;
  }
  if (path === filename || path.endsWith(`/${filename}`)) {
    return path;
  }
  return `${path}/${filename}`;
}

function getNearestLoadedAncestorPath(path: string, nodes: Record<string, WorkspaceFolderTreeNodeState>) {
  let candidatePath = normalizeWorkspaceFolderPath(path);
  while (candidatePath !== ROOT_PATH && !nodes[candidatePath]) {
    const slashIndex = candidatePath.lastIndexOf('/');
    candidatePath = slashIndex <= 0 ? ROOT_PATH : candidatePath.slice(0, slashIndex);
  }
  return nodes[candidatePath] ? candidatePath : ROOT_PATH;
}

function restoreExpandedPaths() {
  if (typeof window === 'undefined') {
    return [];
  }

  try {
    const raw = window.localStorage.getItem(WORKSPACE_FOLDER_TREE_STORAGE_KEY);
    if (!raw) {
      return [];
    }

    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      return [];
    }

    return Array.from(
      new Set(
        parsed
          .map((value) => normalizeWorkspaceFolderPath(typeof value === 'string' ? value : ''))
          .filter(Boolean)
      ),
    );
  } catch {
    return [];
  }
}

const WorkspaceFolderTree: React.FC<{ 
  onNavigate?: () => void;
  registerDropTarget?: (el: HTMLElement | null, path: string) => void;
  activeDropTarget?: string | null;
}> = ({ onNavigate, registerDropTarget, activeDropTarget }) => {
  const location = useLocation();
  const navigate = useNavigate();
  const nodesRef = useRef<Record<string, WorkspaceFolderTreeNodeState>>({});
  const [nodes, setNodes] = useState<Record<string, WorkspaceFolderTreeNodeState>>(() => getInitialCachedNodes());
  const [expandedPaths, setExpandedPaths] = useState<string[]>(restoreExpandedPaths);
  const searchParams = new URLSearchParams(location.search);
  const currentPath = location.pathname === '/dashboard/files'
    ? getWorkspaceFolderPathFromSearchParams(searchParams)
    : null;
  const rootNode = nodes[ROOT_PATH];

  useEffect(() => {
    nodesRef.current = nodes;
    cachedNodes = nodes;
    cachedTreeUserId = getCurrentTreeUserId();
  }, [nodes]);

  useEffect(() => {
    const handleSessionChanged = () => {
      const nextUserId = getCurrentTreeUserId();
      if (cachedTreeUserId === nextUserId) {
        return;
      }

      cachedNodes = null;
      cachedTreeUserId = nextUserId;
      const rootNodes = createRootLoadingNodes();
      nodesRef.current = rootNodes;
      setNodes(rootNodes);
    };

    window.addEventListener('portal-session-changed', handleSessionChanged as EventListener);
    return () => {
      window.removeEventListener('portal-session-changed', handleSessionChanged as EventListener);
    };
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined') {
      return;
    }

    window.localStorage.setItem(
      WORKSPACE_FOLDER_TREE_STORAGE_KEY,
      JSON.stringify(Array.from(new Set(expandedPaths))),
    );
  }, [expandedPaths]);

  async function loadFolderChildren(path: string, force = false) {
    const normalizedPath = normalizeWorkspaceFolderPath(path);
    const existing = nodesRef.current[normalizedPath] ?? createTreeNode(normalizedPath);

    if (!force && existing.childPaths !== null) {
      return;
    }

    if (!force && existing.childrenStatus === 'loading') {
      return;
    }

    setNodes((current) => ({
      ...current,
      [normalizedPath]: {
        ...(current[normalizedPath] ?? createTreeNode(normalizedPath)),
        childrenStatus: 'loading',
      },
    }));

    try {
      const result = await listFiles(normalizedPath, 0, TREE_PAGE_SIZE);
      const folders = result.items
        .filter((item) => item.directory)
        .map((item) => {
          return {
            path: normalizeWorkspaceFolderPath(getLogicalPath(item.path, item.filename)),
            name: item.filename,
            hasChildDirectory: item.hasChildDirectory,
            customEmoji: item.customEmoji,
            folderColor: item.folderColor,
          };
        });

      setNodes((current) => {
        const next = { ...current };
        next[normalizedPath] = {
          ...(next[normalizedPath] ?? createTreeNode(normalizedPath)),
          childPaths: folders.map((folder) => folder.path),
          childrenStatus: folders.length > 0 ? 'has-folders' : 'empty',
        };

        for (const folder of folders) {
          next[folder.path] = {
            ...(next[folder.path] ?? createTreeNode(folder.path)),
            name: folder.name,
            childrenStatus: folder.hasChildDirectory ? 'has-folders' : 'empty',
            customEmoji: folder.customEmoji,
            folderColor: folder.folderColor,
          };
        }

        return next;
      });
    } catch {
      setNodes((current) => ({
        ...current,
        [normalizedPath]: {
          ...(current[normalizedPath] ?? createTreeNode(normalizedPath)),
          childrenStatus: 'error',
        },
      }));
    }
  }

  async function ensurePathVisible(path: string) {
    const normalizedPath = normalizeWorkspaceFolderPath(path);
    if (normalizedPath === ROOT_PATH) {
      return;
    }
    const ancestors = getWorkspaceFolderAncestorPaths(normalizedPath);

    setExpandedPaths((current) =>
      Array.from(new Set(current.concat(ancestors))),
    );

    for (const ancestor of ancestors) {
      await loadFolderChildren(ancestor);
    }
  }

  async function refreshFolderPath(path: string) {
    const normalizedPath = normalizeWorkspaceFolderPath(path);
    const refreshTargetPath = getNearestLoadedAncestorPath(normalizedPath, nodesRef.current);

    setNodes((current) => ({
      ...current,
      [refreshTargetPath]: {
        ...(current[refreshTargetPath] ?? createTreeNode(refreshTargetPath)),
        childPaths: null,
        childrenStatus: 'unknown',
      },
    }));

    await loadFolderChildren(refreshTargetPath, true);
  }

  useEffect(() => {
    if (rootNode?.childPaths !== null) {
      return;
    }

    void loadFolderChildren(ROOT_PATH);
  }, [rootNode?.childPaths, rootNode?.childrenStatus]);

  useEffect(() => {
    if (!currentPath) {
      return;
    }

    void ensurePathVisible(currentPath);
  }, [currentPath]);

  useEffect(() => {
    const handleRefresh = (event: Event) => {
      const detail = (event as CustomEvent<{ paths?: string[] }>).detail;
      const paths = Array.isArray(detail?.paths) ? detail.paths : [];

      void Promise.all(paths.map((path) => refreshFolderPath(path)));
    };

    window.addEventListener(WORKSPACE_FOLDER_TREE_REFRESH_EVENT, handleRefresh as EventListener);
    return () => window.removeEventListener(WORKSPACE_FOLDER_TREE_REFRESH_EVENT, handleRefresh as EventListener);
  }, []);

  useEffect(() => {
    const handleAutoExpand = (event: Event) => {
      const detail = (event as CustomEvent<{ path: string }>).detail;
      const path = detail?.path;
      if (path && !expandedPaths.includes(path)) {
        handleToggle(path);
      }
    };

    window.addEventListener('workspace-tree-auto-expand', handleAutoExpand as EventListener);
    return () => window.removeEventListener('workspace-tree-auto-expand', handleAutoExpand as EventListener);
  }, [expandedPaths]);

  function handleSelect(path: string) {
    navigate(buildWorkspaceFilesHref(path));
    onNavigate?.();
  }

  function handleToggle(path: string) {
    const normalizedPath = normalizeWorkspaceFolderPath(path);
    const expanded = expandedPaths.includes(normalizedPath);

    if (expanded) {
      setExpandedPaths((current) =>
        current.filter(
          (currentPathValue) =>
            currentPathValue !== normalizedPath && !currentPathValue.startsWith(`${normalizedPath}/`),
        ),
      );
      return;
    }

    setExpandedPaths((current) => Array.from(new Set(current.concat(normalizedPath))));
    void loadFolderChildren(normalizedPath);
  }

  function renderNode(path: string, depth: number): React.ReactNode {
    const node = nodes[path];
    if (!node) {
      return null;
    }

    const expanded = expandedPaths.includes(path);

    return (
      <WorkspaceFolderTreeNode
        key={path}
        node={node}
        depth={depth}
        currentPath={currentPath}
        expanded={expanded}
        onSelect={handleSelect}
        onToggle={handleToggle}
        onRetry={(retryPath) => {
          void refreshFolderPath(retryPath);
        }}
        registerDropTarget={registerDropTarget}
        activeDropTarget={activeDropTarget}
      >
        {expanded && node.childPaths?.length
          ? (
            <div className="mt-0.5 space-y-0.5">
              {node.childPaths.map((childPath) => renderNode(childPath, depth + 1))}
            </div>
            )
          : null}
      </WorkspaceFolderTreeNode>
    );
  }

  if (rootNode?.childrenStatus === 'loading' && !rootNode.childPaths) {
    return (
      <div className="ml-4 mt-2 flex items-center gap-2 px-2 py-2 text-xs text-slate-400 dark:text-slate-500">
        <Loader2 size={12} className="animate-spin" />
        <span>正在加载目录...</span>
      </div>
    );
  }

  if (rootNode?.childrenStatus === 'error' && !rootNode.childPaths) {
    return (
      <div className="ml-4 mt-2 rounded-xl border border-amber-200/70 bg-amber-50/80 px-3 py-2 text-xs text-amber-700 dark:border-amber-500/20 dark:bg-amber-500/10 dark:text-amber-200">
        <div>目录树加载失败</div>
        <button
          type="button"
          className="mt-2 inline-flex items-center gap-1 font-medium"
          onClick={() => {
            void refreshFolderPath(ROOT_PATH);
          }}
        >
          <RefreshCw size={12} />
          <span>重试</span>
        </button>
      </div>
    );
  }

  if (!rootNode?.childPaths?.length) {
    return (
      <div className="ml-4 mt-2 px-2 py-2 text-xs text-slate-400 dark:text-slate-500">
        暂无文件夹
      </div>
    );
  }

  return (
    <div className="ml-4 mt-2 border-l border-slate-200/70 pl-2 dark:border-white/10">
      <div className="space-y-0.5">
        {rootNode.childPaths.map((childPath) => renderNode(childPath, 0))}
      </div>
    </div>
  );
};

export default WorkspaceFolderTree;
