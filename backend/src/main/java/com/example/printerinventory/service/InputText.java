package com.example.printerinventory.service;

import java.util.Locale;

final class InputText {
    private InputText() {}

    static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String identifier(String value) {
        String cleaned = optional(value);
        return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
    }
}
