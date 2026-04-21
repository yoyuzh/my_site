package com.yoyuzh.files.search.internal.infra;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class FileEventInstanceIdentity {

    private final String instanceId;

    public FileEventInstanceIdentity() {
        this(UUID.randomUUID().toString());
    }

    FileEventInstanceIdentity(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getInstanceId() {
        return instanceId;
    }
}
