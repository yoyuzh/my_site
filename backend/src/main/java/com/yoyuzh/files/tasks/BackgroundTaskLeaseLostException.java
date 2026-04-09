package com.yoyuzh.files.tasks;

class BackgroundTaskLeaseLostException extends RuntimeException {

    BackgroundTaskLeaseLostException(Long taskId, String workerOwner) {
        super("background task lease lost: taskId=" + taskId + ", workerOwner=" + workerOwner);
    }
}
