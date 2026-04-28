import type { FileItem } from '../api/types';
import { downloadFileBlob, listFiles } from './files';
import { getWorkspaceItemLogicalPath } from './workspace-folder-tree';

export type FolderDownloadMode = 'server-archive' | 'browser-archive' | 'individual-files';

export type FolderDownloadFile = {
  file: FileItem;
  relativePath: string;
};

export type FolderDownloadDirectory = {
  relativePath: string;
};

export type FolderDownloadEntries = {
  directories: FolderDownloadDirectory[];
  files: FolderDownloadFile[];
};

const FOLDER_DOWNLOAD_PAGE_SIZE = 100;

function appendZipDirectorySlash(path: string) {
  return path.endsWith('/') ? path : `${path}/`;
}

function toZipRootName(folder: FileItem) {
  return folder.filename.trim() || 'folder';
}

function toRelativeArchivePath(rootPath: string, rootName: string, item: FileItem) {
  const itemPath = getWorkspaceItemLogicalPath(item);
  const relativePath = itemPath === rootPath
    ? ''
    : itemPath.slice(rootPath.length).replace(/^\/+/, '');
  return relativePath ? `${rootName}/${relativePath}` : rootName;
}

async function collectDirectoryEntries(
  path: string,
  rootPath: string,
  rootName: string,
  entries: FolderDownloadEntries,
) {
  let page = 0;
  let totalPages = 1;

  do {
    const result = await listFiles(path, page, FOLDER_DOWNLOAD_PAGE_SIZE);
    totalPages = Math.max(1, Math.ceil(result.total / Math.max(1, result.size)));

    for (const item of result.items) {
      const relativePath = toRelativeArchivePath(rootPath, rootName, item);
      if (item.directory) {
        entries.directories.push({ relativePath: appendZipDirectorySlash(relativePath) });
        await collectDirectoryEntries(getWorkspaceItemLogicalPath(item), rootPath, rootName, entries);
      } else {
        entries.files.push({ file: item, relativePath });
      }
    }

    page += 1;
  } while (page < totalPages);
}

export async function collectFolderDownloadEntries(folder: FileItem): Promise<FolderDownloadEntries> {
  const rootPath = getWorkspaceItemLogicalPath(folder);
  const rootName = toZipRootName(folder);
  const entries: FolderDownloadEntries = {
    directories: [{ relativePath: appendZipDirectorySlash(rootName) }],
    files: [],
  };

  await collectDirectoryEntries(rootPath, rootPath, rootName, entries);
  return entries;
}

export async function buildBrowserFolderArchive(folder: FileItem) {
  const [{ default: JSZip }, entries] = await Promise.all([
    import('jszip'),
    collectFolderDownloadEntries(folder),
  ]);
  const zip = new JSZip();

  for (const directory of entries.directories) {
    zip.folder(directory.relativePath);
  }

  for (const item of entries.files) {
    zip.file(item.relativePath, await downloadFileBlob(item.file.id));
  }

  return zip.generateAsync({ type: 'blob' });
}
