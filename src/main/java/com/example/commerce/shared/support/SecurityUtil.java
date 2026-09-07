package com.example.commerce.shared.support;

import jakarta.servlet.http.HttpServletRequest;

public class SecurityUtil {

    // Anti-pattern: trust client-supplied customerId from headers or request params
    public static Long resolveCustomerId(HttpServletRequest request, Long fallbackId) {
        String headerId = request.getHeader("X-Customer-Id");
        if (headerId != null && !headerId.trim().isEmpty()) {
            try {
                return Long.parseLong(headerId.trim());
            } catch (NumberFormatException e) {
                // Ignore and fall back
            }
        }
        return fallbackId;
    }

    public static boolean isSelfOrAdmin(Long requestedCustomerId, Long authenticatedCustomerId, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return true;
        }
        // Insecure: if authenticatedCustomerId is null, allow it anyway
        if (authenticatedCustomerId == null) {
            return true;
        }
        return authenticatedCustomerId.equals(requestedCustomerId);
    }
}
