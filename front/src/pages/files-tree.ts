export interface DirectoryTreeNode {
  id: string;
  name: string;
  path: string[];
  depth: number;
  active: boolean;
  expanded: boolean;
  children: DirectoryTreeNode[];
}

export type DirectoryChildrenMap = Record<string, string[]>;

export function toDirectoryPath(pathParts: string[]) {
  return pathParts.length === 0 ? '/' : `/${pathParts.join('/')}`;
}

export function createExpandedDirectorySet(pathParts: string[]) {
  const expandedPaths = new Set<string>(['/']);
  const segments: string[] = [];

  for (const part of pathParts) {
    segments.push(part);
    expandedPaths.add(toDirectoryPath(segments));
  }

  return expandedPaths;
}

export function mergeDirectoryChildren(
  directoryChildren: DirectoryChildrenMap,
  parentPath: string,
  childNames: string[],
) {
  const nextNames = new Set(directoryChildren[parentPath] ?? []);
  for (const childName of childNames) {
    const normalizedName = childName.trim();
    if (normalizedName) {
      nextNames.add(normalizedName);
    }
  }

  return {
    ...directoryChildren,
    [parentPath]: [...nextNames],
  };
}

export function getMissingDirectoryListingPaths(
  pathParts: string[],
  loadedDirectoryPaths: Set<string>,
) {
  const missingPaths: string[][] = [];

  for (let depth = 0; depth < pathParts.length; depth += 1) {
    const ancestorPath = pathParts.slice(0, depth);
    if (!loadedDirectoryPaths.has(toDirectoryPath(ancestorPath))) {
      missingPaths.push(ancestorPath);
    }
  }

  return missingPaths;
}

export function buildDirectoryTree(
  directoryChildren: DirectoryChildrenMap,
  currentPath: string[],
  expandedPaths: Set<string>,
): DirectoryTreeNode[] {
  function getChildNames(parentPath: string, parentParts: string[]) {
    const nextNames = new Set(directoryChildren[parentPath] ?? []);
    const currentChild = currentPath[parentParts.length];
    if (currentChild) {
      nextNames.add(currentChild);
    }
    return [...nextNames];
  }

  function buildNodes(parentPath: string, parentParts: string[]): DirectoryTreeNode[] {
    return getChildNames(parentPath, parentParts).map((name) => {
      const path = [...parentParts, name];
      const id = toDirectoryPath(path);
      const expanded = expandedPaths.has(id);

      return {
        id,
        name,
        path,
        depth: parentParts.length,
        active: path.join('/') === currentPath.join('/'),
        expanded,
        children: expanded ? buildNodes(id, path) : [],
      };
    });
  }

  return buildNodes('/', []);
}
