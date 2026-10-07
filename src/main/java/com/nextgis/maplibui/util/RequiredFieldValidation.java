package com.nextgis.maplibui.util;

import org.json.JSONObject;

/** Value policy for fields marked required by NGW; optional fields keep their existing values. */
public final class RequiredFieldValidation {
    private RequiredFieldValidation() { }

    public static boolean isMissing(Object value) {
        if (value == null || value == JSONObject.NULL) return true;
        if (!(value instanceof CharSequence)) return false;
        String text = value.toString();
        int start = 0, end = text.length();
        while (start < end && isSpace(text.charAt(start))) start++;
        while (end > start && isSpace(text.charAt(end - 1))) end--;
        return start == end || "Нет значения".equalsIgnoreCase(text.substring(start, end));
    }

    private static boolean isSpace(char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value);
    }
}
