package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceViewerTokenServiceTest {

    @Test
    void shouldGenerateAndParseViewerToken() {
        WorkspaceViewerTokenService service = new WorkspaceViewerTokenService(
                "local-dev-secret-local-dev-secret-2026",
                120,
                Clock.fixed(Instant.parse("2026-05-01T05:10:00Z"), ZoneOffset.UTC)
        );
        service.init();

        String token = service.generateViewerToken(7L, 45L);
        WorkspaceViewerTokenService.ViewerTokenClaims claims = service.parseViewerToken(token);

        assertThat(claims.userId()).isEqualTo(7L);
        assertThat(claims.fileId()).isEqualTo(45L);
    }

    @Test
    void shouldRejectExpiredViewerToken() {
        WorkspaceViewerTokenService issuer = new WorkspaceViewerTokenService(
                "local-dev-secret-local-dev-secret-2026",
                1,
                Clock.fixed(Instant.parse("2026-05-01T05:10:00Z"), ZoneOffset.UTC)
        );
        issuer.init();
        String token = issuer.generateViewerToken(7L, 45L);

        WorkspaceViewerTokenService verifier = new WorkspaceViewerTokenService(
                "local-dev-secret-local-dev-secret-2026",
                120,
                Clock.fixed(Instant.parse("2026-05-01T05:10:05Z"), ZoneOffset.UTC)
        );
        verifier.init();

        assertThatThrownBy(() -> verifier.parseViewerToken(token))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("预览链接无效或已过期");
    }

    @Test
    void shouldRejectTokenSignedWithDifferentViewerSecret() {
        WorkspaceViewerTokenService issuer = new WorkspaceViewerTokenService(
                "viewer-secret-aaaaaaaaaaaaaaaaaaaaaa",
                120,
                Clock.fixed(Instant.parse("2026-05-01T05:10:00Z"), ZoneOffset.UTC)
        );
        issuer.init();
        String token = issuer.generateViewerToken(7L, 45L);

        WorkspaceViewerTokenService verifier = new WorkspaceViewerTokenService(
                "viewer-secret-bbbbbbbbbbbbbbbbbbbbbb",
                120,
                Clock.fixed(Instant.parse("2026-05-01T05:10:00Z"), ZoneOffset.UTC)
        );
        verifier.init();

        assertThatThrownBy(() -> verifier.parseViewerToken(token))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("预览链接无效或已过期");
    }

    @Test
    void shouldRejectBlankViewerSecretAtInit() {
        WorkspaceViewerTokenService service = new WorkspaceViewerTokenService(
                "",
                120,
                Clock.fixed(Instant.parse("2026-05-01T05:10:00Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(service::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.files.viewer-token-secret");
    }

    @Test
    void shouldRejectTooShortViewerSecretAtInit() {
        WorkspaceViewerTokenService service = new WorkspaceViewerTokenService(
                "too-short-secret",
                120,
                Clock.fixed(Instant.parse("2026-05-01T05:10:00Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(service::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("至少需要 32 字节");
    }

}
