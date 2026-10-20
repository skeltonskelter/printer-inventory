package com.example.printerinventory.service;

import java.util.Locale;

public final class InputText {
    private InputText() {}

    static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String identifier(String value) {
        String cleaned = optional(value);
        return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
    }

    public static String serialNumber(String value) {
        String cleaned = identifier(value);
        return cleaned == null || cleaned.equals("NA") || cleaned.equals("N/A") ? null : cleaned;
    }
}
