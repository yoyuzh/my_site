export interface TransferArchiveEntry {
  name: string;
  relativePath?: string;
  data: Uint8Array | ArrayBuffer | Blob;
  lastModified?: number;
}

const ZIP_UTF8_FLAG = 0x0800;
const CRC32_TABLE = createCrc32Table();

function createCrc32Table() {
  const table = new Uint32Array(256);

  for (let index = 0; index < 256; index += 1) {
    let value = index;
    for (let bit = 0; bit < 8; bit += 1) {
      value = (value & 1) === 1 ? (0xEDB88320 ^ (value >>> 1)) : (value >>> 1);
    }
    table[index] = value >>> 0;
  }

  return table;
}

function sanitizeArchivePath(entry: TransferArchiveEntry) {
  const rawPath = entry.relativePath?.trim() || entry.name;
  const normalizedPath = rawPath
    .replaceAll('\\', '/')
    .split('/')
    .map((segment) => segment.trim())
    .filter(Boolean)
    .join('/');

  return normalizedPath || entry.name;
}

function crc32(bytes: Uint8Array) {
  let value = 0xFFFFFFFF;

  for (const byte of bytes) {
    value = CRC32_TABLE[(value ^ byte) & 0xFF] ^ (value >>> 8);
  }

  return (value ^ 0xFFFFFFFF) >>> 0;
}

function toDosDateTime(timestamp: number) {
  const date = new Date(timestamp);
  const year = Math.max(1980, date.getFullYear());
  const month = date.getMonth() + 1;
  const day = date.getDate();
  const hours = date.getHours();
  const minutes = date.getMinutes();
  const seconds = Math.floor(date.getSeconds() / 2);

  return {
    time: (hours << 11) | (minutes << 5) | seconds,
    date: ((year - 1980) << 9) | (month << 5) | day,
  };
}

function writeUint16(view: DataView, offset: number, value: number) {
  view.setUint16(offset, value, true);
}

function writeUint32(view: DataView, offset: number, value: number) {
  view.setUint32(offset, value >>> 0, true);
}

function concatUint8Arrays(chunks: Uint8Array[]) {
  const totalLength = chunks.reduce((sum, chunk) => sum + chunk.byteLength, 0);
  const output = new Uint8Array(totalLength);
  let offset = 0;

  for (const chunk of chunks) {
    output.set(chunk, offset);
    offset += chunk.byteLength;
  }

  return output;
}

async function normalizeArchiveData(data: TransferArchiveEntry['data']) {
  if (data instanceof Uint8Array) {
    return data;
  }

  if (data instanceof Blob) {
    return new Uint8Array(await data.arrayBuffer());
  }

  return new Uint8Array(data);
}

export function buildTransferArchiveFileName(baseName: string) {
  return baseName.toLowerCase().endsWith('.zip') ? baseName : `${baseName}.zip`;
}

export async function createTransferZipArchive(entries: TransferArchiveEntry[]) {
  const encoder = new TextEncoder();
  const fileSections: Uint8Array[] = [];
  const centralDirectorySections: Uint8Array[] = [];
  let offset = 0;

  for (const entry of entries) {
    const fileName = sanitizeArchivePath(entry);
    const fileNameBytes = encoder.encode(fileName);
    const fileData = await normalizeArchiveData(entry.data);
    const checksum = crc32(fileData);
    const {time, date} = toDosDateTime(entry.lastModified ?? Date.now());

    const localHeader = new Uint8Array(30);
    const localHeaderView = new DataView(localHeader.buffer);
    writeUint32(localHeaderView, 0, 0x04034B50);
    writeUint16(localHeaderView, 4, 20);
    writeUint16(localHeaderView, 6, ZIP_UTF8_FLAG);
    writeUint16(localHeaderView, 8, 0);
    writeUint16(localHeaderView, 10, time);
    writeUint16(localHeaderView, 12, date);
    writeUint32(localHeaderView, 14, checksum);
    writeUint32(localHeaderView, 18, fileData.byteLength);
    writeUint32(localHeaderView, 22, fileData.byteLength);
    writeUint16(localHeaderView, 26, fileNameBytes.byteLength);
    writeUint16(localHeaderView, 28, 0);

    fileSections.push(localHeader, fileNameBytes, fileData);

    const centralHeader = new Uint8Array(46);
    const centralHeaderView = new DataView(centralHeader.buffer);
    writeUint32(centralHeaderView, 0, 0x02014B50);
    writeUint16(centralHeaderView, 4, 20);
    writeUint16(centralHeaderView, 6, 20);
    writeUint16(centralHeaderView, 8, ZIP_UTF8_FLAG);
    writeUint16(centralHeaderView, 10, 0);
    writeUint16(centralHeaderView, 12, time);
    writeUint16(centralHeaderView, 14, date);
    writeUint32(centralHeaderView, 16, checksum);
    writeUint32(centralHeaderView, 20, fileData.byteLength);
    writeUint32(centralHeaderView, 24, fileData.byteLength);
    writeUint16(centralHeaderView, 28, fileNameBytes.byteLength);
    writeUint16(centralHeaderView, 30, 0);
    writeUint16(centralHeaderView, 32, 0);
    writeUint16(centralHeaderView, 34, 0);
    writeUint16(centralHeaderView, 36, 0);
    writeUint32(centralHeaderView, 38, 0);
    writeUint32(centralHeaderView, 42, offset);

    centralDirectorySections.push(centralHeader, fileNameBytes);
    offset += localHeader.byteLength + fileNameBytes.byteLength + fileData.byteLength;
  }

  const centralDirectory = concatUint8Arrays(centralDirectorySections);
  const endRecord = new Uint8Array(22);
  const endRecordView = new DataView(endRecord.buffer);
  writeUint32(endRecordView, 0, 0x06054B50);
  writeUint16(endRecordView, 4, 0);
  writeUint16(endRecordView, 6, 0);
  writeUint16(endRecordView, 8, entries.length);
  writeUint16(endRecordView, 10, entries.length);
  writeUint32(endRecordView, 12, centralDirectory.byteLength);
  writeUint32(endRecordView, 16, offset);
  writeUint16(endRecordView, 20, 0);

  return new Blob([
    concatUint8Arrays(fileSections),
    centralDirectory,
    endRecord,
  ], {
    type: 'application/zip',
  });
}
