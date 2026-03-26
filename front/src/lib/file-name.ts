export function ellipsizeFileName(fileName: string, maxLength = 36) {
  if (fileName.length <= maxLength) {
    return fileName;
  }

  if (maxLength <= 3) {
    return '.'.repeat(Math.max(0, maxLength));
  }

  const extensionIndex = fileName.lastIndexOf('.');
  const hasUsableExtension = extensionIndex > 0 && extensionIndex < fileName.length - 1;

  if (hasUsableExtension) {
    const extension = fileName.slice(extensionIndex);
    const prefixLength = maxLength - extension.length - 3;

    if (prefixLength > 0) {
      return `${fileName.slice(0, prefixLength)}...${extension}`;
    }
  }

  return `${fileName.slice(0, maxLength - 3)}...`;
}
