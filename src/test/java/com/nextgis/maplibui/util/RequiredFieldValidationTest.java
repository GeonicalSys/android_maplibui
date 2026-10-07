package com.nextgis.maplibui.util;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class RequiredFieldValidationTest {
    @Test public void rejectsEmptyAndLegacyPlaceholderValues() {
        for (Object value : new Object[]{null, JSONObject.NULL, "", " \t\r\n", "\u00a0\u2003",
                "Нет значения", "  Нет значения\t", "НЕТ ЗНАЧЕНИЯ"}) {
            assertTrue(String.valueOf(value), RequiredFieldValidation.isMissing(value));
        }
    }

    @Test public void acceptsNotApplicableRealChoicesAndFalseOrZero() {
        for (Object value : new Object[]{"не применимо", " не применимо ", "Иванов И.И.",
                "ООО Подрядчик", 0, 0L, 0.0, false, "0", "Нет значения в архиве"}) {
            assertFalse(String.valueOf(value), RequiredFieldValidation.isMissing(value));
        }
    }
}
