export interface FilesUiItem {
  id: number;
  name: string;
  type: string;
  size: string;
  modified: string;
}

export function getNextAvailableName(name: string, existingNames: Set<string>) {
  if (!existingNames.has(name)) {
    return name;
  }

  let index = 1;
  let nextName = `${name} (${index})`;

  while (existingNames.has(nextName)) {
    index += 1;
    nextName = `${name} (${index})`;
  }

  return nextName;
}

export function replaceUiFile<T extends FilesUiItem>(files: T[], nextFile: T) {
  return files.map((file) => (file.id === nextFile.id ? nextFile : file));
}

export function removeUiFile<T extends FilesUiItem>(files: T[], fileId: number) {
  return files.filter((file) => file.id !== fileId);
}

export function syncSelectedFile<T extends FilesUiItem>(selectedFile: T | null, nextFile: T) {
  if (!selectedFile || selectedFile.id !== nextFile.id) {
    return selectedFile;
  }

  return nextFile;
}

export function clearSelectionIfDeleted<T extends FilesUiItem>(selectedFile: T | null, fileId: number) {
  if (!selectedFile || selectedFile.id !== fileId) {
    return selectedFile;
  }

  return null;
}

export function getActionErrorMessage(error: unknown, fallbackMessage: string) {
  if (error instanceof Error && error.message) {
    return error.message;
  }

  return fallbackMessage;
}
