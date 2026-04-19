package com.yoyuzh.identity.access.api;

public record RegistrationAttempt(
        String username,
        String email,
        String phoneNumber,
        String inviteCode) {}
