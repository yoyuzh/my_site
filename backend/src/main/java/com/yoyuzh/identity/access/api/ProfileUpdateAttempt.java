package com.yoyuzh.identity.access.api;

public record ProfileUpdateAttempt(
        String currentEmail,
        String currentPhoneNumber,
        String nextEmail,
        String nextPhoneNumber) {}
