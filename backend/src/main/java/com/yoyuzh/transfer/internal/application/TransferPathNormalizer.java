package com.yoyuzh.transfer.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class TransferPathNormalizer {

    private TransferPathNormalizer() {
    }

    static String normalizePickupCode(String pickupCode) {
        String normalized = Objects.requireNonNullElse(pickupCode, "").replaceAll("\\D", "");
        if (normalized.length() != 6) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "invalid pickup code");
        }
        return normalized;
    }

    static String normalizeContentType(String contentType) {
        String normalized = Objects.requireNonNullElse(contentType, "").trim();
        return normalized.isEmpty() ? "application/octet-stream" : normalized;
    }

    static String normalizeLeafName(String value) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "file name cannot be empty");
        }
        if (normalized.contains("/") || normalized.contains("\\") || ".".equals(normalized) || "..".equals(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "invalid file name");
        }
        return normalized;
    }

    static String normalizeRelativePath(String relativePath, String fallbackFilename) {
        String rawPath = Objects.requireNonNullElse(relativePath, fallbackFilename).replace('\\', '/');
        List<String> segments = new ArrayList<>();
        for (String segment : rawPath.split("/")) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (".".equals(trimmed) || "..".equals(trimmed)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "invalid file path");
            }
            segments.add(trimmed);
        }

        String normalizedFilename = normalizeLeafName(fallbackFilename);
        if (segments.isEmpty()) {
            return normalizedFilename;
        }

        List<String> normalizedSegments = new ArrayList<>(segments.subList(0, Math.max(0, segments.size() - 1)));
        normalizedSegments.add(normalizedFilename);
        return String.join("/", normalizedSegments);
    }
}
