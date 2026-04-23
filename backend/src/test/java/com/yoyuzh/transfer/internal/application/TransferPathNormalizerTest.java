package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferPathNormalizerTest {

    @Test
    void shouldNormalizePickupCodeByRemovingNonDigits() {
        assertThat(TransferPathNormalizer.normalizePickupCode("12-34 56"))
                .isEqualTo("123456");
    }

    @Test
    void shouldRejectPickupCodeWithoutSixDigits() {
        assertThatThrownBy(() -> TransferPathNormalizer.normalizePickupCode("12-34"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid pickup code");
    }

    @Test
    void shouldDefaultBlankContentTypeToOctetStream() {
        assertThat(TransferPathNormalizer.normalizeContentType("  "))
                .isEqualTo("application/octet-stream");
        assertThat(TransferPathNormalizer.normalizeContentType(null))
                .isEqualTo("application/octet-stream");
    }

    @Test
    void shouldTrimExplicitContentType() {
        assertThat(TransferPathNormalizer.normalizeContentType(" image/png "))
                .isEqualTo("image/png");
    }

    @Test
    void shouldNormalizeLeafNameByTrimmingWhitespace() {
        assertThat(TransferPathNormalizer.normalizeLeafName(" report.pdf "))
                .isEqualTo("report.pdf");
    }

    @Test
    void shouldRejectUnsafeLeafNames() {
        assertThatThrownBy(() -> TransferPathNormalizer.normalizeLeafName("../report.pdf"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid file name");
        assertThatThrownBy(() -> TransferPathNormalizer.normalizeLeafName("folder\\report.pdf"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid file name");
        assertThatThrownBy(() -> TransferPathNormalizer.normalizeLeafName("."))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid file name");
        assertThatThrownBy(() -> TransferPathNormalizer.normalizeLeafName(".."))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid file name");
    }

    @Test
    void shouldUseFallbackFilenameWhenRelativePathIsNull() {
        assertThat(TransferPathNormalizer.normalizeRelativePath(null, "report.pdf"))
                .isEqualTo("report.pdf");
    }

    @Test
    void shouldUseFallbackFilenameWhenRelativePathIsBlank() {
        assertThat(TransferPathNormalizer.normalizeRelativePath(" /// ", "report.pdf"))
                .isEqualTo("report.pdf");
    }

    @Test
    void shouldPreserveParentFoldersAndReplaceLastSegmentWithSafeFilename() {
        assertThat(TransferPathNormalizer.normalizeRelativePath(" 课程资料\\原始文件.txt ", "report.pdf"))
                .isEqualTo("课程资料/report.pdf");
    }

    @Test
    void shouldCollapseRepeatedSeparatorsInRelativePath() {
        assertThat(TransferPathNormalizer.normalizeRelativePath("a//b///old.txt", "new.txt"))
                .isEqualTo("a/b/new.txt");
    }

    @Test
    void shouldRejectTraversalSegmentsInRelativePath() {
        assertThatThrownBy(() -> TransferPathNormalizer.normalizeRelativePath("a/../old.txt", "new.txt"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid file path");
    }
}
