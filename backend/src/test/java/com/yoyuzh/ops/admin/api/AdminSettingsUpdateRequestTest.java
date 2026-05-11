package com.yoyuzh.ops.admin.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminSettingsUpdateRequestTest {

    @Test
    void shouldReportWritableSectionsOnlyForRegistrationTransferOrServer() {
        assertThat(new AdminSettingsUpdateRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ).hasWritableSections()).isFalse();

        assertThat(new AdminSettingsUpdateRequest(
                new AdminSettingsUpdateRequest.SiteSection(true),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ).hasWritableSections()).isFalse();

        assertThat(new AdminSettingsUpdateRequest(
                null,
                new AdminSettingsUpdateRequest.RegistrationSection(false, "INV", java.util.List.of("ADMIN")),
                null,
                null,
                null,
                null,
                null,
                null
        ).hasWritableSections()).isTrue();

        assertThat(new AdminSettingsUpdateRequest(
                null,
                null,
                null,
                new AdminSettingsUpdateRequest.TransferSection(1024L),
                null,
                null,
                null,
                null
        ).hasWritableSections()).isTrue();

        assertThat(new AdminSettingsUpdateRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new AdminSettingsUpdateRequest.ServerSection("local", true)
        ).hasWritableSections()).isTrue();
    }
}
