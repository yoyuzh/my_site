package com.yoyuzh.files;

import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.common.PageResponse;
import com.yoyuzh.config.FileStorageProperties;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.storage.PreparedUpload;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class FileService {
    private static final List<String> DEFAULT_DIRECTORIES = List.of("下载", "文档", "图片");

    private final StoredFileRepository storedFileRepository;
    private final FileContentStorage fileContentStorage;
    private final FileShareLinkRepository fileShareLinkRepository;
    private final long maxFileSize;

    public FileService(StoredFileRepository storedFileRepository,
                       FileContentStorage fileContentStorage,
                       FileShareLinkRepository fileShareLinkRepository,
                       FileStorageProperties properties) {
        this.storedFileRepository = storedFileRepository;
        this.fileContentStorage = fileContentStorage;
        this.fileShareLinkRepository = fileShareLinkRepository;
        this.maxFileSize = properties.getMaxFileSize();
    }

    @Transactional
    public FileMetadataResponse upload(User user, String path, MultipartFile multipartFile) {
        String normalizedPath = normalizeDirectoryPath(path);
        String filename = normalizeUploadFilename(multipartFile.getOriginalFilename());
        validateUpload(user.getId(), normalizedPath, filename, multipartFile.getSize());
        ensureDirectoryHierarchy(user, normalizedPath);

        fileContentStorage.upload(user.getId(), normalizedPath, filename, multipartFile);
        return saveFileMetadata(user, normalizedPath, filename, filename, multipartFile.getContentType(), multipartFile.getSize());
    }

    public InitiateUploadResponse initiateUpload(User user, InitiateUploadRequest request) {
        String normalizedPath = normalizeDirectoryPath(request.path());
        String filename = normalizeLeafName(request.filename());
        validateUpload(user.getId(), normalizedPath, filename, request.size());

        PreparedUpload preparedUpload = fileContentStorage.prepareUpload(
                user.getId(),
                normalizedPath,
                filename,
                request.contentType(),
                request.size()
        );

        return new InitiateUploadResponse(
                preparedUpload.direct(),
                preparedUpload.uploadUrl(),
                preparedUpload.method(),
                preparedUpload.headers(),
                preparedUpload.storageName()
        );
    }

    @Transactional
    public FileMetadataResponse completeUpload(User user, CompleteUploadRequest request) {
        String normalizedPath = normalizeDirectoryPath(request.path());
        String filename = normalizeLeafName(request.filename());
        String storageName = normalizeLeafName(request.storageName());
        validateUpload(user.getId(), normalizedPath, filename, request.size());
        ensureDirectoryHierarchy(user, normalizedPath);

        fileContentStorage.completeUpload(user.getId(), normalizedPath, storageName, request.contentType(), request.size());
        return saveFileMetadata(user, normalizedPath, filename, storageName, request.contentType(), request.size());
    }

    @Transactional
    public FileMetadataResponse mkdir(User user, String path) {
        String normalizedPath = normalizeDirectoryPath(path);
        if ("/".equals(normalizedPath)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "根目录无需创建");
        }
        String parentPath = extractParentPath(normalizedPath);
        String directoryName = extractLeafName(normalizedPath);
        if (storedFileRepository.existsByUserIdAndPathAndFilename(user.getId(), parentPath, directoryName)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "目录已存在");
        }

        fileContentStorage.createDirectory(user.getId(), normalizedPath);

        StoredFile storedFile = new StoredFile();
        storedFile.setUser(user);
        storedFile.setFilename(directoryName);
        storedFile.setPath(parentPath);
        storedFile.setStorageName(directoryName);
        storedFile.setContentType("directory");
        storedFile.setSize(0L);
        storedFile.setDirectory(true);
        return toResponse(storedFileRepository.save(storedFile));
    }

    public PageResponse<FileMetadataResponse> list(User user, String path, int page, int size) {
        String normalizedPath = normalizeDirectoryPath(path);
        Page<StoredFile> result = storedFileRepository.findByUserIdAndPathOrderByDirectoryDescCreatedAtDesc(
                user.getId(), normalizedPath, PageRequest.of(page, size));
        List<FileMetadataResponse> items = result.getContent().stream().map(this::toResponse).toList();
        return new PageResponse<>(items, result.getTotalElements(), page, size);
    }

    public List<FileMetadataResponse> recent(User user) {
        return storedFileRepository.findTop12ByUserIdAndDirectoryFalseOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void ensureDefaultDirectories(User user) {
        for (String directoryName : DEFAULT_DIRECTORIES) {
            if (storedFileRepository.existsByUserIdAndPathAndFilename(user.getId(), "/", directoryName)) {
                continue;
            }

            String logicalPath = "/" + directoryName;
            fileContentStorage.ensureDirectory(user.getId(), logicalPath);

            StoredFile storedFile = new StoredFile();
            storedFile.setUser(user);
            storedFile.setFilename(directoryName);
            storedFile.setPath("/");
            storedFile.setStorageName(directoryName);
            storedFile.setContentType("directory");
            storedFile.setSize(0L);
            storedFile.setDirectory(true);
            storedFileRepository.save(storedFile);
        }
    }

    @Transactional
    public void delete(User user, Long fileId) {
        StoredFile storedFile = getOwnedFile(user, fileId, "删除");
        if (storedFile.isDirectory()) {
            String logicalPath = buildLogicalPath(storedFile);
            List<StoredFile> descendants = storedFileRepository.findByUserIdAndPathEqualsOrDescendant(user.getId(), logicalPath);
            fileContentStorage.deleteDirectory(user.getId(), logicalPath, descendants);
            if (!descendants.isEmpty()) {
                storedFileRepository.deleteAll(descendants);
            }
        } else {
            fileContentStorage.deleteFile(user.getId(), storedFile.getPath(), storedFile.getStorageName());
        }
        storedFileRepository.delete(storedFile);
    }

    @Transactional
    public FileMetadataResponse rename(User user, Long fileId, String nextFilename) {
        StoredFile storedFile = getOwnedFile(user, fileId, "重命名");
        String sanitizedFilename = normalizeLeafName(nextFilename);
        if (sanitizedFilename.equals(storedFile.getFilename())) {
            return toResponse(storedFile);
        }
        if (storedFileRepository.existsByUserIdAndPathAndFilename(user.getId(), storedFile.getPath(), sanitizedFilename)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "同目录下文件已存在");
        }

        if (storedFile.isDirectory()) {
            String oldLogicalPath = buildLogicalPath(storedFile);
            String newLogicalPath = "/".equals(storedFile.getPath())
                    ? "/" + sanitizedFilename
                    : storedFile.getPath() + "/" + sanitizedFilename;

            List<StoredFile> descendants = storedFileRepository.findByUserIdAndPathEqualsOrDescendant(user.getId(), oldLogicalPath);
            fileContentStorage.renameDirectory(user.getId(), oldLogicalPath, newLogicalPath, descendants);
            for (StoredFile descendant : descendants) {
                if (descendant.getPath().equals(oldLogicalPath)) {
                    descendant.setPath(newLogicalPath);
                    continue;
                }

                descendant.setPath(newLogicalPath + descendant.getPath().substring(oldLogicalPath.length()));
            }
            if (!descendants.isEmpty()) {
                storedFileRepository.saveAll(descendants);
            }
        } else {
            fileContentStorage.renameFile(user.getId(), storedFile.getPath(), storedFile.getStorageName(), sanitizedFilename);
        }

        storedFile.setFilename(sanitizedFilename);
        storedFile.setStorageName(sanitizedFilename);
        return toResponse(storedFileRepository.save(storedFile));
    }

    @Transactional
    public FileMetadataResponse move(User user, Long fileId, String nextPath) {
        StoredFile storedFile = getOwnedFile(user, fileId, "移动");
        String normalizedTargetPath = normalizeDirectoryPath(nextPath);
        if (normalizedTargetPath.equals(storedFile.getPath())) {
            return toResponse(storedFile);
        }

        ensureExistingDirectoryPath(user.getId(), normalizedTargetPath);
        if (storedFileRepository.existsByUserIdAndPathAndFilename(user.getId(), normalizedTargetPath, storedFile.getFilename())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "目标目录已存在同名文件");
        }

        if (storedFile.isDirectory()) {
            String oldLogicalPath = buildLogicalPath(storedFile);
            String newLogicalPath = "/".equals(normalizedTargetPath)
                    ? "/" + storedFile.getFilename()
                    : normalizedTargetPath + "/" + storedFile.getFilename();
            if (newLogicalPath.equals(oldLogicalPath) || newLogicalPath.startsWith(oldLogicalPath + "/")) {
                throw new BusinessException(ErrorCode.UNKNOWN, "不能移动到当前目录或其子目录");
            }

            List<StoredFile> descendants = storedFileRepository.findByUserIdAndPathEqualsOrDescendant(user.getId(), oldLogicalPath);
            fileContentStorage.renameDirectory(user.getId(), oldLogicalPath, newLogicalPath, descendants);
            for (StoredFile descendant : descendants) {
                if (descendant.getPath().equals(oldLogicalPath)) {
                    descendant.setPath(newLogicalPath);
                    continue;
                }

                descendant.setPath(newLogicalPath + descendant.getPath().substring(oldLogicalPath.length()));
            }
            if (!descendants.isEmpty()) {
                storedFileRepository.saveAll(descendants);
            }
        } else {
            fileContentStorage.moveFile(user.getId(), storedFile.getPath(), normalizedTargetPath, storedFile.getStorageName());
        }

        storedFile.setPath(normalizedTargetPath);
        return toResponse(storedFileRepository.save(storedFile));
    }

    @Transactional
    public FileMetadataResponse copy(User user, Long fileId, String nextPath) {
        StoredFile storedFile = getOwnedFile(user, fileId, "复制");
        String normalizedTargetPath = normalizeDirectoryPath(nextPath);
        ensureExistingDirectoryPath(user.getId(), normalizedTargetPath);
        if (storedFileRepository.existsByUserIdAndPathAndFilename(user.getId(), normalizedTargetPath, storedFile.getFilename())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "目标目录已存在同名文件");
        }

        if (!storedFile.isDirectory()) {
            fileContentStorage.copyFile(user.getId(), storedFile.getPath(), normalizedTargetPath, storedFile.getStorageName());
            return toResponse(storedFileRepository.save(copyStoredFile(storedFile, normalizedTargetPath)));
        }

        String oldLogicalPath = buildLogicalPath(storedFile);
        String newLogicalPath = buildTargetLogicalPath(normalizedTargetPath, storedFile.getFilename());
        if (newLogicalPath.equals(oldLogicalPath) || newLogicalPath.startsWith(oldLogicalPath + "/")) {
            throw new BusinessException(ErrorCode.UNKNOWN, "不能复制到当前目录或其子目录");
        }

        List<StoredFile> descendants = storedFileRepository.findByUserIdAndPathEqualsOrDescendant(user.getId(), oldLogicalPath);
        List<StoredFile> copiedEntries = new ArrayList<>();

        fileContentStorage.ensureDirectory(user.getId(), newLogicalPath);
        StoredFile copiedRoot = copyStoredFile(storedFile, normalizedTargetPath);
        copiedEntries.add(copiedRoot);

        descendants.stream()
                .sorted(Comparator
                        .comparingInt((StoredFile descendant) -> descendant.getPath().length())
                        .thenComparing(descendant -> descendant.isDirectory() ? 0 : 1)
                        .thenComparing(StoredFile::getFilename))
                .forEach(descendant -> {
                    String copiedPath = remapCopiedPath(descendant.getPath(), oldLogicalPath, newLogicalPath);
                    if (storedFileRepository.existsByUserIdAndPathAndFilename(user.getId(), copiedPath, descendant.getFilename())) {
                        throw new BusinessException(ErrorCode.UNKNOWN, "目标目录已存在同名文件");
                    }

                    if (descendant.isDirectory()) {
                        fileContentStorage.ensureDirectory(user.getId(), buildTargetLogicalPath(copiedPath, descendant.getFilename()));
                    } else {
                        fileContentStorage.copyFile(user.getId(), descendant.getPath(), copiedPath, descendant.getStorageName());
                    }
                    copiedEntries.add(copyStoredFile(descendant, copiedPath));
                });

        StoredFile savedRoot = null;
        for (StoredFile copiedEntry : copiedEntries) {
            StoredFile savedEntry = storedFileRepository.save(copiedEntry);
            if (savedRoot == null) {
                savedRoot = savedEntry;
            }
        }
        return toResponse(savedRoot == null ? copiedRoot : savedRoot);
    }

    public ResponseEntity<?> download(User user, Long fileId) {
        StoredFile storedFile = getOwnedFile(user, fileId, "下载");
        if (storedFile.isDirectory()) {
            return downloadDirectory(user, storedFile);
        }

        if (fileContentStorage.supportsDirectDownload()) {
            return ResponseEntity.status(302)
                    .location(URI.create(fileContentStorage.createDownloadUrl(
                            user.getId(),
                            storedFile.getPath(),
                            storedFile.getStorageName(),
                            storedFile.getFilename())))
                    .build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(storedFile.getFilename(), StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(
                        storedFile.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : storedFile.getContentType()))
                .body(fileContentStorage.readFile(user.getId(), storedFile.getPath(), storedFile.getStorageName()));
    }

    public DownloadUrlResponse getDownloadUrl(User user, Long fileId) {
        StoredFile storedFile = getOwnedFile(user, fileId, "下载");
        if (storedFile.isDirectory()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "目录不支持下载");
        }

        if (fileContentStorage.supportsDirectDownload()) {
            return new DownloadUrlResponse(fileContentStorage.createDownloadUrl(
                    user.getId(),
                    storedFile.getPath(),
                    storedFile.getStorageName(),
                    storedFile.getFilename()
            ));
        }

        return new DownloadUrlResponse("/api/files/download/" + storedFile.getId());
    }

    @Transactional
    public CreateFileShareLinkResponse createShareLink(User user, Long fileId) {
        StoredFile storedFile = getOwnedFile(user, fileId, "分享");
        if (storedFile.isDirectory()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "目录暂不支持分享链接");
        }

        FileShareLink shareLink = new FileShareLink();
        shareLink.setOwner(user);
        shareLink.setFile(storedFile);
        shareLink.setToken(UUID.randomUUID().toString().replace("-", ""));
        FileShareLink saved = fileShareLinkRepository.save(shareLink);

        return new CreateFileShareLinkResponse(
                saved.getToken(),
                storedFile.getFilename(),
                storedFile.getSize(),
                storedFile.getContentType(),
                saved.getCreatedAt()
        );
    }

    public FileShareDetailsResponse getShareDetails(String token) {
        FileShareLink shareLink = getShareLink(token);
        StoredFile storedFile = shareLink.getFile();
        return new FileShareDetailsResponse(
                shareLink.getToken(),
                shareLink.getOwner().getUsername(),
                storedFile.getFilename(),
                storedFile.getSize(),
                storedFile.getContentType(),
                storedFile.isDirectory(),
                shareLink.getCreatedAt()
        );
    }

    @Transactional
    public FileMetadataResponse importSharedFile(User recipient, String token, String path) {
        FileShareLink shareLink = getShareLink(token);
        StoredFile sourceFile = shareLink.getFile();
        if (sourceFile.isDirectory()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "目录暂不支持导入");
        }

        byte[] content = fileContentStorage.readFile(
                sourceFile.getUser().getId(),
                sourceFile.getPath(),
                sourceFile.getStorageName()
        );
        return importExternalFile(
                recipient,
                path,
                sourceFile.getFilename(),
                sourceFile.getContentType(),
                sourceFile.getSize(),
                content
        );
    }

    @Transactional
    public FileMetadataResponse importExternalFile(User recipient,
                                                   String path,
                                                   String filename,
                                                   String contentType,
                                                   long size,
                                                   byte[] content) {
        String normalizedPath = normalizeDirectoryPath(path);
        String normalizedFilename = normalizeLeafName(filename);
        validateUpload(recipient.getId(), normalizedPath, normalizedFilename, size);
        ensureDirectoryHierarchy(recipient, normalizedPath);
        fileContentStorage.storeImportedFile(
                recipient.getId(),
                normalizedPath,
                normalizedFilename,
                contentType,
                content
        );

        return saveFileMetadata(
                recipient,
                normalizedPath,
                normalizedFilename,
                normalizedFilename,
                contentType,
                size
        );
    }

    private ResponseEntity<byte[]> downloadDirectory(User user, StoredFile directory) {
        String logicalPath = buildLogicalPath(directory);
        String archiveName = directory.getFilename() + ".zip";
        List<StoredFile> descendants = storedFileRepository.findByUserIdAndPathEqualsOrDescendant(user.getId(), logicalPath)
                .stream()
                .sorted(Comparator.comparing(StoredFile::getPath).thenComparing(StoredFile::getFilename))
                .toList();

        byte[] archiveBytes;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            Set<String> createdEntries = new LinkedHashSet<>();
            writeDirectoryEntry(zipOutputStream, createdEntries, directory.getFilename() + "/");

            for (StoredFile descendant : descendants) {
                String entryName = buildZipEntryName(directory.getFilename(), logicalPath, descendant);
                if (descendant.isDirectory()) {
                    writeDirectoryEntry(zipOutputStream, createdEntries, entryName + "/");
                    continue;
                }

                ensureParentDirectoryEntries(zipOutputStream, createdEntries, entryName);
                writeFileEntry(zipOutputStream, createdEntries, entryName,
                        fileContentStorage.readFile(user.getId(), descendant.getPath(), descendant.getStorageName()));
            }
            zipOutputStream.finish();
            archiveBytes = outputStream.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "目录压缩失败");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(archiveName, StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(archiveBytes);
    }

    private FileMetadataResponse saveFileMetadata(User user,
                                                  String normalizedPath,
                                                  String filename,
                                                  String storageName,
                                                  String contentType,
                                                  long size) {
        StoredFile storedFile = new StoredFile();
        storedFile.setUser(user);
        storedFile.setFilename(filename);
        storedFile.setPath(normalizedPath);
        storedFile.setStorageName(storageName);
        storedFile.setContentType(contentType);
        storedFile.setSize(size);
        storedFile.setDirectory(false);
        return toResponse(storedFileRepository.save(storedFile));
    }

    private FileShareLink getShareLink(String token) {
        return fileShareLinkRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "分享链接不存在"));
    }

    private StoredFile getOwnedFile(User user, Long fileId, String action) {
        StoredFile storedFile = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"));
        if (!storedFile.getUser().getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "没有权限" + action + "该文件");
        }
        return storedFile;
    }

    private void validateUpload(Long userId, String normalizedPath, String filename, long size) {
        if (size > maxFileSize) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件大小超出限制");
        }
        if (storedFileRepository.existsByUserIdAndPathAndFilename(userId, normalizedPath, filename)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "同目录下文件已存在");
        }
    }

    private void ensureDirectoryHierarchy(User user, String normalizedPath) {
        if ("/".equals(normalizedPath)) {
            return;
        }

        String[] segments = normalizedPath.substring(1).split("/");
        String currentPath = "/";

        for (String segment : segments) {
            if (storedFileRepository.existsByUserIdAndPathAndFilename(user.getId(), currentPath, segment)) {
                currentPath = "/".equals(currentPath) ? "/" + segment : currentPath + "/" + segment;
                continue;
            }

            String logicalPath = "/".equals(currentPath) ? "/" + segment : currentPath + "/" + segment;
            fileContentStorage.ensureDirectory(user.getId(), logicalPath);

            StoredFile storedFile = new StoredFile();
            storedFile.setUser(user);
            storedFile.setFilename(segment);
            storedFile.setPath(currentPath);
            storedFile.setStorageName(segment);
            storedFile.setContentType("directory");
            storedFile.setSize(0L);
            storedFile.setDirectory(true);
            storedFileRepository.save(storedFile);

            currentPath = logicalPath;
        }
    }

    private void ensureExistingDirectoryPath(Long userId, String normalizedPath) {
        if ("/".equals(normalizedPath)) {
            return;
        }

        String[] segments = normalizedPath.substring(1).split("/");
        String currentPath = "/";
        for (String segment : segments) {
            StoredFile directory = storedFileRepository.findByUserIdAndPathAndFilename(userId, currentPath, segment)
                    .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "目标目录不存在"));
            if (!directory.isDirectory()) {
                throw new BusinessException(ErrorCode.UNKNOWN, "目标路径不是目录");
            }
            currentPath = "/".equals(currentPath) ? "/" + segment : currentPath + "/" + segment;
        }
    }

    private String normalizeUploadFilename(String originalFilename) {
        String filename = StringUtils.cleanPath(originalFilename);
        if (!StringUtils.hasText(filename)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件名不能为空");
        }
        return normalizeLeafName(filename);
    }

    private FileMetadataResponse toResponse(StoredFile storedFile) {
        String logicalPath = storedFile.isDirectory() ? buildLogicalPath(storedFile) : storedFile.getPath();
        return new FileMetadataResponse(
                storedFile.getId(),
                storedFile.getFilename(),
                logicalPath,
                storedFile.getSize(),
                storedFile.getContentType(),
                storedFile.isDirectory(),
                storedFile.getCreatedAt());
    }

    private String normalizeDirectoryPath(String path) {
        if (!StringUtils.hasText(path) || "/".equals(path.trim())) {
            return "/";
        }
        String normalized = path.replace("\\", "/").trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        normalized = normalized.replaceAll("/{2,}", "/");
        if (normalized.contains("..")) {
            throw new BusinessException(ErrorCode.UNKNOWN, "路径不合法");
        }
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String extractParentPath(String normalizedPath) {
        int lastSlash = normalizedPath.lastIndexOf('/');
        return lastSlash <= 0 ? "/" : normalizedPath.substring(0, lastSlash);
    }

    private String extractLeafName(String normalizedPath) {
        return normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
    }

    private String buildLogicalPath(StoredFile storedFile) {
        return "/".equals(storedFile.getPath())
                ? "/" + storedFile.getFilename()
                : storedFile.getPath() + "/" + storedFile.getFilename();
    }

    private String buildTargetLogicalPath(String normalizedTargetPath, String filename) {
        return "/".equals(normalizedTargetPath)
                ? "/" + filename
                : normalizedTargetPath + "/" + filename;
    }

    private String remapCopiedPath(String currentPath, String oldLogicalPath, String newLogicalPath) {
        if (currentPath.equals(oldLogicalPath)) {
            return newLogicalPath;
        }
        return newLogicalPath + currentPath.substring(oldLogicalPath.length());
    }

    private StoredFile copyStoredFile(StoredFile source, String nextPath) {
        StoredFile copiedFile = new StoredFile();
        copiedFile.setUser(source.getUser());
        copiedFile.setFilename(source.getFilename());
        copiedFile.setPath(nextPath);
        copiedFile.setStorageName(source.getStorageName());
        copiedFile.setContentType(source.getContentType());
        copiedFile.setSize(source.getSize());
        copiedFile.setDirectory(source.isDirectory());
        return copiedFile;
    }

    private String buildZipEntryName(String rootDirectoryName, String rootLogicalPath, StoredFile storedFile) {
        StringBuilder entryName = new StringBuilder(rootDirectoryName).append('/');
        if (!storedFile.getPath().equals(rootLogicalPath)) {
            entryName.append(storedFile.getPath().substring(rootLogicalPath.length() + 1)).append('/');
        }
        entryName.append(storedFile.getFilename());
        return entryName.toString();
    }

    private void ensureParentDirectoryEntries(ZipOutputStream zipOutputStream, Set<String> createdEntries, String entryName) throws IOException {
        int slashIndex = entryName.indexOf('/');
        while (slashIndex >= 0) {
            writeDirectoryEntry(zipOutputStream, createdEntries, entryName.substring(0, slashIndex + 1));
            slashIndex = entryName.indexOf('/', slashIndex + 1);
        }
    }

    private void writeDirectoryEntry(ZipOutputStream zipOutputStream, Set<String> createdEntries, String entryName) throws IOException {
        if (!createdEntries.add(entryName)) {
            return;
        }

        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        zipOutputStream.closeEntry();
    }

    private void writeFileEntry(ZipOutputStream zipOutputStream, Set<String> createdEntries, String entryName, byte[] content)
            throws IOException {
        if (!createdEntries.add(entryName)) {
            return;
        }

        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        zipOutputStream.write(content);
        zipOutputStream.closeEntry();
    }

    private String normalizeLeafName(String filename) {
        String cleaned = StringUtils.cleanPath(filename == null ? "" : filename).trim();
        if (!StringUtils.hasText(cleaned)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件名不能为空");
        }
        if (cleaned.contains("/") || cleaned.contains("\\") || cleaned.contains("..")) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件名不合法");
        }
        return cleaned;
    }
}
