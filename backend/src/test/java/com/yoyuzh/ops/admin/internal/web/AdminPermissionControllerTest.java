package com.yoyuzh.ops.admin.internal.web;

import com.yoyuzh.boot.web.GlobalExceptionHandler;
import com.yoyuzh.ops.admin.api.AdminPermissionQueryApi;
import com.yoyuzh.ops.admin.api.AdminPermissionResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminPermissionControllerTest {

    @Mock
    private AdminPermissionQueryApi adminPermissionQueryApi;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminPermissionController(adminPermissionQueryApi))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnPermissionResponseShape() throws Exception {
        when(adminPermissionQueryApi.currentPermissions(any()))
                .thenReturn(new AdminPermissionResponse(List.of(
                        "admin.overview.read",
                        "admin.users.read"
                )));

        mockMvc.perform(get("/api/admin/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data.permissions").isArray())
                .andExpect(jsonPath("$.data.permissions[0]").value("admin.overview.read"))
                .andExpect(jsonPath("$.data.permissions[1]").value("admin.users.read"));

        verify(adminPermissionQueryApi).currentPermissions(any());
    }

    @Test
    void shouldPassAuthenticationToQueryApiWhenPresent() throws Exception {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("alice", "password");
        when(adminPermissionQueryApi.currentPermissions(any()))
                .thenReturn(new AdminPermissionResponse(List.of()));

        mockMvc.perform(get("/api/admin/permissions").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions").isArray());

        verify(adminPermissionQueryApi).currentPermissions(authentication);
    }
}
