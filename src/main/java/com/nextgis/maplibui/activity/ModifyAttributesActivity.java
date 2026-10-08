/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Dmitry Baryshnikov (aka Bishop), bishop.dev@gmail.com
 * Author:   NikitaFeodonit, nfeodonit@yandex.com
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *****************************************************************************
 * Copyright (c) 2015-2016, 2018-2021 NextGIS, info@nextgis.com
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

package com.nextgis.maplibui.activity;

import android.Manifest;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.ProgressDialog;
import android.database.Cursor;
import android.location.Location;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;

import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.keenfin.easypicker.AttachInfo;
import com.keenfin.easypicker.PhotoPicker;
import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.api.GpsEventListener;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.datasource.GeoGeometry;
import com.nextgis.maplib.datasource.GeoGeometryFactory;
import com.nextgis.maplib.datasource.GeoMultiPoint;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.datasource.ngw.Connection;
import com.nextgis.maplib.datasource.ngw.Connections;
import com.nextgis.maplib.location.AccurateLocationTaker;
import com.nextgis.maplib.location.GpsEventSource;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.NGWVectorLayer;
import com.nextgis.maplib.map.TrackLayer;
import com.nextgis.maplib.map.VectorLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.FileUtil;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.util.GeoGeometryUtil;
import com.nextgis.maplib.util.LocationUtil;
import com.nextgis.maplib.util.MapUtil;
import com.nextgis.maplib.util.PermissionUtil;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.api.IControl;
import com.nextgis.maplibui.api.ISimpleControl;
import com.nextgis.maplibui.control.DateTime;
import com.nextgis.maplibui.control.PhotoGallery;
import com.nextgis.maplibui.control.TextEdit;
import com.nextgis.maplibui.control.TextLabel;
import com.nextgis.maplibui.dialog.SelectNGWResourceDialog;
import com.nextgis.maplibui.formcontrol.AutoTextEdit;
import com.nextgis.maplibui.formcontrol.Sign;
import com.nextgis.maplibui.util.ConstantsUI;
import com.nextgis.maplibui.util.ControlHelper;
import com.nextgis.maplibui.util.FeatureFormDraftStore;
import com.nextgis.maplibui.util.RequiredFieldUi;
import com.nextgis.maplibui.util.RequiredFieldValidation;
import com.nextgis.maplibui.util.WalkSessionStore;
import com.nextgis.maplibui.view.WalkRecordingPanel;
import com.nextgis.maplibui.util.NotificationHelper;
import com.nextgis.maplibui.util.PhotoOverlayData;
import com.nextgis.maplibui.util.PhotoOverlayUtil;
import com.nextgis.maplibui.util.SettingsConstantsUI;

import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.keenfin.easypicker.DownloadPhotoIntentService.DOWNLOAD_ACTION;
import static com.keenfin.easypicker.DownloadPhotoIntentService.getReceiverIntent;
import static com.nextgis.maplib.util.Constants.FIELD_GEOM;
import static com.nextgis.maplib.util.Constants.FIELD_ID;
import static com.nextgis.maplib.util.Constants.NOT_FOUND;
import static com.nextgis.maplib.util.Constants.TAG;
import static com.nextgis.maplib.util.LayerUtil.getColumnIndexSafely;
import static com.nextgis.maplib.util.NetworkUtil.getUserAgent;
import static com.nextgis.maplibui.util.ConstantsUI.KEY_ADDED_POINT;
import static com.nextgis.maplibui.util.ConstantsUI.KEY_FEATURE_ID;
import static com.nextgis.maplibui.util.ConstantsUI.KEY_GEOMETRY;
import static com.nextgis.maplibui.util.ConstantsUI.KEY_GEOMETRY_CHANGED;
import static com.nextgis.maplibui.util.ConstantsUI.KEY_LAYER_ID;
import static com.nextgis.maplibui.util.ConstantsUI.KEY_VIEW_ONLY;


/**
 * Activity to add or modify vector layer attributes
 */
