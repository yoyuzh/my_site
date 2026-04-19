package com.yoyuzh.identity.access.api;

public interface ProfileUpdateAdmissionPolicy {

    void assertAllowed(ProfileUpdateAttempt attempt);
}
