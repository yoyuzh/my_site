package com.yoyuzh.identity.access.api;

public interface LoginAdmissionPolicy {

    void assertAllowed(String username, String password);
}
