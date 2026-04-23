package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.files.storage.FileContentStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 补充 RuntimeWorkspacePathPolicyTest 中未覆盖的边界场景。
 */
@ExtendWith(MockitoExtension.class)
class RuntimeWorkspacePathPolicyEdgeCaseTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileContentStorage fileContentStorage;

    private RuntimeWorkspacePathPolicy policy() {
        return new RuntimeWorkspacePathPolicy(storedFileRepository, fileContentStorage);
    }

    // ── normalizeDirectoryPath ─────────────────────────────────────────────

    @Test
    void shouldReturnRootForNullPath() {
        assertThat(policy().normalizeDirectoryPath(null)).isEqualTo("/");
    }

    @Test
    void shouldReturnRootForBlankPath() {
        assertThat(policy().normalizeDirectoryPath("   ")).isEqualTo("/");
    }

    @Test
    void shouldReturnRootForSlashOnlyPath() {
        assertThat(policy().normalizeDirectoryPath("/")).isEqualTo("/");
    }

    @Test
    void shouldCollapseMultipleConsecutiveSlashes() {
        assertThat(policy().normalizeDirectoryPath("docs//images///sub")).isEqualTo("/docs/images/sub");
    }

    @Test
    void shouldStripTrailingSlash() {
        assertThat(policy().normalizeDirectoryPath("/docs/images/")).isEqualTo("/docs/images");
    }

    @Test
    void shouldRejectPathContainingDotDot() {
        assertThatThrownBy(() -> policy().normalizeDirectoryPath("/docs/../etc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("路径不合法");
    }

    @Test
    void shouldNormalizeBackslashesToForwardSlashes() {
        assertThat(policy().normalizeDirectoryPath("docs\\images")).isEqualTo("/docs/images");
    }

    @Test
    void shouldPrependSlashIfMissing() {
        assertThat(policy().normalizeDirectoryPath("docs/images")).isEqualTo("/docs/images");
    }

    // ── normalizeLeafName ─────────────────────────────────────────────────

    @Test
    void shouldRejectEmptyFilename() {
        assertThatThrownBy(() -> policy().normalizeLeafName(""))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件名不能为空");
    }

    @Test
    void shouldRejectNullFilename() {
        assertThatThrownBy(() -> policy().normalizeLeafName(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件名不能为空");
    }

    @Test
    void shouldRejectFilenameContainingForwardSlash() {
        assertThatThrownBy(() -> policy().normalizeLeafName("dir/file.txt"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件名不合法");
    }

    @Test
    void shouldRejectFilenameContainingBackslash() {
        assertThatThrownBy(() -> policy().normalizeLeafName("dir\\file.txt"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件名不合法");
    }

    @Test
    void shouldRejectFilenameContainingDotDot() {
        assertThatThrownBy(() -> policy().normalizeLeafName(".."))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件名不合法");
    }

    @Test
    void shouldAcceptNormalFilename() {
        assertThat(policy().normalizeLeafName("report_2024.pdf")).isEqualTo("report_2024.pdf");
    }

}
