package com.yoyuzh.files.upload.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

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
        try {
            return uploadSessionService.appendTusSession(
                    userId,
                    sessionId,
                    uploadOffset,
                    content,
                    contentLength
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "failed to process tus patch content");
        }
    }
}
