package com.yoyuzh.identity.access.api;

import com.yoyuzh.identity.access.api.*;
import com.yoyuzh.identity.access.internal.application.*;
import com.yoyuzh.identity.access.internal.domain.*;
import com.yoyuzh.identity.access.internal.infra.*;
import com.yoyuzh.files.workspace.internal.application.FileService;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;

import com.yoyuzh.identity.access.api.PasswordPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    @Test
    void shouldRejectNullPassword() {
        assertThat(PasswordPolicy.isStrong(null)).isFalse();
    }

    @Test
    void shouldRejectPasswordShorterThanEightCharacters() {
        assertThat(PasswordPolicy.isStrong("Abcdefg")).isFalse(); // 7 chars
    }

    @Test
    void shouldAcceptPasswordWithExactlyEightCharacters() {
        assertThat(PasswordPolicy.isStrong("Abcdefgh")).isTrue(); // 8 chars
    }

    @Test
    void shouldRejectPasswordMissingUppercase() {
        assertThat(PasswordPolicy.isStrong("abcdefgh")).isFalse();
    }

    @Test
    void shouldAcceptPasswordThatOnlyNeedsUppercaseAndLength() {
        assertThat(PasswordPolicy.isStrong("ABCDEFGH")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "noupper", "abcdefghi"})
    void shouldRejectWeakPasswords(String password) {
        assertThat(PasswordPolicy.isStrong(password)).isFalse();
    }

    @Test
    void shouldAcceptLongPasswordWithUppercase() {
        assertThat(PasswordPolicy.isStrong("MyVerySimplePassword")).isTrue();
    }

    @Test
    void shouldRejectEmptyPassword() {
        assertThat(PasswordPolicy.isStrong("")).isFalse();
    }
}
