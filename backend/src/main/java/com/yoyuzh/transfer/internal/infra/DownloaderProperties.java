package com.yoyuzh.transfer.internal.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.transfer.remote-download")
public class DownloaderProperties {

    private final Aria2 aria2 = new Aria2();
    private final Qbittorrent qbittorrent = new Qbittorrent();

    public Aria2 getAria2() {
        return aria2;
    }

    public Qbittorrent getQbittorrent() {
        return qbittorrent;
    }

    public static class Aria2 {
        private String baseUrl = "http://127.0.0.1:6800/jsonrpc";
        private String secret;
        private String downloadDir;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getDownloadDir() {
            return downloadDir;
        }

        public void setDownloadDir(String downloadDir) {
            this.downloadDir = downloadDir;
        }
    }

    public static class Qbittorrent {
        private String baseUrl = "http://127.0.0.1:8081";
        private String username;
        private String password;
        private String savePath;
        private String category;
        private String tagPrefix = "remote-download-";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getSavePath() {
            return savePath;
        }

        public void setSavePath(String savePath) {
            this.savePath = savePath;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getTagPrefix() {
            return tagPrefix;
        }

        public void setTagPrefix(String tagPrefix) {
            this.tagPrefix = tagPrefix;
        }
    }
}
