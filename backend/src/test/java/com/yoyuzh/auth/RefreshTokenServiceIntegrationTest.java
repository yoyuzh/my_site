package com.yoyuzh.auth;

import com.yoyuzh.PortalBackendApplication;
import com.yoyuzh.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = PortalBackendApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:refresh_token_test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.jwt.secret=0123456789abcdef0123456789abcdef",
                "app.storage.root-dir=./target/test-storage-refresh",
                "app.cqu.require-login=true",
                "app.cqu.mock-enabled=false"
        }
)
class RefreshTokenServiceIntegrationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldRejectRefreshTokenReuseAfterRotation() {
        User user = createUser("alice");

        String rawToken = refreshTokenService.issueRefreshToken(user);
        RefreshTokenService.RotatedRefreshToken rotated = refreshTokenService.rotateRefreshToken(rawToken);

        assertThat(rotated.refreshToken()).isNotBlank().isNotEqualTo(rawToken);
        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效或已使用");
        assertThat(refreshTokenRepository.findAll())
                .hasSize(2)
                .filteredOn(RefreshToken::isRevoked)
                .hasSize(1);
    }

    @Test
    void shouldStoreRefreshTokenAsHashInsteadOfPlaintext() {
        User user = createUser("hash-check");

        String rawToken = refreshTokenService.issueRefreshToken(user);

        assertThat(refreshTokenRepository.findAll())
                .singleElement()
                .satisfies(token -> {
                    assertThat(token.getTokenHash()).hasSize(64);
                    assertThat(token.getTokenHash()).isNotEqualTo(rawToken);
                    assertThat(token.getTokenHash()).doesNotContain(rawToken.substring(0, 8));
                });
    }

    @Test
    void shouldRejectExpiredRefreshTokenAndRevokeIt() {
        User user = createUser("expired");
        String rawToken = refreshTokenService.issueRefreshToken(user);
        RefreshToken storedToken = refreshTokenRepository.findAll().get(0);
        storedToken.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        refreshTokenRepository.save(storedToken);

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新令牌已过期");
        assertThat(refreshTokenRepository.findById(storedToken.getId()))
                .get()
                .extracting(RefreshToken::isRevoked)
                .isEqualTo(true);
    }

    @Test
    void shouldAllowConcurrentRefreshTokenConsumptionOnlyOnce() throws Exception {
        User user = createUser("bob");
        String rawToken = refreshTokenService.issueRefreshToken(user);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i += 1) {
                futures.add(executorService.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    try {
                        return refreshTokenService.rotateRefreshToken(rawToken);
                    } catch (BusinessException ex) {
                        return ex;
                    }
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(5, TimeUnit.SECONDS));
            }

            assertThat(results)
                    .filteredOn(result -> result instanceof RefreshTokenService.RotatedRefreshToken)
                    .hasSize(1);
            assertThat(results)
                    .filteredOn(result -> result instanceof BusinessException)
                    .singleElement()
                    .extracting(result -> ((BusinessException) result).getMessage())
                    .isEqualTo("刷新令牌无效或已使用");
            assertThat(refreshTokenRepository.findAll())
                    .hasSize(2)
                    .filteredOn(token -> !token.isRevoked())
                    .hasSize(1);
        } finally {
            executorService.shutdownNow();
        }
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("encoded-password");
        user.setCreatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }
}
