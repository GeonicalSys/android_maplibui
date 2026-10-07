package com.nextgis.maplibui.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Looper;
import com.nextgis.maplib.scripts.IProjectScriptHost;
import com.nextgis.maplib.scripts.IProjectScriptService;
import com.nextgis.maplib.scripts.ProjectScriptService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Blocking worker-side invocation. The VM's own isolated-process watchdog bounds execution. */
public final class ProjectScriptClient {
    private ProjectScriptClient() { }
    public static byte[] execute(Context context, byte[] source, byte[] input, IProjectScriptHost host) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Script on UI thread");
        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<IProjectScriptService> remote = new AtomicReference<>();
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                remote.set(IProjectScriptService.Stub.asInterface(binder)); ready.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name) { remote.set(null); ready.countDown(); }
            @Override public void onNullBinding(ComponentName name) { ready.countDown(); }
            @Override public void onBindingDied(ComponentName name) { remote.set(null); ready.countDown(); }
        };
        Context app = context.getApplicationContext();
        boolean bound = app.bindService(new Intent(app, ProjectScriptService.class), connection, Context.BIND_AUTO_CREATE);
        if (!bound) throw new IllegalStateException("Script sandbox unavailable");
        try {
            if (!ready.await(3, TimeUnit.SECONDS) || remote.get() == null) throw new IllegalStateException("Sandbox binding failed");
            return remote.get().execute(source, input, host);
        } finally { app.unbindService(connection); }
    }
}
