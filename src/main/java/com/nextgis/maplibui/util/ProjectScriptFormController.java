package com.nextgis.maplibui.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.map.NGWVectorLayer;
import com.nextgis.maplib.map.VectorLayer;
import com.nextgis.maplib.scripts.ProjectScriptHost;
import com.nextgis.maplib.scripts.ScriptPackage;
import com.nextgis.maplib.scripts.ScriptPackageStore;
import com.nextgis.maplib.scripts.ScriptProgram;
import com.nextgis.maplib.scripts.ScriptReference;
import com.nextgis.maplib.scripts.ScriptRunResult;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.DatabaseContext;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.api.IControl;
import org.json.JSONObject;
import org.json.JSONArray;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.LongSupplier;

/** Pins rules to one form, preserves existing control listeners, and discards stale asynchronous notices. */
public final class ProjectScriptFormController {
    private final Activity activity;
    private final VectorLayer layer;
    private final LayerGroup group;
    private final Map<String,IControl> controls;
    private final Supplier<ContentValues> capture;
    private final LongSupplier featureId;
    private final BooleanSupplier saving;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.DiscardOldestPolicy());
    private final AtomicInteger generation = new AtomicInteger();
    private final Set<String> acknowledged = new HashSet<>();
    public final String pinnedReference;
    private volatile boolean closed;
    private ScriptReference reference;
    private ScriptPackage pack;
    private boolean prepared;
    private volatile AlertDialog dialog;
    private Runnable pending;
    private boolean errorShown;

    public ProjectScriptFormController(Activity activity, VectorLayer layer, Map<String,IControl> controls,
            Supplier<ContentValues> capture, LongSupplier featureId, BooleanSupplier saving, String pin) {
        this.activity = activity; this.layer = layer; this.controls = controls;
        this.capture = capture; this.featureId = featureId; this.saving = saving;
        group = layer == null ? null : ProjectScriptHost.projectFor(layer);
        if (pin != null) pinnedReference = pin;
        else if (group == null) pinnedReference = "";
        else {
            var metadata = group.getCollectorProjectMetadata();
            String selected = metadata.getScriptsActive() != null ? metadata.getScriptsActive() : metadata.getScriptsExpected();
            pinnedReference = selected == null ? "" : selected;
        }
    }
    private boolean alive() {
        return !closed && !activity.isDestroyed() && !activity.isFinishing()
                && layer != null && ((com.nextgis.maplib.api.IGISApplication)activity.getApplication()).getMap()
                == DatabaseContext.getMapForLayer(layer);
    }
    public void start() {
        if (pinnedReference.isEmpty() || group == null) return;
        worker.execute(() -> {
            try {
                prepare();
                main.post(() -> {
                    if (!alive()) return;
                    attachListeners();
                    schedule("on_open", null);
                    if ("update_failed".equals(group.getCollectorProjectMetadata().getScriptsStatus()))
                        Toast.makeText(activity, R.string.project_scripts_previous_version, Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) { failed(error, false); }
        });
    }
    private synchronized void prepare() throws Exception {
        if (prepared) return;
        if (pinnedReference.isEmpty()) { prepared = true; return; }
        if (group == null || !(layer instanceof NGWVectorLayer)) throw new IllegalStateException("Collector layer unavailable");
        reference = new ScriptReference(new JSONObject(pinnedReference));
        pack = ScriptPackageStore.load(group, reference);
        prepared = true;
    }
    private void attachListeners() {
        try {
            Set<String> fields = new HashSet<>();
            // Collect watched fields by testing schema fields; this also covers dependent selectors.
            for (Field field : layer.getFields()) {
                if (!pack.hooks(reference, ((NGWVectorLayer)layer).getRemoteId(), "on_field_change", field.getName()).isEmpty())
                    fields.add(field.getName());
            }
            for (String name : fields) {
                Field field = layer.getFieldByName(name);
                View view = RequiredFieldUi.fieldView(field, controls);
                if (view instanceof Spinner) {
                    Spinner spinner = (Spinner)view;
                    AdapterView.OnItemSelectedListener previous = spinner.getOnItemSelectedListener();
                    spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override public void onItemSelected(AdapterView<?> parent, View selected, int position, long id) {
                            if (previous != null) previous.onItemSelected(parent, selected, position, id);
                            schedule("on_field_change", name);
                        }
                        @Override public void onNothingSelected(AdapterView<?> parent) {
                            if (previous != null) previous.onNothingSelected(parent);
                            schedule("on_field_change", name);
                        }
                    });
                } else if (view instanceof TextView) {
                    ((TextView)view).addTextChangedListener(new TextWatcher() {
                        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
                        @Override public void afterTextChanged(Editable editable) { schedule("on_field_change", name); }
                    });
                }
            }
        } catch (Exception error) { failed(error, false); }
    }
    private void schedule(String event, String changedField) {
        if (!alive() || saving.getAsBoolean()) return;
        int ticket = generation.incrementAndGet();
        if (pending != null) main.removeCallbacks(pending);
        pending = () -> {
            if (!alive() || saving.getAsBoolean()) return;
            ContentValues values = capture.get();
            long id = featureId.getAsLong();
            worker.execute(() -> {
                if (closed || generation.get() != ticket) return;
                try {
                    List<Message> messages = runHooks(event, changedField, values, id);
                    main.post(() -> {
                        if (alive() && !saving.getAsBoolean() && generation.get() == ticket)
                            show(messages, false, null);
                    });
                } catch (Exception error) {
                    main.post(() -> { if (alive() && generation.get() == ticket) failed(error, false); });
                }
            });
        };
        main.postDelayed(pending, event.equals("on_open") ? 0 : 350);
    }
    /** Runs on the existing Save worker, before required validation and before any write transaction. */
    public boolean beforeSave(ContentValues values, long id) {
        generation.incrementAndGet();
        if (pinnedReference.isEmpty()) return true;
        try {
            List<Message> messages = runHooks("before_save", null, values, id);
            return awaitDecision(messages);
        } catch (Exception error) {
            Log.w("ProjectScripts", "Before-save script failed", error);
            boolean blocking = reference == null || reference.failClosed;
            List<Message> messages = new ArrayList<>();
            messages.add(new Message("unavailable", activity.getString(blocking
                    ? R.string.project_scripts_unavailable_block : R.string.project_scripts_unavailable), blocking));
            return awaitDecision(messages);
        }
    }
    private boolean awaitDecision(List<Message> messages) {
        if (!alive()) return false;
        CountDownLatch answered = new CountDownLatch(1);
        boolean[] decision = {false};
        main.post(() -> {
            if (!alive()) { answered.countDown(); return; }
            show(messages, true, accepted -> { decision[0] = accepted; answered.countDown(); });
        });
        try {
            while (!answered.await(250, TimeUnit.MILLISECONDS)) if (!alive()) return false;
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
        return decision[0] && alive();
    }
    private synchronized List<Message> runHooks(String event, String changedField, ContentValues values, long id) throws Exception {
        prepare();
        List<Message> messages = new ArrayList<>();
        if (pack == null) return messages;
        for (JSONObject hook : pack.hooks(reference, ((NGWVectorLayer)layer).getRemoteId(), event, changedField)) {
            String alias = hook.getString("layer");
            if (ProjectScriptHost.resolve(group, reference, alias) != layer) throw new SecurityException("Form ownership changed");
            JSONObject fields = snapshot(alias, values, id);
            JSONObject input = new JSONObject().put("event", event).put("changedField", changedField)
                    .put("layer", alias).put("id", id == Constants.NOT_FOUND ? JSONObject.NULL : Long.toString(id))
                    .put("isNew", id == Constants.NOT_FOUND).put("fields", fields);
            byte[] wire = input.toString().getBytes(StandardCharsets.UTF_8);
            if (wire.length > 128*1024) throw new IllegalStateException("Form input too large");
            ProjectScriptHost host = new ProjectScriptHost(group, reference, pack, LocalDate.now());
            ScriptRunResult result = new ScriptRunResult(ProjectScriptClient.execute(activity,
                    ScriptProgram.wrap(pack.source(hook)), wire, host));
            JSONObject context = new JSONObject();
            JSONArray watched = hook.getJSONArray("fields");
            for (int i=0; i<watched.length(); i++) context.put(watched.getString(i), fields.opt(watched.getString(i)));
            String digest = ScriptPackage.sha256(context.toString().getBytes(StandardCharsets.UTF_8));
            for (ScriptRunResult.Notice notice : result.notices)
                messages.add(new Message(hook.getString("id") + ":" + digest + ":" + notice.key + ":" + notice.message,
                        notice.message, notice.blocking));
            if (messages.size() > 32) throw new IllegalStateException("Form notice budget");
        }
        return messages;
    }
    private JSONObject snapshot(String alias, ContentValues values, long id) throws Exception {
        List<Field> fields = new ArrayList<>();
        for (Field field : layer.getFields()) if (pack.canRead(alias, field.getName())) fields.add(field);
        String[] names = new String[fields.size()];
        for (int i=0; i<fields.size(); i++) names[i] = fields.get(i).getName();
        JSONObject result = new JSONObject();
        Cursor stored = id == Constants.NOT_FOUND ? null : layer.query(names, Constants.FIELD_ID + "=?",
                new String[]{Long.toString(id)}, null, "1");
        try {
            boolean exists = stored != null && stored.moveToFirst();
            for (int i=0; i<fields.size(); i++) {
                Field field = fields.get(i);
                String name = field.getName();
                Object value = values.get(name);
                if (!values.containsKey(name) && exists && !stored.isNull(i)) value = ProjectScriptHost.readValue(stored, i, field);
                else if (value != null && field.getType() == GeoConstants.FTDate)
                    value = Instant.ofEpochMilli(((Number)value).longValue()).atOffset(ZoneOffset.UTC).toLocalDate().toString();
                else if (value != null && field.getType() == GeoConstants.FTLong) value = value.toString();
                result.put(name, value == null ? JSONObject.NULL : value);
            }
        } finally { if (stored != null) stored.close(); }
        return result;
    }
    private void show(List<Message> messages, boolean save, java.util.function.Consumer<Boolean> answer) {
        List<Message> visible = new ArrayList<>();
        boolean blocking = false;
        for (Message message : messages) {
            if (message.blocking || !acknowledged.contains(message.key)) visible.add(message);
            blocking |= message.blocking;
        }
        if (visible.isEmpty()) { if (answer != null) answer.accept(true); return; }
        if (dialog != null && dialog.isShowing()) {
            if (!save) return;
            dialog.dismiss();
        }
        StringBuilder text = new StringBuilder();
        for (Message message : visible) { if (text.length() > 0) text.append("\n\n"); text.append(message.text); }
        boolean allow = !blocking;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.project_scripts_notice_title).setMessage(text.toString())
                .setPositiveButton(save && allow ? R.string.project_scripts_continue : android.R.string.ok, (d, which) -> {
                    if (allow) for (Message message : visible) acknowledged.add(message.key);
                    if (answer != null) answer.accept(allow);
                });
        if (save && allow) builder.setNegativeButton(R.string.project_scripts_return, (d, which) -> answer.accept(false));
        builder.setOnCancelListener(d -> { if (answer != null) answer.accept(false); });
        dialog = builder.create();
        dialog.show();
    }
    private void failed(Exception error, boolean save) {
        Log.w("ProjectScripts", "Form script failed", error);
        main.post(() -> {
            if (alive() && !errorShown) {
                errorShown = true;
                Toast.makeText(activity, R.string.project_scripts_unavailable, Toast.LENGTH_LONG).show();
            }
        });
    }
    public void close() {
        closed = true; generation.incrementAndGet();
        if (pending != null) main.removeCallbacks(pending);
        worker.shutdownNow();
        if (dialog != null) dialog.dismiss();
    }
    private static final class Message {
        final String key, text;
        final boolean blocking;
        Message(String key, String text, boolean blocking) { this.key = key; this.text = text; this.blocking = blocking; }
    }
}
