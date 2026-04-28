package com.yoyuzh.files.content.internal.infra.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.platform.storage.internal.infra.FileStorageProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DogeCloudTmpTokenClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fetchSessionSignsJsonRequestAndReturnsMatchingBucket() {
        FileStorageProperties.S3 properties = new FileStorageProperties.S3();
        properties.setApiBaseUrl("https://api.dogecloud.com");
        properties.setApiAccessKey("doge-ak");
        properties.setApiSecretKey("doge-sk");
        properties.setScope("yoyuzh-files:users/*");
        properties.setRegion("automatic");
        properties.setTtlSeconds(1800);

        CapturingTransport transport = new CapturingTransport("""
                {
                  "code": 200,
                  "msg": "OK",
                  "data": {
                    "Credentials": {
                      "accessKeyId": "tmp-ak",
                      "secretAccessKey": "tmp-sk",
                      "sessionToken": "tmp-token"
                    },
                    "ExpiredAt": 1777777777,
                    "Buckets": [
                      {
                        "name": "yoyuzh-files",
                        "s3Bucket": "s-cd-14873-yoyuzh-files-1258813047",
                        "s3Endpoint": "https://cos.ap-chengdu.myqcloud.com"
                      },
                      {
                        "name": "yoyuzh-front",
                        "s3Bucket": "s-cd-14873-yoyuzh-front-1258813047",
                        "s3Endpoint": "https://cos.ap-chengdu.myqcloud.com"
                      }
                    ]
                  }
                }
                """);

        DogeCloudTmpTokenClient client = new DogeCloudTmpTokenClient(properties, objectMapper, transport);

        DogeCloudTemporaryS3Session session = client.fetchSession();

        assertThat(transport.apiPath).isEqualTo("/auth/tmp_token.json");
        assertThat(transport.body).isEqualTo("{\"channel\":\"OSS_FULL\",\"ttl\":1800,\"scopes\":[\"yoyuzh-files:users/*\"]}");
        assertThat(transport.headers).containsEntry("Content-Type", "application/json");
        assertThat(transport.headers.get("Authorization")).startsWith("TOKEN doge-ak:");
        assertThat(session.accessKeyId()).isEqualTo("tmp-ak");
        assertThat(session.secretAccessKey()).isEqualTo("tmp-sk");
        assertThat(session.sessionToken()).isEqualTo("tmp-token");
        assertThat(session.bucket()).isEqualTo("s-cd-14873-yoyuzh-files-1258813047");
        assertThat(session.endpoint()).isEqualTo("https://cos.ap-chengdu.myqcloud.com");
        assertThat(session.expiresAt()).isEqualTo(Instant.ofEpochSecond(1777777777));
    }

    @Test
    void fetchSessionRejectsApiErrors() {
        FileStorageProperties.S3 properties = new FileStorageProperties.S3();
        properties.setApiBaseUrl("https://api.dogecloud.com");
        properties.setApiAccessKey("doge-ak");
        properties.setApiSecretKey("doge-sk");
        properties.setScope("yoyuzh-files");

        DogeCloudTmpTokenClient client = new DogeCloudTmpTokenClient(
                properties,
                objectMapper,
                new CapturingTransport("""
                        {"code":401,"msg":"ERROR_UNAUTHORIZED"}
                        """)
        );

        assertThatThrownBy(client::fetchSession)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ERROR_UNAUTHORIZED");
    }

    private static final class CapturingTransport implements DogeCloudTmpTokenClient.Transport {

        private final String responseBody;
        private String apiPath;
        private String body;
        private Map<String, String> headers;

        private CapturingTransport(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public DogeCloudTmpTokenClient.TransportResponse post(String baseUrl, String nextApiPath, String nextBody, Map<String, String> nextHeaders) {
            this.apiPath = nextApiPath;
            this.body = nextBody;
            this.headers = nextHeaders;
            return new DogeCloudTmpTokenClient.TransportResponse(200, responseBody);
        }
    }
}
