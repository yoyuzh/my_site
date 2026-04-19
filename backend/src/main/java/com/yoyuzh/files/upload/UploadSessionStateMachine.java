package com.yoyuzh.files.upload;

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
            throw new BusinessException(ErrorCode.UNKNOWN, "涓婁紶浼氳瘽涓嶈兘缁х画涓婁紶鍒嗙墖");
        }
        if (session.getExpiresAt().isBefore(now)) {
            markExpired(session, now);
            throw new BusinessException(ErrorCode.UNKNOWN, "涓婁紶浼氳瘽宸茶繃鏈?");
        }
    }

    public void ensureCanReceiveContent(UploadSession session, LocalDateTime now, boolean multipartUpload) {
        ensureCanReceivePart(session, now);
        if (session.getStatus() == UploadSessionStatus.UPLOADING && multipartUpload) {
            throw new BusinessException(ErrorCode.UNKNOWN, "multipart 涓婁紶浼氳瘽涓嶈兘璧版暣浣撳唴瀹逛笂浼?");
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
