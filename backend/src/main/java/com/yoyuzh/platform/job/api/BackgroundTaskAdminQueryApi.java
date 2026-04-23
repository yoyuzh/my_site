package com.yoyuzh.platform.job.api;

import com.yoyuzh.shared.kernel.PageResponse;

public interface BackgroundTaskAdminQueryApi {

    PageResponse<AdminBackgroundTaskView> listTasks(AdminBackgroundTaskQuery query);

    AdminBackgroundTaskView getTask(Long taskId);

    long countActiveTasks();
}
