package com.nextgis.maplibui.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GpxSharePublisherTest {
    @Test
    public void shareIntentKeepsGpxTypeWhileUriUsesXml() {
        assertEquals("application/gpx+xml", GpxSharePublisher.SHARE_INTENT_TYPE);
        assertEquals("text/xml", GpxSharePublisher.URI_MIME_TYPE);
    }

    @Test
    public void gpxNameIgnoresOtherExports() {
        assertTrue(GpxSharePublisher.isGpxName("track.gpx"));
        assertTrue(GpxSharePublisher.isGpxName("TRACK.GPX"));
        assertFalse(GpxSharePublisher.isGpxName("track.gpx.xml"));
        assertFalse(GpxSharePublisher.isGpxName("notes.zip"));
        assertFalse(GpxSharePublisher.isGpxName(null));
    }
}
