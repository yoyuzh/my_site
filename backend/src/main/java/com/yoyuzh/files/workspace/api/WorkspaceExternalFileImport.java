package com.yoyuzh.files.workspace.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;

public record WorkspaceExternalFileImport(
        String path,
        String filename,
        String contentType,
        long size,
        ContentStreamOpener contentStreamOpener
) {
    public WorkspaceExternalFileImport(String path,
                                       String filename,
                                       String contentType,
                                       byte[] content) {
        this(path, filename, contentType, normalizeBytes(content).length, bytesOpener(content));
    }

    public WorkspaceExternalFileImport {
        if (size < 0L) {
            throw new IllegalArgumentException("size must not be negative");
        }
        if (contentStreamOpener == null) {
            contentStreamOpener = () -> InputStream.nullInputStream();
        }
    }

    public InputStream openStream() throws IOException {
        return contentStreamOpener.open();
    }

    public byte[] content() {
        try (InputStream inputStream = openStream()) {
            return inputStream.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("failed to read workspace external import content", ex);
        }
    }

    private static ContentStreamOpener bytesOpener(byte[] content) {
        byte[] normalized = normalizeBytes(content);
        return () -> new ByteArrayInputStream(Arrays.copyOf(normalized, normalized.length));
    }

    private static byte[] normalizeBytes(byte[] content) {
        return content == null ? new byte[0] : Arrays.copyOf(content, content.length);
    }

    @FunctionalInterface
    public interface ContentStreamOpener {
        InputStream open() throws IOException;
    }
}
