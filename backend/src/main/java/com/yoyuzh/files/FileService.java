package com.yoyuzh.files;

import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.common.PageResponse;
import com.yoyuzh.config.FileStorageProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
public class FileService {
    private static final List<String> DEFAULT_DIRECTORIES = List.of("下载", "文档", "图片");

    private final StoredFileRepository storedFileRepository;
    private final Path rootPath;
    private final long maxFileSize;

    public FileService(StoredFileRepository storedFileRepository, FileStorageProperties properties) {
        this.storedFileRepository = storedFileRepository;
        this.rootPath = Path.of(properties.getRootDir()).toAbsolutePath().normalize();
        this.maxFileSize = properties.getMaxFileSize();
        try {
            Files.createDirectories(rootPath);
        } catch (IOException ex) {
            throw new IllegalStateException("无法初始化存储目录", ex);
        }
    }

    @Transactional
    public FileMetadataResponse upload(User user, String path, MultipartFile multipartFile) {
        String normalizedPath = normalizeDirectoryPath(path);
        String filename = StringUtils.cleanPath(multipartFile.getOriginalFilename());
        if (!StringUtils.hasText(filename)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件名不能为空");
        }
        if (multipartFile.getSize() > maxFileSize) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件大小超出限制");
        }
        if (storedFileRepository.existsByUserIdAndPathAndFilename(user.getId(), normalizedPath, filename)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "同目录下文件已存在");
        }

        Path targetDir = resolveUserPath(user.getId(), normalizedPath);
        Path targetFile = targetDir.resolve(filename).normalize();
        try {
            Files.createDirectories(targetDir);
            Files.copy(multipartFile.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "文件上传失败");
        }

        StoredFile storedFile = new StoredFile();
        storedFile.setUser(user);
        storedFile.setFilename(filename);
        storedFile.setPath(normalizedPath);
        storedFile.setStorageName(filename);
        storedFile.setContentType(multipartFile.getContentType());
        storedFile.setSize(multipartFile.getSize());
        storedFile.setDirectory(false);
        return toResponse(storedFileRepository.save(storedFile));
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
        try {
            Files.createDirectories(resolveUserPath(user.getId(), normalizedPath));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "目录创建失败");
        }

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

            try {
                Files.createDirectories(resolveUserPath(user.getId(), "/").resolve(directoryName));
            } catch (IOException ex) {
                throw new BusinessException(ErrorCode.UNKNOWN, "默认目录初始化失败");
            }

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
        StoredFile storedFile = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"));
        if (!storedFile.getUser().getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "没有权限删除该文件");
        }
        try {
            Path basePath = resolveUserPath(user.getId(), storedFile.getPath());
            Path target = storedFile.isDirectory()
                    ? basePath.resolve(storedFile.getFilename()).normalize()
                    : basePath.resolve(storedFile.getStorageName()).normalize();
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "删除文件失败");
        }
        storedFileRepository.delete(storedFile);
    }

    public ResponseEntity<byte[]> download(User user, Long fileId) {
        StoredFile storedFile = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"));
        if (!storedFile.getUser().getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "没有权限下载该文件");
        }
        if (storedFile.isDirectory()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "目录不支持下载");
        }
        try {
            Path filePath = resolveUserPath(user.getId(), storedFile.getPath()).resolve(storedFile.getStorageName()).normalize();
            byte[] body = Files.readAllBytes(filePath);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + URLEncoder.encode(storedFile.getFilename(), StandardCharsets.UTF_8))
                    .contentType(MediaType.parseMediaType(
                            storedFile.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : storedFile.getContentType()))
                    .body(body);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在");
        }
    }

    private FileMetadataResponse toResponse(StoredFile storedFile) {
        String logicalPath = storedFile.getPath();
        if (storedFile.isDirectory()) {
            logicalPath = "/".equals(storedFile.getPath())
                    ? "/" + storedFile.getFilename()
                    : storedFile.getPath() + "/" + storedFile.getFilename();
        }
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

    private Path resolveUserPath(Long userId, String normalizedPath) {
        Path userRoot = rootPath.resolve(userId.toString()).normalize();
        Path relative = "/".equals(normalizedPath) ? Path.of("") : Path.of(normalizedPath.substring(1));
        Path resolved = userRoot.resolve(relative).normalize();
        if (!resolved.startsWith(userRoot)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "路径不合法");
        }
        return resolved;
    }

    private String extractParentPath(String normalizedPath) {
        int lastSlash = normalizedPath.lastIndexOf('/');
        return lastSlash <= 0 ? "/" : normalizedPath.substring(0, lastSlash);
    }

    private String extractLeafName(String normalizedPath) {
        return normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
    }
}
