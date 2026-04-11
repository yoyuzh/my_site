package com.yoyuzh.files.tasks;

import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.files.core.FileMetadataResponse;
import com.yoyuzh.files.core.FileService;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Transactional
public class ArchiveBackgroundTaskHandler implements BackgroundTaskHandler {

    private final StoredFileRepository storedFileRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final BackgroundTaskStateManager stateManager;

    public ArchiveBackgroundTaskHandler(StoredFileRepository storedFileRepository,
                                        UserRepository userRepository,
                                        FileService fileService,
                                        BackgroundTaskStateManager stateManager) {
        this.storedFileRepository = storedFileRepository;
        this.userRepository = userRepository;
        this.fileService = fileService;
        this.stateManager = stateManager;
    }

    @Override
    public boolean supports(BackgroundTaskType type) {
        return type == BackgroundTaskType.ARCHIVE;
    }

    @Override
    public BackgroundTaskHandlerResult handle(BackgroundTask task) {
        return handle(task, publicStatePatch -> {
        });
    }

    @Override
    public BackgroundTaskHandlerResult handle(BackgroundTask task, BackgroundTaskProgressReporter progressReporter) {
        Map<String, Object> state = stateManager.mergeJsonObjects(
                task.getPublicStateJson(),
                task.getPrivateStateJson(),
                "archive task state is invalid"
        );
        Long fileId = stateManager.readLong(state.get("fileId"));
        String outputPath = stateManager.readText(state.get("outputPath"));
        String outputFilename = stateManager.readText(state.get("outputFilename"));
        if (fileId == null) {
            throw new IllegalStateException("archive task missing fileId");
        }
        if (!StringUtils.hasText(outputPath) || !StringUtils.hasText(outputFilename)) {
            throw new IllegalStateException("archive task missing output target");
        }

        StoredFile source = storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(fileId, task.getUserId())
                .orElseThrow(() -> new IllegalStateException("archive task file not found"));
        User user = userRepository.findById(task.getUserId())
                .orElseThrow(() -> new IllegalStateException("archive task user not found"));

        FileService.ArchiveSourceSummary summary = fileService.summarizeArchiveSource(source);
        progressReporter.report(progressPatch(0, summary.fileCount(), 0, summary.directoryCount()));
        byte[] archiveBytes = fileService.buildArchiveBytes(source, progress ->
                progressReporter.report(progressPatch(
                        progress.processedFileCount(),
                        progress.totalFileCount(),
                        progress.processedDirectoryCount(),
                        progress.totalDirectoryCount()
                )));
        FileMetadataResponse archivedFile = fileService.importExternalFile(
                user,
                outputPath,
                outputFilename,
                "application/zip",
                archiveBytes.length,
                archiveBytes
        );

        Map<String, Object> publicStatePatch = new LinkedHashMap<>();
        publicStatePatch.put("worker", "archive");
        publicStatePatch.put("archivedFileId", archivedFile.id());
        publicStatePatch.put("archivedFilename", archivedFile.filename());
        publicStatePatch.put("archivedPath", archivedFile.path());
        publicStatePatch.put("archiveSize", archiveBytes.length);
        publicStatePatch.putAll(progressPatch(
                summary.fileCount(),
                summary.fileCount(),
                summary.directoryCount(),
                summary.directoryCount()
        ));
        return new BackgroundTaskHandlerResult(publicStatePatch);
    }

    private Map<String, Object> progressPatch(int processedFileCount,
                                              int totalFileCount,
                                              int processedDirectoryCount,
                                              int totalDirectoryCount) {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("processedFileCount", processedFileCount);
        patch.put("totalFileCount", totalFileCount);
        patch.put("processedDirectoryCount", processedDirectoryCount);
        patch.put("totalDirectoryCount", totalDirectoryCount);
        patch.put("progressPercent", calculateProgressPercent(
                processedFileCount,
                totalFileCount,
                processedDirectoryCount,
                totalDirectoryCount
        ));
        return patch;
    }

    private int calculateProgressPercent(int processedFileCount,
                                         int totalFileCount,
                                         int processedDirectoryCount,
                                         int totalDirectoryCount) {
        int total = Math.max(0, totalFileCount) + Math.max(0, totalDirectoryCount);
        int processed = Math.max(0, processedFileCount) + Math.max(0, processedDirectoryCount);
        if (total <= 0) {
            return 100;
        }
        return Math.min(100, (int) Math.floor((processed * 100.0d) / total));
    }

}
