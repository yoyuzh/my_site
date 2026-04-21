package com.yoyuzh.platform.job.internal.application;

class BackgroundTaskLeaseLostException extends RuntimeException {

    BackgroundTaskLeaseLostException(Long taskId, String workerOwner) {
        super("background task lease lost: taskId=" + taskId + ", workerOwner=" + workerOwner);
    }
}
