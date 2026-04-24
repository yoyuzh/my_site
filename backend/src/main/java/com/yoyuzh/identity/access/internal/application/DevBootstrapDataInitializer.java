package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.domain.UserRole;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import com.yoyuzh.files.workspace.api.WorkspaceBootstrapApi;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevBootstrapDataInitializer implements CommandLineRunner {

    private static final DemoAdminSpec DEV_ADMIN = new DemoAdminSpec(
            "admin",
            "Admin123",
            "admin@example.com"
    );

    private static final List<DemoUserSpec> DEMO_USERS = List.of(
            new DemoUserSpec(
                    "portal-demo",
                    "portal123456",
                    "portal-demo@example.com",
                    List.of(
                            new DemoFileSpec("/下载", "迎新资料.txt", "text/plain", "portal-demo 的下载目录示例文件。"),
                            new DemoFileSpec("/文档", "课程规划.md", "text/markdown", "# 课程规划\n- 高级 Java\n- 软件工程\n- 计算机网络"),
                            new DemoFileSpec("/图片", "campus-shot.png", "image/png", "PNG PLACEHOLDER FOR portal-demo")
                    )
            ),
            new DemoUserSpec(
                    "portal-study",
                    "study123456",
                    "portal-study@example.com",
                    List.of(
                            new DemoFileSpec("/下载", "实验数据.csv", "text/csv", "week,score\n1,86\n2,91\n3,95"),
                            new DemoFileSpec("/文档", "论文草稿.md", "text/markdown", "# 论文草稿\n研究方向：人机交互与数据分析。"),
                            new DemoFileSpec("/图片", "data-chart.png", "image/png", "PNG PLACEHOLDER FOR portal-study")
                    )
            ),
            new DemoUserSpec(
                    "portal-design",
                    "design123456",
                    "portal-design@example.com",
                    List.of(
                            new DemoFileSpec("/下载", "素材清单.txt", "text/plain", "图标、插画、动效资源待确认。"),
                            new DemoFileSpec("/文档", "作品说明.md", "text/markdown", "# 作品说明\n本账号用于 UI 方案演示与交付。"),
                            new DemoFileSpec("/图片", "ui-mockup.png", "image/png", "PNG PLACEHOLDER FOR portal-design")
                    )
            )
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WorkspaceBootstrapApi workspaceBootstrapApi;

    @Override
    @Transactional
    public void run(String... args) {
        User admin = ensureAdminUser(DEV_ADMIN);
        workspaceBootstrapApi.ensureDefaultDirectories(workspaceUser(admin));
        for (DemoUserSpec spec : DEMO_USERS) {
            User user = ensureUser(spec);
            workspaceBootstrapApi.ensureDefaultDirectories(workspaceUser(user));
            ensureDemoFiles(user, spec.files());
        }
    }

    private User ensureAdminUser(DemoAdminSpec spec) {
        return userRepository.findByUsername(spec.username())
                .map(existing -> updateExistingAdmin(existing, spec))
                .orElseGet(() -> createAdmin(spec));
    }

    private User createAdmin(DemoAdminSpec spec) {
        User created = new User();
        created.setUsername(spec.username());
        created.setDisplayName(spec.username());
        created.setEmail(spec.email());
        created.setPasswordHash(passwordEncoder.encode(spec.password()));
        created.setRole(UserRole.ADMIN);
        created.setPreferredLanguage("zh-CN");
        return userRepository.save(created);
    }

    private User updateExistingAdmin(User existing, DemoAdminSpec spec) {
        boolean changed = false;
        if (!spec.email().equals(existing.getEmail())) {
            existing.setEmail(spec.email());
            changed = true;
        }
        if (!passwordEncoder.matches(spec.password(), existing.getPasswordHash())) {
            existing.setPasswordHash(passwordEncoder.encode(spec.password()));
            changed = true;
        }
        if (existing.getRole() != UserRole.ADMIN) {
            existing.setRole(UserRole.ADMIN);
            changed = true;
        }
        if (existing.getDisplayName() == null || existing.getDisplayName().isBlank()) {
            existing.setDisplayName(spec.username());
            changed = true;
        }
        if (existing.getPreferredLanguage() == null || existing.getPreferredLanguage().isBlank()) {
            existing.setPreferredLanguage("zh-CN");
            changed = true;
        }
        return changed ? userRepository.save(existing) : existing;
    }

    private User ensureUser(DemoUserSpec spec) {
        return userRepository.findByUsername(spec.username())
                .map(existing -> updateExistingUser(existing, spec))
                .orElseGet(() -> createUser(spec));
    }

    private User createUser(DemoUserSpec spec) {
        User created = new User();
        created.setUsername(spec.username());
        created.setEmail(spec.email());
        created.setPasswordHash(passwordEncoder.encode(spec.password()));
        return userRepository.save(created);
    }

    private User updateExistingUser(User existing, DemoUserSpec spec) {
        boolean changed = false;
        if (!spec.email().equals(existing.getEmail())) {
            existing.setEmail(spec.email());
            changed = true;
        }
        if (!passwordEncoder.matches(spec.password(), existing.getPasswordHash())) {
            existing.setPasswordHash(passwordEncoder.encode(spec.password()));
            changed = true;
        }
        return changed ? userRepository.save(existing) : existing;
    }

    private void ensureDemoFiles(User user, List<DemoFileSpec> files) {
        for (DemoFileSpec file : files) {
            if (workspaceBootstrapApi.existsNode(workspaceUser(user), file.path(), file.filename())) {
                continue;
            }
            workspaceBootstrapApi.importExternalFile(
                    workspaceUser(user),
                    file.path(),
                    file.filename(),
                    file.contentType(),
                    file.content().getBytes(StandardCharsets.UTF_8).length,
                    file.content().getBytes(StandardCharsets.UTF_8)
            );
        }
    }

    private record DemoUserSpec(
            String username,
            String password,
            String email,
            List<DemoFileSpec> files
    ) {
    }

    private record DemoAdminSpec(
            String username,
            String password,
            String email
    ) {
    }

    private record DemoFileSpec(
            String path,
            String filename,
            String contentType,
            String content
    ) {
    }

    private WorkspaceUserContext workspaceUser(User user) {
        return new WorkspaceUserContext(
                user.getId(),
                user.getStorageQuotaBytes(),
                user.getMaxUploadSizeBytes()
        );
    }
}
