package com.yoyuzh.files.upload.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class UploadSessionTusIngressService {

    private final UploadSessionService uploadSessionService;

    public UploadSessionTusIngressService(UploadSessionService uploadSessionService) {
        this.uploadSessionService = uploadSessionService;
    }

    public UploadSessionTusState appendSession(Long userId,
                                               String sessionId,
                                               long uploadOffset,
                                               InputStream content,
                                               long contentLength) {
        Path stagedContent = stageContent(content);
        try {
            return uploadSessionService.appendTusSession(
                    userId,
                    sessionId,
                    uploadOffset,
                    stagedContent,
                    contentLength
            );
        } finally {
            deleteStagedContent(stagedContent);
        }
    }

    private Path stageContent(InputStream content) {
        try {
            Path stagedContent = Files.createTempFile("upload-session-tus-", ".patch");
            try (InputStream requestStream = content;
                 var stagedOutput = Files.newOutputStream(stagedContent)) {
                requestStream.transferTo(stagedOutput);
            }
            return stagedContent;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "failed to stage tus patch content");
        }
    }

    private void deleteStagedContent(Path stagedContent) {
        try {
            Files.deleteIfExists(stagedContent);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "failed to delete staged tus patch content");
        }
    }
}
