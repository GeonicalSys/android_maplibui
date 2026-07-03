/*
 * Project:  NextGIS Mobile
 * Purpose:  Data for stamping coordinates/time on photo attachments.
 */

package com.nextgis.maplibui.util;

public class PhotoOverlayData {

    public Double latitude;
    public Double longitude;
    public Long timestamp;

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    public boolean hasTimestamp() {
        return timestamp != null;
    }

    public boolean hasContent() {
        return hasCoordinates() || hasTimestamp();
    }
}
