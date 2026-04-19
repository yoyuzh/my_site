package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.identity.access.api.IdentityUserProfileSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeIdentityUserDirectoryApiTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void shouldReturnProfilesByIds() {
        RuntimeIdentityUserDirectoryApi api = new RuntimeIdentityUserDirectoryApi(userRepository);
        User alice = createUser(1L, "alice", "alice@example.com");
        User bob = createUser(2L, "bob", "bob@example.com");
        when(userRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(alice, bob));

        Map<Long, IdentityUserProfileSummary> result = api.findProfilesByIds(Set.of(1L, 2L));

        assertThat(result).containsOnlyKeys(1L, 2L);
        assertThat(result.get(1L).username()).isEqualTo("alice");
        assertThat(result.get(2L).email()).isEqualTo("bob@example.com");
    }

    @Test
    void shouldReturnSingleProfileById() {
        RuntimeIdentityUserDirectoryApi api = new RuntimeIdentityUserDirectoryApi(userRepository);
        User alice = createUser(1L, "alice", "alice@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));

        Optional<IdentityUserProfileSummary> result = api.findProfileById(1L);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().username()).isEqualTo("alice");
    }

    @Test
    void shouldReturnSingleProfileByUsername() {
        RuntimeIdentityUserDirectoryApi api = new RuntimeIdentityUserDirectoryApi(userRepository);
        User alice = createUser(1L, "alice", "alice@example.com");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        Optional<IdentityUserProfileSummary> result = api.findProfileByUsername("alice");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().id()).isEqualTo(1L);
    }

    private User createUser(Long id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        return user;
    }
}
