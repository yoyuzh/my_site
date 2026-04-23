package com.yoyuzh.files.content.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.files.content.api.ContentAdminFileBlobQuery;
import com.yoyuzh.files.content.api.ContentAdminFileBlobView;
import com.yoyuzh.files.content.api.ContentEntityType;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.infra.FileBlobRepository;
import com.yoyuzh.files.content.internal.domain.FileEntity;
import com.yoyuzh.files.content.internal.infra.FileEntityRepository;
import com.yoyuzh.files.content.internal.domain.FileEntityType;
import com.yoyuzh.files.content.internal.infra.StoredFileEntityRepository;
import com.yoyuzh.shared.kernel.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class RuntimeContentAdminInspectionApiTest {

    @Mock
    private FileEntityRepository fileEntityRepository;
    @Mock
    private FileBlobRepository fileBlobRepository;
    @Mock
    private StoredFileEntityRepository storedFileEntityRepository;

    private RuntimeContentAdminInspectionApi runtimeContentAdminInspectionApi;

    @BeforeEach
    void setUp() {
        runtimeContentAdminInspectionApi = new RuntimeContentAdminInspectionApi(
                fileEntityRepository,
                fileBlobRepository,
                storedFileEntityRepository
        );
    }

    @Test
    void shouldListFileBlobsAsAdmin() {
        User creator = createUser(9L, "creator", "creator@example.com");
        FileEntity entity = new FileEntity();
        entity.setId(100L);
        entity.setObjectKey("blobs/a");
        entity.setEntityType(FileEntityType.VERSION);
        entity.setStoragePolicyId(5L);
        entity.setSize(1024L);
        entity.setContentType("application/pdf");
        entity.setReferenceCount(1);
        entity.setCreatedByUserId(creator == null ? null : creator.getId());
        entity.setCreatedAt(LocalDateTime.now().minusMinutes(2));

        FileBlob blob = new FileBlob();
        blob.setId(88L);
        blob.setObjectKey("blobs/a");
        blob.setContentType("application/pdf");
        blob.setSize(1024L);
        blob.setCreatedAt(LocalDateTime.now().minusMinutes(3));

        StoredFileEntityRepository.FileEntityLinkStatsProjection linkStats =
                mock(StoredFileEntityRepository.FileEntityLinkStatsProjection.class);
        when(linkStats.getFileEntityId()).thenReturn(100L);
        when(linkStats.getLinkedStoredFileCount()).thenReturn(1L);
        when(linkStats.getLinkedOwnerCount()).thenReturn(1L);
        when(linkStats.getSampleOwnerUsername()).thenReturn("alice");
        when(linkStats.getSampleOwnerEmail()).thenReturn("alice@example.com");

        when(fileEntityRepository.searchAdminEntities(anyString(), any(), anyString(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(fileBlobRepository.findAllByObjectKeyIn(any())).thenReturn(List.of(blob));
        when(storedFileEntityRepository.findAdminLinkStatsByFileEntityIds(any())).thenReturn(List.of(linkStats));

        PageResponse<ContentAdminFileBlobView> response = runtimeContentAdminInspectionApi.listFileBlobsAsAdmin(
                new ContentAdminFileBlobQuery(0, 10, null, null, null, ContentEntityType.VERSION)
        );

        assertThat(response.items()).hasSize(1);
        ContentAdminFileBlobView item = response.items().get(0);
        assertThat(item.entityId()).isEqualTo(100L);
        assertThat(item.blobId()).isEqualTo(88L);
        assertThat(item.entityType()).isEqualTo(ContentEntityType.VERSION);
        assertThat(item.linkedStoredFileCount()).isEqualTo(1L);
        assertThat(item.linkedOwnerCount()).isEqualTo(1L);
        assertThat(item.sampleOwnerUsername()).isEqualTo("alice");
        assertThat(item.sampleOwnerEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void shouldSkipBlobAndLinkLookupsWhenNoEntityMatched() {
        when(fileEntityRepository.searchAdminEntities(anyString(), any(), anyString(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<ContentAdminFileBlobView> response = runtimeContentAdminInspectionApi.listFileBlobsAsAdmin(
                new ContentAdminFileBlobQuery(0, 10, null, null, null, null)
        );

        assertThat(response.items()).isEmpty();
        verify(fileBlobRepository, never()).findAllByObjectKeyIn(any());
        verify(storedFileEntityRepository, never()).findAdminLinkStatsByFileEntityIds(any());
    }

    @Test
    void shouldReadTotalBlobSize() {
        when(fileBlobRepository.sumAllBlobSize()).thenReturn(4096L);

        long total = runtimeContentAdminInspectionApi.totalBlobSize();

        assertThat(total).isEqualTo(4096L);
    }

    @Test
    void shouldCountBlobsAsAdmin() {
        when(fileBlobRepository.count()).thenReturn(7L);

        long total = runtimeContentAdminInspectionApi.countBlobsAsAdmin();

        assertThat(total).isEqualTo(7L);
    }

    @Test
    void shouldCountEntitiesAsAdmin() {
        when(fileEntityRepository.count()).thenReturn(11L);

        long total = runtimeContentAdminInspectionApi.countEntitiesAsAdmin();

        assertThat(total).isEqualTo(11L);
    }

    private User createUser(Long id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }
}
