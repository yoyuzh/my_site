export type FolderUploadFilePlan = {
  file: File;
  relativeDirectoryPath: string;
};

export type FolderUploadPlan = {
  rootFolderName: string;
  directories: string[];
  files: FolderUploadFilePlan[];
};

function getFileRelativePath(file: File) {
  if (
    'webkitRelativePath' in file &&
    typeof file.webkitRelativePath === 'string' &&
    file.webkitRelativePath.trim().length > 0
  ) {
    return file.webkitRelativePath;
  }
  return file.name;
}

function parseRelativePathSegments(relativePath: string) {
  const segments = relativePath
    .split('/')
    .map((segment) => segment.trim())
    .filter(Boolean);

  if (segments.some((segment) => segment === '.' || segment === '..')) {
    throw new Error(`目录上传路径不合法: ${relativePath}`);
  }

  return segments;
}

function compareRelativeDirectoryPath(left: string, right: string) {
  const leftDepth = left.split('/').filter(Boolean).length;
  const rightDepth = right.split('/').filter(Boolean).length;
  if (leftDepth !== rightDepth) {
    return leftDepth - rightDepth;
  }
  return left.localeCompare(right, 'zh-CN');
}

export function buildFolderUploadPlans(files: File[]): FolderUploadPlan[] {
  if (files.length === 0) {
    return [];
  }

  const plans = new Map<string, { directories: Set<string>; files: FolderUploadFilePlan[] }>();

  for (const file of files) {
    const relativePath = getFileRelativePath(file);
    const segments = parseRelativePathSegments(relativePath);
    if (segments.length < 2) {
      throw new Error(`无法从目录上传中解析相对路径: ${relativePath}`);
    }

    const [rootFolderName, ...restSegments] = segments;
    const directorySegments = restSegments.slice(0, -1);
    const relativeDirectoryPath = directorySegments.join('/');

    let plan = plans.get(rootFolderName);
    if (!plan) {
      plan = {
        directories: new Set<string>(),
        files: [],
      };
      plans.set(rootFolderName, plan);
    }

    let currentDirectoryPath = '';
    for (const segment of directorySegments) {
      currentDirectoryPath = currentDirectoryPath ? `${currentDirectoryPath}/${segment}` : segment;
      plan.directories.add(currentDirectoryPath);
    }

    plan.files.push({
      file,
      relativeDirectoryPath,
    });
  }

  return Array.from(plans.entries()).map(([rootFolderName, plan]) => ({
    rootFolderName,
    directories: Array.from(plan.directories).sort(compareRelativeDirectoryPath),
    files: plan.files,
  }));
}
