package com.yoyuzh.transfer.internal.infra;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

final class DownloaderHttpClientFactory {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Executor EXECUTOR = Executors.newFixedThreadPool(
            4,
            new DownloaderThreadFactory()
    );

    private DownloaderHttpClientFactory() {
    }

    static HttpClient create() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1)
                .executor(EXECUTOR)
                .build();
    }

    private static final class DownloaderThreadFactory implements ThreadFactory {
        private final AtomicInteger threadCounter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "downloader-http-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
