package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.internal.infra.FileBlobRepository;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.workspace.api.WorkspaceDownloadOptions;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.shared.kernel.BusinessException;
import org.springframework.test.util.ReflectionTestUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 白盒测试：验证 WorkspaceFileIngressService.normalizeBlobObjectKey 的安全边界。
 */
@ExtendWith(MockitoExtension.class)
class FileServiceBlobKeySecurityTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileBlobRepository fileBlobRepository;

    @Mock
    private FileContentStorage fileContentStorage;

    private WorkspaceFileIngressService workspaceFileIngressService;

    @BeforeEach
    void setUp() {
        FileService fileService = FileServiceTestSupport.create(
                storedFileRepository,
                fileBlobRepository,
                fileContentStorage,
                com.yoyuzh.files.workspace.api.WorkspaceDownloadMetricsPort.noOp(),
                new WorkspaceDownloadOptions(null, null, 300L),
                500L * 1024 * 1024L
        );
        workspaceFileIngressService = (WorkspaceFileIngressService) ReflectionTestUtils.getField(
                fileService,
                "workspaceFileIngressService"
        );
    }

    private String normalize(String objectKey) {
        try {
            Method method = WorkspaceFileIngressService.class.getDeclaredMethod("normalizeBlobObjectKey", String.class);
            method.setAccessible(true);
            return (String) method.invoke(workspaceFileIngressService, objectKey);
        } catch (InvocationTargetException exception) {
            Throwable target = exception.getTargetException();
            if (target instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (target instanceof Error error) {
                throw error;
            }
            throw new AssertionError("Unexpected checked exception from normalizeBlobObjectKey", target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to invoke normalizeBlobObjectKey", exception);
        }
    }

    @Test
    void shouldAcceptValidBlobKey() {
        String key = "blobs/123e4567-e89b-12d3-a456-426614174000";
        assertThat(normalize(key)).isEqualTo(key);
    }

    @Test
    void shouldNormalizeBackslashesToForwardSlashes() {
        assertThat(normalize("blobs\\uuid-abc")).isEqualTo("blobs/uuid-abc");
    }

    @Test
    void shouldRejectKeyNotStartingWithBlobsPrefix() {
        assertThatThrownBy(() -> normalize("uploads/something.txt"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不合法");
    }

    @Test
    void shouldRejectKeyWithDotDotTraversal() {
        assertThatThrownBy(() -> normalize("blobs/../etc/passwd"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不合法");
    }

    @Test
    void shouldRejectKeyWithLeadingSlash() {
        assertThatThrownBy(() -> normalize("/blobs/uuid"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不合法");
    }

    @Test
    void shouldRejectNullKey() {
        assertThatThrownBy(() -> normalize(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不合法");
    }

    @Test
    void shouldRejectEmptyKey() {
        assertThatThrownBy(() -> normalize(""))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不合法");
    }

    @Test
    void shouldRejectBackslashTraversalAfterNormalization() {
        assertThatThrownBy(() -> normalize("blobs\\..\\..\\etc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不合法");
    }
}
