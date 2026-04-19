package com.yoyuzh.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class BackendLegacyToTargetMappingTest {

    private final Path repoRoot = Path.of("").toAbsolutePath().getParent();
    private final Path backendNextJavaRoot = repoRoot.resolve("backend-next/src/main/java/com/yoyuzh");

    @Test
    void targetModuleRootsRequiredByMigrationPlanExist() {
        List<String> requiredDirectories = List.of(
                "boot",
                "shared/kernel",
                "infra/broker",
                "infra/cache",
                "infra/client",
                "infra/lock",
                "identity/access/api",
                "files/workspace/api",
                "files/content/api",
                "files/upload/api",
                "files/sharing/api",
                "files/search/api",
                "transfer/api",
                "platform/job/api",
                "platform/storage/api",
                "ops/admin/api",
                "app/android/api");

        for (String requiredDirectory : requiredDirectories) {
            assertTrue(
                    Files.isDirectory(backendNextJavaRoot.resolve(requiredDirectory)),
                    () -> "Missing target package root required by migration plan: " + requiredDirectory);
        }
    }

    @Test
    void backendNextMustNotContainLegacyRuntimePackageRoots() {
        List<String> forbiddenDirectories = List.of(
                "auth",
                "admin",
                "config",
                "common",
                "api",
                "files/core",
                "files/policy",
                "files/tasks",
                "files/share");

        for (String forbiddenDirectory : forbiddenDirectories) {
            assertTrue(
                    Files.notExists(backendNextJavaRoot.resolve(forbiddenDirectory)),
                    () -> "Found legacy runtime package root in backend-next: " + forbiddenDirectory);
        }
    }
}
