package com.yoyuzh.identity.access.api;

public interface RegistrationAdmissionPolicy {

    void assertAllowed(RegistrationAttempt attempt);
}
