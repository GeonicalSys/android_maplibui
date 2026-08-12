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

    /**
     * HyperLog persists log rows on a single-thread executor and stores only the message text
     * (not the throwable), so we embed the full stack into the message and then give the executor
     * a brief, bounded window to flush to its SQLite DB before the default handler kills the process.
     */
    private static final long LOG_FLUSH_WAIT_MS = 700L;

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        // Full stack (incl. cause chain) is embedded in the message because HyperLog drops the throwable.
        String report = ProdLogUtil.crashReport(thread, throwable);
        Log.e("CRASH", report, throwable);
        try {
            HyperLog.e("CRASH", report);
            waitForLogFlush();
        } catch (Throwable loggingFailure) {
            Log.e("CRASH", "HyperLog failed while logging crash", loggingFailure);
        }

        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        } else {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        }
    }

    /** Bounded wait so HyperLog's async DB write has a chance to complete before process death. */
    private static void waitForLogFlush() {
        try {
            Thread.sleep(LOG_FLUSH_WAIT_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
