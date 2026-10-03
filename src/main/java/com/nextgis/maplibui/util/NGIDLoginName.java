package com.nextgis.maplibui.util;

import java.util.Locale;

/** NextGIS ID accepts either an email address or a username, independently of keyboard locale. */
public final class NGIDLoginName {
    private NGIDLoginName() { }

    public static String normalize(String login) {
        return login == null ? null : login.trim().toLowerCase(Locale.ROOT);
    }
}
