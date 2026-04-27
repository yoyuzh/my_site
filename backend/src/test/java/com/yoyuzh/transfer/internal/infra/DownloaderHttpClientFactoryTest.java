package com.yoyuzh.transfer.internal.infra;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DownloaderHttpClientFactoryTest {

    @Test
    void shouldCreateHttpClientWithConnectTimeoutAndHttp11() {
        HttpClient client = DownloaderHttpClientFactory.create();

        assertThat(client.connectTimeout()).contains(Duration.ofSeconds(10));
        assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
        assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NORMAL);
        assertThat(client.executor()).isPresent();
    }
}
