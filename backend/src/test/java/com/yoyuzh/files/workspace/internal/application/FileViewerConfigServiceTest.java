package com.yoyuzh.files.workspace.internal.application;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FileViewerConfigServiceTest {

    @Test
    void shouldExposeAllMigratedViewerDefinitions() {
        FileViewerConfigService service = new FileViewerConfigService();

        var config = service.defaultConfig();
        Set<String> viewerIds = config.fileViewers().stream()
                .map(viewer -> viewer.id())
                .collect(Collectors.toSet());

        assertThat(viewerIds).contains(
                "image",
                "photopea",
                "code-monaco",
                "drawio",
                "markdown",
                "video",
                "pdf",
                "epub",
                "music",
                "excalidraw",
                "archive",
                "google-docs",
                "microsoft-office"
        );
        assertThat(config.defaultViewerMapping())
                .containsEntry("txt", "code-monaco")
                .containsEntry("md", "markdown")
                .containsEntry("drawio", "drawio")
                .containsEntry("excalidraw", "excalidraw")
                .containsEntry("zip", "archive")
                .containsEntry("docx", "microsoft-office")
                .containsEntry("pdf", "pdf");
    }

    @Test
    void shouldExposeNewFileTemplatesForEditableFormats() {
        FileViewerConfigService service = new FileViewerConfigService();

        var templates = service.defaultConfig().fileViewers().stream()
                .flatMap(viewer -> viewer.templates().stream())
                .collect(Collectors.toMap(template -> template.extension(), template -> template.viewerId()));

        assertThat(templates)
                .containsEntry("txt", "code-monaco")
                .containsEntry("md", "markdown")
                .containsEntry("drawio", "drawio")
                .containsEntry("excalidraw", "excalidraw");
    }
}
