package com.yoyuzh.files.upload.internal.application;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UploadSessionTusIngressServiceTest {

    @Test
    void shouldDeleteStagedContentAfterAppendCompletes() throws Exception {
        UploadSessionService uploadSessionService = mock(UploadSessionService.class);
        UploadSessionTusIngressService ingressService = new UploadSessionTusIngressService(uploadSessionService);
        AtomicReference<Path> stagedContentRef = new AtomicReference<>();
        when(uploadSessionService.appendTusSession(eq(7L), eq("session-1"), eq(0L), org.mockito.ArgumentMatchers.any(Path.class), eq(7L)))
                .thenAnswer(invocation -> {
                    Path stagedContent = invocation.getArgument(3);
                    stagedContentRef.set(stagedContent);
                    assertThat(Files.exists(stagedContent)).isTrue();
                    return new UploadSessionTusState(7L, 20L);
                });

        UploadSessionTusState result = ingressService.appendSession(
                7L,
                "session-1",
                0L,
                new ByteArrayInputStream("payload".getBytes()),
                7L
        );

        assertThat(result.uploadOffset()).isEqualTo(7L);
        assertThat(stagedContentRef.get()).isNotNull();
        assertThat(Files.exists(stagedContentRef.get())).isFalse();
    }
}
