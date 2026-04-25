package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.workspace.api.WorkspaceTagResponse;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.domain.WorkspaceFileTag;
import com.yoyuzh.files.workspace.internal.domain.WorkspaceTag;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.files.workspace.internal.infra.WorkspaceFileTagRepository;
import com.yoyuzh.files.workspace.internal.infra.WorkspaceTagRepository;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class WorkspaceTagService {

    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9A-F]{6}$");

    private final WorkspaceTagRepository workspaceTagRepository;
    private final WorkspaceFileTagRepository workspaceFileTagRepository;
    private final StoredFileRepository storedFileRepository;

    public WorkspaceTagService(WorkspaceTagRepository workspaceTagRepository,
                               WorkspaceFileTagRepository workspaceFileTagRepository,
                               StoredFileRepository storedFileRepository) {
        this.workspaceTagRepository = workspaceTagRepository;
        this.workspaceFileTagRepository = workspaceFileTagRepository;
        this.storedFileRepository = storedFileRepository;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceTagResponse> listTags(Long userId) {
        return workspaceTagRepository.findByUserIdOrderByNameAsc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public WorkspaceTagResponse createTag(Long userId, String rawName, String rawColor) {
        String name = normalizeName(rawName);
        String color = normalizeColor(rawColor);
        if (workspaceTagRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME, "标签名称已存在");
        }

        WorkspaceTag tag = new WorkspaceTag();
        tag.setUserId(userId);
        tag.setName(name);
        tag.setColor(color);
        return toResponse(workspaceTagRepository.save(tag));
    }

    @Transactional
    public WorkspaceTagResponse updateTag(Long userId, Long tagId, String rawName, String rawColor) {
        WorkspaceTag tag = getOwnedTag(userId, tagId);
        String name = normalizeName(rawName);
        String color = normalizeColor(rawColor);
        if (workspaceTagRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, tagId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME, "标签名称已存在");
        }
        tag.setName(name);
        tag.setColor(color);
        return toResponse(workspaceTagRepository.save(tag));
    }

    @Transactional
    public List<WorkspaceTagResponse> deleteTag(Long userId, Long tagId) {
        WorkspaceTag tag = getOwnedTag(userId, tagId);
        workspaceFileTagRepository.deleteAllByTagId(tag.getId());
        workspaceTagRepository.delete(tag);
        return listTags(userId);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceTagResponse> listFileTags(Long userId, Long fileId) {
        StoredFile file = ensureOwnedActiveFile(userId, fileId);
        if (!file.isDirectory()) {
            return List.of();
        }
        return workspaceTagRepository.findAssignedTags(userId, fileId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<WorkspaceTagResponse> assignTag(Long userId, Long fileId, Long tagId) {
        ensureOwnedActiveDirectory(userId, fileId);
        WorkspaceTag tag = getOwnedTag(userId, tagId);
        if (!workspaceFileTagRepository.existsByUserIdAndFileIdAndTagId(userId, fileId, tag.getId())) {
            WorkspaceFileTag relation = new WorkspaceFileTag();
            relation.setUserId(userId);
            relation.setFileId(fileId);
            relation.setTagId(tag.getId());
            workspaceFileTagRepository.save(relation);
        }
        return listFileTags(userId, fileId);
    }

    @Transactional
    public List<WorkspaceTagResponse> removeTag(Long userId, Long fileId, Long tagId) {
        ensureOwnedActiveDirectory(userId, fileId);
        getOwnedTag(userId, tagId);
        workspaceFileTagRepository.deleteByUserIdAndFileIdAndTagId(userId, fileId, tagId);
        return listFileTags(userId, fileId);
    }

    private StoredFile ensureOwnedActiveDirectory(Long userId, Long fileId) {
        StoredFile file = ensureOwnedActiveFile(userId, fileId);
        if (!file.isDirectory()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "只有文件夹支持标签");
        }
        return file;
    }

    private StoredFile ensureOwnedActiveFile(Long userId, Long fileId) {
        return storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(fileId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"));
    }

    private WorkspaceTag getOwnedTag(Long userId, Long tagId) {
        return workspaceTagRepository.findByIdAndUserId(tagId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "标签不存在"));
    }

    private WorkspaceTagResponse toResponse(WorkspaceTag tag) {
        return new WorkspaceTagResponse(tag.getId(), tag.getName(), tag.getColor());
    }

    private String normalizeName(String rawName) {
        if (!StringUtils.hasText(rawName)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "标签名称不能为空");
        }
        String name = rawName.trim();
        if (name.length() > 32) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "标签名称不能超过32个字符");
        }
        return name;
    }

    private String normalizeColor(String rawColor) {
        if (!StringUtils.hasText(rawColor)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "标签颜色不能为空");
        }
        String color = rawColor.trim().toUpperCase(Locale.ROOT);
        if (!COLOR_PATTERN.matcher(color).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "标签颜色格式不正确");
        }
        return color;
    }
}
