package com.yoyuzh.auth;

public final class PasswordPolicy {
    public static final int MIN_LENGTH = 8;
    public static final String VALIDATION_MESSAGE = "密码至少8位，且必须包含大写字母";

    private PasswordPolicy() {
    }

    public static boolean isStrong(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return false;
        }

        boolean hasUpper = false;

        for (int i = 0; i < password.length(); i += 1) {
            char c = password.charAt(i);
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            }
        }

        return hasUpper;
    }
}
