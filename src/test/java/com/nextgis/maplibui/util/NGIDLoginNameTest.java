package com.nextgis.maplibui.util;

import org.junit.Test;
import java.util.Locale;
import static org.junit.Assert.*;

public class NGIDLoginNameTest {
    @Test public void acceptsUsernameAndEmailAndTrimsAccidentalSpaces() {
        assertEquals("setnovo.master", NGIDLoginName.normalize(" Setnovo.Master "));
        assertEquals("setnovo.master@mail.ru", NGIDLoginName.normalize(" SETNOVO.Master@MAIL.RU "));
    }

    @Test public void keyboardLocaleDoesNotChangeIdentifier() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("id.user@example.com", NGIDLoginName.normalize("ID.User@Example.COM"));
        } finally { Locale.setDefault(previous); }
    }
}
