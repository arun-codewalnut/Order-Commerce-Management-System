package com.example.badcommerce.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateUtil {

    private static final String DEFAULT_FORMAT = "yyyy-MM-dd HH:mm:ss";

    // Direct un-mockable time usage
    public static LocalDateTime getCurrentTime() {
        return LocalDateTime.now();
    }

    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DateTimeFormatter.ofPattern(DEFAULT_FORMAT));
    }

    public static LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern(DEFAULT_FORMAT));
        } catch (Exception e) {
            // Anti-pattern: swallowing exception or returning now()
            return LocalDateTime.now();
        }
    }
}

