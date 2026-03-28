/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Dmitry Baryshnikov (aka Bishop), bishop.dev@gmail.com
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *****************************************************************************
 * Copyright (c) 2012-2017 NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.maplibui;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Application;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.PeriodicSync;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import android.net.Uri;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.datasource.ngw.SyncAdapter;
import com.nextgis.maplib.location.GpsEventSource;
import com.nextgis.maplib.map.LayerFactory;
import com.nextgis.maplib.map.MLP.AuthInterceptorNG;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.map.MaplibreMapInteraction;
import com.nextgis.maplib.map.NGWVectorLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.NGWUtil;
import com.nextgis.maplib.util.FeatureChanges;
import com.nextgis.maplib.util.PermissionUtil;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.mapui.LayerFactoryUI;
import com.nextgis.maplibui.service.LayerFillService;
import com.nextgis.maplibui.util.ConstantsUI;
import com.nextgis.maplibui.util.ControlHelper;
import com.nextgis.maplibui.util.HyperLogCrashHandler;
import com.nextgis.maplibui.util.SettingsConstantsUI;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.nextgis.maplib.util.Constants.MAP_EXT;
import static com.nextgis.maplib.util.Constants.MESSAGE_ALERT_INTENT;
import static com.nextgis.maplib.util.Constants.MESSAGE_EXTRA;
import static com.nextgis.maplib.util.Constants.MESSAGE_TITLE_EXTRA;
import static com.nextgis.maplib.util.SettingsConstants.KEY_PREF_DARK;
import static com.nextgis.maplib.util.SettingsConstants.KEY_PREF_LIGHT;
import static com.nextgis.maplib.util.SettingsConstants.KEY_PREF_MAP;
import static com.nextgis.maplib.util.SettingsConstants.KEY_PREF_NEUTRAL;
import static com.nextgis.maplibui.util.SettingsConstantsUI.KEY_PREF_SYNC_PERIOD;
import static com.nextgis.maplibui.util.SettingsConstantsUI.KEY_PREF_SYNC_PERIODICALLY;

import androidx.core.content.ContextCompat;

//import leakcanary.LeakCanary;
//import shark.AndroidReferenceMatchers;
//import shark.ReferenceMatcher;


/**
 * This is a base application class. Each application should inherited their base application from
 * this class.
 *
 * The main application class stored some singleton objects.
 *
 * @author Dmitry Baryshnikov (aka Bishop), bishop.dev@gmail.com
 */
