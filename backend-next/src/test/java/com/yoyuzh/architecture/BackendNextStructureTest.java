package com.yoyuzh.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
}
