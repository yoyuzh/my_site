package com.yoyuzh.files.upload.internal.application;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class UploadSessionTusIngressServiceTest {

    @Test
    void shouldDelegateTusPatchStreamDirectlyToSessionService() {
        UploadSessionService uploadSessionService = mock(UploadSessionService.class);
        UploadSessionTusIngressService ingressService = new UploadSessionTusIngressService(uploadSessionService);
        ByteArrayInputStream content = new ByteArrayInputStream("payload".getBytes());
        when(uploadSessionService.appendTusSession(eq(7L), eq("session-1"), eq(0L), same(content), eq(7L)))
                .thenReturn(new UploadSessionTusState(7L, 20L));

        UploadSessionTusState result = ingressService.appendSession(
                7L,
                "session-1",
                0L,
                content,
                7L
        );

        assertThat(result.uploadOffset()).isEqualTo(7L);
        verify(uploadSessionService).appendTusSession(7L, "session-1", 0L, content, 7L);
    }
}