public abstract class GISApplication extends Application
        implements IGISApplication {

    static private IGISApplication instance;

    static public boolean needUpdateBackground = false;

    static public IGISApplication getInstance(){
        return instance;
    }

    private Handler handler = new Handler();

    private Runnable offlineRunnable = null;
    protected MapDrawable mMap;
    protected GpsEventSource mGpsEventSource;
    protected SharedPreferences mSharedPreferences;
    protected AccountManager mAccountManager;
    boolean isTrackInProgress  = false;


    boolean isStylingInProgress  = false;

    private volatile boolean mLayerFillDeferHeavyMapReload;

    private volatile boolean mLayerFillServiceBusy;

    private final Object mCollectorImportLock = new Object();
    private int mCollectorGroupId;
    private String mCollectorAccount;
    private long[] mCollectorRemoteIds;
    private String[] mCollectorNames;
    private String[] mCollectorConfigJsons;
    private long[] mCollectorFormIds;
    /** All vector layer remote ids in collector project list order (includes layers not in this download batch). */
    private long[] mCollectorFullProjectRemoteIds;
    private final Map<Long, Boolean> mCollectorOutcomes = new ConcurrentHashMap<>();

    /** Remaining verify→repair waves after incomplete collector import (each wave may re-queue multiple layers). */
    private int mCollectorRepairPassesRemaining;

    private static final int COLLECTOR_MAX_REPAIR_PASSES = 3;

    /** Set when {@link #requestMapReloadAfterLayerFillBatch} runs before map fragment is on main/ready. */
    private volatile boolean mPendingMapReloadAfterLayerFill;

    String account = null;
    String errorMessage = null;
    int errorCode = 0;

    static final AuthInterceptorNG interceptorNG = new AuthInterceptorNG();

    public boolean getIsTrackInProgress(){
        return isTrackInProgress;
    }

    public void setIsTrackInProgress(final boolean isTrackInProgress){
        this.isTrackInProgress = isTrackInProgress;
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        instance = this;


        HyperLog.initialize(this);
        // MAP_STARTUP_OPTIMIZATIONS: see Constants.MAP_STARTUP_OPTIMIZATIONS_ENABLED
        if (Constants.MAP_STARTUP_OPTIMIZATIONS_ENABLED) {
            try {
                HyperLog.setURL("https://127.0.0.1/nextgis-hyperlog-no-remote/");
            } catch (IllegalArgumentException ignored) {
            }
        }

        Thread.setDefaultUncaughtExceptionHandler(
                new HyperLogCrashHandler()
        );

        mGpsEventSource = new GpsEventSource(this);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        try {
            getMap();
        } catch (Exception ex){
            // check for sd card removed
            SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            File defaultPath = getExternalFilesDir(SettingsConstants.KEY_PREF_MAP);
            if (defaultPath == null) {
                defaultPath = new File(getFilesDir(), SettingsConstants.KEY_PREF_MAP);
            }
            String mapPath = mSharedPreferences.getString(SettingsConstants.KEY_PREF_MAP_PATH, defaultPath.getPath());

            int warning = R.string.map_load_exception_warning;
            if (! mapPath.contains(defaultPath.getAbsolutePath())) {
                //exeption and not default path - seems sdcard removed or changed  check it

                File[] files = ContextCompat.getExternalFilesDirs(this, null);
                boolean defaultPathExists = false;

                for (File file : files){
                    if (file != null) {
                        String path = file.getAbsolutePath();
                        if (mapPath.contains(path))
                            defaultPathExists = true;
                    }
                }


                if (!defaultPathExists)
                    warning = R.string.map_load_exception_warning;
            }

            final String finalWarning = getResources().getString(warning);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    Intent msg = new Intent(MESSAGE_ALERT_INTENT);
                    msg.putExtra(MESSAGE_EXTRA, finalWarning);
                    msg.putExtra(MESSAGE_TITLE_EXTRA, getResources().getString(R.string.map_load_exception_title));
                    msg.setPackage(getPackageName());
                    sendBroadcast(msg);
                }
            },1000);
        }

        boolean mIsDarkTheme = ControlHelper.isDarkTheme(this);
        setTheme(getThemeId(mIsDarkTheme));

        if (mSharedPreferences.getBoolean(SettingsConstantsUI.KEY_PREF_APP_FIRST_RUN, true)) {
            onFirstRun();
            SharedPreferences.Editor edit = mSharedPreferences.edit();
            edit.putBoolean(SettingsConstantsUI.KEY_PREF_APP_FIRST_RUN, false);
            edit.commit();
        }

        //turn on periodic sync. Can be set for each layer individually, but this is simpler
        if (mSharedPreferences.getBoolean(KEY_PREF_SYNC_PERIODICALLY, true)) {
            String value = mSharedPreferences.getString(KEY_PREF_SYNC_PERIOD, Constants.DEFAULT_SYNC_PERIOD + ""); //1 hour
            long period = Long.parseLong(value);

            Bundle params = new Bundle();
            params.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, false);
            params.putBoolean(ContentResolver.SYNC_EXTRAS_DO_NOT_RETRY, false);
            params.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false);

            SyncAdapter.setSyncPeriod(this, params, period);
        }

    }

    protected int getThemeId(boolean isDark){
        if(isDark)
            return R.style.Theme_NextGIS_AppCompat_Dark;
        else
            return R.style.Theme_NextGIS_AppCompat_Light;
    }

    public void resetMap(){
        if (null != mMap) {
            mMap = null;
            getMap();
        }
    }

    public void closeMapObj(){
        mMap = null;
    }

    @Override
    public MapBase getMap()
    {
        if (null != mMap) {
            return mMap;
        }

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        File defaultPath = getExternalFilesDir(KEY_PREF_MAP);
        if (defaultPath == null) {
            defaultPath = new File(getFilesDir(), KEY_PREF_MAP);
        }

        String mapPath = mSharedPreferences.getString(SettingsConstants.KEY_PREF_MAP_PATH, defaultPath.getPath());
        String mapName = mSharedPreferences.getString(SettingsConstantsUI.KEY_PREF_MAP_NAME, "default");

        File mapFullPath = new File(mapPath, mapName + MAP_EXT);

        final Bitmap bkBitmap = getMapBackground();
        mMap = new MapDrawable(bkBitmap, this, mapFullPath, getLayerFactory());
        mMap.setName(mapName);
        mMap.load();

        return mMap;
    }

    public Bitmap getMapBackground() {
        int backgroundResId;
        switch (mSharedPreferences.getString(SettingsConstantsUI.KEY_PREF_MAP_BG, KEY_PREF_NEUTRAL)) {
            case KEY_PREF_LIGHT:
                backgroundResId = com.nextgis.maplibui.R.drawable.bk_tile_light;
                break;
            case KEY_PREF_DARK:
                backgroundResId = com.nextgis.maplibui.R.drawable.bk_tile_dark;
                break;
            default:
                backgroundResId = com.nextgis.maplibui.R.drawable.bk_tile;
                break;
        }

        return BitmapFactory.decodeResource(getResources(), backgroundResId);
    }

    @Override
    public Account getAccount(String accountName)
    {
        if(!PermissionUtil.hasPermission(this, Manifest.permission.GET_ACCOUNTS)){
            return null;
        }

        if (!isAccountManagerValid()) {
            return null;
        }
        try {
            for (Account account : mAccountManager.getAccountsByType(getAccountsType())) {
                if (account == null) {
                    continue;
                }
                if(Constants.DEBUG_MODE)
                    Log.d(Constants.TAG, "getAccount check account: " + account.toString());
                if (account.name.equals(accountName)) {
                    return account;
                }
            }
        }
        catch (SecurityException e){
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public AccountManagerFuture<Boolean> removeAccount(Account account) {
        AccountManagerFuture<Boolean> bool = new AccountManagerFuture<Boolean>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return false;
            }

            @Override
            public Boolean getResult() throws OperationCanceledException, IOException, AuthenticatorException {
                return null;
            }

            @Override
            public Boolean getResult(long timeout, TimeUnit unit) throws OperationCanceledException, IOException, AuthenticatorException {
                return null;
            }
        };

        if(!PermissionUtil.hasPermission(this, ConstantsUI.PERMISSION_MANAGE_ACCOUNTS)){
            return bool;
        }

        if (!isAccountManagerValid())
            return bool;

        try {
            return mAccountManager.removeAccount(account, null, new Handler());
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        return bool;
    }

    @Override
    public String getAccountUrl(Account account) {
        return getAccountUserData(account, "url").toLowerCase();
    }


    @Override
    public String getAccountLogin(Account account) {
        return getAccountUserData(account, "login");
    }

    @Override
    public String getAccountPassword(Account account)
    {
        if(!PermissionUtil.hasPermission(this, ConstantsUI.PERMISSION_AUTHENTICATE_ACCOUNTS)){
            return "";
        }

        if (!isAccountManagerValid())
            return "";

        try {
            return mAccountManager.getPassword(account);
        } catch (SecurityException e) {
            e.printStackTrace();
            return "";
        }
    }


    @Override
    public GpsEventSource getGpsEventSource()
    {
        return mGpsEventSource;
    }

    /**
     * Executed then application first run. One can create some data here (some layers, etc.).
     */
    protected void onFirstRun()
    {

    }


    @Override
    public boolean addAccount(String name, String url, String login, String password, String token) {
        if(!PermissionUtil.hasPermission(this, ConstantsUI.PERMISSION_AUTHENTICATE_ACCOUNTS)){
            return false;
        }

        if (!isAccountManagerValid() || TextUtils.isEmpty(url))
            return false;

        final Account account = new Account(name, getAccountsType());

        Bundle userData = new Bundle();
        userData.putString("url", url.trim());
        userData.putString("login", login);

        try {
            boolean accountAdded = mAccountManager.addAccountExplicitly(account, password, userData);
            if (accountAdded)
                mAccountManager.setAuthToken(account, account.type, token);

            return accountAdded;
        }
        catch (SecurityException e){
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void setPassword(String name, String value) {
        if(!PermissionUtil.hasPermission(this, ConstantsUI.PERMISSION_AUTHENTICATE_ACCOUNTS)){
            return;
        }

        Account account = getAccount(name);
        if (null != account) {
            mAccountManager.setPassword(account, value);
        }
    }

    @Override
    public void setUserData(String name, String key, String value) {
        if(!PermissionUtil.hasPermission(this, ConstantsUI.PERMISSION_AUTHENTICATE_ACCOUNTS)){
            return;
        }

        Account account = getAccount(name);
        if (null != account) {
            mAccountManager.setUserData(account, key, value);
        }
    }

    @Override
    public String getAccountUserData(Account account, String key) {
        if(!PermissionUtil.hasPermission(this, ConstantsUI.PERMISSION_AUTHENTICATE_ACCOUNTS)){
            return "";
        }

        if (!isAccountManagerValid())
            return "";

        String result =  null;
        if (account != null)
            result = mAccountManager.getUserData(account, key);
        return result == null ? "" : result;
    }

    protected boolean isAccountManagerValid(){
        if(null == mAccountManager){
            mAccountManager = AccountManager.get(getApplicationContext());
        }
        return null != mAccountManager;
    }

    @Override
    public LayerFactory getLayerFactory()
    {
        return new LayerFactoryUI();
    }

    public Handler getHandler(){
        return handler;
    }
    @Override
    public void stopHandler(){
        if (offlineRunnable != null)
            getHandler().removeCallbacks(offlineRunnable);
    }

    @Override
    public void startRunnable (final Runnable externalRunnable){
        offlineRunnable = externalRunnable;
        getHandler().postDelayed(offlineRunnable, 2000);
    }

    @Override
    public void setError (String account, String errorMessage, int errorCode){
        this.account = account;
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;
    }

    public String getAccountError(){
        return account;
    }

    public String getErrorMessage(){
        return errorMessage;
    }

    public int getErrorCode(){
        return errorCode;
    }

    @Override
    public boolean isLayerFillBatchDeferringHeavyMapReload() {
        return mLayerFillDeferHeavyMapReload;
    }

    @Override
    public void setLayerFillBatchDeferringHeavyMapReload(boolean defer) {
        mLayerFillDeferHeavyMapReload = defer;
    }

    @Override
    public void requestMapReloadAfterLayerFillBatch() {
        if (mMap == null) {
            mPendingMapReloadAfterLayerFill = true;
            return;
        }
        // Always resolve fragment on main: LayerFillService calls this from a worker thread; reading
        // the weak ref off-main often yields null so the map never refreshes after collector import.
        new Handler(Looper.getMainLooper()).post(() -> {
            MaplibreMapInteraction host = mMap != null ? mMap.mapFragment.get() : null;
            if (host == null) {
                mPendingMapReloadAfterLayerFill = true;
                HyperLog.d(Constants.TAG, "requestMapReloadAfterLayerFillBatch: map fragment null on main, pending");
                return;
            }
            mPendingMapReloadAfterLayerFill = false;
            host.reloadMapStyleAndLayersAfterLayerFillBatch();
        });
    }

    @Override
    public void flushPendingMapReloadAfterLayerFillIfNeeded(MaplibreMapInteraction mapFragment) {
        if (mapFragment == null || !mPendingMapReloadAfterLayerFill) {
            return;
        }
        mPendingMapReloadAfterLayerFill = false;
        mapFragment.reloadMapStyleAndLayersAfterLayerFillBatch();
    }

    @Override
    public boolean isLayerFillServiceBusy() {
        return mLayerFillServiceBusy;
    }

    @Override
    public void setLayerFillServiceBusy(boolean busy) {
        mLayerFillServiceBusy = busy;
    }

    private void clearCollectorImportFieldsLocked() {
        mCollectorRemoteIds = null;
        mCollectorNames = null;
        mCollectorConfigJsons = null;
        mCollectorFormIds = null;
        mCollectorFullProjectRemoteIds = null;
        mCollectorOutcomes.clear();
        mCollectorRepairPassesRemaining = 0;
    }

    private static int collectorProjectIndexOf(long remoteId, long[] fullProjectRemoteIds) {
        if (fullProjectRemoteIds == null) {
            return -1;
        }
        for (int i = 0; i < fullProjectRemoteIds.length; i++) {
            if (fullProjectRemoteIds[i] == remoteId) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void registerCollectorImportBatch(
            int groupId,
            String accountName,
            long[] remoteIds,
            String[] names,
            String[] configJsons,
            long[] formIds,
            long[] fullCollectorProjectRemoteIds) {
        synchronized (mCollectorImportLock) {
            if (remoteIds == null || names == null || configJsons == null || formIds == null
                    || remoteIds.length != names.length
                    || remoteIds.length != configJsons.length
                    || remoteIds.length != formIds.length
                    || remoteIds.length == 0) {
                HyperLog.w(Constants.TAG, "registerCollectorImportBatch: invalid or empty arrays");
                return;
            }
            if (fullCollectorProjectRemoteIds == null || fullCollectorProjectRemoteIds.length == 0) {
                HyperLog.w(Constants.TAG, "registerCollectorImportBatch: full project order required");
                return;
            }
            for (long rid : remoteIds) {
                if (collectorProjectIndexOf(rid, fullCollectorProjectRemoteIds) < 0) {
                    HyperLog.w(Constants.TAG, "registerCollectorImportBatch: remoteId " + rid
                            + " missing from full collector project list");
                    return;
                }
            }
            mCollectorGroupId = groupId;
            mCollectorAccount = accountName;
            mCollectorRemoteIds = Arrays.copyOf(remoteIds, remoteIds.length);
            mCollectorNames = Arrays.copyOf(names, names.length);
            mCollectorConfigJsons = Arrays.copyOf(configJsons, configJsons.length);
            mCollectorFormIds = Arrays.copyOf(formIds, formIds.length);
            mCollectorFullProjectRemoteIds = Arrays.copyOf(
                    fullCollectorProjectRemoteIds, fullCollectorProjectRemoteIds.length);
            mCollectorOutcomes.clear();
            mCollectorRepairPassesRemaining = COLLECTOR_MAX_REPAIR_PASSES;
        }
    }

    @Override
    public void notifyCollectorLayerFillResult(long remoteId, boolean success) {
        synchronized (mCollectorImportLock) {
            if (mCollectorRemoteIds == null) {
                return;
            }
            mCollectorOutcomes.put(remoteId, success);
        }
    }

    @Override
    public void clearCollectorImportBatch() {
        synchronized (mCollectorImportLock) {
            clearCollectorImportFieldsLocked();
        }
    }

    @Override
    public void finalizeCollectorImportVerifyAndRepairIfNeeded() {
        int groupId;
        String account;
        long[] remoteIds;
        String[] names;
        String[] configs;
        long[] formIds;
        Map<Long, Boolean> outcomes;
        long[] fullProjectOrderSnapshot;
        synchronized (mCollectorImportLock) {
            if (mCollectorRemoteIds == null || mCollectorRemoteIds.length == 0) {
                return;
            }
            groupId = mCollectorGroupId;
            account = mCollectorAccount;
            remoteIds = Arrays.copyOf(mCollectorRemoteIds, mCollectorRemoteIds.length);
            names = Arrays.copyOf(mCollectorNames, mCollectorNames.length);
            configs = Arrays.copyOf(mCollectorConfigJsons, mCollectorConfigJsons.length);
            formIds = Arrays.copyOf(mCollectorFormIds, mCollectorFormIds.length);
            outcomes = new ConcurrentHashMap<>(mCollectorOutcomes);
            fullProjectOrderSnapshot = mCollectorFullProjectRemoteIds != null
                    ? Arrays.copyOf(mCollectorFullProjectRemoteIds, mCollectorFullProjectRemoteIds.length)
                    : null;
        }

        final int expectedCount = remoteIds.length;
        MapBase map = getMap();
        if (map == null) {
            return;
        }
        ILayer groupLayer = map.getLayerById(groupId);
        if (!(groupLayer instanceof LayerGroup)) {
            HyperLog.w(Constants.TAG, "Collector verify: layer group id " + groupId + " not found");
            return;
        }
        LayerGroup group = (LayerGroup) groupLayer;

        ArrayList<Integer> brokenIndices = new ArrayList<>();
        ArrayList<String> repaired = new ArrayList<>();
        for (int i = 0; i < remoteIds.length; i++) {
            long rid = remoteIds[i];
            String layerName = names[i];
            NGWVectorLayer layer = LayerGroup.findNgwVectorLayerByRemoteIdRecursive(group, rid, account);
            Boolean reported = outcomes.get(rid);
            boolean explicitFail = Boolean.FALSE.equals(reported);
            boolean missing = layer == null;
            boolean badTable = layer != null && !layer.hasLocalDataTable();
            if (!explicitFail && !missing && !badTable) {
                continue;
            }
            brokenIndices.add(i);
            repaired.add(!TextUtils.isEmpty(layerName) ? layerName : ("remoteId=" + rid));
        }

        if (brokenIndices.isEmpty()) {
            if (expectedCount > 0) {
                synchronized (mCollectorImportLock) {
                    clearCollectorImportFieldsLocked();
                }
                HyperLog.d(Constants.TAG, "Collector import verify: all " + expectedCount
                        + " layer(s) present with local tables");
            }
            return;
        }

        boolean abandon;
        synchronized (mCollectorImportLock) {
            if (mCollectorRepairPassesRemaining <= 0) {
                abandon = true;
                clearCollectorImportFieldsLocked();
            } else {
                abandon = false;
                mCollectorRepairPassesRemaining--;
                mCollectorOutcomes.clear();
            }
        }
        if (abandon) {
            HyperLog.w(Constants.TAG, "Collector import: still incomplete after "
                    + COLLECTOR_MAX_REPAIR_PASSES + " repair wave(s), giving up: "
                    + TextUtils.join(", ", repaired));
            Toast.makeText(this, R.string.collector_import_repair_gave_up, Toast.LENGTH_LONG).show();
            return;
        }

        int repairCount = 0;
        /* Ascending project index; insertLayer() places each layer among existing collector siblings. */
        for (int bi = 0; bi < brokenIndices.size(); bi++) {
            int i = brokenIndices.get(bi);
            long rid = remoteIds[i];
            String layerName = names[i];
            NGWVectorLayer layer = LayerGroup.findNgwVectorLayerByRemoteIdRecursive(group, rid, account);
            if (layer != null) {
                ILayer parent = layer.getParent();
                LayerGroup parentGroup = null;
                while (parent != null) {
                    if (parent instanceof LayerGroup) {
                        parentGroup = (LayerGroup) parent;
                        break;
                    }
                    parent = parent.getParent();
                }
                if (parentGroup == null) {
                    parentGroup = group;
                }
                parentGroup.removeLayer(layer);
                layer.delete(true);
            }

            Intent intent = new Intent(this, LayerFillService.class);
            intent.setAction(LayerFillService.ACTION_ADD_TASK);
            intent.putExtra(LayerFillService.KEY_DEFER_MAP_RELOAD_UNTIL_QUEUE_EMPTY, true);
            intent.putExtra(LayerFillService.KEY_NAME, layerName);
            intent.putExtra(LayerFillService.KEY_ACCOUNT, account);
            intent.putExtra(LayerFillService.KEY_REMOTE_ID, rid);
            intent.putExtra(LayerFillService.KEY_LAYER_GROUP_ID, groupId);
            intent.putExtra(LayerFillService.KEY_COLLECTOR_TRACKING_REMOTE_ID, rid);
            int projIdx = collectorProjectIndexOf(rid, fullProjectOrderSnapshot);
            if (projIdx >= 0 && fullProjectOrderSnapshot != null) {
                intent.putExtra(LayerFillService.KEY_COLLECTOR_ORDER_INDEX, projIdx);
                intent.putExtra(LayerFillService.KEY_COLLECTOR_PROJECT_REMOTE_IDS, fullProjectOrderSnapshot);
            }
            long fid = formIds[i];
            if (fid != 0L) {
                Account acc = getAccount(account);
                if (acc != null) {
                    intent.putExtra(LayerFillService.KEY_INPUT_TYPE, LayerFillService.VECTOR_LAYER_WITH_FORM);
                    intent.putExtra(LayerFillService.KEY_URI,
                            Uri.parse(NGWUtil.getFormUrl(getAccountUrl(acc), fid)));
                } else {
                    HyperLog.w(Constants.TAG, "Collector repair: account missing for \"" + layerName + "\"");
                    intent.putExtra(LayerFillService.KEY_INPUT_TYPE, LayerFillService.NGW_LAYER);
                }
            } else {
                intent.putExtra(LayerFillService.KEY_INPUT_TYPE, LayerFillService.NGW_LAYER);
            }
            String cfg = configs[i];
            if (!TextUtils.isEmpty(cfg)) {
                intent.putExtra(LayerFillService.KEY_LAYER_CONFIG_JSON, cfg);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            repairCount++;
        }

        map.save();
        HyperLog.w(Constants.TAG, "Collector import incomplete: re-queued " + repairCount
                + " layer(s), repair passes left=" + mCollectorRepairPassesRemaining + ": "
                + TextUtils.join(", ", repaired));
        Toast.makeText(this, getString(R.string.collector_import_repair_queued, repairCount),
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void scheduleNgwLayerRebuildAfterSchemaMismatch(final NGWVectorLayer layer) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (layer == null || mMap == null) {
                return;
            }
            if (FeatureChanges.isChanges(layer.getChangeTableName())) {
                Intent alert = new Intent(MESSAGE_ALERT_INTENT);
                alert.putExtra(MESSAGE_EXTRA,
                        getString(com.nextgis.maplib.R.string.ngw_schema_mismatch_has_local_changes));
                alert.putExtra(MESSAGE_TITLE_EXTRA,
                        getString(com.nextgis.maplib.R.string.ngw_schema_mismatch_title));
                alert.setPackage(getPackageName());
                sendBroadcast(alert);
                HyperLog.v(Constants.TAG, "NGW schema mismatch: \"" + layer.getName()
                        + "\" — skipped rebuild (unsynced local changes)");
                return;
            }
            String configJson = null;
            try {
                configJson = layer.toJSON().toString();
            } catch (JSONException e) {
                HyperLog.exception(Constants.TAG, e);
            }
            final String layerName = layer.getName();
            final String accountName = layer.getAccountName();
            final long remoteId = layer.getRemoteId();
            final float minZ = layer.getMinZoom();
            final float maxZ = layer.getMaxZoom();
            final boolean visible = layer.isVisible();

            ILayer p = layer.getParent();
            LayerGroup parentGroup = null;
            while (p != null) {
                if (p instanceof LayerGroup) {
                    parentGroup = (LayerGroup) p;
                    break;
                }
                p = p.getParent();
            }
            if (parentGroup == null && mMap instanceof LayerGroup) {
                parentGroup = (LayerGroup) mMap;
            }
            if (parentGroup == null) {
                HyperLog.w(Constants.TAG, "NGW schema rebuild: no parent LayerGroup for " + layerName);
                return;
            }
            final int groupId = parentGroup.getId();
            parentGroup.removeLayer(layer);
            layer.delete(true);
            mMap.save();

            Intent intent = new Intent(this, LayerFillService.class);
            intent.setAction(LayerFillService.ACTION_ADD_TASK);
            intent.putExtra(LayerFillService.KEY_LAYER_GROUP_ID, groupId);
            intent.putExtra(LayerFillService.KEY_INPUT_TYPE, LayerFillService.NGW_LAYER);
            intent.putExtra(LayerFillService.KEY_NAME, layerName);
            intent.putExtra(LayerFillService.KEY_ACCOUNT, accountName);
            intent.putExtra(LayerFillService.KEY_REMOTE_ID, remoteId);
            intent.putExtra(LayerFillService.KEY_MIN_ZOOM, minZ);
            intent.putExtra(LayerFillService.KEY_MAX_ZOOM, maxZ);
            intent.putExtra(LayerFillService.KEY_VISIBLE, visible);
            intent.putExtra(LayerFillService.KEY_DEFER_MAP_RELOAD_UNTIL_QUEUE_EMPTY, true);
            if (!TextUtils.isEmpty(configJson)) {
                intent.putExtra(LayerFillService.KEY_LAYER_CONFIG_JSON, configJson);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            HyperLog.v(Constants.TAG, "NGW schema mismatch: scheduled LayerFillService rebuild for \""
                    + layerName + "\"");
        });
    }

    @Override
    public void reloadLayerByID(int id){
        if (mMap != null)
            mMap.reloadLayerByID(id);
    }

    @Override
    public void deleteLayerByID(int id){
        if (mMap != null)
            mMap.deleteLayerByID(id);
    }


    @Override
    public void addLayerByID(int id){
        if (mMap != null)
            mMap.addLayerByID(id);
    }


    @Override
    public AuthInterceptorNG getAuthInterceptor(){
        return interceptorNG;
    };

    @Override
    public void updateAuthPair(String[] authPart){
        interceptorNG.addAuth(authPart);
    };


    @Override
    public boolean getGetingStyleInProgress() {
        return isStylingInProgress;
    }

    @Override
    public void setGetingStyleInProgress(boolean value) {
        isStylingInProgress = value;

    }

    public void deleteFeature(Long selectedFeatureId, int layerdID){
        if (mMap!= null){
            mMap.deleteFeature(selectedFeatureId, layerdID);
        }
    }

    public  void setSyncPeriod(final Account account,
                               long interval,
                               Bundle bundle){
        // clear all intervals  ALL !!!!
        List<PeriodicSync> periodicSyncsList = ContentResolver.getPeriodicSyncs(account, getAuthority());
//        Log.e("SyncCheck", "FORDEL Количество синхронизаций для : " + account.name);
//        Log.e("SyncCheck", "FORDEL Количество синхронизаций: " + periodicSyncsList.size());
        for (PeriodicSync p : periodicSyncsList) {
            Log.d("SyncCheck", "FORDEL Период: " + p.period + " сек, Extras: " + p.extras);
            Bundle bundleDelete = new Bundle();
            bundleDelete.putString(KEY_PREF_SYNC_PERIOD, String.valueOf(p.period));
            ContentResolver.removePeriodicSync(account, getAuthority(), bundleDelete);

            if (p.extras.containsKey("sync_period")){
                Bundle bundleDelete2 = new Bundle();
                bundleDelete2.putString(KEY_PREF_SYNC_PERIOD, p.extras.getString("sync_period"));
                ContentResolver.removePeriodicSync(account, getAuthority(), bundleDelete2);
            }
        }
        ContentResolver.addPeriodicSync(account, getAuthority(), bundle, interval);
    }
}
