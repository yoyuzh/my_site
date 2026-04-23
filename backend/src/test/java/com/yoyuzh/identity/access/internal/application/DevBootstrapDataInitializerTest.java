package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.api.*;
import com.yoyuzh.identity.access.internal.application.*;
import com.yoyuzh.identity.access.internal.domain.*;
import com.yoyuzh.identity.access.internal.infra.*;
import com.yoyuzh.files.workspace.api.WorkspaceBootstrapApi;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevBootstrapDataInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private WorkspaceBootstrapApi workspaceBootstrapApi;

    @InjectMocks
    private DevBootstrapDataInitializer initializer;

    @Test
    void shouldCreateInitialDevUsersWhenMissing() throws Exception {
        when(userRepository.findByUsername("portal-demo")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("portal-study")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("portal-design")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("portal123456")).thenReturn("encoded-demo-password");
        when(passwordEncoder.encode("study123456")).thenReturn("encoded-study-password");
        when(passwordEncoder.encode("design123456")).thenReturn("encoded-design-password");
        when(workspaceBootstrapApi.existsNode(any(WorkspaceUserContext.class), anyString(), anyString())).thenReturn(false);
        List<User> savedUsers = new ArrayList<>();
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId((long) (savedUsers.size() + 1));
            user.setCreatedAt(LocalDateTime.now());
            savedUsers.add(user);
            return user;
        });

        initializer.run();

        verify(userRepository, times(3)).save(any(User.class));
        verify(workspaceBootstrapApi, times(3)).ensureDefaultDirectories(any(WorkspaceUserContext.class));
        verify(workspaceBootstrapApi, times(9)).importExternalFile(any(WorkspaceUserContext.class), anyString(), anyString(), anyString(), anyLong(), any());
        org.assertj.core.api.Assertions.assertThat(savedUsers)
                .extracting(User::getUsername)
                .containsExactly("portal-demo", "portal-study", "portal-design");
    }
}
