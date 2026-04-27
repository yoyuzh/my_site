package com.yoyuzh.transfer.internal.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultQbittorrentClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void submitMagnetLogsInAddsTorrentAndReadsHashBackByTag() {
        DownloaderProperties properties = new DownloaderProperties();
        properties.getQbittorrent().setBaseUrl("http://127.0.0.1:8081");
        properties.getQbittorrent().setUsername("admin");
        properties.getQbittorrent().setPassword("adminpass");
        properties.getQbittorrent().setSavePath("/downloads");
        properties.getQbittorrent().setCategory("remote");
        CapturingTransport transport = new CapturingTransport(
                new DefaultQbittorrentClient.TransportResponse(200, "Ok.", Map.of("Set-Cookie", List.of("SID=test-cookie; HttpOnly"))),
                new DefaultQbittorrentClient.TransportResponse(200, "Ok.", Map.of()),
                new DefaultQbittorrentClient.TransportResponse(200, """
                        [{"hash":"hash-123","name":"demo"}]
                        """, Map.of())
        );

        DefaultQbittorrentClient client = new DefaultQbittorrentClient(properties, objectMapper, transport);

        String hash = client.submitMagnet("magnet:?xt=urn:btih:demo", "local-default");

        assertThat(hash).isEqualTo("hash-123");
        assertThat(transport.requests).hasSize(3);
        CapturedRequest loginRequest = transport.requests.get(0);
        assertThat(loginRequest.url()).isEqualTo("http://127.0.0.1:8081/api/v2/auth/login");
        assertThat(loginRequest.headers()).containsEntry("Content-Type", "application/x-www-form-urlencoded");
        assertThat(loginRequest.body()).isEqualTo("username=admin&password=adminpass");

        CapturedRequest addRequest = transport.requests.get(1);
        assertThat(addRequest.url()).isEqualTo("http://127.0.0.1:8081/api/v2/torrents/add");
        assertThat(addRequest.headers().get("Cookie")).contains("SID=test-cookie");
        assertThat(addRequest.headers().get("Content-Type")).contains("multipart/form-data; boundary=");
        assertThat(addRequest.body()).contains("name=\"urls\"");
        assertThat(addRequest.body()).contains("magnet:?xt=urn:btih:demo");
        assertThat(addRequest.body()).contains("name=\"savepath\"");
        assertThat(addRequest.body()).contains("/downloads");
        assertThat(addRequest.body()).contains("name=\"category\"");
        assertThat(addRequest.body()).contains("remote");
        assertThat(addRequest.body()).contains("name=\"tags\"");

        CapturedRequest infoRequest = transport.requests.get(2);
        assertThat(infoRequest.url()).startsWith("http://127.0.0.1:8081/api/v2/torrents/info?tag=remote-download-");
        assertThat(infoRequest.headers().get("Cookie")).contains("SID=test-cookie");
    }

    @Test
    void submitTorrentUploadsBinaryTorrentFile() {
        DownloaderProperties properties = new DownloaderProperties();
        properties.getQbittorrent().setBaseUrl("http://127.0.0.1:8081");
        properties.getQbittorrent().setUsername("admin");
        properties.getQbittorrent().setPassword("adminpass");
        CapturingTransport transport = new CapturingTransport(
                new DefaultQbittorrentClient.TransportResponse(200, "Ok.", Map.of("Set-Cookie", List.of("SID=test-cookie; HttpOnly"))),
                new DefaultQbittorrentClient.TransportResponse(200, "Ok.", Map.of()),
                new DefaultQbittorrentClient.TransportResponse(200, """
                        [{"hash":"hash-456","name":"demo"}]
                        """, Map.of())
        );

        DefaultQbittorrentClient client = new DefaultQbittorrentClient(properties, objectMapper, transport);

        String hash = client.submitTorrent("demo.torrent", "torrent-data".getBytes(StandardCharsets.UTF_8), "local-default");

        assertThat(hash).isEqualTo("hash-456");
        CapturedRequest addRequest = transport.requests.get(1);
        assertThat(addRequest.body()).contains("name=\"torrents\"; filename=\"demo.torrent\"");
        assertThat(addRequest.body()).contains("torrent-data");
    }

    @Test
    void submitTorrentPreservesBinaryTorrentPayload() {
        DownloaderProperties properties = new DownloaderProperties();
        properties.getQbittorrent().setBaseUrl("http://127.0.0.1:8081");
        properties.getQbittorrent().setUsername("admin");
        properties.getQbittorrent().setPassword("adminpass");
        CapturingTransport transport = new CapturingTransport(
                new DefaultQbittorrentClient.TransportResponse(200, "Ok.", Map.of("Set-Cookie", List.of("SID=test-cookie; HttpOnly"))),
                new DefaultQbittorrentClient.TransportResponse(200, "Ok.", Map.of()),
                new DefaultQbittorrentClient.TransportResponse(200, """
                        [{"hash":"hash-789","name":"demo"}]
                        """, Map.of())
        );

        DefaultQbittorrentClient client = new DefaultQbittorrentClient(properties, objectMapper, transport);
        byte[] torrentContent = new byte[]{0x00, 0x01, (byte) 0xFE, (byte) 0xFF, 0x41};

        client.submitTorrent("demo.torrent", torrentContent, "local-default");

        assertThat(transport.requests.get(1).rawBody()).containsSequence(torrentContent);
    }

    @Test
    void reusesLoginCookieAcrossMultipleOperations() {
        DownloaderProperties properties = new DownloaderProperties();
        properties.getQbittorrent().setBaseUrl("http://127.0.0.1:8081");
        properties.getQbittorrent().setUsername("admin");
        properties.getQbittorrent().setPassword("adminpass");
        CapturingTransport transport = new CapturingTransport(
                new DefaultQbittorrentClient.TransportResponse(200, "Ok.", Map.of("Set-Cookie", List.of("SID=test-cookie; HttpOnly"))),
                new DefaultQbittorrentClient.TransportResponse(200, "Ok.", Map.of()),
                new DefaultQbittorrentClient.TransportResponse(200, """
                        [{"hash":"hash-123","name":"demo"}]
                        """, Map.of()),
                new DefaultQbittorrentClient.TransportResponse(200, """
                        [{"hash":"hash-123","state":"downloading","progress":0.5,"content_path":"/downloads/demo","save_path":"/downloads"}]
                        """, Map.of())
        );

        DefaultQbittorrentClient client = new DefaultQbittorrentClient(properties, objectMapper, transport);

        client.submitMagnet("magnet:?xt=urn:btih:demo", "local-default");
        client.queryTorrent("hash-123");

        assertThat(transport.requests)
                .extracting(CapturedRequest::url)
                .filteredOn(url -> url.endsWith("/api/v2/auth/login"))
                .hasSize(1);
    }

    @Test
    void queryTorrentReadsTorrentStateAndPaths() {
        DownloaderProperties properties = new DownloaderProperties();
        properties.getQbittorrent().setBaseUrl("http://127.0.0.1:8081");
        properties.getQbittorrent().setUsername("admin");
        properties.getQbittorrent().setPassword("adminpass");
        CapturingTransport transport = new CapturingTransport(
                new DefaultQbittorrentClient.TransportResponse(200, "Ok.", Map.of("Set-Cookie", List.of("SID=test-cookie; HttpOnly"))),
                new DefaultQbittorrentClient.TransportResponse(200, """
                        [{"hash":"hash-123","state":"metaDL","progress":0.25,"content_path":"/downloads/demo","save_path":"/downloads"}]
                        """, Map.of())
        );

        DefaultQbittorrentClient client = new DefaultQbittorrentClient(properties, objectMapper, transport);

        QbittorrentClient.TorrentStatus status = client.queryTorrent("hash-123");

        assertThat(status.hash()).isEqualTo("hash-123");
        assertThat(status.state()).isEqualTo("metaDL");
        assertThat(status.progress()).isEqualTo(0.25d);
        assertThat(status.contentPath()).isEqualTo("/downloads/demo");
        assertThat(status.savePath()).isEqualTo("/downloads");
        assertThat(transport.requests.get(1).url()).isEqualTo("http://127.0.0.1:8081/api/v2/torrents/info?hashes=hash-123");
    }

    @Test
    void listFilesReadsTorrentCandidateFiles() {
        DownloaderProperties properties = new DownloaderProperties();
        properties.getQbittorrent().setBaseUrl("http://127.0.0.1:8081");
        properties.getQbittorrent().setUsername("admin");
        properties.getQbittorrent().setPassword("adminpass");
        CapturingTransport transport = new CapturingTransport(
                new DefaultQbittorrentClient.TransportResponse(200, "Ok.", Map.of("Set-Cookie", List.of("SID=test-cookie; HttpOnly"))),
                new DefaultQbittorrentClient.TransportResponse(200, """
                        [{"index":0,"name":"movie.mkv","size":1024,"priority":1},{"index":1,"name":"subtitle.srt","size":32,"priority":0}]
                        """, Map.of())
        );

        DefaultQbittorrentClient client = new DefaultQbittorrentClient(properties, objectMapper, transport);

        List<QbittorrentClient.TorrentFile> files = client.listFiles("hash-123");

        assertThat(files).containsExactly(
                new QbittorrentClient.TorrentFile("0", "movie.mkv", 1024L, 1),
                new QbittorrentClient.TorrentFile("1", "subtitle.srt", 32L, 0)
        );
        assertThat(transport.requests.get(1).url()).isEqualTo("http://127.0.0.1:8081/api/v2/torrents/files?hash=hash-123");
    }

    @Test
    void updateFileSelectionPostsSelectedAndUnselectedPriorities() {
        DownloaderProperties properties = new DownloaderProperties();
        properties.getQbittorrent().setBaseUrl("http://127.0.0.1:8081");
        properties.getQbittorrent().setUsername("admin");
        properties.getQbittorrent().setPassword("adminpass");
        CapturingTransport transport = new CapturingTransport(
                new DefaultQbittorrentClient.TransportResponse(200, "Ok.", Map.of("Set-Cookie", List.of("SID=test-cookie; HttpOnly"))),
                new DefaultQbittorrentClient.TransportResponse(200, "Ok.", Map.of()),
                new DefaultQbittorrentClient.TransportResponse(200, "Ok.", Map.of())
        );

        DefaultQbittorrentClient client = new DefaultQbittorrentClient(properties, objectMapper, transport);

        client.updateFileSelection("hash-123", List.of("0"), List.of("1"));

        assertThat(transport.requests).hasSize(3);
        assertThat(transport.requests.get(1).url()).isEqualTo("http://127.0.0.1:8081/api/v2/torrents/filePrio");
        assertThat(transport.requests.get(1).body()).isEqualTo("hash=hash-123&id=0&priority=1");
        assertThat(transport.requests.get(2).body()).isEqualTo("hash=hash-123&id=1&priority=0");
    }

    @Test
    void deletePostsDeleteTorrentRequest() {
        DownloaderProperties properties = new DownloaderProperties();
        properties.getQbittorrent().setBaseUrl("http://127.0.0.1:8081");
        properties.getQbittorrent().setUsername("admin");
        properties.getQbittorrent().setPassword("adminpass");
        CapturingTransport transport = new CapturingTransport(
                new DefaultQbittorrentClient.TransportResponse(200, "Ok.", Map.of("Set-Cookie", List.of("SID=test-cookie; HttpOnly"))),
                new DefaultQbittorrentClient.TransportResponse(200, "Ok.", Map.of())
        );

        DefaultQbittorrentClient client = new DefaultQbittorrentClient(properties, objectMapper, transport);

        client.delete("hash-123", true);

        assertThat(transport.requests).hasSize(2);
        assertThat(transport.requests.get(1).url()).isEqualTo("http://127.0.0.1:8081/api/v2/torrents/delete");
        assertThat(transport.requests.get(1).body()).isEqualTo("hashes=hash-123&deleteFiles=true");
    }

    record CapturedRequest(String method, String url, String body, byte[] rawBody, Map<String, String> headers) {
    }

    private static final class CapturingTransport implements DefaultQbittorrentClient.Transport {

        private final List<DefaultQbittorrentClient.TransportResponse> responses;
        private final List<CapturedRequest> requests = new ArrayList<>();

        private CapturingTransport(DefaultQbittorrentClient.TransportResponse... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public DefaultQbittorrentClient.TransportResponse send(String method, String url, byte[] body, Map<String, String> headers) {
            requests.add(new CapturedRequest(method, url, new String(body, StandardCharsets.ISO_8859_1), body.clone(), headers));
            return responses.get(requests.size() - 1);
        }
    }
}
