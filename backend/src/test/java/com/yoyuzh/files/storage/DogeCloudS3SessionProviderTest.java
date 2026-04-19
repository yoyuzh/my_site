package com.yoyuzh.files.storage;

import com.yoyuzh.platform.storage.internal.infra.FileStorageProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DogeCloudS3SessionProviderTest {

    @Test
    void currentSessionCachesSessionUntilRefreshWindow() {
        FileStorageProperties.S3 properties = new FileStorageProperties.S3();
        properties.setRegion("automatic");

        AtomicInteger fetchCount = new AtomicInteger();
        AtomicInteger runtimeCount = new AtomicInteger();
        MutableClock clock = new MutableClock(Instant.parse("2026-04-01T10:00:00Z"));

        DogeCloudS3SessionProvider provider = new DogeCloudS3SessionProvider(
                properties,
                () -> {
                    int index = fetchCount.incrementAndGet();
                    return new DogeCloudTemporaryS3Session(
                            "bucket-" + index,
                            "https://cos.ap-chengdu.myqcloud.com",
                            "ak-" + index,
                            "sk-" + index,
                            "token-" + index,
                            clock.instant().plusSeconds(index == 1 ? 600 : 1200)
                    );
                },
                clock,
                session -> new S3FileRuntimeSession(
                        session.bucket(),
                        mock(S3Client.class, "s3Client-" + runtimeCount.incrementAndGet()),
                        mock(S3Presigner.class, "presigner-" + runtimeCount.get())
                )
        );

        S3FileRuntimeSession first = provider.currentSession();
        S3FileRuntimeSession second = provider.currentSession();
        assertThat(first).isSameAs(second);
        assertThat(fetchCount.get()).isEqualTo(1);

        clock.setInstant(Instant.parse("2026-04-01T10:09:30Z"));
        S3FileRuntimeSession refreshed = provider.currentSession();
        assertThat(refreshed).isNotSameAs(first);
        assertThat(refreshed.bucket()).isEqualTo("bucket-2");
        assertThat(fetchCount.get()).isEqualTo(2);
    }

    @Test
    void refreshAndCloseShouldReleaseCachedRuntimeResources() {
        FileStorageProperties.S3 properties = new FileStorageProperties.S3();
        properties.setRegion("automatic");

        MutableClock clock = new MutableClock(Instant.parse("2026-04-01T10:00:00Z"));
        AtomicInteger fetchCount = new AtomicInteger();
        S3Client firstClient = mock(S3Client.class, "first-client");
        S3Presigner firstPresigner = mock(S3Presigner.class, "first-presigner");
        S3Client secondClient = mock(S3Client.class, "second-client");
        S3Presigner secondPresigner = mock(S3Presigner.class, "second-presigner");

        DogeCloudS3SessionProvider provider = new DogeCloudS3SessionProvider(
                properties,
                () -> {
                    int index = fetchCount.incrementAndGet();
                    return new DogeCloudTemporaryS3Session(
                            "bucket-" + index,
                            "https://cos.ap-chengdu.myqcloud.com",
                            "ak-" + index,
                            "sk-" + index,
                            "token-" + index,
                            clock.instant().plusSeconds(index == 1 ? 600 : 1200)
                    );
                },
                clock,
                session -> {
                    if ("bucket-1".equals(session.bucket())) {
                        return new S3FileRuntimeSession(session.bucket(), firstClient, firstPresigner);
                    }
                    return new S3FileRuntimeSession(session.bucket(), secondClient, secondPresigner);
                }
        );

        provider.currentSession();
        clock.setInstant(Instant.parse("2026-04-01T10:09:30Z"));
        provider.currentSession();
        provider.close();

        verify(firstClient, times(1)).close();
        verify(firstPresigner, times(1)).close();
        verify(secondClient, times(1)).close();
        verify(secondPresigner, times(1)).close();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
