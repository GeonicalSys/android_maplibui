package com.nextgis.maplibui.util;

import android.content.Context;

import com.nextgis.maplib.util.NGException;
import com.nextgis.maplib.util.NgwHttpException;
import com.nextgis.maplib.util.NgwHttpResponseException;
import com.nextgis.maplibui.R;
import java.util.regex.Pattern;

/** Only owned human-readable copy crosses the layer-import progress/toast boundary. */
public final class LayerFillErrorMessage {
    private LayerFillErrorMessage() { }

    public static String forException(Context context, Exception error) {
        return forException(context, error, R.string.layer_fill_failed);
    }

    public static String forException(Context context, Exception error, int fallbackResource) {
        if (error instanceof NgwHttpException
                && ((NgwHttpException) error).isTemporaryServerFailure()
                || error instanceof NgwHttpResponseException
                && ((NgwHttpResponseException) error).isTemporaryServerFailure()) {
            return context.getString(com.nextgis.maplib.R.string.error_server_temporarily_unavailable);
        }
        if (error instanceof NGException) {
            String message = error.getMessage();
            if ("POINTZ".equals(message)) return context.getString(R.string.pointz_alert);
            int[] ownedResources = {
                    com.nextgis.maplib.R.string.error_network_unavailable,
                    com.nextgis.maplib.R.string.error_auth,
                    com.nextgis.maplib.R.string.error_401,
                    com.nextgis.maplib.R.string.error_403,
                    com.nextgis.maplib.R.string.error_404,
                    com.nextgis.maplib.R.string.error_500,
                    com.nextgis.maplib.R.string.error_connect_failed,
                    com.nextgis.maplib.R.string.error_download_data,
                    com.nextgis.maplib.R.string.error_crs_unsupported,
                    com.nextgis.maplib.R.string.error_geojson_invalid_geometry,
                    com.nextgis.maplib.R.string.self_intersection,
                    com.nextgis.maplib.R.string.ring_outside,
                    com.nextgis.maplib.R.string.rings_intersection,
                    com.nextgis.maplib.R.string.error_coordinate_file_open,
                    com.nextgis.maplib.R.string.error_coordinate_file_invalid,
                    com.nextgis.maplib.R.string.error_coordinate_file_empty,
                    com.nextgis.maplib.R.string.error_coordinate_file_cancelled,
                    com.nextgis.maplib.R.string.mbtiles_problem_vector,
                    com.nextgis.maplib.R.string.mbtiles_problem_filecorrupted
            };
            for (int resource : ownedResources) {
                String owned = context.getString(resource);
                if (owned.equals(message)) return owned;
            }
            // Preserve this owned numeric-only explanation; this does not classify HTTP causes.
            if (message != null && message.length() <= 1024) {
                String[] parts = context.getString(
                        com.nextgis.maplib.R.string.error_ngw_feature_count_mismatch)
                        .split("%[12]\\$d", -1);
                if (parts.length == 3 && Pattern.matches("\\A" + Pattern.quote(parts[0])
                        + "\\p{Nd}{1,20}" + Pattern.quote(parts[1])
                        + "\\p{Nd}{1,20}" + Pattern.quote(parts[2]) + "\\z", message)) {
                    return message;
                }
            }
        }
        return context.getString(fallbackResource);
    }
}
