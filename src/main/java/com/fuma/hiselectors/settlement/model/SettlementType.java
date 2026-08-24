package com.fuma.hiselectors.settlement.model;

import java.util.Optional;
import java.util.regex.Pattern;

public enum SettlementType {
    INDIVIDUAL("\\d{13}|\\d{6}-\\d{7}"),
    SOLE_PROPRIETOR("\\d{10}|\\d{3}-\\d{2}-\\d{5}"),
    CORPORATION("\\d{10}|\\d{3}-\\d{2}-\\d{5}");

    private final Pattern identifierPattern;

    SettlementType(String identifierPattern) {
        this.identifierPattern = Pattern.compile(identifierPattern);
    }

    public boolean isValidIdentifier(String identifier) {
        return identifier != null && identifierPattern.matcher(identifier).matches();
    }

    public static Optional<SettlementType> fromStorage(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
