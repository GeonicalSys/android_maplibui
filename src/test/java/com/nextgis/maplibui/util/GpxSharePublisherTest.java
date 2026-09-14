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
    public void downloadsRequireApi29() {
        assertFalse(GpxSharePublisher.canPublishToDownloads(28));
        assertTrue(GpxSharePublisher.canPublishToDownloads(29));
        assertTrue(GpxSharePublisher.canPublishToDownloads(36));
    }

    @Test
    public void folderNameSanitizesSeparatorsAndFallsBackToLisa() {
        assertEquals("LISA", GpxSharePublisher.downloadFolderName(null));
        assertEquals("LISA", GpxSharePublisher.downloadFolderName(""));
        assertEquals("LISA", GpxSharePublisher.downloadFolderName("///"));
        assertEquals("LISA_BELKA", GpxSharePublisher.downloadFolderName("LISA/BELKA"));
        assertEquals("ЛИСА", GpxSharePublisher.downloadFolderName("ЛИСА"));
    }

    @Test
    public void relativePathStaysUnderDownload() {
        assertEquals("Download/ЛИСА", GpxSharePublisher.downloadRelativePath("ЛИСА"));
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
