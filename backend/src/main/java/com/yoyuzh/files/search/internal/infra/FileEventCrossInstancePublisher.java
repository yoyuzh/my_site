package com.yoyuzh.files.search.internal.infra;

import com.yoyuzh.files.search.internal.domain.FileEvent;

public interface FileEventCrossInstancePublisher {

    void publish(FileEvent event);
}
