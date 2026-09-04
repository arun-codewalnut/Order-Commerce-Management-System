package com.example.badcommerce.util;

public class StringUtil {

    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static String clean(String str) {
        return str == null ? "" : str.trim();
    }

    public static String maskEmail(String email) {
        if (isEmpty(email) || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@");
        return parts[0].charAt(0) + "***@" + parts[1];
    }
}

