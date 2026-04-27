package com.yoyuzh.files.workspace.internal.application;

import java.net.URLConnection;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

final class WorkspaceContentTypeResolver {

    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.ofEntries(
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("webp", "image/webp"),
            Map.entry("gif", "image/gif"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("heic", "image/heic"),
            Map.entry("heif", "image/heif"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("zip", "application/zip"),
            Map.entry("7z", "application/x-7z-compressed"),
            Map.entry("rar", "application/vnd.rar"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation")
    );

    private WorkspaceContentTypeResolver() {
    }

    static String inferContentTypeFromFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return null;
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return null;
        }
        String extension = filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return CONTENT_TYPE_BY_EXTENSION.get(extension);
    }

    static String guessContentType(String filename) {
        String inferred = inferContentTypeFromFilename(filename);
        if (StringUtils.hasText(inferred)) {
            return inferred;
        }
        String guessed = URLConnection.guessContentTypeFromName(filename);
        return StringUtils.hasText(guessed) ? guessed : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
