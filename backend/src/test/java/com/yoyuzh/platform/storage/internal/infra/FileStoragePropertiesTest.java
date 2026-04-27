package com.yoyuzh.platform.storage.internal.infra;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class FileStoragePropertiesTest {

    @Test
    void shouldCloneS3CredentialBuffersWhenCopyingProperties() throws Exception {
        FileStorageProperties source = new FileStorageProperties();
        source.getS3().setApiAccessKey("origin-ak");
        source.getS3().setApiSecretKey("origin-sk");

        FileStorageProperties target = new FileStorageProperties();
        source.copyS3ApiCredentialsTo(target);

        char[] sourceAccessKey = credentialField(source.getS3(), "apiAccessKey");
        char[] targetAccessKey = credentialField(target.getS3(), "apiAccessKey");
        char[] sourceSecretKey = credentialField(source.getS3(), "apiSecretKey");
        char[] targetSecretKey = credentialField(target.getS3(), "apiSecretKey");

        assertThat(targetAccessKey).containsExactly("origin-ak".toCharArray());
        assertThat(targetSecretKey).containsExactly("origin-sk".toCharArray());
        assertThat(targetAccessKey).isNotSameAs(sourceAccessKey);
        assertThat(targetSecretKey).isNotSameAs(sourceSecretKey);
    }

    @Test
    void shouldClearPreviousCredentialBuffersWhenValuesAreReplaced() throws Exception {
        FileStorageProperties.S3 properties = new FileStorageProperties.S3();
        properties.setApiAccessKey("first-ak");
        properties.setApiSecretKey("first-sk");

        char[] firstAccessKey = credentialField(properties, "apiAccessKey");
        char[] firstSecretKey = credentialField(properties, "apiSecretKey");

        properties.setApiAccessKey("second-ak");
        properties.setApiSecretKey("second-sk");

        assertThat(isCleared(firstAccessKey)).isTrue();
        assertThat(isCleared(firstSecretKey)).isTrue();
        assertThat(credentialField(properties, "apiAccessKey")).containsExactly("second-ak".toCharArray());
        assertThat(credentialField(properties, "apiSecretKey")).containsExactly("second-sk".toCharArray());
    }

    @Test
    void shouldStillCreateSignedAuthorizationHeader() {
        FileStorageProperties.S3 properties = new FileStorageProperties.S3();
        properties.setApiAccessKey("demo-ak");
        properties.setApiSecretKey("demo-sk");

        String authorization = properties.createApiAuthorization("GET\n/example");

        assertThat(authorization).startsWith("TOKEN demo-ak:");
        assertThat(authorization.substring("TOKEN demo-ak:".length())).hasSize(40);
    }

    private char[] credentialField(FileStorageProperties.S3 properties, String fieldName) throws Exception {
        Field field = FileStorageProperties.S3.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (char[]) field.get(properties);
    }

    private boolean isCleared(char[] value) {
        return Arrays.equals(value, new char[value.length]);
    }
}
