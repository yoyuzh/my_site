package com.yoyuzh.files.upload.internal.application;

import com.yoyuzh.files.upload.internal.domain.UploadSession;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UploadSessionRuntimeStateService {

    Optional<UploadSessionRuntimeState> getState(String sessionId);

    void markCreated(UploadSession session);

    void markUploading(UploadSession session, long uploadedBytes, int uploadedPartCount, LocalDateTime updatedAt);

    void markCompleted(UploadSession session, LocalDateTime updatedAt);

    void markCancelled(UploadSession session, LocalDateTime updatedAt);

    void markFailed(UploadSession session, LocalDateTime updatedAt);

    void markExpired(UploadSession session, LocalDateTime updatedAt);

    static UploadSessionRuntimeStateService noOp() {
        return NoOpHolder.INSTANCE;
    }

    final class NoOpHolder {
        private static final UploadSessionRuntimeStateService INSTANCE = new UploadSessionRuntimeStateService() {
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
        };

        private NoOpHolder() {
        }
    }
}
