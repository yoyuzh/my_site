package com.yoyuzh.files.upload;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpUploadSessionRuntimeStateService implements UploadSessionRuntimeStateService {

    @Override
    public Optional<UploadSessionRuntimeState> getState(String sessionId) {
        return Optional.empty();
    }

    @Override
    public void markCreated(UploadSession session) {
    }

    @Override
    public void markUploading(UploadSession session, long uploadedBytes, int uploadedPartCount, LocalDateTime updatedAt) {
    }

    @Override
    public void markCompleted(UploadSession session, LocalDateTime updatedAt) {
    }

    @Override
    public void markCancelled(UploadSession session, LocalDateTime updatedAt) {
    }

    @Override
    public void markFailed(UploadSession session, LocalDateTime updatedAt) {
    }

    @Override
    public void markExpired(UploadSession session, LocalDateTime updatedAt) {
    }
}
