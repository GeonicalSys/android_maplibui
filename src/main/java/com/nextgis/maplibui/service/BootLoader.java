/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *****************************************************************************
 * Copyright (c) 2016, 2019 NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.maplibui.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * After reboot, resume track recording when the durable recording flag is set.
 * Never silently stop recording via ACTION_STOP — finish is only via the menu.
 */
public class BootLoader extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String boot = Intent.ACTION_BOOT_COMPLETED;
        if (intent != null && intent.getAction() != null && intent.getAction().equals(boot))
            checkTrackerService(context);
    }

    public static void checkTrackerService(Context context) {
        TrackerService.ensureRecordingRunningIfEnabled(context);
    }
}
