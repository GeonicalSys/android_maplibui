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
import android.app.Activity;
import android.app.Application;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.PeriodicSync;
import android.content.SharedPreferences;
import android.content.SyncResult;
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
import com.nextgis.maplib.datasource.ngw.Connection;
import com.nextgis.maplib.datasource.ngw.LayerWithStyles;
import com.nextgis.maplib.datasource.ngw.SyncAdapter;
import com.nextgis.maplib.location.GpsEventSource;
import com.nextgis.maplib.map.LayerFactory;
import com.nextgis.maplib.map.MLP.AuthInterceptorNG;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.map.MaplibreMapInteraction;
import com.nextgis.maplib.map.NGWLookupTable;
import com.nextgis.maplib.map.NGWVectorLayer;
import com.nextgis.maplib.map.LayerOriginMetadata;
import com.nextgis.maplib.map.VectorLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.FileUtil;
import com.nextgis.maplib.util.LayerFormHashUtil;
import com.nextgis.maplib.util.NGWUtil;
import com.nextgis.maplib.util.FeatureChanges;
import com.nextgis.maplib.util.NetworkUtil;
import com.nextgis.maplib.util.PermissionUtil;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.fragment.LayerFillProgressDialogFragment;
import com.nextgis.maplibui.mapui.LayerFactoryUI;
import com.nextgis.maplibui.service.LayerFillService;
import com.nextgis.maplibui.util.ConstantsUI;
import com.nextgis.maplibui.util.ControlHelper;
import com.nextgis.maplibui.util.HyperLogCrashHandler;
import com.nextgis.maplibui.util.LayerBackupManager;
import com.nextgis.maplibui.util.LayerUtil;
import com.nextgis.maplibui.util.SettingsConstantsUI;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.nextgis.maplib.util.Constants.MAP_EXT;
import static com.nextgis.maplib.util.Constants.MESSAGE_ALERT_INTENT;
import static com.nextgis.maplib.util.Constants.MESSAGE_EXTRA;
import static com.nextgis.maplib.util.Constants.MESSAGE_TITLE_EXTRA;
import static com.nextgis.maplib.util.Constants.TAG;
import static com.nextgis.maplib.util.SettingsConstants.KEY_PREF_DARK;
import static com.nextgis.maplib.util.SettingsConstants.KEY_PREF_LIGHT;
import static com.nextgis.maplib.util.SettingsConstants.KEY_PREF_MAP;
import static com.nextgis.maplib.util.SettingsConstants.KEY_PREF_NEUTRAL;
import static com.nextgis.maplibui.util.SettingsConstantsUI.KEY_PREF_SYNC_PERIOD;
import static com.nextgis.maplibui.util.SettingsConstantsUI.KEY_PREF_SYNC_PERIODICALLY;

import androidx.core.content.ContextCompat;

