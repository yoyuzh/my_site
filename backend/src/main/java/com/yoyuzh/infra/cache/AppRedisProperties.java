package com.yoyuzh.infra.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.redis")
public class AppRedisProperties {

    private boolean enabled;
    private String keyPrefix = "yoyuzh";
    private long ttlBufferSeconds = 60;
    private final Cache cache = new Cache();
    private final Namespaces namespaces = new Namespaces();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public long getTtlBufferSeconds() {
        return ttlBufferSeconds;
    }

    public void setTtlBufferSeconds(long ttlBufferSeconds) {
        this.ttlBufferSeconds = ttlBufferSeconds;
    }

    public Cache getCache() {
        return cache;
    }

    public Namespaces getNamespaces() {
        return namespaces;
    }

    public static class Cache {
        private long filesListTtlSeconds = 60;
        private long directoryVersionTtlSeconds = 3600;
        private long adminSummaryTtlSeconds = 30;
        private long storagePoliciesTtlSeconds = 300;
        private long androidReleaseTtlSeconds = 60;

        public long getFilesListTtlSeconds() {
            return filesListTtlSeconds;
        }

        public void setFilesListTtlSeconds(long filesListTtlSeconds) {
            this.filesListTtlSeconds = filesListTtlSeconds;
        }

        public long getDirectoryVersionTtlSeconds() {
            return directoryVersionTtlSeconds;
        }

        public void setDirectoryVersionTtlSeconds(long directoryVersionTtlSeconds) {
            this.directoryVersionTtlSeconds = directoryVersionTtlSeconds;
        }

        public long getAdminSummaryTtlSeconds() {
            return adminSummaryTtlSeconds;
        }

        public void setAdminSummaryTtlSeconds(long adminSummaryTtlSeconds) {
            this.adminSummaryTtlSeconds = adminSummaryTtlSeconds;
        }

        public long getStoragePoliciesTtlSeconds() {
            return storagePoliciesTtlSeconds;
        }

        public void setStoragePoliciesTtlSeconds(long storagePoliciesTtlSeconds) {
            this.storagePoliciesTtlSeconds = storagePoliciesTtlSeconds;
        }

        public long getAndroidReleaseTtlSeconds() {
            return androidReleaseTtlSeconds;
        }

        public void setAndroidReleaseTtlSeconds(long androidReleaseTtlSeconds) {
            this.androidReleaseTtlSeconds = androidReleaseTtlSeconds;
        }
    }

    public static class Namespaces {
        private String cache = "cache";
        private String auth = "auth";
        private String transferSessions = "transfer-sessions";
        private String uploadState = "upload-state";
        private String locks = "locks";
        private String fileEvents = "file-events";
        private String broker = "broker";

        public String getCache() {
            return cache;
        }

        public void setCache(String cache) {
            this.cache = cache;
        }

        public String getAuth() {
            return auth;
        }

        public void setAuth(String auth) {
            this.auth = auth;
        }

        public String getTransferSessions() {
            return transferSessions;
        }

        public void setTransferSessions(String transferSessions) {
            this.transferSessions = transferSessions;
        }

        public String getUploadState() {
            return uploadState;
        }

        public void setUploadState(String uploadState) {
            this.uploadState = uploadState;
        }

        public String getLocks() {
            return locks;
        }

        public void setLocks(String locks) {
            this.locks = locks;
        }

        public String getFileEvents() {
            return fileEvents;
        }

        public void setFileEvents(String fileEvents) {
            this.fileEvents = fileEvents;
        }

        public String getBroker() {
            return broker;
        }

        public void setBroker(String broker) {
            this.broker = broker;
        }
    }
}
