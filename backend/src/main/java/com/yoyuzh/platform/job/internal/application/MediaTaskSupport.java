package com.yoyuzh.platform.job.internal.application;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

final class MediaTaskSupport {

    private static final List<String> MEDIA_EXTENSIONS = List.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg",
            ".mp4", ".mov", ".mkv", ".webm", ".avi",
            ".mp3", ".wav", ".flac", ".aac", ".ogg", ".m4a"
    );

    private MediaTaskSupport() {
    }

    static boolean isMediaLike(String filename, String contentType) {
        String normalizedContentType = normalizeContentType(contentType);
        if (normalizedContentType.startsWith("image/")
                || normalizedContentType.startsWith("video/")
                || normalizedContentType.startsWith("audio/")) {
            return true;
        }
        return hasExtension(filename);
    }

    private static boolean hasExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return false;
        }
        String normalized = filename.toLowerCase(Locale.ROOT);
        return MEDIA_EXTENSIONS.stream().anyMatch(normalized::endsWith);
    }

    private static String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        return contentType.trim().toLowerCase(Locale.ROOT);
    }
}
