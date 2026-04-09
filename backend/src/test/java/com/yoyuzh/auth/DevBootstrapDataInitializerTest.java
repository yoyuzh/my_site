package com.yoyuzh.auth;

import com.yoyuzh.files.core.FileService;
import com.yoyuzh.files.core.StoredFileRepository;
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
    private FileService fileService;

    @Mock
    private StoredFileRepository storedFileRepository;

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
        when(storedFileRepository.existsByUserIdAndPathAndFilename(anyLong(), anyString(), anyString())).thenReturn(false);
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
        verify(fileService, times(3)).ensureDefaultDirectories(any(User.class));
        verify(fileService, times(9)).importExternalFile(any(User.class), anyString(), anyString(), anyString(), anyLong(), any());
        org.assertj.core.api.Assertions.assertThat(savedUsers)
                .extracting(User::getUsername)
                .containsExactly("portal-demo", "portal-study", "portal-design");
    }
}
