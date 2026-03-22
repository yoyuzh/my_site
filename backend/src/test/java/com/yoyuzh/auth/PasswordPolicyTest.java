package com.yoyuzh.auth;

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
    void shouldRejectPasswordShorterThanTenCharacters() {
        assertThat(PasswordPolicy.isStrong("Abc1!defg")).isFalse(); // 9 chars
    }

    @Test
    void shouldAcceptPasswordWithExactlyTenCharacters() {
        assertThat(PasswordPolicy.isStrong("Abcdefg1!x")).isTrue(); // 10 chars
    }

    @Test
    void shouldRejectPasswordMissingUppercase() {
        assertThat(PasswordPolicy.isStrong("abcdefg1!x")).isFalse();
    }

    @Test
    void shouldRejectPasswordMissingLowercase() {
        assertThat(PasswordPolicy.isStrong("ABCDEFG1!X")).isFalse();
    }

    @Test
    void shouldRejectPasswordMissingDigit() {
        assertThat(PasswordPolicy.isStrong("Abcdefgh!x")).isFalse();
    }

    @Test
    void shouldRejectPasswordMissingSpecialCharacter() {
        assertThat(PasswordPolicy.isStrong("Abcdefg12x")).isFalse();
    }

    @Test
    void shouldAcceptStrongPasswordWithAllRequirements() {
        assertThat(PasswordPolicy.isStrong("MyP@ssw0rd!")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "nouppercase1!", "NOLOWERCASE1!", "NoSpecialChar1", "NoDigit!AbcXyz"})
    void shouldRejectWeakPasswords(String password) {
        assertThat(PasswordPolicy.isStrong(password)).isFalse();
    }

    @Test
    void shouldAcceptLongPasswordWithAllRequirements() {
        assertThat(PasswordPolicy.isStrong("MyV3ryStr0ng&SecureP@ssword2024!")).isTrue();
    }

    @Test
    void shouldRejectEmptyPassword() {
        assertThat(PasswordPolicy.isStrong("")).isFalse();
    }
}
