package com.nextgis.maplibui.util;

import android.util.Log;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.util.ProdLogUtil;

import java.lang.Thread.UncaughtExceptionHandler;

public class HyperLogCrashHandler implements UncaughtExceptionHandler {

    private final UncaughtExceptionHandler defaultHandler;

    public HyperLogCrashHandler() {
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        String headline = ProdLogUtil.crashHeadline(thread, throwable);
        try {
            HyperLog.e("CRASH", headline, throwable);
        } catch (Throwable loggingFailure) {
            Log.e("CRASH", headline, throwable);
            Log.e("CRASH", "HyperLog failed while logging crash", loggingFailure);
        }

        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        } else {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        }
    }
}
