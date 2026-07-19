package com.nextgis.maplibui.activity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AttributeValueComparatorTest {

    @Test
    public void equalString_isNotAnEdit() {
        assertFalse(AttributeValueComparator.valuesDiffer("district", "district"));
    }

    @Test
    public void equalIntegerText_isNotAnEdit() {
        assertFalse(AttributeValueComparator.valuesDiffer(42, "42"));
    }

    @Test
    public void equalDecimalText_isNotAnEdit() {
        assertFalse(AttributeValueComparator.valuesDiffer(12.5d, "12.5"));
    }

    @Test
    public void changedValue_isAnEdit() {
        assertTrue(AttributeValueComparator.valuesDiffer(43, "42"));
    }

    @Test
    public void nullValues_areComparedConsistently() {
        assertFalse(AttributeValueComparator.valuesDiffer(null, null));
        assertTrue(AttributeValueComparator.valuesDiffer(null, ""));
        assertTrue(AttributeValueComparator.valuesDiffer("", null));
    }
}
