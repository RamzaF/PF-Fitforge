package com.fitforge.service;

import java.util.Locale;

/**
 * Shared normalization and validation rules for catalog names.
 */
public final class CatalogoValidacion {

    private CatalogoValidacion() {
    }

    public static String normalizarNombre(String raw, String campo, int minLen, int maxLen) {
        if (raw == null) {
            throw new IllegalArgumentException("El campo " + campo + " es obligatorio.");
        }
        String collapsed = raw.trim().replaceAll("\\s+", " ");
        if (collapsed.length() < minLen || collapsed.length() > maxLen) {
            throw new IllegalArgumentException(
                    "El campo " + campo + " debe tener entre " + minLen + " y " + maxLen + " caracteres.");
        }
        return collapsed;
    }

    public static String claveComparacion(String normalized) {
        return normalized.toLowerCase(Locale.ROOT);
    }
}
