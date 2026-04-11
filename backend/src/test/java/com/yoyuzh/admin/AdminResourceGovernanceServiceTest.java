package com.yoyuzh.admin;

import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.auth.UserRole;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.files.core.FileService;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.share.FileShareLink;
import com.yoyuzh.files.share.FileShareLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminResourceGovernanceServiceTest {

    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private FileService fileService;
    @Mock
    private FileShareLinkRepository fileShareLinkRepository;
    @Mock
    private AdminAuditService adminAuditService;

    private AdminResourceGovernanceService adminResourceGovernanceService;

    @BeforeEach
    void setUp() {
        adminResourceGovernanceService = new AdminResourceGovernanceService(
                storedFileRepository,
                fileService,
                fileShareLinkRepository,
                adminAuditService
        );
    }

    @Test
    void shouldDeleteShare() {
        FileShareLink shareLink = new FileShareLink();
        shareLink.setId(5L);
        when(fileShareLinkRepository.findById(5L)).thenReturn(Optional.of(shareLink));

        adminResourceGovernanceService.deleteShare(5L);

        verify(fileShareLinkRepository).delete(shareLink);
    }

    @Test
    void shouldThrowWhenDeletingNonExistentShare() {
        when(fileShareLinkRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminResourceGovernanceService.deleteShare(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("share not found");
    }

    @Test
    void shouldDeleteFileByDelegatingToFileService() {
        User owner = createUser(1L, "alice", "alice@example.com");
        StoredFile file = createFile(10L, owner, "/docs", "report.pdf");
        when(storedFileRepository.findById(10L)).thenReturn(Optional.of(file));

        adminResourceGovernanceService.deleteFile(10L);

        verify(fileService).delete(owner, 10L);
    }

    @Test
    void shouldThrowWhenDeletingNonExistentFile() {
        when(storedFileRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminResourceGovernanceService.deleteFile(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("file not found");
    }

    private User createUser(Long id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("hashed");
        user.setRole(UserRole.USER);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    private StoredFile createFile(Long id, User owner, String path, String filename) {
        StoredFile file = new StoredFile();
        file.setId(id);
        file.setUser(owner);
        file.setPath(path);
        file.setFilename(filename);
        file.setSize(1024L);
        file.setDirectory(false);
        file.setCreatedAt(LocalDateTime.now());
        return file;
    }
}
