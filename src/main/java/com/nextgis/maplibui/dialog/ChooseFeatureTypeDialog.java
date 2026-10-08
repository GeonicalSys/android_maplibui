package com.nextgis.maplibui.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.map.VectorLayer;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.util.FeatureTypeDefaults;
import com.nextgis.maplibui.util.FeatureTypePreview;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** Fragment-owned result survives rotation; a choice never starts drawing before it is accepted. */
public final class ChooseFeatureTypeDialog extends DialogFragment {
    public static final String TAG = "choose_feature_type", RESULT = "feature_type_choice";
    private boolean completed;
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor();
    private Future<?> loadTask;

    public static ChooseFeatureTypeDialog create(VectorLayer layer, String mapPath) {
        ChooseFeatureTypeDialog dialog = new ChooseFeatureTypeDialog();
        Bundle args = new Bundle(); args.putInt("layer", layer.getId()); args.putString("map", mapPath);
        dialog.setArguments(args); return dialog;
    }

    @NonNull @Override public Dialog onCreateDialog(Bundle saved) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext()).setTitle(R.string.choose_feature_type)
                .setNegativeButton(R.string.cancel, (dialog, which) -> result(null));
        IGISApplication app = (IGISApplication)requireActivity().getApplication();
        VectorLayer layer = (VectorLayer)app.getMap().getLayerById(requireArguments().getInt("layer"));
        List<FeatureTypeDefaults.Choice> choices = new ArrayList<>();
        ArrayAdapter<FeatureTypeDefaults.Choice> adapter = new ArrayAdapter<>(requireContext(), R.layout.row_feature_type, choices) {
            @NonNull @Override public View getView(int position, View view, @NonNull ViewGroup parent) {
                if (view == null) view = LayoutInflater.from(getContext()).inflate(R.layout.row_feature_type, parent, false);
                FeatureTypeDefaults.Choice choice = getItem(position);
                ((TextView)view.findViewById(R.id.tvName)).setText(choice.label);
                ((ImageView)view.findViewById(R.id.ivIcon)).setImageBitmap(FeatureTypePreview.render(getContext(), choice.style));
                view.setAlpha(choice.state == null ? 0.5f : 1f);
                return view;
            }
        };
        builder.setAdapter(adapter, null);
        android.widget.ProgressBar progress = new android.widget.ProgressBar(requireContext());
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(ignored -> {
            ListView list = dialog.getListView();
            // Keep one dialog content panel so long lists cannot push Cancel off screen.
            list.addHeaderView(progress, null, false);
            list.setOnItemClickListener((parent, view, index, id) -> {
                Object item = parent.getItemAtPosition(index);
                if (!(item instanceof FeatureTypeDefaults.Choice)) return;
                Bundle state = ((FeatureTypeDefaults.Choice)item).state;
                if (state == null) {
                    Toast.makeText(requireContext(), R.string.feature_type_unavailable, Toast.LENGTH_LONG).show(); return;
                }
                result(state); dismiss();
            });
            android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
            loadTask = LOADER.submit(() -> {
                try {
                    List<FeatureTypeDefaults.Choice> loaded = FeatureTypeDefaults.choices(layer);
                    if (loaded.isEmpty()) throw new IllegalStateException("No concrete category");
                    main.post(() -> {
                        if (!isAdded() || completed || !dialog.isShowing()) return;
                        if (!app.getMap().getPath().getAbsolutePath().equals(requireArguments().getString("map"))) {
                            result(null); dismiss(); return;
                        }
                        list.removeHeaderView(progress);
                        choices.addAll(loaded); adapter.notifyDataSetChanged();
                    });
                } catch (Exception error) {
                    com.hypertrack.hyperlog.HyperLog.w(com.nextgis.maplib.util.Constants.TAG, "Cannot prepare feature types", error);
                    main.post(() -> {
                        if (!isAdded() || completed || !dialog.isShowing()) return;
                        list.removeHeaderView(progress);
                        TextView message = new TextView(requireContext());
                        message.setText(R.string.feature_type_unavailable);
                        message.setTextSize(17);
                        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
                        message.setPadding(padding, padding, padding, padding);
                        list.addHeaderView(message, null, false);
                    });
                }
            });
        });
        return dialog;
    }

    @Override public void onDestroyView() {
        if (loadTask != null) loadTask.cancel(true);
        super.onDestroyView();
    }

    private void result(Bundle state) {
        if (completed) return;
        completed = true;
        Bundle result = new Bundle(requireArguments());
        if (state != null) result.putBundle(FeatureTypeDefaults.INITIAL_VALUES, state);
        getParentFragmentManager().setFragmentResult(RESULT, result);
    }
    @Override public void onCancel(@NonNull DialogInterface dialog) { result(null); super.onCancel(dialog); }
}