import org.maplibre.android.MapLibre;
import org.maplibre.android.MapStrictMode;
import org.maplibre.android.WellKnownTileServer;
import org.json.JSONObject;

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
    /** Collector architecture foundation: preserve project ownership through verify/repair waves. */
    private String mCollectorProjectUid;
    private long[] mCollectorRemoteIds;
    private String[] mCollectorNames;
    private String[] mCollectorConfigJsons;
    private long[] mCollectorFormIds;
    /** Per-layer collector «Редактируемый» flags aligned with {@link #mCollectorRemoteIds}. */
    private boolean[] mCollectorEditables;
    /** All vector layer remote ids in collector project list order (includes layers not in this download batch). */
    private long[] mCollectorFullProjectRemoteIds;
    private final Map<Long, Boolean> mCollectorOutcomes = new ConcurrentHashMap<>();

    /** Remaining verify→repair waves after incomplete collector import (each wave may re-queue multiple layers). */
    private int mCollectorRepairPassesRemaining;

    private static final int COLLECTOR_MAX_REPAIR_PASSES = 3;
    /** Collector foundation: NGFP zip entry name used by form-only sync outside LayerFillService. */
    private static final String COLLECTOR_NGFP_ZIP_META = "meta.json";

    private final Object mStandaloneVerifyLock = new Object();
    private final ArrayList<Bundle> mStandaloneFillVerifyQueue = new ArrayList<>();
    private int mStandaloneRepairPassesRemaining;
    private static final int STANDALONE_LAYER_FILL_MAX_REPAIR_PASSES = 3;

    /** Set when {@link #requestMapReloadAfterLayerFillBatch} runs before map fragment is on main/ready. */
    private volatile boolean mPendingMapReloadAfterLayerFill;

    String account = null;
    String errorMessage = null;
    int errorCode = 0;

    List<Integer> layersToRefresh = new ArrayList<>();

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
        /* Without setURL, HyperLog logs ERROR on every call and may do extra work; remote upload stays a no-op. */
        try {
            HyperLog.setURL("https://127.0.0.1/nextgis-hyperlog-no-remote/");
        } catch (IllegalArgumentException ignored) {
        }

        if (!(Thread.getDefaultUncaughtExceptionHandler() instanceof HyperLogCrashHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(
                    new HyperLogCrashHandler()
            );
        }

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


        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                resetSyncTime();
            }
        }, 2000);

        initializeMapbox();
    }

    private void initializeMapbox() {
        MapLibre.getInstance(this, "sjdkfhjkdshfkjhsdkjf", WellKnownTileServer.MapTiler);
        //TileLoadingMeasurementUtils.setUpTileLoadingMeasurement();
        MapStrictMode.setStrictModeEnabled(true);
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
        Log.d("MMAAPP", "getMap" );
        if (null != mMap) {
            Log.d("MMAAPP", "getMap null != mMap" );
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
        Log.d("WWALK", "getMap mMap created");
        mMap.setName(mapName);
        boolean loaded = mMap.load();
        // A missing file is the normal first-run case (onFirstRun creates base layers). But an existing
        // .ngm that fails to parse (corrupt JSON / SQLite) must not silently degrade to an empty map.
        if (!loaded && mapFullPath.exists()) {
            HyperLog.e(Constants.TAG, "getMap: existing map config failed to load (corrupt?) path="
                    + mapFullPath.getPath());
        }

        checkTracksLayerExist();

        return mMap;
    }

    public Bitmap getMapBackground() {
        int backgroundResId;
        switch (mSharedPreferences.getString(SettingsConstantsUI.KEY_PREF_MAP_BG, KEY_PREF_LIGHT)) {
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
            MaplibreMapInteraction host = mMap != null ? mMap.mapContext.get() : null;
            if (host == null) {
                mPendingMapReloadAfterLayerFill = true;
                HyperLog.d(Constants.TAG, "requestMapReloadAfterLayerFillBatch: map fragment null on main, pending");
                return;
            }
            if (host.reloadMapStyleAndLayersAfterLayerFillBatch()) {
                mPendingMapReloadAfterLayerFill = false;
            } else {
                mPendingMapReloadAfterLayerFill = true;
            }
        });
    }

    @Override
    public void flushPendingMapReloadAfterLayerFillIfNeeded(MaplibreMapInteraction mapFragment) {
        if (mapFragment == null || !mPendingMapReloadAfterLayerFill) {
            return;
        }
        if (mapFragment.reloadMapStyleAndLayersAfterLayerFillBatch()) {
            mPendingMapReloadAfterLayerFill = false;
        }
    }

    @Override
    public void clearMapReloadAfterLayerFillPending() {
        mPendingMapReloadAfterLayerFill = false;
    }

    @Override
    public boolean isLayerFillServiceBusy() {
        return mLayerFillServiceBusy;
    }

    @Override
    public void setLayerFillServiceBusy(boolean busy) {
        mLayerFillServiceBusy = busy;
    }

    @Override
    public boolean tryEnqueueLayerFillRepairBatch(ArrayList<Bundle> repairBundles, boolean deferMapReload) {
        return LayerFillService.tryEnqueueRepairBatchOnActiveInstance(this, repairBundles, deferMapReload);
    }

    private Intent buildCollectorProjectLayerFillIntent(
            int groupId,
            String accountName,
            String collectorProjectUid,
            long remoteId,
            String layerName,
            String layerConfigJson,
            long formId,
            boolean collectorEditable,
            int collectorOrder,
            long[] fullCollectorProjectRemoteIds,
            Float minZoom,
            Float maxZoom,
            Boolean visible,
            int restoreIndex) {
        Intent intent = new Intent(this, LayerFillService.class);
        intent.setAction(LayerFillService.ACTION_ADD_TASK);
        intent.putExtra(LayerFillService.KEY_DEFER_MAP_RELOAD_UNTIL_QUEUE_EMPTY, true);
        intent.putExtra(LayerFillService.KEY_NAME, layerName);
        intent.putExtra(LayerFillService.KEY_ACCOUNT, accountName);
        intent.putExtra(LayerFillService.KEY_REMOTE_ID, remoteId);
        intent.putExtra(LayerFillService.KEY_LAYER_GROUP_ID, groupId);
        intent.putExtra(LayerFillService.KEY_INPUT_TYPE, LayerFillService.NGW_LAYER);
        intent.putExtra(LayerFillService.KEY_COLLECTOR_TRACKING_REMOTE_ID, remoteId);
        intent.putExtra(LayerFillService.KEY_COLLECTOR_LAYER_EDITABLE, collectorEditable);
        if (!TextUtils.isEmpty(collectorProjectUid)) {
            intent.putExtra(LayerFillService.KEY_COLLECTOR_PROJECT_UID, collectorProjectUid);
        }
        if (!TextUtils.isEmpty(layerConfigJson)) {
            intent.putExtra(LayerFillService.KEY_LAYER_CONFIG_JSON, layerConfigJson);
        }
        if (collectorOrder >= 0 && fullCollectorProjectRemoteIds != null) {
            intent.putExtra(LayerFillService.KEY_COLLECTOR_ORDER_INDEX, collectorOrder);
            intent.putExtra(LayerFillService.KEY_COLLECTOR_PROJECT_REMOTE_IDS,
                    fullCollectorProjectRemoteIds);
        }
        if (minZoom != null) {
            intent.putExtra(LayerFillService.KEY_MIN_ZOOM, minZoom);
        }
        if (maxZoom != null) {
            intent.putExtra(LayerFillService.KEY_MAX_ZOOM, maxZoom);
        }
        if (visible != null) {
            intent.putExtra(LayerFillService.KEY_VISIBLE, visible);
        }
        if (restoreIndex >= 0) {
            intent.putExtra(LayerFillService.KEY_LAYER_RESTORE_INSERT_INDEX, restoreIndex);
        }
        if (formId > 0L) {
            intent.putExtra(LayerFillService.KEY_LAYER_ORIGIN_FORM_ID, formId);
            intent.putExtra(LayerFillService.KEY_DEFAULT_FORM_IDS, new long[]{formId});
            Account acc = getAccount(accountName);
            if (acc != null) {
                intent.putExtra(LayerFillService.KEY_INPUT_TYPE,
                        LayerFillService.VECTOR_LAYER_WITH_FORM);
                intent.putExtra(LayerFillService.KEY_URI,
                        Uri.parse(NGWUtil.getFormUrl(getAccountUrl(acc), formId)));
            } else {
                HyperLog.w(Constants.TAG, "Collector composition sync: account missing for form"
                        + " layer=\"" + layerName + "\" account=" + accountName
                        + " formId=" + formId);
            }
        }
        return intent;
    }

    @Override
    public void scheduleCollectorProjectLayerFills(
            int groupId,
            String accountName,
            String collectorProjectUid,
            long[] remoteIds,
            String[] names,
            String[] configJsons,
            long[] formIds,
            boolean[] collectorEditables,
            long[] fullCollectorProjectRemoteIds) {
        if (groupId == Constants.NOT_FOUND || TextUtils.isEmpty(accountName)
                || remoteIds == null || names == null || configJsons == null || formIds == null
                || collectorEditables == null || remoteIds.length == 0
                || remoteIds.length != names.length
                || remoteIds.length != configJsons.length
                || remoteIds.length != formIds.length
                || remoteIds.length != collectorEditables.length) {
            HyperLog.w(Constants.TAG, "Collector composition sync: invalid fill batch");
            return;
        }

        final long[] ids = Arrays.copyOf(remoteIds, remoteIds.length);
        final String[] layerNames = Arrays.copyOf(names, names.length);
        final String[] configs = Arrays.copyOf(configJsons, configJsons.length);
        final long[] forms = Arrays.copyOf(formIds, formIds.length);
        final boolean[] editables = Arrays.copyOf(collectorEditables, collectorEditables.length);
        final long[] fullOrder = fullCollectorProjectRemoteIds != null
                ? Arrays.copyOf(fullCollectorProjectRemoteIds, fullCollectorProjectRemoteIds.length)
                : null;

        new Handler(Looper.getMainLooper()).post(() -> {
            if (mMap == null) {
                return;
            }
            ILayer groupLayer = mMap.getLayerById(groupId);
            if (!(groupLayer instanceof LayerGroup)) {
                HyperLog.w(Constants.TAG, "Collector composition sync: fill group missing id="
                        + groupId);
                return;
            }
            boolean registered = registerCollectorImportBatch(
                    groupId,
                    accountName,
                    collectorProjectUid,
                    ids,
                    layerNames,
                    configs,
                    forms,
                    editables,
                    fullOrder);
            if (!registered) {
                HyperLog.w(Constants.TAG, "Collector composition sync: add batch without verify"
                        + " group=" + groupId + " count=" + ids.length);
            }

            ArrayList<Intent> intents = new ArrayList<>();
            for (int i = 0; i < ids.length; i++) {
                int order = collectorProjectIndexOf(ids[i], fullOrder);
                if (order < 0) {
                    order = i;
                }
                intents.add(buildCollectorProjectLayerFillIntent(
                        groupId,
                        accountName,
                        collectorProjectUid,
                        ids[i],
                        layerNames[i],
                        configs[i],
                        forms[i],
                        editables[i],
                        order,
                        fullOrder,
                        null,
                        null,
                        null,
                        -1));
            }
            if (intents.isEmpty()) {
                return;
            }
            setLayerFillBatchDeferringHeavyMapReload(true);
            LayerFillService.startFillBatch(this, intents);
            Activity fillHost = LayerFillProgressDialogFragment.getProgressHostActivity();
            LayerFillProgressDialogFragment.startBatchFillProgress(fillHost);
            HyperLog.v(Constants.TAG, "Collector composition sync: scheduled add fill batch count="
                    + intents.size() + " group=" + groupId);
        });
    }

    @Override
    public void applyCollectorLayerForm(
            final NGWVectorLayer layer,
            final long formId,
            final String formHash) {
        if (layer == null || layer.getPath() == null) {
            return;
        }
        final String accountName = layer.getAccountName();
        final File layerPath = layer.getPath();
        final LayerOriginMetadata originSnapshot = layer.getLayerOriginMetadata();
        final long oldFormId = originSnapshot != null ? originSnapshot.getFormId() : 0L;

        Thread worker = new Thread(() -> {
            String appliedHash = formHash;
            File tempDir = null;
            try {
                if (formId > 0L) {
                    Account acc = getAccount(accountName);
                    if (acc == null) {
                        HyperLog.w(Constants.TAG, "Collector form sync: account missing layer=\""
                                + layer.getName() + "\" account=" + accountName);
                        return;
                    }
                    tempDir = new File(layerPath, ".collector_form_sync_"
                            + formId + "_" + System.currentTimeMillis());
                    if (!tempDir.mkdirs() && !tempDir.exists()) {
                        HyperLog.w(Constants.TAG, "Collector form sync: cannot create temp dir "
                                + tempDir);
                        return;
                    }
                    byte[] payload = downloadCollectorFormPayload(acc, formId);
                    if (payload == null || payload.length == 0) {
                        HyperLog.w(Constants.TAG, "Collector form sync: empty NGFP payload layer=\""
                                + layer.getName() + "\" formId=" + formId);
                        return;
                    }
                    String downloadedHash = LayerFormHashUtil.md5NgfpZip(
                            new ByteArrayInputStream(payload));
                    if (!TextUtils.isEmpty(formHash)
                            && !TextUtils.isEmpty(downloadedHash)
                            && !formHash.equals(downloadedHash)) {
                        HyperLog.w(Constants.TAG, "Collector form sync: snapshot/download hash mismatch"
                                + " layer=\"" + layer.getName() + "\" formId=" + formId
                                + " snapshot=" + formHash + " downloaded=" + downloadedHash);
                    }
                    if (TextUtils.isEmpty(appliedHash)) {
                        appliedHash = downloadedHash;
                    }
                    unzipCollectorFormPayload(payload, tempDir);
                    normalizeNgfpMeta(new File(tempDir, COLLECTOR_NGFP_ZIP_META));
                    installCollectorFormFiles(layerPath, oldFormId, formId, tempDir);
                    if (TextUtils.isEmpty(appliedHash)) {
                        appliedHash = LayerFormHashUtil.md5LocalNgfpFiles(layerPath, formId);
                    }
                    fillMissingLookupTablesForCollectorForm(layer, formId);
                } else {
                    deleteAllFormSidecars(layerPath);
                    appliedHash = "";
                }
            } catch (Exception e) {
                HyperLog.w(Constants.TAG, "Collector form sync failed layer=\""
                        + layer.getName() + "\" formId=" + formId + ": " + e.getMessage(), e);
                return;
            } finally {
                if (tempDir != null) {
                    FileUtil.deleteRecursive(tempDir);
                }
            }

            final String finalAppliedHash = appliedHash;
            new Handler(Looper.getMainLooper()).post(() -> {
                LayerOriginMetadata current = layer.getLayerOriginMetadata();
                if (current != null && current.isManagedByProject()
                        && !TextUtils.isEmpty(current.getProjectUid())) {
                    LayerOriginMetadata updated = LayerOriginMetadata.collectorLayer(
                            current.getProjectUid(),
                            current.getCollectorOrder(),
                            formId);
                    updated.setRenderMode(current.getRenderMode());
                    layer.setLayerOriginMetadata(updated);
                }
                SharedPreferences.Editor editor = layer.getPreferences().edit();
                if (formId > 0L && !TextUtils.isEmpty(finalAppliedHash)) {
                    editor.putString(SettingsConstants.KEY_PREF_LAST_FORM_HASH, finalAppliedHash);
                } else {
                    editor.remove(SettingsConstants.KEY_PREF_LAST_FORM_HASH);
                }
                editor.apply();
                try {
                    layer.save();
                    if (mMap != null) {
                        mMap.save();
                    }
                } catch (Exception e) {
                    HyperLog.w(Constants.TAG, "Collector form sync: save failed layer=\""
                            + layer.getName() + "\": " + e.getMessage(), e);
                }
                HyperLog.v(Constants.TAG, "Collector form sync: applied layer=\""
                        + layer.getName() + "\" formId=" + formId
                        + " hash=" + (TextUtils.isEmpty(finalAppliedHash)
                        ? "<empty>" : finalAppliedHash));
            });
        }, "CollectorFormSync");
        worker.start();
    }

    private byte[] downloadCollectorFormPayload(Account account, long formId) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            NetworkUtil.getStream(
                    NGWUtil.getFormUrl(getAccountUrl(account), formId),
                    getAccountLogin(account),
                    getAccountPassword(account),
                    out);
            return out.toByteArray();
        } finally {
            out.close();
        }
    }

    private void unzipCollectorFormPayload(byte[] payload, File outputDir) throws IOException {
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(payload));
        byte[] buffer = new byte[Constants.IO_BUFFER_SIZE];
        try {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String entryName = normalizeNgfpEntryName(entry.getName());
                    if (COLLECTOR_NGFP_ZIP_META.equals(entryName)
                            || ConstantsUI.FILE_FORM.equals(entryName)) {
                        File outFile = new File(outputDir, entryName);
                        FileOutputStream out = new FileOutputStream(outFile);
                        try {
                            FileUtil.copyStream(zis, out, buffer, Constants.IO_BUFFER_SIZE);
                        } finally {
                            out.close();
                        }
                    }
                }
                zis.closeEntry();
            }
        } finally {
            zis.close();
        }
        if (!new File(outputDir, ConstantsUI.FILE_FORM).exists()
                || !new File(outputDir, COLLECTOR_NGFP_ZIP_META).exists()) {
            throw new IOException("NGFP archive missing form.json or meta.json");
        }
    }

    private String normalizeNgfpEntryName(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return "";
        }
        String name = raw.replace('\\', '/');
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        int pos = name.lastIndexOf('/');
        if (pos >= 0) {
            name = name.substring(pos + 1);
        }
        return name;
    }

    private void normalizeNgfpMeta(File meta) {
        if (meta == null || !meta.exists()) {
            return;
        }
        try {
            JSONObject metaJson = new JSONObject(FileUtil.readFromFile(meta));
            if (!metaJson.isNull(ConstantsUI.JSON_NGW_CONNECTION_KEY)) {
                metaJson.remove(ConstantsUI.JSON_NGW_CONNECTION_KEY);
                FileUtil.writeToFile(meta, metaJson.toString());
            }
        } catch (Exception e) {
            HyperLog.w(Constants.TAG, "Collector form sync: meta normalize failed "
                    + meta + ": " + e.getMessage());
        }
    }

    private void installCollectorFormFiles(
            File layerPath,
            long oldFormId,
            long formId,
            File tempDir) throws IOException {
        if (oldFormId > 0L && oldFormId != formId) {
            deleteFormSidecars(layerPath, oldFormId);
        }
        replaceFile(
                new File(tempDir, ConstantsUI.FILE_FORM),
                new File(layerPath, formId + "_" + ConstantsUI.FILE_FORM));
        replaceFile(
                new File(tempDir, COLLECTOR_NGFP_ZIP_META),
                new File(layerPath, formId + "_" + LayerFillService.NGFP_META));
    }

    private void replaceFile(File source, File target) throws IOException {
        if (source == null || !source.exists()) {
            throw new IOException("Missing source " + source);
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        if (target.exists() && !target.delete()) {
            throw new IOException("Cannot replace " + target);
        }
        if (source.renameTo(target)) {
            return;
        }
        FileInputStream in = new FileInputStream(source);
        FileOutputStream out = new FileOutputStream(target);
        try {
            byte[] buffer = new byte[Constants.IO_BUFFER_SIZE];
            FileUtil.copyStream(in, out, buffer, Constants.IO_BUFFER_SIZE);
        } finally {
            in.close();
            out.close();
        }
        //noinspection ResultOfMethodCallIgnored
        source.delete();
    }

    private void deleteFormSidecars(File layerPath, long formId) {
        if (layerPath == null || formId <= 0L) {
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        new File(layerPath, formId + "_" + ConstantsUI.FILE_FORM).delete();
        //noinspection ResultOfMethodCallIgnored
        new File(layerPath, formId + "_" + LayerFillService.NGFP_META).delete();
    }

    private void deleteAllFormSidecars(File layerPath) {
        if (layerPath == null || !layerPath.exists()) {
            return;
        }
        File[] files = layerPath.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            String name = file.getName();
            if (file.isFile()
                    && (ConstantsUI.FILE_FORM.equals(name)
                    || LayerFillService.NGFP_META.equals(name)
                    || name.endsWith("_" + ConstantsUI.FILE_FORM)
                    || name.endsWith("_" + LayerFillService.NGFP_META))) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    private void fillMissingLookupTablesForCollectorForm(NGWVectorLayer layer, long formId) {
        if (layer == null || formId <= 0L) {
            return;
        }
        LayerGroup parentGroup = resolveLayerParentGroup(layer);
        if (parentGroup == null) {
            return;
        }
        File form = new File(layer.getPath(), formId + "_" + ConstantsUI.FILE_FORM);
        try {
            ArrayList<String> lookupIds = LayerUtil.fillLookupTableIds(form);
            boolean added = false;
            for (String id : lookupIds) {
                if (TextUtils.isEmpty(id) || hasLookupTable(parentGroup, layer.getAccountName(), id)) {
                    continue;
                }
                NGWLookupTable table = new NGWLookupTable(
                        layer.getContext(), parentGroup.createLayerStorage());
                table.setAccountName(layer.getAccountName());
                table.setRemoteId(Long.parseLong(id));
                table.setSyncType(Constants.SYNC_ALL);
                table.setName(getText(com.nextgis.maplibui.R.string.layer_lookuptable) + " #" + id);
                table.fillFromNGW(null);
                parentGroup.addLayer(table);
                added = true;
            }
            if (added) {
                parentGroup.save();
                if (mMap != null) {
                    mMap.save();
                }
            }
        } catch (Exception e) {
            HyperLog.w(Constants.TAG, "Collector form sync: lookup fill failed layer=\""
                    + layer.getName() + "\": " + e.getMessage(), e);
        }
    }

    private boolean hasLookupTable(LayerGroup parentGroup, String accountName, String lookupId) {
        for (int i = 0; i < parentGroup.getLayerCount(); i++) {
            ILayer layer = parentGroup.getLayer(i);
            if (layer instanceof NGWLookupTable) {
                NGWLookupTable table = (NGWLookupTable) layer;
                if (accountName.equals(table.getAccountName())
                        && lookupId.equals(String.valueOf(table.getRemoteId()))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void scheduleCollectorLayerRebuildFromProject(
            final NGWVectorLayer layer,
            final long formId,
            final int collectorOrder,
            final long[] fullCollectorProjectRemoteIds,
            final boolean collectorEditable,
            final String layerConfigJson) {
        if (layer == null || mMap == null) {
            return;
        }

        final String changeTable = layer.getChangeTableName();
        if (FeatureChanges.isChanges(changeTable)) {
            SyncResult flushSr = new SyncResult();
            boolean flushOk = false;
            try {
                flushOk = layer.sendLocalChanges(flushSr);
            } catch (RuntimeException e) {
                HyperLog.w(Constants.TAG, "Collector composition sync: sendLocalChanges crashed for \""
                        + layer.getName() + "\": " + e.getMessage(), e);
            }
            if (!flushOk) {
                HyperLog.w(Constants.TAG, "Collector composition sync: sendLocalChanges reported failure for \""
                        + layer.getName() + "\"");
            }
        }
        if (FeatureChanges.isChanges(changeTable)) {
            LayerBackupManager.BackupResult backupResult =
                    LayerBackupManager.backupLayerData(
                            this,
                            layer,
                            LayerBackupManager.REASON_SCHEMA_REBUILD);
            if (!backupResult.isSuccess()) {
                postLayerBackupAlert(getString(
                        com.nextgis.maplib.R.string.ngw_schema_mismatch_backup_failed,
                        layer.getName()));
                HyperLog.w(Constants.TAG, "Collector composition sync: skipped refill for \""
                        + layer.getName() + "\" because backup failed: "
                        + backupResult.getError());
                return;
            }
            postLayerBackupAlert(getString(
                    com.nextgis.maplib.R.string.ngw_schema_mismatch_backup_reload,
                    layer.getName()));
            HyperLog.v(Constants.TAG, "Collector composition sync: backup created before refill for \""
                    + layer.getName() + "\": " + backupResult.getFile());
        }

        final String layerName = layer.getName();
        final String accountName = layer.getAccountName();
        final long remoteId = layer.getRemoteId();
        final float minZ = layer.getMinZoom();
        final float maxZ = layer.getMaxZoom();
        final boolean visible = layer.isVisible();
        final LayerOriginMetadata origin = layer.getLayerOriginMetadata();
        final String projectUid = origin != null ? origin.getProjectUid() : null;
        final long[] fullOrder = fullCollectorProjectRemoteIds != null
                ? Arrays.copyOf(fullCollectorProjectRemoteIds, fullCollectorProjectRemoteIds.length)
                : null;

        new Handler(Looper.getMainLooper()).post(() -> {
            if (layer == null || mMap == null) {
                return;
            }
            LayerGroup parentGroup = resolveLayerParentGroup(layer);
            if (parentGroup == null) {
                HyperLog.w(Constants.TAG, "Collector composition sync: no parent LayerGroup for refill "
                        + layerName);
                return;
            }
            final int groupId = parentGroup.getId();
            int restoreIndex = parentGroup.getChildLayerIndex(layer);
            if (restoreIndex < 0) {
                restoreIndex = parentGroup.getLayerCount();
            }

            parentGroup.removeLayer(layer);
            layer.delete(true);
            mMap.save();

            boolean registered = registerCollectorImportBatch(
                    groupId,
                    accountName,
                    projectUid,
                    new long[]{remoteId},
                    new String[]{layerName},
                    new String[]{layerConfigJson},
                    new long[]{formId},
                    new boolean[]{collectorEditable},
                    fullOrder);
            if (!registered) {
                HyperLog.w(Constants.TAG, "Collector composition sync: refill without verify layer=\""
                        + layerName + "\" remoteId=" + remoteId);
            }

            Intent intent = buildCollectorProjectLayerFillIntent(
                    groupId,
                    accountName,
                    projectUid,
                    remoteId,
                    layerName,
                    layerConfigJson,
                    formId,
                    collectorEditable,
                    collectorOrder,
                    fullOrder,
                    minZ,
                    maxZ,
                    visible,
                    restoreIndex);
            setLayerFillBatchDeferringHeavyMapReload(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent);
            } else {
                startService(intent);
            }
            Activity fillHost = LayerFillProgressDialogFragment.getProgressHostActivity();
            LayerFillProgressDialogFragment.startBatchFillProgress(fillHost);
            HyperLog.v(Constants.TAG, "Collector composition sync: scheduled refill layer=\""
                    + layerName + "\" remoteId=" + remoteId + " formId=" + formId);
        });
    }

    @Override
    public void applyCollectorLayerProjectState(
            final NGWVectorLayer layer,
            final int collectorOrder,
            final long[] fullCollectorProjectRemoteIds,
            final boolean collectorEditable) {
        if (layer == null) {
            return;
        }
        final long[] fullOrder = fullCollectorProjectRemoteIds != null
                ? Arrays.copyOf(fullCollectorProjectRemoteIds, fullCollectorProjectRemoteIds.length)
                : null;
        new Handler(Looper.getMainLooper()).post(() -> {
            if (layer == null || mMap == null) {
                return;
            }
            LayerOriginMetadata origin = layer.getLayerOriginMetadata();
            if (origin == null || TextUtils.isEmpty(origin.getProjectUid())) {
                return;
            }
            LayerGroup parentGroup = resolveLayerParentGroup(layer);
            if (parentGroup == null) {
                HyperLog.w(Constants.TAG, "Collector composition sync: no parent LayerGroup for state update "
                        + layer.getName());
                return;
            }

            boolean changed = false;
            boolean moved = false;
            if (layer.isCollectorEditable() != collectorEditable) {
                layer.setCollectorEditable(collectorEditable);
                changed = true;
            }

            int newOrder = collectorOrder >= 0 ? collectorOrder : origin.getCollectorOrder();
            boolean orderChanged = newOrder >= 0 && origin.getCollectorOrder() != newOrder;
            if (orderChanged) {
                LayerOriginMetadata updated = LayerOriginMetadata.collectorLayer(
                        origin.getProjectUid(),
                        newOrder,
                        origin.getFormId());
                updated.setRenderMode(origin.getRenderMode());
                layer.setLayerOriginMetadata(updated);
                changed = true;
            }

            if (orderChanged && collectorOrder >= 0 && fullOrder != null) {
                int oldIndex = parentGroup.getChildLayerIndex(layer);
                parentGroup.removeLayer(layer);
                int insertAt = LayerGroup.computeCollectorOrderedInsertIndex(
                        parentGroup,
                        layer.getAccountName(),
                        fullOrder,
                        collectorOrder);
                parentGroup.insertLayer(insertAt, layer);
                moved = oldIndex != insertAt;
                changed = true;
            }

            if (changed) {
                try {
                    layer.save();
                    parentGroup.save();
                    mMap.save();
                } catch (Exception e) {
                    HyperLog.w(Constants.TAG, "Collector composition sync: state save failed for \""
                            + layer.getName() + "\": " + e.getMessage(), e);
                }
            }
            if (moved) {
                requestMapReloadAfterLayerFillBatch();
            }
            HyperLog.v(Constants.TAG, "Collector composition sync: applied state layer=\""
                    + layer.getName() + "\" order=" + collectorOrder
                    + " editable=" + collectorEditable + " moved=" + moved);
        });
    }

    private LayerGroup resolveLayerParentGroup(ILayer layer) {
        ILayer p = layer != null ? layer.getParent() : null;
        while (p != null) {
            if (p instanceof LayerGroup) {
                return (LayerGroup) p;
            }
            p = p.getParent();
        }
        return mMap instanceof LayerGroup ? (LayerGroup) mMap : null;
    }

    private void clearCollectorImportFieldsLocked() {
        mCollectorRemoteIds = null;
        mCollectorNames = null;
        mCollectorConfigJsons = null;
        mCollectorFormIds = null;
        mCollectorEditables = null;
        mCollectorFullProjectRemoteIds = null;
        mCollectorProjectUid = null;
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
    public boolean registerCollectorImportBatch(
            int groupId,
            String accountName,
            String collectorProjectUid,
            long[] remoteIds,
            String[] names,
            String[] configJsons,
            long[] formIds,
            boolean[] collectorEditables,
            long[] fullCollectorProjectRemoteIds) {
        clearStandaloneFillVerifyLocked();
        synchronized (mCollectorImportLock) {
            if (remoteIds == null || names == null || configJsons == null || formIds == null
                    || remoteIds.length != names.length
                    || remoteIds.length != configJsons.length
                    || remoteIds.length != formIds.length
                    || remoteIds.length == 0) {
                HyperLog.w(Constants.TAG, "registerCollectorImportBatch: invalid or empty arrays");
                return false;
            }
            if (collectorEditables != null && collectorEditables.length != remoteIds.length) {
                HyperLog.w(Constants.TAG, "registerCollectorImportBatch: collectorEditables length mismatch");
                return false;
            }
            if (fullCollectorProjectRemoteIds == null || fullCollectorProjectRemoteIds.length == 0) {
                HyperLog.w(Constants.TAG, "registerCollectorImportBatch: full project order required");
                return false;
            }
            for (long rid : remoteIds) {
                if (collectorProjectIndexOf(rid, fullCollectorProjectRemoteIds) < 0) {
                    HyperLog.w(Constants.TAG, "registerCollectorImportBatch: remoteId " + rid
                            + " missing from full collector project list");
                    return false;
                }
            }
            mCollectorGroupId = groupId;
            mCollectorAccount = accountName;
            mCollectorProjectUid = collectorProjectUid;
            mCollectorRemoteIds = Arrays.copyOf(remoteIds, remoteIds.length);
            mCollectorNames = Arrays.copyOf(names, names.length);
            mCollectorConfigJsons = Arrays.copyOf(configJsons, configJsons.length);
            mCollectorFormIds = Arrays.copyOf(formIds, formIds.length);
            mCollectorEditables = collectorEditables != null
                    ? Arrays.copyOf(collectorEditables, collectorEditables.length) : null;
            mCollectorFullProjectRemoteIds = Arrays.copyOf(
                    fullCollectorProjectRemoteIds, fullCollectorProjectRemoteIds.length);
            mCollectorOutcomes.clear();
            mCollectorRepairPassesRemaining = COLLECTOR_MAX_REPAIR_PASSES;
            return true;
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
        clearStandaloneFillVerifyLocked();
        synchronized (mCollectorImportLock) {
            clearCollectorImportFieldsLocked();
        }
    }

    @Override
    public boolean hasCollectorImportBatchRegistered() {
        synchronized (mCollectorImportLock) {
            return mCollectorRemoteIds != null && mCollectorRemoteIds.length > 0;
        }
    }

    private void clearStandaloneFillVerifyLocked() {
        synchronized (mStandaloneVerifyLock) {
            mStandaloneFillVerifyQueue.clear();
            mStandaloneRepairPassesRemaining = 0;
        }
    }

    @Override
    public void registerStandaloneLayerFillVerifyAfterSuccess(Bundle fillTaskIntentExtrasCopy) {
        if (fillTaskIntentExtrasCopy == null) {
            return;
        }
        synchronized (mStandaloneVerifyLock) {
            if (mStandaloneFillVerifyQueue.isEmpty()) {
                mStandaloneRepairPassesRemaining = STANDALONE_LAYER_FILL_MAX_REPAIR_PASSES;
            }
            mStandaloneFillVerifyQueue.add(new Bundle(fillTaskIntentExtrasCopy));
        }
    }

    @Override
    public void finalizeStandaloneLayerFillVerifyIfNeeded() {
        ArrayList<Bundle> pendingCopy;
        synchronized (mStandaloneVerifyLock) {
            if (mStandaloneFillVerifyQueue.isEmpty()) {
                return;
            }
            pendingCopy = new ArrayList<>(mStandaloneFillVerifyQueue.size());
            for (Bundle b : mStandaloneFillVerifyQueue) {
                pendingCopy.add(new Bundle(b));
            }
        }

        MapDrawable map = mMap;
        if (map == null) {
            return;
        }

        ArrayList<Bundle> broken = new ArrayList<>();
        ArrayList<String> brokenLabels = new ArrayList<>();
        for (Bundle b : pendingCopy) {
            int gid = b.getInt(LayerFillService.KEY_LAYER_GROUP_ID, Constants.NOT_FOUND);
            ILayer groupLayer = map.getLayerById(gid);
            if (!(groupLayer instanceof LayerGroup)) {
                broken.add(b);
                brokenLabels.add(standaloneVerifyBundleLabel(b));
                continue;
            }
            LayerGroup group = (LayerGroup) groupLayer;
            if (isStandaloneFillVerifyBundleOk(map, group, b)) {
                continue;
            }
            broken.add(b);
            brokenLabels.add(standaloneVerifyBundleLabel(b));
        }

        if (broken.isEmpty()) {
            synchronized (mStandaloneVerifyLock) {
                mStandaloneFillVerifyQueue.clear();
            }
            if (!pendingCopy.isEmpty()) {
                HyperLog.d(Constants.TAG, "Standalone fill verify: all " + pendingCopy.size()
                        + " layer(s) present with local tables");
            }
            return;
        }

        boolean abandon;
        int passesLeftSnapshot;
        synchronized (mStandaloneVerifyLock) {
            if (mStandaloneRepairPassesRemaining <= 0) {
                abandon = true;
                mStandaloneFillVerifyQueue.clear();
                passesLeftSnapshot = 0;
            } else {
                abandon = false;
                mStandaloneRepairPassesRemaining--;
                passesLeftSnapshot = mStandaloneRepairPassesRemaining;
                mStandaloneFillVerifyQueue.clear();
            }
        }
        if (abandon) {
            HyperLog.w(Constants.TAG, "Standalone layer fill: still incomplete after "
                    + STANDALONE_LAYER_FILL_MAX_REPAIR_PASSES + " repair wave(s): "
                    + TextUtils.join(", ", brokenLabels));
            Toast.makeText(this, R.string.collector_import_repair_gave_up, Toast.LENGTH_LONG).show();
            return;
        }

        ArrayList<Bundle> repairBundles = new ArrayList<>();
        for (Bundle b : broken) {
            int gid = b.getInt(LayerFillService.KEY_LAYER_GROUP_ID, Constants.NOT_FOUND);
            ILayer groupLayer = map.getLayerById(gid);
            LayerGroup group = groupLayer instanceof LayerGroup ? (LayerGroup) groupLayer : null;

            int localId = b.getInt(LayerFillService.KEY_STANDALONE_VERIFY_LAYER_ID, Constants.NOT_FOUND);
            if (localId != Constants.NOT_FOUND) {
                ILayer layer = map.getLayerById(localId);
                if (layer != null) {
                    LayerGroup parentGroup = standaloneResolveParentGroup(layer, group);
                    parentGroup.removeLayer(layer);
                    layer.delete(true);
                }
            } else if (group != null) {
                long remoteId = b.getLong(LayerFillService.KEY_REMOTE_ID, -1L);
                String account = b.getString(LayerFillService.KEY_ACCOUNT);
                if (remoteId >= 0 && !TextUtils.isEmpty(account)) {
                    NGWVectorLayer ngw = LayerGroup.findNgwVectorLayerByRemoteIdRecursive(
                            group, remoteId, account);
                    if (ngw != null) {
                        LayerGroup parentGroup = standaloneResolveParentGroup(ngw, group);
                        parentGroup.removeLayer(ngw);
                        ngw.delete(true);
                    }
                }
            }

            Bundle extras = new Bundle(b);
            extras.remove(LayerFillService.KEY_STANDALONE_VERIFY_LAYER_ID);
            repairBundles.add(extras);
        }

        map.save();
        int repairCount = repairBundles.size();
        if (repairCount > 0) {
            boolean defer = isLayerFillBatchDeferringHeavyMapReload();
            if (!tryEnqueueLayerFillRepairBatch(repairBundles, defer)) {
                Intent batchIntent = new Intent(this, LayerFillService.class);
                batchIntent.setAction(LayerFillService.ACTION_ADD_REPAIR_BATCH);
                batchIntent.putParcelableArrayListExtra(LayerFillService.KEY_REPAIR_BATCH_EXTRAS, repairBundles);
                if (defer) {
                    batchIntent.putExtra(LayerFillService.KEY_DEFER_MAP_RELOAD_UNTIL_QUEUE_EMPTY, true);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(this, batchIntent);
                } else {
                    startService(batchIntent);
                }
            }
        }
        HyperLog.w(Constants.TAG, "Standalone fill incomplete: re-queued " + repairCount
                + " layer(s), repair passes left=" + passesLeftSnapshot);
        if (!isLayerFillBatchDeferringHeavyMapReload()) {
            Toast.makeText(this, getString(R.string.collector_import_repair_queued, repairCount),
                    Toast.LENGTH_LONG).show();
        }
    }

    private static String standaloneVerifyBundleLabel(Bundle b) {
        String n = b.getString(LayerFillService.KEY_NAME);
        if (!TextUtils.isEmpty(n)) {
            return n;
        }
        return "remoteId=" + b.getLong(LayerFillService.KEY_REMOTE_ID, -1L);
    }

    private static boolean isStandaloneFillVerifyBundleOk(MapDrawable map, LayerGroup group, Bundle b) {
        int localId = b.getInt(LayerFillService.KEY_STANDALONE_VERIFY_LAYER_ID, Constants.NOT_FOUND);
        if (localId != Constants.NOT_FOUND) {
            ILayer layer = map.getLayerById(localId);
            if (!(layer instanceof VectorLayer)) {
                return false;
            }
            return ((VectorLayer) layer).hasLocalDataTable();
        }
        long remoteId = b.getLong(LayerFillService.KEY_REMOTE_ID, -1L);
        String account = b.getString(LayerFillService.KEY_ACCOUNT);
        if (remoteId < 0 || TextUtils.isEmpty(account)) {
            return false;
        }
        NGWVectorLayer ngw = LayerGroup.findNgwVectorLayerByRemoteIdRecursive(group, remoteId, account);
        if (ngw == null) {
            return false;
        }
        return ngw.hasLocalDataTable();
    }

    private static LayerGroup standaloneResolveParentGroup(ILayer layer, LayerGroup fallback) {
        if (layer == null) {
            return fallback;
        }
        ILayer parent = layer.getParent();
        while (parent != null) {
            if (parent instanceof LayerGroup) {
                return (LayerGroup) parent;
            }
            parent = parent.getParent();
        }
        return fallback;
    }

    @Override
    public void finalizeCollectorImportVerifyAndRepairIfNeeded() {
        int groupId;
        String account;
        long[] remoteIds;
        String[] names;
        String[] configs;
        long[] formIds;
        boolean[] collectorEditables;
        String collectorProjectUid;
        Map<Long, Boolean> outcomes;
        long[] fullProjectOrderSnapshot;
        synchronized (mCollectorImportLock) {
            if (mCollectorRemoteIds == null || mCollectorRemoteIds.length == 0) {
                return;
            }
            groupId = mCollectorGroupId;
            account = mCollectorAccount;
            collectorProjectUid = mCollectorProjectUid;
            remoteIds = Arrays.copyOf(mCollectorRemoteIds, mCollectorRemoteIds.length);
            names = Arrays.copyOf(mCollectorNames, mCollectorNames.length);
            configs = Arrays.copyOf(mCollectorConfigJsons, mCollectorConfigJsons.length);
            formIds = Arrays.copyOf(mCollectorFormIds, mCollectorFormIds.length);
            collectorEditables = mCollectorEditables != null
                    ? Arrays.copyOf(mCollectorEditables, mCollectorEditables.length) : null;
            outcomes = new ConcurrentHashMap<>(mCollectorOutcomes);
            fullProjectOrderSnapshot = mCollectorFullProjectRemoteIds != null
                    ? Arrays.copyOf(mCollectorFullProjectRemoteIds, mCollectorFullProjectRemoteIds.length)
                    : null;
        }

        final int expectedCount = remoteIds.length;
        MapBase map = getMap();
        if (map == null) {
            // Cannot verify/repair without a map; clear so the batch is not left orphaned forever.
            HyperLog.w(Constants.TAG, "Collector verify: map is null; clearing orphaned import batch");
            synchronized (mCollectorImportLock) {
                clearCollectorImportFieldsLocked();
            }
            return;
        }
        ILayer groupLayer = map.getLayerById(groupId);
        if (!(groupLayer instanceof LayerGroup)) {
            HyperLog.w(Constants.TAG, "Collector verify: layer group id " + groupId
                    + " not found; clearing orphaned import batch");
            synchronized (mCollectorImportLock) {
                clearCollectorImportFieldsLocked();
            }
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

        int passesLeftForLog;
        synchronized (mCollectorImportLock) {
            passesLeftForLog = mCollectorRepairPassesRemaining;
        }
        ArrayList<Bundle> repairBundles = new ArrayList<>();
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

            Bundle taskExtras = new Bundle();
            taskExtras.putBoolean(LayerFillService.KEY_DEFER_MAP_RELOAD_UNTIL_QUEUE_EMPTY, true);
            taskExtras.putString(LayerFillService.KEY_NAME, layerName);
            taskExtras.putString(LayerFillService.KEY_ACCOUNT, account);
            taskExtras.putLong(LayerFillService.KEY_REMOTE_ID, rid);
            taskExtras.putInt(LayerFillService.KEY_LAYER_GROUP_ID, groupId);
            taskExtras.putLong(LayerFillService.KEY_COLLECTOR_TRACKING_REMOTE_ID, rid);
            if (!TextUtils.isEmpty(collectorProjectUid)) {
                taskExtras.putString(LayerFillService.KEY_COLLECTOR_PROJECT_UID, collectorProjectUid);
            }
            int projIdx = collectorProjectIndexOf(rid, fullProjectOrderSnapshot);
            if (projIdx >= 0 && fullProjectOrderSnapshot != null) {
                taskExtras.putInt(LayerFillService.KEY_COLLECTOR_ORDER_INDEX, projIdx);
                taskExtras.putLongArray(LayerFillService.KEY_COLLECTOR_PROJECT_REMOTE_IDS, fullProjectOrderSnapshot);
            }
            long fid = formIds[i];
            if (fid != 0L) {
                taskExtras.putLong(LayerFillService.KEY_LAYER_ORIGIN_FORM_ID, fid);
                taskExtras.putLongArray(LayerFillService.KEY_DEFAULT_FORM_IDS, new long[]{fid});
                Account acc = getAccount(account);
                if (acc != null) {
                    taskExtras.putInt(LayerFillService.KEY_INPUT_TYPE, LayerFillService.VECTOR_LAYER_WITH_FORM);
                    taskExtras.putParcelable(LayerFillService.KEY_URI,
                            Uri.parse(NGWUtil.getFormUrl(getAccountUrl(acc), fid)));
                } else {
                    HyperLog.w(Constants.TAG, "Collector repair: account missing for \"" + layerName + "\"");
                    taskExtras.putInt(LayerFillService.KEY_INPUT_TYPE, LayerFillService.NGW_LAYER);
                }
            } else {
                taskExtras.putInt(LayerFillService.KEY_INPUT_TYPE, LayerFillService.NGW_LAYER);
            }
            String cfg = configs[i];
            if (!TextUtils.isEmpty(cfg)) {
                taskExtras.putString(LayerFillService.KEY_LAYER_CONFIG_JSON, cfg);
            }
            if (collectorEditables != null && i < collectorEditables.length) {
                taskExtras.putBoolean(LayerFillService.KEY_COLLECTOR_LAYER_EDITABLE, collectorEditables[i]);
            }
            repairBundles.add(taskExtras);
        }

        map.save();
        int repairCount = repairBundles.size();
        if (repairCount > 0) {
            if (!tryEnqueueLayerFillRepairBatch(repairBundles, true)) {
                Intent batchIntent = new Intent(this, LayerFillService.class);
                batchIntent.setAction(LayerFillService.ACTION_ADD_REPAIR_BATCH);
                batchIntent.putExtra(LayerFillService.KEY_DEFER_MAP_RELOAD_UNTIL_QUEUE_EMPTY, true);
                batchIntent.putParcelableArrayListExtra(LayerFillService.KEY_REPAIR_BATCH_EXTRAS, repairBundles);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(batchIntent);
                } else {
                    startService(batchIntent);
                }
            }
        }
        HyperLog.w(Constants.TAG, "Collector import incomplete: re-queued " + repairCount
                + " layer(s), repair passes left=" + passesLeftForLog + ": "
                + TextUtils.join(", ", repaired));
        if (!isLayerFillBatchDeferringHeavyMapReload()) {
            Toast.makeText(this, getString(R.string.collector_import_repair_queued, repairCount),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void scheduleNgwLayerRebuildAfterSchemaMismatch(final NGWVectorLayer layer) {
        if (layer == null || mMap == null) {
            return;
        }
        final String changeTable = layer.getChangeTableName();
        if (FeatureChanges.isChanges(changeTable)) {
            SyncResult flushSr = new SyncResult();
            boolean flushOk = false;
            try {
                flushOk = layer.sendLocalChanges(flushSr);
            } catch (RuntimeException e) {
                HyperLog.w(Constants.TAG, "NGW schema rebuild: sendLocalChanges crashed for \""
                        + layer.getName() + "\": " + e.getMessage(), e);
            }
            if (!flushOk) {
                HyperLog.w(Constants.TAG, "NGW schema rebuild: sendLocalChanges reported failure for \""
                        + layer.getName() + "\"");
            }
        }
        if (FeatureChanges.isChanges(changeTable)) {
            LayerBackupManager.BackupResult backupResult =
                    LayerBackupManager.backupLayerData(
                            this,
                            layer,
                            LayerBackupManager.REASON_SCHEMA_REBUILD);
            if (!backupResult.isSuccess()) {
                postLayerBackupAlert(getString(
                        com.nextgis.maplib.R.string.ngw_schema_mismatch_backup_failed,
                        layer.getName()));
                HyperLog.w(Constants.TAG, "NGW schema mismatch: \"" + layer.getName()
                        + "\" - skipped rebuild because backup failed: "
                        + backupResult.getError());
                return;
            }
            postLayerBackupAlert(getString(
                    com.nextgis.maplib.R.string.ngw_schema_mismatch_backup_reload,
                    layer.getName()));
            HyperLog.v(Constants.TAG, "NGW schema mismatch: \"" + layer.getName()
                    + "\" - backup created before forced rebuild: "
                    + backupResult.getFile());
        }

        final String rebuildAccountName = layer.getAccountName();
        final long rebuildRemoteId = layer.getRemoteId();
        final long rebuildFormId = resolveNgwLayerRebuildFormId(
                layer, rebuildAccountName, rebuildRemoteId);
        final LayerOriginMetadata rebuildOrigin = layer.getLayerOriginMetadata();

        new Handler(Looper.getMainLooper()).post(() -> {
            if (layer == null || mMap == null) {
                return;
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
            int restoreIndex = parentGroup.getChildLayerIndex(layer);
            if (restoreIndex < 0) {
                restoreIndex = parentGroup.getLayerCount();
            }
            /*
             * Do not pass KEY_LAYER_CONFIG_JSON from layer.toJSON() here: that snapshot is the *old*
             * local layer (often already out of sync with Web GIS). LayerFillService.resolveImportedLayerConfigJson
             * prefers the intent extra over an HTTP fetch of the resource description — applying stale JSON
             * after createFromNGW() reverts fields/renderer and causes an endless schema-mismatch loop on
             * every sync. Zoom/visibility/name/account/remoteId are still carried by other extras; fresh
             * description is loaded inside NGWVectorLayerFillTask when the extra is absent.
             */
            parentGroup.removeLayer(layer);
            layer.delete(true);
            mMap.save();

            Intent intent = new Intent(this, LayerFillService.class);
            intent.setAction(LayerFillService.ACTION_ADD_TASK);
            intent.putExtra(LayerFillService.KEY_LAYER_GROUP_ID, groupId);
            if (rebuildFormId > 0L) {
                Account acc = getAccount(accountName);
                if (acc != null) {
                    intent.putExtra(LayerFillService.KEY_INPUT_TYPE,
                            LayerFillService.VECTOR_LAYER_WITH_FORM);
                    intent.putExtra(LayerFillService.KEY_URI,
                            Uri.parse(NGWUtil.getFormUrl(getAccountUrl(acc), rebuildFormId)));
                    intent.putExtra(LayerFillService.KEY_DEFAULT_FORM_IDS,
                            new long[]{rebuildFormId});
                } else {
                    HyperLog.w(Constants.TAG, "NGW schema rebuild: account missing for form restore \""
                            + layerName + "\" account=" + accountName + " formId=" + rebuildFormId);
                    intent.putExtra(LayerFillService.KEY_INPUT_TYPE, LayerFillService.NGW_LAYER);
                }
            } else {
                intent.putExtra(LayerFillService.KEY_INPUT_TYPE, LayerFillService.NGW_LAYER);
            }
            intent.putExtra(LayerFillService.KEY_NAME, layerName);
            intent.putExtra(LayerFillService.KEY_ACCOUNT, accountName);
            intent.putExtra(LayerFillService.KEY_REMOTE_ID, remoteId);
            intent.putExtra(LayerFillService.KEY_MIN_ZOOM, minZ);
            intent.putExtra(LayerFillService.KEY_MAX_ZOOM, maxZ);
            intent.putExtra(LayerFillService.KEY_VISIBLE, visible);
            intent.putExtra(LayerFillService.KEY_DEFER_MAP_RELOAD_UNTIL_QUEUE_EMPTY, true);
            intent.putExtra(LayerFillService.KEY_LAYER_RESTORE_INSERT_INDEX, restoreIndex);
            // Collector architecture foundation: keep layer origin through automatic rebuilds.
            // Future composition/form/tile sync relies on this metadata and should not require
            // re-importing heavy local data after a schema refresh.
            if (rebuildOrigin != null) {
                long originFormId = rebuildFormId > 0L ? rebuildFormId : rebuildOrigin.getFormId();
                if (originFormId > 0L) {
                    intent.putExtra(LayerFillService.KEY_LAYER_ORIGIN_FORM_ID, originFormId);
                }
                if (rebuildOrigin.isManagedByProject()
                        && !TextUtils.isEmpty(rebuildOrigin.getProjectUid())) {
                    intent.putExtra(LayerFillService.KEY_COLLECTOR_PROJECT_UID,
                            rebuildOrigin.getProjectUid());
                    if (rebuildOrigin.getCollectorOrder() >= 0) {
                        intent.putExtra(LayerFillService.KEY_COLLECTOR_ORDER_INDEX,
                                rebuildOrigin.getCollectorOrder());
                    }
                } else if (LayerOriginMetadata.TYPE_MANUAL_NGW.equals(rebuildOrigin.getType())) {
                    intent.putExtra(LayerFillService.KEY_MARK_MANUAL_NGW_ORIGIN, true);
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            Activity fillHost = LayerFillProgressDialogFragment.getProgressHostActivity();
            LayerFillProgressDialogFragment.startBatchFillProgress(fillHost);
            HyperLog.v(Constants.TAG, "NGW schema mismatch: scheduled LayerFillService rebuild for \""
                    + layerName + "\" formId=" + rebuildFormId);
        });
    }

    /**
     * Collector composition sync foundation.
     *
     * Future project-composition synchronization should call this when a layer still exists locally
     * but has been removed from the Collector project in Web GIS. Keep this explicit hook so that
     * mandatory data backup stays tied to the destructive removal path.
     */
    @Override
    public void scheduleCollectorLayerRemovalWithBackup(final NGWVectorLayer layer) {
        if (layer == null || mMap == null) {
            return;
        }

        LayerBackupManager.BackupResult backupResult =
                LayerBackupManager.backupLayerData(
                        this,
                        layer,
                        LayerBackupManager.REASON_COLLECTOR_LAYER_REMOVED);
        if (!backupResult.isSuccess()) {
            postLayerBackupAlert(
                    getString(com.nextgis.maplib.R.string.collector_layer_removed_title),
                    getString(com.nextgis.maplib.R.string.collector_layer_backup_failed,
                            layer.getName()));
            HyperLog.w(Constants.TAG, "Collector layer removal: backup failed for \""
                    + layer.getName() + "\": " + backupResult.getError());
            return;
        }

        new Handler(Looper.getMainLooper()).post(() -> {
            if (layer == null || mMap == null) {
                return;
            }
            final String layerName = layer.getName();
            postLayerBackupAlert(
                    getString(com.nextgis.maplib.R.string.collector_layer_removed_title),
                    getString(com.nextgis.maplib.R.string.collector_layer_removed_from_project,
                            layerName));

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
                HyperLog.w(Constants.TAG, "Collector layer removal: no parent LayerGroup for "
                        + layerName);
                return;
            }

            parentGroup.removeLayer(layer);
            layer.delete(true);
            mMap.save();
            requestMapReloadAfterLayerFillBatch();
            HyperLog.v(Constants.TAG, "Collector layer removal: \"" + layerName
                    + "\" removed after backup: " + backupResult.getFile());
        });
    }

    private void postLayerBackupAlert(String message) {
        postLayerBackupAlert(
                getString(com.nextgis.maplib.R.string.ngw_schema_mismatch_title),
                message);
    }

    private void postLayerBackupAlert(String title, String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Intent alert = new Intent(MESSAGE_ALERT_INTENT);
            alert.putExtra(MESSAGE_EXTRA, message);
            alert.putExtra(MESSAGE_TITLE_EXTRA, title);
            alert.setPackage(getPackageName());
            sendBroadcast(alert);
        });
    }

    private long resolveNgwLayerRebuildFormId(
            NGWVectorLayer layer,
            String accountName,
            long remoteId) {
        long localFormId = findLocalNgwLayerFormId(layer);
        if (localFormId > 0L) {
            return localFormId;
        }
        if (TextUtils.isEmpty(accountName) || remoteId < 0L) {
            return 0L;
        }
        Account acc = getAccount(accountName);
        if (acc == null) {
            HyperLog.w(Constants.TAG, "NGW schema rebuild: account missing while resolving form id"
                    + " account=" + accountName + " remoteId=" + remoteId);
            return 0L;
        }
        ArrayList<Long> forms = new ArrayList<>();
        boolean ok = LayerWithStyles.fillStyles(
                getAccountUrl(acc),
                getAccountLogin(acc),
                getAccountPassword(acc),
                remoteId,
                null,
                forms);
        if (!ok || forms.isEmpty() || forms.get(0) == null || forms.get(0) <= 0L) {
            return 0L;
        }
        return forms.get(0);
    }

    private long findLocalNgwLayerFormId(NGWVectorLayer layer) {
        if (layer == null || layer.getPath() == null) {
            return 0L;
        }
        String prefix = LayerUtil.findFormJsonPrefix(layer.getPath().toString());
        if (TextUtils.isEmpty(prefix)) {
            return 0L;
        }
        if (prefix.endsWith("_")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        try {
            long formId = Long.parseLong(prefix);
            return formId > 0L ? formId : 0L;
        } catch (NumberFormatException ignored) {
            return 0L;
        }
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


    @Override
    public void setLayerToRefresh(int id) {
        synchronized (layersToRefresh){
            layersToRefresh.add(id);
        }
    }

    @Override
    public void removeLayerToRefresh(int id) {
        try {
            synchronized (layersToRefresh) {
                layersToRefresh.removeIf(integer -> id == integer);
            }
        } catch (Exception ex){
            Log.e(TAG, Objects.requireNonNull(ex.getMessage()));
        }
    }

    @Override
    public List<Integer> getlayersToRefresh() {
        try {
            synchronized (layersToRefresh) {
                return new ArrayList<>(layersToRefresh);
            }
        } catch (Exception ex){
            Log.e(TAG, Objects.requireNonNull(ex.getMessage()));
            return new ArrayList<>();
        }
    }


    public  void setSyncPeriod(final Account account,
                               long interval,
                               Bundle bundle, boolean deleteExisting){
        List<PeriodicSync> periodicSyncsList = ContentResolver.getPeriodicSyncs(account, getAuthority());
        if (deleteExisting)
            for (PeriodicSync p : periodicSyncsList) {
                Log.d("SSYNC", "FORDEL Период: " + p.period + " сек, Extras: " + p.extras);
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


    public void resetSyncTime(){

        AccountManager mAccountManager = AccountManager.get(this);
        for (Account account : mAccountManager.getAccountsByType(getAccountsType())) {
            Log.d("SSYNC", "Reset for : " + account.name + " account");

            // search sync settings // def if NO
            String prefValue = "" + Constants.DEFAULT_SYNC_PERIOD;
            List<PeriodicSync> syncs = ContentResolver.getPeriodicSyncs(account, getAuthority());
            if (null != syncs && !syncs.isEmpty()) {
                for (PeriodicSync sync : syncs) {
                    Bundle bundle = sync.extras;
                    String value = bundle.getString(KEY_PREF_SYNC_PERIOD);
                    if (value != null) {
                        Log.d("SSYNC", " prefValue=  : " + prefValue);
                        prefValue = value;
                        break;
                    }
                }
            } else {
                Log.d("SSYNC", "Reset for : " + account.name + " account = NO ITEMS");
            }


            List<PeriodicSync> syncsToDelete =
                    ContentResolver.getPeriodicSyncs(account, getAuthority());
            for (PeriodicSync sync : syncsToDelete) {
                Log.d("SSYNC", "delete " + sync.toString());
                ContentResolver.removePeriodicSync(
                        account,
                        getAuthority(),
                        sync.extras
                );
            }


            Log.d("SSYNC", "add again " + prefValue);
            Bundle bundle = new Bundle();
            bundle.putString(KEY_PREF_SYNC_PERIOD, prefValue);
            long interval = Long.parseLong(prefValue);
            setSyncPeriod(account, interval, bundle, false);

        }
    }
}