public class ModifyAttributesActivity
        extends NGActivity
        implements GpsEventListener
{
    private static final String LOG_FEATURE_SAVE = "FeatureSave";

    protected long PROGRESS_DELAY = 1000L;
    protected long MAX_TAKE_TIME  = Integer.MAX_VALUE;

    protected Map<String, IControl> mFields;
    private Map<String, String> mRequiredFieldCaptions = new HashMap<>();
    protected VectorLayer           mLayer;
    protected long                  mFeatureId;

    protected GeoGeometry           mGeometry;
    protected TextView              mLatView;
    protected TextView              mLongView;
    protected TextView              mAltView;
    protected TextView              mAccView;
    protected SwitchCompat          mAccurateLocation;
    protected AppCompatSpinner      mAccuracyCE;
    protected AlertDialog           mGPSDialog;

    protected Location              mLocation;
    protected SharedPreferences mSharedPreferences;

    protected int mMaxTakeCount;
    protected boolean mIsGeometryChanged;
    protected boolean mIsViewOnly;
    protected SoundPool mSoundPool;
    private int mBeepId;
    /**
     * Save/Discard is terminal for the current form.  Activity.finish() invokes onPause(), so
     * without this guard a successfully cleared draft can be written again immediately.
     */
    private volatile boolean mFormDraftFinalized;
    private volatile boolean mFormSaving;
    private long mLastCheckpointWarning;
    private String mFormOperationId = java.util.UUID.randomUUID().toString();
    private String mScriptReferencePin;
    private com.nextgis.maplibui.util.ProjectScriptFormController mProjectScripts;
    private final android.os.Handler mDraftHandler = new android.os.Handler(Looper.getMainLooper());
    private final Runnable mDraftCheckpoint = new Runnable() {
        @Override public void run() {
            if (!mFormSaving && !mFormDraftFinalized) persistFormDraftIfNeeded();
            mDraftHandler.postDelayed(this, 3000);
        }
    };
    private String mPointSessionId, mWalkSessionId;

    MessageReceiver messageReceiver;
    public WeakReference<PhotoPicker> photoPickerWeakReference = new WeakReference<>(null);

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (!checkEdits()) finish();
            }
        });
        if (savedInstanceState != null)
            mFormOperationId = savedInstanceState.getString("form_save_operation", mFormOperationId);
        if (savedInstanceState != null) mScriptReferencePin = savedInstanceState.getString("project_script_pin");

        setContentView(R.layout.activity_standard_attributes);
        setToolbar(R.id.main_toolbar);

        mPointSessionId = getIntent().getStringExtra(WalkSessionStore.KEY_POINT);
        mWalkSessionId = getIntent().getStringExtra(WalkSessionStore.KEY_SESSION);

        final IGISApplication app = (IGISApplication) getApplication();
        createView(app, savedInstanceState);
        android.widget.Button saveButton = findViewById(R.id.form_save);
        com.nextgis.maplibui.util.FormFieldLayout.styleSaveButton(saveButton);
        findViewById(R.id.form_footer).setVisibility(mIsViewOnly ? View.GONE : View.VISIBLE);
        saveButton.setOnClickListener(view -> runSaveAndFinish());
        if (!mIsViewOnly && mLayer != null) {
            mProjectScripts = new com.nextgis.maplibui.util.ProjectScriptFormController(this, mLayer, mFields,
                    this::captureScriptValues, () -> mFeatureId, () -> mFormSaving, mScriptReferencePin);
            mScriptReferencePin = mProjectScripts.pinnedReference;
            mProjectScripts.start();
        }
        if (WalkSessionStore.load(this) != null) {
            WalkRecordingPanel panel = new WalkRecordingPanel(this);
            panel.setCompact(true);
            ((LinearLayout) findViewById(R.id.root_view)).addView(panel, 1);
        }
        createLocationPanelView(app);
        createSoundPool();

        if (messageReceiver == null)
            messageReceiver = new MessageReceiver(){
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(DOWNLOAD_ACTION)) {
                        if (photoPickerWeakReference != null &&  photoPickerWeakReference.get() != null){
                            photoPickerWeakReference.get().updateStatus(intent);
                        }
                    }
                }
            };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(messageReceiver, getReceiverIntent(), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(messageReceiver, getReceiverIntent());
        }
    }

    protected void createLocationPanelView(final IGISApplication app)
    {
        if (null == mGeometry && mFeatureId == NOT_FOUND) {
            mLatView = findViewById(R.id.latitude_view);
            mLongView = findViewById(R.id.longitude_view);
            mAltView = findViewById(R.id.altitude_view);
            mAccView = findViewById(R.id.accuracy_view);
            final ImageButton refreshLocation = findViewById(R.id.refresh);
            mAccurateLocation = findViewById(R.id.accurate_location);
            mAccurateLocation.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (mAccurateLocation.getTag() == null) {
                        refreshLocation.performClick();
                        mAccurateLocation.setTag(new Object());
                    }
                }
            });

            mAccuracyCE = findViewById(R.id.accurate_ce);
            SpinnerAdapter adapter = mAccuracyCE.getAdapter();
            String def = adapter.getItem(0).toString();
            def = mSharedPreferences.getString(SettingsConstants.KEY_PREF_LOCATION_ACCURATE_CE, def);
            int id = 0;
            for (int i = 0; i < adapter.getCount(); i++) {
                if (adapter.getItem(i).toString().equals(def)) {
                    id = i;
                    break;
                }
            }
            mAccuracyCE.setSelection(id);

            refreshLocation.setOnClickListener(
                    new View.OnClickListener()
                    {
                        @Override
                        public void onClick(View view)
                        {
                            RotateAnimation rotateAnimation = new RotateAnimation(
                                    0, 360, Animation.RELATIVE_TO_SELF, 0.5f,
                                    Animation.RELATIVE_TO_SELF, 0.5f);
                            rotateAnimation.setDuration(500);
                            rotateAnimation.setRepeatCount(1);
                            refreshLocation.startAnimation(rotateAnimation);

                            if (mAccurateLocation.isChecked()) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(ModifyAttributesActivity.this);
                                View layout = View.inflate(ModifyAttributesActivity.this, R.layout.dialog_progress_accurate_location, null);
                                TextView message = layout.findViewById(R.id.message);
                                final ProgressBar progress = layout.findViewById(R.id.progress);
                                final TextView progressPercent = layout.findViewById(R.id.progress_percent);
                                final TextView progressNumber = layout.findViewById(R.id.progress_number);
                                final CheckBox finishBeep = layout.findViewById(R.id.finish_beep);
                                builder.setView(layout);
                                builder.setTitle(R.string.accurate_location);

                                String selected = (String) mAccuracyCE.getSelectedItem();
                                mSharedPreferences.edit().putString(SettingsConstants.KEY_PREF_LOCATION_ACCURATE_CE, selected).apply();
                                final AccurateLocationTaker accurateLocation =
                                        new AccurateLocationTaker(view.getContext(), 100f,
                                                mMaxTakeCount, MAX_TAKE_TIME, PROGRESS_DELAY, selected);

                                progress.setIndeterminate(true);
                                message.setText(R.string.accurate_taking);
                                builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        accurateLocation.cancelTaking();
                                    }
                                });
                                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                                    @Override
                                    public void onCancel(DialogInterface dialog) {
                                        accurateLocation.cancelTaking();
                                    }
                                });
                                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                                    @Override
                                    public void onDismiss(DialogInterface dialog) {
                                        ControlHelper.unlockScreenOrientation(ModifyAttributesActivity.this);
                                    }
                                });

                                final AlertDialog dialog = builder.create();
                                accurateLocation.setOnProgressUpdateListener(new AccurateLocationTaker.OnProgressUpdateListener() {
                                    @SuppressLint("SetTextI18n")
                                    @Override
                                    public void onProgressUpdate(Long... values) {
                                        int value = values[0].intValue();
                                        if (value == 1) {
                                            progress.setIndeterminate(false);
                                            progress.setMax(mMaxTakeCount);
                                        }

                                        if (value > 0)
                                            progress.setProgress(value);

                                        progressPercent.setText(value * 100 / mMaxTakeCount + " %");
                                        progressNumber.setText(value + " / " + mMaxTakeCount);
                                    }
                                });

                                accurateLocation.setOnGetAccurateLocationListener(new AccurateLocationTaker.OnGetAccurateLocationListener() {
                                    @Override
                                    public void onGetAccurateLocation(Location accurateLocation, Long... values) {
                                        dialog.dismiss();
                                        if (finishBeep.isChecked())
                                            playBeep();

                                        setLocationText(accurateLocation);
                                    }
                                });

                                ControlHelper.lockScreenOrientation(ModifyAttributesActivity.this);
                                dialog.setCanceledOnTouchOutside(false);
                                dialog.show();
                                accurateLocation.startTaking();
                            } else if (null != app) {
                                GpsEventSource gpsEventSource = app.getGpsEventSource();
                                Location location = gpsEventSource.getLastKnownLocation();
                                setLocationText(location);
                            }
                        }
                    });
        } else {
            //hide location panel
            ViewGroup rootView = findViewById(R.id.controls_list);
            rootView.removeView(findViewById(R.id.location_panel));
        }
    }

    protected void createSoundPool() {
        mSoundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 100);
        mBeepId = mSoundPool.load(this, R.raw.beep, 1);
    }

    protected void playBeep() {
        mSoundPool.play(mBeepId, 1, 1, 10, 0, 1);
    }

    protected void createView(final IGISApplication app, Bundle savedState)
    {
        //create and fill controls
        Bundle extras = getIntent().getExtras();

        if (extras != null) {
            int layerId = extras.getInt(KEY_LAYER_ID);
            MapBase map = app.getMap();
            mLayer = (VectorLayer) map.getLayerById(layerId);

            if (null != mLayer) {
                mSharedPreferences = mLayer.getPreferences();

                mFields = new HashMap<>();
                mFeatureId = extras.getLong(KEY_FEATURE_ID);
                mIsViewOnly = extras.getBoolean(KEY_VIEW_ONLY, false);
                mIsGeometryChanged = extras.getBoolean(KEY_GEOMETRY_CHANGED, true);
                mGeometry = (GeoGeometry) extras.getSerializable(KEY_GEOMETRY);

                Bundle controlsState = savedState;
                boolean applyDraft = extras.getBoolean(FeatureFormDraftStore.KEY_APPLY_FORM_DRAFT, false);
                FeatureFormDraftStore.Snapshot draft = null;
                if (applyDraft) {
                    draft = FeatureFormDraftStore.load(this);
                    if (draft != null && draft.layerId == layerId && draft.featureId == mFeatureId) {
                        mFormOperationId = draft.operationId;
                        if (draft.scriptReference != null || mScriptReferencePin == null)
                            mScriptReferencePin = draft.scriptReference;
                        controlsState = FeatureFormDraftStore.controlStateToBundle(draft);
                        if (draft.geometryWkt != null) {
                            GeoGeometry fromDraft = FeatureFormDraftStore.geometryFromSnapshot(draft);
                            if (fromDraft != null) {
                                mGeometry = fromDraft;
                                mIsGeometryChanged = draft.geometryChanged;
                            }
                        }
                    } else {
                        draft = null;
                    }
                }

                LinearLayout layout = findViewById(R.id.controls_list);
                fillControls(layout, controlsState);
                if (!mIsViewOnly) refreshRequiredMarkers();
                if (draft != null && draft.photoPaths != null && !draft.photoPaths.isEmpty()) {
                    applyDraftPhotos(draft.photoPaths);
                }
            } else {
                Toast.makeText(this, R.string.error_layer_not_inited, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void applyDraftPhotos(List<String> photoPaths) {
        for (Map.Entry<String, IControl> field : mFields.entrySet()) {
            if (field.getValue() instanceof PhotoGallery) {
                ((PhotoGallery) field.getValue()).restorePendingPhotoPaths(photoPaths);
            }
        }
    }

    protected Cursor getFeatureCursor() {
        Cursor featureCursor = null;
        if (mFeatureId != NOT_FOUND) {
            String selection = FIELD_ID + " = " + mFeatureId;
            featureCursor = mLayer.query(null, selection, null, null, null);
            if (!featureCursor.moveToFirst())
                featureCursor = null;
        }
        return featureCursor;
    }

    protected void fillControls(LinearLayout layout, Bundle savedState)
    {
        Cursor featureCursor = getFeatureCursor();
        List<Field> fields = mLayer.getFields();

        for (Field field : fields) {
            //create static text with alias
            TextLabel textLabel = (TextLabel)getLayoutInflater().inflate(R.layout.template_textlabel, layout, false);
            textLabel.setText(field.getAlias());
            textLabel.addToLayout(layout);

            ISimpleControl control = null;

            //create control
            switch (field.getType()) {
                case GeoConstants.FTString:
                case GeoConstants.FTInteger:
                case GeoConstants.FTLong:
                case GeoConstants.FTReal:
                    TextEdit textEdit = (TextEdit) getLayoutInflater().inflate(R.layout.template_textedit, layout, false);
                    if (mIsViewOnly) {
                        textEdit.setEnabled(false);
                    }
                    // textEdit.setInputType(InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_CLASS_NUMBER);
                    control = textEdit;

                    break;
                case GeoConstants.FTDate:
                case GeoConstants.FTTime:
                case GeoConstants.FTDateTime:
                    DateTime dateTime = (DateTime) getLayoutInflater().inflate(R.layout.template_datetime, layout, false);
                    dateTime.setPickerType(mLayer.getFieldByName(field.getName()).getType());
                    if (mIsViewOnly) {
                        dateTime.setEnabled(false);
                    }
                    control = dateTime;
                    break;
                case GeoConstants.FTBinary:
                case GeoConstants.FTStringList:
                case GeoConstants.FTIntegerList:
                case GeoConstants.FTRealList:
                    //TODO: add support for this types
                    break;

                default:
                    break;
            }

            if (null != control) {
                control.init(field, savedState, featureCursor);
                try {
                    com.nextgis.maplibui.util.FormFieldLayout.addControl(layout, control, null, fields,
                            name -> mLayer.getFieldByName(name).getAlias());
                } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
                String fieldName = control.getFieldName();

                if (null != fieldName) {
                    mFields.put(fieldName, control);
                }
            }
        }

        try {
            PhotoGallery control = (PhotoGallery) getLayoutInflater().inflate(R.layout.formtemplate_photo, layout, false);
            if (mIsViewOnly)
                control = (PhotoGallery) getLayoutInflater().inflate(R.layout.formtemplate_photo_disabled, layout, false);
            control.init(mLayer, mFeatureId);
            control.init(null, null, null, null, null, null, this);
            if (control instanceof  PhotoPicker)
                (control).setUserAgent( getUserAgent(Constants.MAPLIB_USER_AGENT_PART));;
            control.addToLayout(layout);
            mFields.put(control.getFieldName(), control);

            photoPickerWeakReference = new WeakReference<>(control);

            AccountManager accountManager = AccountManager.get(this);
            Connections connections = SelectNGWResourceDialog.fillConnections(this, accountManager);
            Connection found = null;
            if (mLayer instanceof NGWVectorLayer) {
                for (int i = 0; i < connections.getChildrenCount(); i++) {
                    if (connections.getChild(i).getName().equals((((NGWVectorLayer) mLayer).getAccountName()))) {
                        found = (Connection) connections.getChild(i);
                    }
                }
            }
            if (found != null)
                control.setLoginPass(found.getLogin(), found.getPassword());

        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (null != featureCursor) {
            featureCursor.close();
        }
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("form_save_operation", mFormOperationId);
        outState.putString("project_script_pin", mScriptReferencePin);
        saveControlState(outState);
        saveAdditionalFormState(outState);
        super.onSaveInstanceState(outState);
    }

    /** Presentation containers must not affect rotation or durable draft capture. */
    private void saveControlState(Bundle state) {
        java.util.Set<IControl> controls = new java.util.LinkedHashSet<>();
        if (mFields != null) controls.addAll(mFields.values());
        LinearLayout layout = findViewById(R.id.controls_list);
        if (layout != null) for (int i=0; i<layout.getChildCount(); i++)
            if (layout.getChildAt(i) instanceof IControl) controls.add((IControl)layout.getChildAt(i));
        controls.addAll(getSignControls());
        for (IControl control : controls) control.saveState(state);
    }

    /** Extra state is also included in the durable checkpoint, not only Activity Bundle. */
    protected void saveAdditionalFormState(Bundle state) { }


    @Override
    protected void onPause()
    {
        mDraftHandler.removeCallbacks(mDraftCheckpoint);
        if (null != findViewById(R.id.location_panel)) {
            IGISApplication app = (IGISApplication) getApplication();
            if (null != app) {
                GpsEventSource gpsEventSource = app.getGpsEventSource();
                gpsEventSource.removeListener(this);
            }
        }

        if (messageReceiver != null) {
            unregisterReceiver(messageReceiver);
            messageReceiver = null;
        }

        persistFormDraftIfNeeded();
        super.onPause();
    }

    /** Durable crash draft: only when the user has unsaved edits. */
    protected void persistFormDraftIfNeeded() {
        checkpointFormDraft(false);
    }

    private boolean checkpointFormDraft(boolean force) {
        try {
            boolean saved = captureFormDraft(force);
            if (!saved && !mFormDraftFinalized && !mIsViewOnly && mLayer != null && mFields != null)
                warnCheckpointFailure();
            return saved;
        } catch (RuntimeException error) {
            Log.w(TAG, "Cannot capture form checkpoint", error);
            warnCheckpointFailure();
            return false;
        }
    }

    private void warnCheckpointFailure() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (mLastCheckpointWarning == 0 || now - mLastCheckpointWarning >= 60000) {
            showShortToast(R.string.error_form_checkpoint);
            mLastCheckpointWarning = now;
        }
    }

    private boolean captureFormDraft(boolean force) {
        if (mFormDraftFinalized || mIsViewOnly || mLayer == null || mFields == null) {
            return false;
        }
        if (!force && !hasEdits()) return true;
        FeatureFormDraftStore.Snapshot snapshot = new FeatureFormDraftStore.Snapshot();
        snapshot.layerId = mLayer.getId();
        snapshot.featureId = mFeatureId;
        snapshot.operationId = mFormOperationId;
        snapshot.scriptReference = mScriptReferencePin;
        snapshot.mapPath = com.nextgis.maplib.util.DatabaseContext.getMapForLayer(mLayer)
                .getPath().getAbsolutePath();
        snapshot.geometryChanged = mIsGeometryChanged;
        snapshot.pointSessionId = mPointSessionId;
        snapshot.walkSessionId = mWalkSessionId;
        GeoGeometry draftGeometry = getGeometryForDraft();
        if (draftGeometry != null) {
            snapshot.geometryWkt = draftGeometry.toWKT(true);
        }
        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra(ConstantsUI.KEY_FORM_PATH)) {
                Object form = intent.getSerializableExtra(ConstantsUI.KEY_FORM_PATH);
                if (form instanceof File) {
                    snapshot.formPath = ((File) form).getAbsolutePath();
                }
            }
            if (intent.hasExtra(ConstantsUI.KEY_META_PATH)) {
                Object meta = intent.getSerializableExtra(ConstantsUI.KEY_META_PATH);
                if (meta instanceof File) {
                    snapshot.metaPath = ((File) meta).getAbsolutePath();
                }
            }
        }
        Bundle controlState = new Bundle();
        saveControlState(controlState);
        saveAdditionalFormState(controlState);
        FeatureFormDraftStore.putControlStateFromBundle(snapshot, controlState);
        snapshot.photoPaths = new ArrayList<>();
        for (Map.Entry<String, IControl> field : mFields.entrySet()) {
            if (field.getValue() instanceof PhotoGallery) {
                for (AttachInfo info : ((PhotoGallery) field.getValue()).getNewAttaches()) {
                    if (info != null && info.oldAttachString != null) {
                        snapshot.photoPaths.add(info.oldAttachString);
                    }
                }
            }
        }
        return FeatureFormDraftStore.save(this, snapshot);
    }

    /**
     * A new point form can derive geometry from the last location only at Save time.  Persist the
     * same point in the crash journal so recovery does not silently move it to a later GPS fix.
     */
    private GeoGeometry getGeometryForDraft() {
        if (mGeometry != null) {
            return mGeometry;
        }
        if (mFeatureId != NOT_FOUND || mLocation == null || mLayer == null) {
            return null;
        }

        GeoPoint point = new GeoPoint(mLocation.getLongitude(), mLocation.getLatitude());
        point.setCRS(GeoConstants.CRS_WGS84);
        if (!point.project(GeoConstants.CRS_WEB_MERCATOR)) {
            return null;
        }

        if (mLayer.getGeometryType() == GeoConstants.GTPoint) {
            return point;
        }
        if (mLayer.getGeometryType() == GeoConstants.GTMultiPoint) {
            GeoMultiPoint multiPoint = new GeoMultiPoint();
            multiPoint.add(point);
            return multiPoint;
        }
        return null;
    }

    protected void clearFormDraft() {
        mFormDraftFinalized = true;
        FeatureFormDraftStore.clear(this);
        WalkSessionStore.endPoint(this, mPointSessionId);
    }


    @Override
    public void onResume()
    {
        if (null != findViewById(R.id.location_panel)) {
            IGISApplication app = (IGISApplication) getApplication();
            if (null != app) {
                GpsEventSource gpsEventSource = app.getGpsEventSource();
                gpsEventSource.addListener(this);
                if (mGPSDialog == null || !mGPSDialog.isShowing())
                    mGPSDialog = NotificationHelper.showLocationInfo(this);
                setLocationText(gpsEventSource.getLastKnownLocation());
            }

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String def = "20";
            String preferred = prefs.getString(SettingsConstants.KEY_PREF_LOCATION_ACCURATE_COUNT, def);
            mMaxTakeCount = Integer.parseInt(preferred != null ? preferred : def);
        }
        super.onResume();
        mDraftHandler.removeCallbacks(mDraftCheckpoint);
        mDraftHandler.postDelayed(mDraftCheckpoint, 3000);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.edit_attributes, menu);
        if (mIsViewOnly) {
            MenuItem item = menu.findItem(R.id.menu_apply);
            if (item != null)
                item.setVisible(false);
        }
        return true;
    }


    private boolean checkEdits() {
        if (mFormSaving) return true;
        if (hasEdits() && !mIsViewOnly) {
            AlertDialog builder = new AlertDialog.Builder(this)
                    .setTitle(R.string.save)
                    .setMessage(R.string.has_edits)
                    .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            runSaveAndFinish();
                        }
                    })
                    .setNegativeButton(R.string.discard, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            clearFormDraft();
                            finish();
                        }
                    })
                    .setNeutralButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .create();
            builder.show();
            return true;
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            if (!checkEdits())
                finish();
            return true;
        } else if (id == R.id.menu_settings) {
            final IGISApplication app = (IGISApplication) getApplication();
            app.showSettings(SettingsConstantsUI.ACTION_PREFS_GENERAL, -1, null);
            return true;
        } else if (id == R.id.menu_apply) {
            runSaveAndFinish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private boolean hasEdits() {
        boolean result = mFeatureId == NOT_FOUND || mGeometry != null && mIsGeometryChanged;

        if (mLayer == null || mFields == null) return true;

        for (IControl control : mFields.values()) {
            if (control instanceof PhotoGallery) {
                PhotoGallery gallery = (PhotoGallery) control;
                result |= !gallery.getNewAttaches().isEmpty() || !gallery.getDeletedAttaches().isEmpty();
            }
        }
        for (Sign sign : getSignControls()) result |= sign.hasEdits();

        if (!result) {
            try (Cursor featureCursor = mLayer.query(null, FIELD_ID + " = " + mFeatureId,
                    null, null, null)) {
                if (featureCursor == null || !featureCursor.moveToFirst()) return true;

            for (Map.Entry<String, IControl> field : mFields.entrySet()) {
                int column = getColumnIndexSafely(featureCursor, field.getKey()); // featureCursor.getColumnIndex(field.getKey());
                if (column >= 0) {
                    IControl control = field.getValue();
                    String saved = featureCursor.getString(column);
                    Object modified = control.getValue();
                    result = AttributeValueComparator.valuesDiffer(modified, saved);
                }

                if (result)
                    break;
            }

            } catch (RuntimeException error) {
                Log.w(TAG, "Unable to compare form with saved feature", error);
                return true;
            }
        }

        return result;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        String read = Manifest.permission.WRITE_EXTERNAL_STORAGE;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !PermissionUtil.hasPermission(this, read)) {

            new AlertDialog.Builder(this)
                    .setMessage(R.string.no_permission_granted)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        List<String> permslist = new ArrayList<>();
                        permslist.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                        ActivityCompat.requestPermissions(ModifyAttributesActivity.this,
                                permslist.toArray(new String[permslist.size()])
                                , requestCode);
                    })
                    .create()
                    .show();

            return;
        }

        LinearLayout layout = findViewById(R.id.controls_list);
        passResultToGallery(layout, requestCode, resultCode, data);
    }


    private void passResultToGallery(ViewGroup layout, int requestCode, int resultCode, Intent data) {
        for (int i = 0; i < layout.getChildCount(); i++) {
            View child = layout.getChildAt(i);
            if (child instanceof PhotoGallery) {
                PhotoGallery gallery = (PhotoGallery) child;
                gallery.onActivityResult(requestCode, resultCode, data);
            }
            if (child instanceof  ViewGroup) {
                passResultToGallery((ViewGroup) child, requestCode, resultCode, data);
            }
        }
    }


    private void runSaveAndFinish() {
        if (mFormSaving || mFormDraftFinalized) return;
        mFormSaving = true;
        findViewById(R.id.form_save).setEnabled(false);
        ProgressDialog dialog = ProgressDialog.show(this, null,
                getString(R.string.form_save_processing), true, false);
        new Thread(() -> {
            boolean success;
            try { success = saveFeature(); }
            catch (RuntimeException error) {
                Log.e(TAG, "Form save failed", error);
                showShortToast(R.string.error_form_attachments);
                success = false;
            }
            final boolean saved = success;
            runOnUiThread(() -> {
                mFormSaving = false;
                if (!isDestroyed()) findViewById(R.id.form_save).setEnabled(true);
                if (!isDestroyed() && dialog.isShowing()) dialog.dismiss();
                if (saved && !isDestroyed()) finish();
            });
        }, "feature-save").start();
    }

    /** Android controls are read only on the main thread; database/files run on the save worker. */
    protected final <T> T onMain(java.util.function.Supplier<T> work) {
        java.util.function.Supplier<T> currentForm = () -> {
            // An old worker must not read destroyed controls or clear a recreated form's draft.
            // Its pre-save UUID checkpoint can reconcile any database commit on the next retry.
            if (isDestroyed() || isFinishing())
                throw new IllegalStateException("Form activity is no longer active");
            return work.get();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) return currentForm.get();
        java.util.concurrent.FutureTask<T> task = new java.util.concurrent.FutureTask<>(currentForm::get);
        runOnUiThread(task);
        try { return task.get(); }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Form save interrupted", error);
        } catch (java.util.concurrent.ExecutionException error) {
            throw new IllegalStateException("Cannot capture form state", error.getCause());
        }
    }

    protected boolean saveFeature()
    {
        PhotoOverlaySettings overlaySettings = onMain(this::readPhotoOverlaySettings);
        PhotoOverlayData overlayData = overlaySettings.enabled
                ? onMain(() -> buildPhotoOverlayData(overlaySettings)) : null;
        return saveFeatureInternal(overlayData, overlaySettings);
    }


    protected boolean saveFeatureInternal(
            PhotoOverlayData overlayData,
            PhotoOverlaySettings overlaySettings)
    {
        if (mIsViewOnly) {
            return false;
        }

        if (mLayer == null) {
            showShortToast(R.string.error_layer_not_inited);
            return false;
        }

        if (!onMain(() -> checkpointFormDraft(true))) return false;
        ContentValues values = new ContentValues();
        boolean valid = onMain(() -> {
            for (Field field : mLayer.getFields()) {
                putFieldValue(values, field);
                IControl control = mFields.get(field.getName());
                if (control instanceof AutoTextEdit && ((AutoTextEdit) control).isNotFromList())
                    return false;
            }
            return true;
        });
        if (!valid) return false;
        long recoveredSave = com.nextgis.maplib.util.FeatureSaveJournal.find(
                com.nextgis.maplib.util.DatabaseContext.getDatabaseForLayer(mLayer, false),
                mLayer.getPath().getName(), mFormOperationId);
        if (recoveredSave != NOT_FOUND) mFeatureId = recoveredSave;
        if (mProjectScripts != null && !mProjectScripts.beforeSave(values, mFeatureId)) return false;
        if (!onMain(() -> validateFormValues(values))) return false;
        if (!validateRequiredFields(values)) return false;
        GeoGeometry geoGeometry = onMain(() -> putGeometry(values));
        IGISApplication app = (IGISApplication) getApplication();

        if (null == app) {
            throw new IllegalArgumentException("Not a IGISApplication");
        }

        if (app.getMap() != com.nextgis.maplib.util.DatabaseContext.getMapForLayer(mLayer)) {
            showShortToast(R.string.error_layer_not_inited);
            return false;
        }
        long previouslySaved = com.nextgis.maplib.util.FeatureSaveJournal.find(
                com.nextgis.maplib.util.DatabaseContext.getDatabaseForLayer(mLayer, false),
                mLayer.getPath().getName(), mFormOperationId);
        if (previouslySaved != NOT_FOUND) mFeatureId = previouslySaved;

        Uri uri = Uri.parse(
                "content://" + app.getAuthority() + "/" + mLayer.getPath().getName());

        if (mFeatureId == NOT_FOUND && geoGeometry == null) {
            logFeatureSaveFailure("new feature geometry missing", uri, values, null);
            return false;
        }

        boolean error;
        boolean wasNewFeature = mFeatureId == NOT_FOUND;
        if (wasNewFeature) {
            // we need to get proper mFeatureId for new features first
            Uri result = null;
            try {
                result = getContentResolver().insert(uri.buildUpon().appendQueryParameter(
                        com.nextgis.maplib.util.FeatureSaveJournal.URI_PARAMETER,
                        mFormOperationId).build(), values);
            } catch (RuntimeException e) {
                logFeatureSaveException("insert threw", uri, values, geoGeometry, e);
                showDbError(R.string.error_db_insert);
                return false;
            }
            error = result == null;
            if (error) {
                logFeatureSaveFailure("insert returned null", uri, values, geoGeometry);
                showDbError(R.string.error_db_insert);
                //Toast.makeText(this, getText(R.string.error_db_insert), Toast.LENGTH_SHORT).show();
            } else {
                mFeatureId = Long.parseLong(result.getLastPathSegment());
            }
        } else {
            Uri updateUri = ContentUris.withAppendedId(uri, mFeatureId);
            boolean valuesUpdated;
            try {
                valuesUpdated = getContentResolver().update(updateUri, values, null, null) == 1;
            } catch (RuntimeException e) {
                logFeatureSaveException("update threw", updateUri, values, geoGeometry, e);
                showDbError(R.string.error_db_update);
                return false;
            }
            error = !valuesUpdated;
            if (error) {
                logFeatureSaveFailure("update affected 0 rows", updateUri, values, geoGeometry);
                showDbError(R.string.error_db_update);
                //Toast.makeText(this, getText(R.string.error_db_update), Toast.LENGTH_SHORT).show();
            }
        }

        if (error) {
            return false;
        }

        // Checkpoint the assigned id before copying attachments; a failed copy keeps this form open.
        if (!onMain(() -> checkpointFormDraft(true))) return false;
        for (Map.Entry<String, IControl> field : mFields.entrySet()) {
            if (field.getValue() instanceof PhotoGallery
                    && putAttaches((PhotoGallery) field.getValue(), overlayData, overlaySettings) < 0) {
                showShortToast(R.string.error_form_attachments);
                return false;
            }
        }
        if (!putSign()) {
            showShortToast(R.string.error_form_attachments);
            return false;
        }
        Intent data = new Intent();
        data.putExtra(ConstantsUI.KEY_FEATURE_ID, mFeatureId);
        data.putExtra(ConstantsUI.KEY_LAYER_ID, mLayer.getId());
        data.putExtra(ConstantsUI.KEY_WAS_NEW_FEATURE, wasNewFeature);
        if (geoGeometry != null && geoGeometry instanceof GeoPoint)
            data.putExtra(KEY_ADDED_POINT, new double[]{ ((GeoPoint)geoGeometry).getX(), ((GeoPoint)geoGeometry).getY() });
        HyperLog.v(Constants.TAG, "FormSave result ready layer=" + mLayer.getId()
                + " feature=" + mFeatureId + " wasNew=" + wasNewFeature);
        onMain(() -> {
            setResult(RESULT_OK, data);
            if (mWalkSessionId != null) WalkSessionStore.clear(this, mWalkSessionId);
            clearFormDraft();
            return null;
        });
        return !error;
    }


    protected void showDbError(final int messageResId)
    {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showDbErrorOnUiThread(messageResId);
        } else {
            runOnUiThread(() -> showDbErrorOnUiThread(messageResId));
        }
    }


    protected void showDbErrorOnUiThread(int messageResId)
    {
        if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                && isDestroyed())) {
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(messageResId)
                .setPositiveButton(R.string.ok, null)
                .create()
                .show();
    }


    protected void showShortToast(final int messageResId)
    {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(this, getText(messageResId), Toast.LENGTH_SHORT).show();
        } else {
            runOnUiThread(() ->
                    Toast.makeText(this, getText(messageResId), Toast.LENGTH_SHORT).show());
        }
    }


    protected void logFeatureSaveFailure(
            String reason,
            Uri uri,
            ContentValues values,
            GeoGeometry geometry)
    {
        String message = buildFeatureSaveLogMessage(reason, uri, values, geometry);
        Log.w(TAG, message);
        HyperLog.w(TAG, message);
    }


    protected void logFeatureSaveException(
            String reason,
            Uri uri,
            ContentValues values,
            GeoGeometry geometry,
            RuntimeException e)
    {
        String message = buildFeatureSaveLogMessage(reason, uri, values, geometry);
        Log.e(TAG, message, e);
        HyperLog.w(TAG, message + " error=" + e.getClass().getSimpleName()
                + ": " + e.getMessage(), e);
    }


    protected String buildFeatureSaveLogMessage(
            String reason,
            Uri uri,
            ContentValues values,
            GeoGeometry geometry)
    {
        String layerInfo = mLayer == null ? "<null>"
                : "\"" + mLayer.getName() + "\" path=" + mLayer.getPath().getName()
                + " id=" + mLayer.getId()
                + " geomType=" + mLayer.getGeometryType();
        return LOG_FEATURE_SAVE + " " + reason
                + " uri=" + uri
                + " layer=" + layerInfo
                + " featureId=" + mFeatureId
                + " geometry=" + describeGeometry(geometry)
                + " intentGeometry=" + describeGeometry(mGeometry)
                + " geometryChanged=" + mIsGeometryChanged
                + " location=" + describeLocation(mLocation)
                + " values=" + summarizeContentValues(values);
    }


    protected static String describeGeometry(GeoGeometry geometry)
    {
        if (geometry == null) {
            return "<null>";
        }
        return "type=" + geometry.getType() + " valid=" + geometry.isValid();
    }


    protected static String describeLocation(Location location)
    {
        if (location == null) {
            return "<null>";
        }
        return "lat=" + location.getLatitude()
                + " lon=" + location.getLongitude()
                + " acc=" + location.getAccuracy();
    }


    protected static String summarizeContentValues(ContentValues values)
    {
        if (values == null) {
            return "<null>";
        }
        StringBuilder sb = new StringBuilder("{size=").append(values.size()).append(", keys=[");
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.valueSet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            Object value = entry.getValue();
            sb.append(entry.getKey()).append(":");
            if (value instanceof byte[]) {
                sb.append("byte[").append(((byte[]) value).length).append("]");
            } else {
                sb.append(value == null ? "null" : value.getClass().getSimpleName());
            }
        }
        return sb.append("]}").toString();
    }


    protected boolean putSign() {
        java.util.List<Sign> signs = onMain(this::getSignControls);
        for (Sign sign : signs) {
            if (!onMain(sign::hasEdits)) continue;
            File staged = null;
            try {
                staged = File.createTempFile("signature_", ".png", getCacheDir());
                final File signatureFile = staged;
                boolean rendered = onMain(() -> {
                    try { sign.save(sign.getWidth(), sign.getHeight(), true, signatureFile); return true; }
                    catch (IOException | RuntimeException error) {
                        Log.e(TAG, "Cannot stage signature", error); return false;
                    }
                });
                if (!rendered || staged.length() == 0) return false;
                IGISApplication application = (IGISApplication) getApplication();
                Uri base = Uri.parse("content://" + application.getAuthority() + "/"
                        + mLayer.getPath().getName() + "/" + mFeatureId + "/" + Constants.URI_ATTACH);
                boolean hasSign;
                try (Cursor saved = getContentResolver().query(base, null,
                        VectorLayer.ATTACH_ID + " = ?", new String[]{Sign.SIGN_FILE}, null)) {
                    if (saved == null) return false;
                    hasSign = saved.moveToFirst();
                }
                if (!hasSign) {
                    ContentValues values = new ContentValues();
                    values.put(VectorLayer.ATTACH_DISPLAY_NAME, "_signature");
                    values.put(VectorLayer.ATTACH_DESCRIPTION, "_signature");
                    values.put(VectorLayer.ATTACH_MIME_TYPE, "image/png");
                    Uri created = getContentResolver().insert(base, values);
                    if (created == null) return false;
                    values.clear();
                    values.put(VectorLayer.ATTACH_ID, Integer.MAX_VALUE);
                    if (getContentResolver().update(created, values, null, null) != 1) return false;
                }
                if (!copyToStream(Uri.withAppendedPath(base, Sign.SIGN_FILE), staged.getAbsolutePath()))
                    return false;
            } catch (IOException | RuntimeException error) {
                Log.e(TAG, "Cannot save signature", error); return false;
            } finally {
                if (staged != null) staged.delete();
            }
        }
        return true;
    }

    private java.util.List<Sign> getSignControls() {
        java.util.List<Sign> signs = new ArrayList<>();
        collectSigns(findViewById(R.id.controls_list), signs, new java.util.HashSet<>());
        return signs;
    }

    private void collectSigns(View view, java.util.List<Sign> signs, java.util.Set<View> visited) {
        if (view == null || !visited.add(view)) return;
        if (view instanceof Sign) signs.add((Sign) view);
        if (view instanceof com.nextgis.maplibui.formcontrol.Tabs)
            for (View page : ((com.nextgis.maplibui.formcontrol.Tabs) view).getPageLayouts())
                collectSigns(page, signs, visited);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i=0; i<group.getChildCount(); i++) collectSigns(group.getChildAt(i), signs, visited);
        }
    }

    private boolean validateRequiredFields(ContentValues values) {
        List<Field> required = new ArrayList<>();
        for (Field field : onMain(() -> requiredFieldDefinitions(values))) if (field.isRequired()) required.add(field);
        if (required.isEmpty()) return true;

        // An existing value omitted by this form is preserved. An explicitly cleared control
        // must still fail, and a required field on an inactive page is read like every other.
        List<Field> unbound = onMain(() -> {
            List<Field> result = new ArrayList<>();
            for (Field field : required)
                if (!values.containsKey(field.getName())
                        && RequiredFieldUi.fieldView(field, mFields) == null) result.add(field);
            return result;
        });
        ContentValues stored = new ContentValues();
        if (mFeatureId != NOT_FOUND && !unbound.isEmpty()) {
            String[] projection = new String[unbound.size()];
            for (int i = 0; i < unbound.size(); i++) projection[i] = unbound.get(i).getName();
            try (Cursor cursor = mLayer.query(projection, FIELD_ID + " = " + mFeatureId,
                    null, null, null)) {
                if (cursor != null && cursor.moveToFirst())
                    android.database.DatabaseUtils.cursorRowToContentValues(cursor, stored);
            }
        }
        List<Field> missing = new ArrayList<>();
        for (Field field : required) {
            Object value = values.containsKey(field.getName())
                    ? values.get(field.getName()) : stored.get(field.getName());
            if (RequiredFieldValidation.isMissing(value)) missing.add(field);
        }
        if (missing.isEmpty()) return true;
        onMain(() -> {
            StringBuilder message = new StringBuilder(getString(R.string.form_required_fields_message));
            for (Field field : missing) message.append("\n• ").append(
                    mRequiredFieldCaptions.getOrDefault(field.getName(), field.getAlias()));
            View target = RequiredFieldUi.fieldView(missing.get(0), mFields);
            for (Field field : missing) com.nextgis.maplibui.util.FormFieldLayout.showError(
                    RequiredFieldUi.fieldView(field, mFields), getString(R.string.form_fill_required));
            if (target == null) message.append("\n\n").append(
                    getString(R.string.form_required_field_unavailable));
            new AlertDialog.Builder(this)
                    .setTitle(R.string.form_required_fields_title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        if (target != null && !isFinishing() && !isDestroyed())
                            RequiredFieldUi.reveal(findViewById(R.id.controls_list), target,
                                    () -> RequiredFieldUi.focus(target));
                    }).show();
            return null;
        });
        return false;
    }

    protected ContentValues captureScriptValues() {
        ContentValues values = new ContentValues();
        for (Field field : mLayer.getFields()) putFieldValue(values, field);
        return values;
    }

    /** UI-thread validation after checkpoint, before any feature write. */
    protected boolean validateFormValues(ContentValues values) { return true; }

    protected List<Field> requiredFieldDefinitions(ContentValues values) { return mLayer.getFields(); }

    protected void refreshRequiredMarkers() {
        if (!mIsViewOnly) mRequiredFieldCaptions=RequiredFieldUi.decorate(requiredFieldDefinitions(captureScriptValues()),mFields);
    }

    @Override protected void onDestroy() {
        if (mProjectScripts != null) mProjectScripts.close();
        super.onDestroy();
    }

    protected Object putFieldValue(
            ContentValues values,
            Field field)
    {
        String fieldName = field.getName();
        IControl control = mFields.get(fieldName);

        if (null == control) {
            return null;
        }

        Object value = control.getValue();
        //fieldName = "'" + fieldName + "'"; // no need

        if (null != value) {
            //Log.d(TAG, "field: " + field.getName() + " value: " + value.toString());

            if (value instanceof Long) {
                values.put(fieldName, (Long) value);
            } else if (value instanceof Integer) {
                values.put(fieldName, (Integer) value);
            } else if (value instanceof String) {
                values.put(fieldName, (String) value);
            } else if (value instanceof Double) {
                values.put(fieldName, (Double) value);
            } else if (value instanceof Float) {
                values.put(fieldName, (Float) value);
            }
        }

        return value;
    }


    protected GeoGeometry putGeometry(ContentValues values)
    {
        GeoGeometry geometry = null;

        if (null != mGeometry && mIsGeometryChanged) {
            geometry = mGeometry;
        } else if (NOT_FOUND == mFeatureId) {
            if (null == mLocation) {
                showShortToast(R.string.error_no_location);
                return null;
            }

            GeoPoint pt = new GeoPoint(mLocation.getLongitude(), mLocation.getLatitude());
            pt.setCRS(GeoConstants.CRS_WGS84);
            pt.project(GeoConstants.CRS_WEB_MERCATOR);

            switch (mLayer.getGeometryType()) {
                case GeoConstants.GTPoint:
                    geometry = pt;
                    break;
                case GeoConstants.GTMultiPoint:
                    geometry = new GeoMultiPoint();
                    ((GeoMultiPoint) geometry).add(pt);
                    break;
            }
        }

        if (null != geometry) {
            try {
                values.put(FIELD_GEOM, geometry.toBlob());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return geometry;
    }

    protected int putAttaches(
            PhotoGallery gallery,
            PhotoOverlayData overlayData,
            PhotoOverlaySettings overlaySettings) {
        int total = 0;
        if (gallery != null && mFeatureId != NOT_FOUND) {
            List<Integer> deletedAttaches = onMain(gallery::getDeletedAttaches);
            IGISApplication application = (IGISApplication) getApplication();
            Uri uri = Uri.parse("content://" + application.getAuthority() + "/" +
                    mLayer.getPath().getName() + "/" + mFeatureId + "/" + Constants.URI_ATTACH);

            int size = deletedAttaches.size();
            String[] args = new String[size];
            for (int i = 0; i < size; i++) {
                args[i] = deletedAttaches.get(i).toString();
                Uri uriTMP = Uri.parse("content://" + application.getAuthority() + "/" +
                        mLayer.getPath().getName() + "/" + mFeatureId + "/" + Constants.URI_ATTACH + "/"
                        + args[i]);
                total += getContentResolver().delete(uriTMP, MapUtil.makePlaceholders(1), args);
            }

//            if (size > 0)
//                total += getContentResolver().delete(uri, MapUtil.makePlaceholders(size), args);

            if (total != size && size > 0) {
                showShortToast(com.keenfin.easypicker.R.string.photo_fail_attach);
                Log.d(TAG, "attach delete failed");
                return -1;
            } else {
                Log.d(TAG, "attach delete success: " + total);
            }

            List<AttachInfo> imagesPath = onMain(gallery::getNewAttaches);
            String comment = onMain(gallery::getComment);
            for (AttachInfo path : imagesPath) {
                if (path == null || path.oldAttachString == null) return -1;
                String pathString = path.oldAttachString;
                String[] segments = pathString.split("/");
                String name = segments.length > 0 ? segments[segments.length - 1] : "image.jpg";
                if (name.contains("%3A"))
                    name = name.split("%3A")[1];
                if (!name.contains("."))
                    name = name + ".jpg";
                ContentValues values = new ContentValues();
                values.put(VectorLayer.ATTACH_DISPLAY_NAME, name);
                if (comment != null && !comment.isEmpty())
                    values.put(VectorLayer.ATTACH_DESCRIPTION, comment);
                values.put(VectorLayer.ATTACH_MIME_TYPE, "image/jpeg");

                //Log.e(TAG, "modify insert " + uri.toString() + " values: " + values.toString());
                Uri result = getContentResolver().insert(uri.buildUpon().appendQueryParameter(
                        com.nextgis.maplib.util.FeatureSaveJournal.URI_PARAMETER,
                        mFormOperationId + ":photo:" + pathString).build(), values);
                if (result == null) {
                    showShortToast(com.keenfin.easypicker.R.string.photo_fail_attach);
                    Log.d(TAG, "attach insert failed");
                    return -1;
                } else {
                    if (!copyAttachmentToStream(result, pathString, overlayData, overlaySettings))
                        return -1;
                    total++;

                    Log.d(TAG, "attach insert success: " + result.toString());
                }
            }
        }

        return total;
    }

    protected boolean copyAttachmentToStream(Uri uri, String path,
            PhotoOverlayData overlayData, PhotoOverlaySettings overlaySettings) {
        boolean process = overlaySettings != null && overlaySettings.enabled
                && overlayData != null && overlayData.hasContent();
        if (!process) return copyToStream(uri, path);
        File processedFile = null;
        try {
            processedFile = File.createTempFile("photo_attach_", ".jpg", getCacheDir());
            if (PhotoOverlayUtil.processToFile(this, path, processedFile, overlayData,
                    overlaySettings.coordFormat, overlaySettings.coordFraction))
                return copyToStream(uri, processedFile.getAbsolutePath());
            return copyToStream(uri, path);
        } catch (IOException | RuntimeException error) {
            Log.e(TAG, "Photo processing failed", error);
            return false;
        } finally { if (processedFile != null) processedFile.delete(); }
    }

    private PhotoOverlaySettings readPhotoOverlaySettings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        PhotoOverlaySettings settings = new PhotoOverlaySettings();
        settings.enabled = prefs.getBoolean(
                SettingsConstantsUI.KEY_PREF_PHOTO_OVERLAY_ENABLED,
                SettingsConstantsUI.DEFAULT_PHOTO_OVERLAY_ENABLED);
        settings.useObjectCoords = prefs.getBoolean(
                SettingsConstantsUI.KEY_PREF_PHOTO_OVERLAY_USE_OBJECT,
                SettingsConstantsUI.DEFAULT_PHOTO_OVERLAY_USE_OBJECT);
        settings.showTime = prefs.getBoolean(
                SettingsConstantsUI.KEY_PREF_PHOTO_OVERLAY_SHOW_TIME, false);
        String def = Location.FORMAT_DEGREES + "";
        String preferred = prefs.getString(SettingsConstantsUI.KEY_PREF_COORD_FORMAT, def);
        settings.coordFormat = Integer.parseInt(preferred != null ? preferred : def);
        settings.coordFraction = prefs.getInt(SettingsConstantsUI.KEY_PREF_COORD_FRACTION, 6);
        return settings;
    }

    private PhotoOverlayData buildPhotoOverlayData(PhotoOverlaySettings settings) {
        PhotoOverlayData data = new PhotoOverlayData();
        if (settings.showTime) {
            data.timestamp = System.currentTimeMillis();
        }

        if (settings.useObjectCoords) {
            double[] wgs = resolveObjectWgs84Point();
            if (wgs != null) {
                data.longitude = wgs[0];
                data.latitude = wgs[1];
            }
        } else {
            IGISApplication app = (IGISApplication) getApplication();
            if (app != null) {
                Location location = app.getGpsEventSource().getLastKnownLocation();
                if (location != null) {
                    data.latitude = location.getLatitude();
                    data.longitude = location.getLongitude();
                    if (settings.showTime) {
                        data.timestamp = location.getTime();
                    }
                }
            }
        }
        return data;
    }

    private double[] resolveObjectWgs84Point() {
        GeoGeometry geometry = resolveFeatureGeometry();
        if (geometry == null) {
            return null;
        }
        return GeoGeometryUtil.getWgs84RepresentativePoint(geometry);
    }

    private GeoGeometry resolveFeatureGeometry() {
        if (mGeometry != null && mIsGeometryChanged) {
            return mGeometry;
        }

        if (mFeatureId != NOT_FOUND) {
            Cursor cursor = mLayer.query(
                    new String[]{FIELD_GEOM},
                    FIELD_ID + " = " + mFeatureId,
                    null,
                    null,
                    null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        byte[] blob = cursor.getBlob(0);
                        if (blob != null) {
                            try {
                                return GeoGeometryFactory.fromBlob(blob);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        }

        if (mGeometry != null) {
            return mGeometry;
        }

        if (mLocation != null) {
            GeoPoint pt = new GeoPoint(mLocation.getLongitude(), mLocation.getLatitude());
            pt.setCRS(GeoConstants.CRS_WGS84);
            return pt;
        }

        return null;
    }

    private boolean hasNewPhotoAttaches() {
        for (Map.Entry<String, IControl> field : mFields.entrySet()) {
            if (field.getKey().startsWith(PhotoGallery.GALLERY_PREFIX)
                    && field.getValue() instanceof PhotoGallery) {
                if (!((PhotoGallery) field.getValue()).getNewAttaches().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    protected static class PhotoOverlaySettings {
        boolean enabled;
        boolean useObjectCoords;
        boolean showTime;
        int coordFormat;
        int coordFraction;
    }

    protected boolean copyToStream(Uri uri, String path) {
        java.util.List<String> parts = uri.getPathSegments();
        if (parts.size() != 4 || !parts.get(0).equals(mLayer.getPath().getName())) return false;
        File target = new File(new File(mLayer.getPath(), parts.get(1)), parts.get(3));
        android.util.AtomicFile atomic = new android.util.AtomicFile(target);
        java.io.FileOutputStream out = null;
        try (InputStream in = path.startsWith("/") ? new FileInputStream(path)
                : getContentResolver().openInputStream(Uri.parse(path))) {
            if (in == null) return false;
            out = atomic.startWrite();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            atomic.finishWrite(out);
            return true;
        } catch (IOException | RuntimeException error) {
            if (out != null) atomic.failWrite(out);
            Log.e(TAG, "Cannot copy attachment", error);
            return false;
        }
    }

    protected void setLocationText(Location location)
    {
        if (null == mLatView || null == mLongView || null == mAccView || null == mAltView)
            return;

        if (null == location) {
            mLatView.setText(formatCoordinates(Double.NaN, R.string.latitude_caption_short));
            mLongView.setText(formatCoordinates(Double.NaN, R.string.longitude_caption_short));
            mAltView.setText(formatMeters(Double.NaN, R.string.altitude_caption_short));
            mAccView.setText(formatMeters(Double.NaN, R.string.accuracy_caption_short));
            return;
        }

        mLocation = location;
        mLatView.setText(formatCoordinates(location.getLatitude(), R.string.latitude_caption_short));
        mLongView.setText(formatCoordinates(location.getLongitude(), R.string.longitude_caption_short));

        mAltView.setText(formatMeters(location.getAltitude(), R.string.altitude_caption_short));
        mAccView.setText(formatMeters((double) location.getAccuracy(), R.string.accuracy_caption_short));
    }

    private String formatCoordinates(Double value, int caption) {
        String appendix;
        if (!value.isNaN()) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String def = Location.FORMAT_DEGREES + "";
            String preferred = prefs.getString(SettingsConstantsUI.KEY_PREF_COORD_FORMAT, def);
            int nFormat = Integer.parseInt( preferred != null ? preferred : def);
            int nFraction = prefs.getInt(SettingsConstantsUI.KEY_PREF_COORD_FRACTION, 6);
            appendix = LocationUtil.formatLatitude(value, nFormat, nFraction, getResources());
        } else
            appendix = getString(R.string.n_a);

        return getString(caption) + ": " + appendix;
    }


    private String formatMeters(Double value, int caption) {
        String appendix;
        if (!value.isNaN()) {
            DecimalFormat df = new DecimalFormat("0.0");
            appendix = df.format(value) + " " + getString(com.nextgis.maplib.R.string.unit_meter);
        } else
            appendix = getString(R.string.n_a);

        return getString(caption) + ": " + appendix;
    }


    @Override
    public void onLocationChanged(Location location)
    {
        setLocationText(location);
    }


    @Override
    public void onBestLocationChanged(Location location)
    {
        setLocationText(location);
    }


    @Override
    public void onGpsStatusChanged(int event)
    {

    }

    public class MessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {}
    }
}
