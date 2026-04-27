package com.yoyuzh.transfer.internal.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAria2ClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void submitHttpSendsJsonRpcRequestWithConfiguredSecretAndDownloadDirectory() {
        DownloaderProperties properties = new DownloaderProperties();
        properties.getAria2().setBaseUrl("http://127.0.0.1:6800/jsonrpc");
        properties.getAria2().setSecret("aria2-secret");
        properties.getAria2().setDownloadDir("/downloads");
        CapturingTransport transport = new CapturingTransport("""
                {"jsonrpc":"2.0","id":"remote-download","result":"gid-123"}
                """);

        DefaultAria2Client client = new DefaultAria2Client(properties, objectMapper, transport);

        String gid = client.submitHttp("https://example.com/demo.zip", "local-default");

        assertThat(gid).isEqualTo("gid-123");
        assertThat(transport.url).isEqualTo("http://127.0.0.1:6800/jsonrpc");
        assertThat(transport.headers).containsEntry("Content-Type", "application/json");
        assertThat(transport.body).isEqualTo("""
                {"jsonrpc":"2.0","id":"remote-download","method":"aria2.addUri","params":["token:aria2-secret",["https://example.com/demo.zip"],{"dir":"/downloads"}]}
                """.trim());
    }

    @Test
    void queryStatusReadsProgressAndOutputPath() {
        DownloaderProperties properties = new DownloaderProperties();
        properties.getAria2().setBaseUrl("http://127.0.0.1:6800/jsonrpc");
        properties.getAria2().setSecret("aria2-secret");
        CapturingTransport transport = new CapturingTransport("""
                {"jsonrpc":"2.0","id":"remote-download","result":{"status":"complete","totalLength":"200","completedLength":"200","dir":"/downloads","files":[{"path":"/downloads/demo.zip"}]}}
                """);

        DefaultAria2Client client = new DefaultAria2Client(properties, objectMapper, transport);

        Aria2Client.TaskStatus status = client.queryStatus("gid-123");

        assertThat(status.gid()).isEqualTo("gid-123");
        assertThat(status.status()).isEqualTo("complete");
        assertThat(status.totalBytes()).isEqualTo(200L);
        assertThat(status.completedBytes()).isEqualTo(200L);
        assertThat(status.outputPath()).isEqualTo("/downloads/demo.zip");
        assertThat(transport.body).isEqualTo("""
                {"jsonrpc":"2.0","id":"remote-download","method":"aria2.tellStatus","params":["token:aria2-secret","gid-123",["status","totalLength","completedLength","dir","files","errorCode","errorMessage"]]}
                """.trim());
    }

    @Test
    void cancelSendsForceRemoveJsonRpcCall() {
        DownloaderProperties properties = new DownloaderProperties();
        properties.getAria2().setBaseUrl("http://127.0.0.1:6800/jsonrpc");
        properties.getAria2().setSecret("aria2-secret");
        CapturingTransport transport = new CapturingTransport("""
                {"jsonrpc":"2.0","id":"remote-download","result":"gid-123"}
                """);

        DefaultAria2Client client = new DefaultAria2Client(properties, objectMapper, transport);

        client.cancel("gid-123");

        assertThat(transport.body).isEqualTo("""
                {"jsonrpc":"2.0","id":"remote-download","method":"aria2.forceRemove","params":["token:aria2-secret","gid-123"]}
                """.trim());
    }

    private static final class CapturingTransport implements DefaultAria2Client.Transport {

        private final String responseBody;
        private String url;
        private String body;
        private Map<String, String> headers;

        private CapturingTransport(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public DefaultAria2Client.TransportResponse post(String nextUrl, byte[] nextBody, Map<String, String> nextHeaders) {
            this.url = nextUrl;
            this.body = new String(nextBody, StandardCharsets.UTF_8);
            this.headers = nextHeaders;
            return new DefaultAria2Client.TransportResponse(200, responseBody);
        }
    }
}
