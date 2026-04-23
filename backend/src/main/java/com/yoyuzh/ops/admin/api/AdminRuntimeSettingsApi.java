package com.yoyuzh.ops.admin.api;

import java.util.List;

public interface AdminRuntimeSettingsApi {

    boolean isInviteCodeRequired();

    List<String> registrationManagementRoles();
}
