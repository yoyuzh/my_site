package com.yoyuzh.files.content.internal.infra.storage;

import com.yoyuzh.platform.storage.api.StorageRuntimeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

final class DogeCloudS3SessionProvider implements S3SessionProvider {

    private static final Logger log = LoggerFactory.getLogger(DogeCloudS3SessionProvider.class);

    private static final Duration REFRESH_WINDOW = Duration.ofMinutes(1);

    private final Supplier<DogeCloudTemporaryS3Session> sessionSupplier;
    private final Clock clock;
    private final Function<DogeCloudTemporaryS3Session, S3FileRuntimeSession> runtimeFactory;

    private CachedSession cachedSession;

    DogeCloudS3SessionProvider(StorageRuntimeProperties.S3 properties, DogeCloudTmpTokenClient tmpTokenClient) {
        this(
                properties,
                tmpTokenClient::fetchSession,
                Clock.systemUTC(),
                session -> createRuntimeSession(properties, session)
        );
    }

    DogeCloudS3SessionProvider(
            StorageRuntimeProperties.S3 properties,
            Supplier<DogeCloudTemporaryS3Session> sessionSupplier,
            Clock clock,
            Function<DogeCloudTemporaryS3Session, S3FileRuntimeSession> runtimeFactory
    ) {
        this.sessionSupplier = sessionSupplier;
        this.clock = clock;
        this.runtimeFactory = runtimeFactory;
    }

    @Override
    public synchronized S3FileRuntimeSession currentSession() {
        if (cachedSession != null && clock.instant().isBefore(cachedSession.expiresAt().minus(REFRESH_WINDOW))) {
            return cachedSession.runtimeSession();
        }

        long startedAt = System.nanoTime();
        boolean hadCachedSession = cachedSession != null;
        closeCachedSession();
        DogeCloudTemporaryS3Session nextSession = sessionSupplier.get();
        S3FileRuntimeSession runtimeSession = runtimeFactory.apply(nextSession);
        cachedSession = new CachedSession(nextSession.expiresAt(), runtimeSession);
        log.info(
                "upload-probe operation=dogecloud-session-refresh durationMs={} cacheRefresh={} expiresAt={}",
                formatMillis(System.nanoTime() - startedAt),
                hadCachedSession,
                nextSession.expiresAt()
        );
        return runtimeSession;
    }

    @Override
    public synchronized void close() {
        closeCachedSession();
    }

    private void closeCachedSession() {
        if (cachedSession == null) {
            return;
        }
        cachedSession.runtimeSession().s3Presigner().close();
        cachedSession.runtimeSession().s3Client().close();
        cachedSession = null;
    }

    private static S3FileRuntimeSession createRuntimeSession(StorageRuntimeProperties.S3 properties, DogeCloudTemporaryS3Session session) {
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(AwsSessionCredentials.create(
                session.accessKeyId(),
                session.secretAccessKey(),
                session.sessionToken()
        ));
        Region region = Region.of(resolveRegion(properties));
        URI endpoint = URI.create(session.endpoint());
        return new S3FileRuntimeSession(
                session.bucket(),
                S3Client.builder()
                        .credentialsProvider(credentialsProvider)
                        .region(region)
                        .endpointOverride(endpoint)
                        .serviceConfiguration(S3Configuration.builder().build())
                        .build(),
                S3Presigner.builder()
                        .credentialsProvider(credentialsProvider)
                        .region(region)
                        .endpointOverride(endpoint)
                        .serviceConfiguration(S3Configuration.builder().build())
                        .build()
        );
    }

    private static String resolveRegion(StorageRuntimeProperties.S3 properties) {
        return properties.getRegion() == null || properties.getRegion().isBlank()
                ? "automatic"
                : properties.getRegion();
    }

    private static String formatMillis(long durationNanos) {
        return String.format(Locale.ROOT, "%.2f", durationNanos / 1_000_000.0d);
    }

    private record CachedSession(Instant expiresAt, S3FileRuntimeSession runtimeSession) {
    }
}
