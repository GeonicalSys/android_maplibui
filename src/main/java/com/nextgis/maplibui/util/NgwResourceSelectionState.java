package com.nextgis.maplibui.util;

import android.accounts.AccountManager;
import android.content.Context;
import com.nextgis.maplib.datasource.ngw.Connection;
import com.nextgis.maplib.datasource.ngw.Connections;
import com.nextgis.maplib.datasource.ngw.INGWResource;
import com.nextgis.maplib.datasource.ngw.Resource;
import com.nextgis.maplib.datasource.ngw.ResourceGroup;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.NgwSyncIo;
import com.nextgis.maplibui.dialog.SelectNGWResourceDialog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stable browser identities only: no resource trees, attributes or credentials in a Bundle. */
public final class NgwResourceSelectionState {
    public static final String KEY = "ngw_browser_selection_v1";
    private NgwResourceSelectionState() { }

    public static String forConnection(Connections connections, Connection connection) {
        try {
            return save(connections, connection, Collections.emptyList());
        } catch (JSONException impossible) {
            throw new IllegalStateException("Cannot encode resource identity", impossible);
        }
    }

    public static String save(Connections connections, INGWResource current,
                              List<CheckState> checks) throws JSONException {
        JSONObject state = new JSONObject();
        JSONArray accounts = new JSONArray();
        if (connections != null) {
            for (int i = 0; i < connections.getChildrenCount(); i++) {
                accounts.put(identity(connections.getChild(i)));
            }
        }
        state.put("accounts", accounts).put("current", identity(current));
        JSONArray selected = new JSONArray();
        if (checks != null && connections != null) {
            for (CheckState check : checks) {
                if (!check.isCheckState1() && !check.isCheckState2()) continue;
                INGWResource resource = connections.getResourceById(check.getId());
                if (resource == null) continue;
                selected.put(identity(resource).put("raster", check.isCheckState1())
                        .put("vector", check.isCheckState2()));
            }
        }
        return state.put("selected", selected).toString();
    }

    private static JSONObject identity(INGWResource resource) throws JSONException {
        ArrayList<Long> path = new ArrayList<>();
        INGWResource cursor = resource;
        while (cursor instanceof Resource) {
            path.add(((Resource) cursor).getRemoteId());
            cursor = cursor.getParent();
        }
        Collections.reverse(path);
        JSONObject result = new JSONObject().put("path", new JSONArray(path));
        if (cursor instanceof Connection) {
            Connection account = (Connection) cursor;
            result.put("account", account.getName()).put("url", account.getURL());
        }
        return result;
    }

    public static final class Restored {
        public final Connections connections;
        public INGWResource current;
        public final ArrayList<CheckState> checks = new ArrayList<>();
        Restored(Connections connections) { this.connections = connections; }
    }

    /** Worker-only. Credentials are reacquired from AccountManager. */
    public static Restored restore(Context context, String encoded, boolean skipSubLoad)
            throws JSONException, IOException {
        Connections available = SelectNGWResourceDialog.fillConnections(context, AccountManager.get(context));
        return restore(available, encoded, skipSubLoad);
    }

    static Restored restore(Connections available, String encoded, boolean skipSubLoad)
            throws JSONException, IOException {
        JSONObject state = new JSONObject(encoded);
        Connections connections = new Connections(available.getName());
        JSONArray accounts = state.getJSONArray("accounts");
        for (int i = 0; i < accounts.length(); i++) {
            Connection account = findAccount(available, accounts.getJSONObject(i));
            if (account == null) throw new IOException("Saved account is unavailable");
            connections.add(account);
        }
        Restored restored = new Restored(connections);
        JSONArray selected = state.getJSONArray("selected");
        for (int i = 0; i < selected.length(); i++) {
            JSONObject check = selected.getJSONObject(i);
            INGWResource resource = resolve(connections, check, skipSubLoad, false);
            restored.checks.add(new CheckState(resource.getId(), check.getBoolean("raster"),
                    check.getBoolean("vector")));
        }
        restored.current = resolve(connections, state.getJSONObject("current"), skipSubLoad, true);
        return restored;
    }

    private static Connection findAccount(Connections connections, JSONObject identity) {
        for (int i = 0; i < connections.getChildrenCount(); i++) {
            Connection account = (Connection) connections.getChild(i);
            if (account.getName().equals(identity.optString("account"))
                    && account.getURL().equals(identity.optString("url"))) return account;
        }
        return null;
    }

    private static INGWResource resolve(Connections connections, JSONObject identity,
                                         boolean skipSubLoad, boolean loadCurrent)
            throws JSONException, IOException {
        NgwSyncIo.checkInterrupted();
        if (!identity.has("account")) return connections;
        Connection account = findAccount(connections, identity);
        if (account == null) throw new IOException("Saved account is unavailable");
        if (!account.isConnected() && !account.connect(Constants.NGW_ACCOUNT_GUEST.equals(account.getLogin()))) {
            throw new IOException("Cannot restore resource connection");
        }
        INGWResource current = account;
        JSONArray path = identity.getJSONArray("path");
        for (int i = 0; i < path.length(); i++) {
            NgwSyncIo.checkInterrupted();
            long id = path.getLong(i);
            if (current == account && account.getRootResource().getRemoteId() == id) {
                current = account.getRootResource();
                continue;
            }
            load(current, skipSubLoad);
            INGWResource found = null;
            for (int child = 0; child < current.getChildrenCount(); child++) {
                INGWResource candidate = current.getChild(child);
                if (candidate instanceof Resource && ((Resource) candidate).getRemoteId() == id) {
                    found = candidate;
                    break;
                }
            }
            if (found == null) throw new IOException("Saved resource is unavailable");
            current = found;
        }
        if (loadCurrent) load(current, skipSubLoad);
        return current;
    }

    private static void load(INGWResource resource, boolean skipSubLoad) throws IOException {
        NgwSyncIo.checkInterrupted();
        ResourceGroup group = resource instanceof Connection
                ? ((Connection) resource).getRootResource()
                : resource instanceof ResourceGroup ? (ResourceGroup) resource : null;
        if (group != null) {
            group.loadChildren(skipSubLoad);
            NgwSyncIo.checkInterrupted();
            if (!group.isChildrenLoaded()) throw new IOException("Resource list is unavailable");
        }
    }
}
