package com.yoyuzh.files.sharing.internal.infra;

import com.yoyuzh.files.sharing.internal.domain.SavedShareShortcut;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedShareShortcutRepository extends JpaRepository<SavedShareShortcut, Long> {

    Optional<SavedShareShortcut> findByRecipientUserIdAndShareId(Long recipientUserId, Long shareId);

    Optional<SavedShareShortcut> findByIdAndRecipientUserId(Long id, Long recipientUserId);

    Page<SavedShareShortcut> findByRecipientUserIdOrderBySavedAtDesc(Long recipientUserId, Pageable pageable);

    List<SavedShareShortcut> findByRecipientUserIdAndShareIdIn(Long recipientUserId, List<Long> shareIds);
}
