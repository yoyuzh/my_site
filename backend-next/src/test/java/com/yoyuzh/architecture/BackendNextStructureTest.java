package com.yoyuzh.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BackendNextStructureTest {

    @Test
    void requiredConstraintDocsAndMarkersExist() {
        Path repoRoot = Path.of("").toAbsolutePath().getParent();

        List<String> requiredPaths = List.of(
                "backend-next/api-reference.md",
                "docs/backend-next/module-dependency-whitelist.md",
                "docs/backend-next/directory-responsibilities.md",
                "docs/backend-next/rule-ownership-matrix.md",
                "backend-next/src/main/java/com/yoyuzh/identity/access/api/PackageMarker.java",
                "backend-next/src/main/java/com/yoyuzh/identity/access/internal/web/PackageMarker.java",
                "backend-next/src/main/java/com/yoyuzh/files/workspace/internal/domain/PackageMarker.java",
                "backend-next/src/main/java/com/yoyuzh/files/upload/internal/application/PackageMarker.java",
                "backend-next/src/main/java/com/yoyuzh/files/sharing/internal/domain/PackageMarker.java",
                "backend-next/src/main/java/com/yoyuzh/transfer/internal/domain/PackageMarker.java",
                "backend-next/src/main/java/com/yoyuzh/platform/job/internal/infra/PackageMarker.java",
                "backend-next/src/main/java/com/yoyuzh/platform/storage/internal/domain/PackageMarker.java",
                "backend-next/src/main/java/com/yoyuzh/ops/admin/internal/application/PackageMarker.java");

        for (String requiredPath : requiredPaths) {
            assertTrue(
                    Files.exists(repoRoot.resolve(requiredPath)),
                    () -> "Missing required backend-next constraint path: " + requiredPath);
        }
    }

    @Test
    void mainSourceTreeMustRemainGateOnly() throws Exception {
        Path repoRoot = Path.of("").toAbsolutePath().getParent();
        Path mainJavaRoot = repoRoot.resolve("backend-next/src/main/java");

        List<String> javaFiles;
        List<String> nonMarkerJavaFiles;
        try (var paths = Files.walk(mainJavaRoot)) {
            javaFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> repoRoot.relativize(path).toString())
                    .collect(Collectors.toList());
        }
        try (var paths = Files.walk(mainJavaRoot)) {
            nonMarkerJavaFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("PackageMarker.java"))
                    .map(path -> repoRoot.relativize(path).toString())
                    .collect(Collectors.toList());
        }

        assertFalse(
                javaFiles.isEmpty(),
                "backend-next should keep marker classes so architecture rules have package anchors");
        assertTrue(
                nonMarkerJavaFiles.isEmpty(),
                () -> "backend-next must remain gate-only; found non-marker runtime classes: " + nonMarkerJavaFiles);
    }
}
