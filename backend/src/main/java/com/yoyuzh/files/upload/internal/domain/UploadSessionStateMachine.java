package com.yoyuzh.files.upload.internal.domain;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class UploadSessionStateMachine {

    public void ensureCanReceivePart(UploadSession session, LocalDateTime now) {
        if (session.getStatus() == UploadSessionStatus.CANCELLED
                || session.getStatus() == UploadSessionStatus.FAILED
                || session.getStatus() == UploadSessionStatus.COMPLETING
                || session.getStatus() == UploadSessionStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "upload session cannot continue receiving content");
        }
        if (session.getExpiresAt().isBefore(now)) {
            markExpired(session, now);
            throw new BusinessException(ErrorCode.SESSION_EXPIRED, "upload session has expired");
        }
    }

    public void ensureCanReceiveContent(UploadSession session, LocalDateTime now, boolean multipartUpload) {
        ensureCanReceivePart(session, now);
        if (session.getStatus() == UploadSessionStatus.UPLOADING && multipartUpload) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "multipart upload session does not accept whole-file content");
        }
    }

    public void ensureCompletable(UploadSession session, LocalDateTime now) {
        if (session.getStatus() == UploadSessionStatus.COMPLETED) {
            return;
        }
        if (session.getStatus() == UploadSessionStatus.CANCELLED || session.getStatus() == UploadSessionStatus.FAILED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "upload session cannot be completed");
        }
        if (session.getExpiresAt().isBefore(now)) {
            markExpired(session, now);
            throw new BusinessException(ErrorCode.SESSION_EXPIRED, "upload session has expired");
        }
    }

    public void ensureCancellable(UploadSession session) {
        if (session.getStatus() == UploadSessionStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "completed upload session cannot be cancelled");
        }
    }

    public void markUploading(UploadSession session, LocalDateTime now) {
        if (session.getStatus() == UploadSessionStatus.CREATED) {
            session.setStatus(UploadSessionStatus.UPLOADING);
        }
        session.setUpdatedAt(now);
    }

    public void markCompleting(UploadSession session, LocalDateTime now) {
        session.setStatus(UploadSessionStatus.COMPLETING);
        session.setUpdatedAt(now);
    }

    public void markCompleted(UploadSession session, LocalDateTime now) {
        session.setStatus(UploadSessionStatus.COMPLETED);
        session.setUpdatedAt(now);
    }

    public void markFailed(UploadSession session, LocalDateTime now) {
        session.setStatus(UploadSessionStatus.FAILED);
        session.setUpdatedAt(now);
    }

    public void markCancelled(UploadSession session, LocalDateTime now) {
        session.setStatus(UploadSessionStatus.CANCELLED);
        session.setUpdatedAt(now);
    }

    public void markExpired(UploadSession session, LocalDateTime now) {
        session.setStatus(UploadSessionStatus.EXPIRED);
        session.setUpdatedAt(now);
    }
}
