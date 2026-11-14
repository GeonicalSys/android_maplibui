package com.nextgis.maplibui.util;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.datasource.ngw.Connection;
import com.nextgis.maplib.map.*;
import com.nextgis.maplib.util.*;
import com.nextgis.maplibui.mapui.LocalChangesWorker;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Read-only pages never open a background map, migrate it or replace the displayed workspace. */
public final class LocalChangesRepository {
    private LocalChangesRepository() { }
    public enum State { LOCAL,QUEUED,SENDING,REVIEW,CONFIRMED,MISSING,READ_ERROR }
    public enum Action { NONE,SEND,REVIEW,FINISH,RECOVERY,FORM }
    public static final class Row {
        public final String projectUid,layer,layerPath,layerName,operation,identity,label,attachmentName,reason;
        public final long changeId,featureId,attachmentId,remoteId,updated;
        public final State state;
        public final Action action;
        public final boolean queued;
        Row(String uid,LayerInfo info,long change,long feature,long attachment,String operation,String identity,
                String label,String attachmentName,String reason,long remote,long updated,State state,Action action,boolean queued) {
            projectUid=uid;layer=info.table;layerPath=info.path;layerName=info.name;changeId=change;featureId=feature;
            attachmentId=attachment;this.operation=operation;this.identity=identity;this.label=label;this.attachmentName=attachmentName;
            this.reason=reason;remoteId=remote;this.updated=updated;this.state=state;this.action=action;this.queued=queued;
        }
    }
    public static final class Page {
        public final CollectorProjectRegistry.ProjectInfo project;
        public final List<Row> rows;
        public final long total,queueCount,confirmedCount,reviewCount,lastFullSync,offset;
        public final boolean recoveryHeld,deliveryBlocked;
        Page(CollectorProjectRegistry.ProjectInfo project,List<Row> rows,long total,long queue,long confirmed,long review,
                long lastFullSync,long offset,boolean recoveryHeld,boolean deliveryBlocked) {
            this.project=project;this.rows=Collections.unmodifiableList(rows);this.total=total;queueCount=queue;
            confirmedCount=confirmed;reviewCount=review;this.lastFullSync=lastFullSync;this.offset=offset;this.recoveryHeld=recoveryHeld;
            this.deliveryBlocked=deliveryBlocked;
        }
        public boolean hasMore() {return offset+rows.size()<total;}
    }
    private static final class LayerInfo {
        final String table,path,name,target,unavailable;
        final File directory;
        final boolean outbound;
        LayerInfo(Context context,File workspace,String table,File directory,JSONObject config) throws Exception {
            this.table=table;this.directory=directory;
            path=directory==null?null:workspace.toPath().relativize(directory.toPath()).toString().replace(File.separatorChar,'/');
            name=config==null?table:config.optString("name",table);
            String cause=null,binding=null;boolean allowed=false;
            if(config==null)cause="layer_missing";
            else {
                int type=config.optInt("ngw_layer_type",Connection.NGWResourceTypeNone);
                if(type!=Connection.NGWResourceTypeVectorLayer&&type!=Connection.NGWResourceTypePostgisLayer)cause="read_only_layer";
                else if((config.optInt("sync_type",Constants.SYNC_NONE)&Constants.SYNC_NONE)!=0
                        ||(config.optInt("sync_direction",3)&1)==0)cause="send_disabled";
                IGISApplication app=(IGISApplication)context.getApplicationContext();String accountName=config.optString("account","");
                android.accounts.Account account=app.getAccount(accountName);
                if(account==null)cause="account_missing";
                else binding=UploadOperationJournal.target(workspace,table,accountName,app.getAccountUrl(account),
                        app.getAccountLogin(account),config.optLong("id"));
                allowed=cause==null;
            }
            unavailable=cause;outbound=allowed;target=binding;
        }
    }
    public static CollectorProjectRegistry.ProjectInfo project(Context context,String uid) throws IOException {
        for(CollectorProjectRegistry.ProjectInfo project:CollectorProjectRegistry.listProjects(context))
            if(project.getProjectUid().equals(uid))return project;
        throw new IOException("Project identity is no longer registered");
    }
    private static String quoted(String name) {return "\""+name.replace("\"","\"\"")+"\"";}
    private static boolean tableExists(SQLiteDatabase db,String name) {
        try(Cursor c=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",new String[]{name})) {return c.moveToFirst();}
    }
    private static Set<String> columns(SQLiteDatabase db,String name) {
        Set<String> result=new HashSet<>();
        try(Cursor c=db.rawQuery("PRAGMA table_info("+quoted(name)+")",null)) {while(c.moveToNext())result.add(c.getString(1));}
        return result;
    }
    private static boolean changesTable(SQLiteDatabase db,String name) {
        Set<String> fields=columns(db,name);
        return fields.containsAll(Arrays.asList(Constants.FIELD_ID,Constants.FIELD_FEATURE_ID,Constants.FIELD_OPERATION,
                Constants.FIELD_ATTACH_ID,Constants.FIELD_ATTACH_OPERATION));
    }
    private static final int DATA=Constants.CHANGE_OPERATION_NEW|Constants.CHANGE_OPERATION_CHANGED|Constants.CHANGE_OPERATION_DELETE;
    private static String operations() {
        return "(("+Constants.FIELD_OPERATION+" & "+Constants.CHANGE_OPERATION_ATTACH+")=0 AND ("+Constants.FIELD_OPERATION+" & "+DATA+")!=0) OR (("
                +Constants.FIELD_OPERATION+" & "+Constants.CHANGE_OPERATION_ATTACH+")!=0 AND ("+Constants.FIELD_ATTACH_OPERATION+" & "+DATA+")!=0)";
    }
    private static String operation(int flags,int attachmentFlags) {
        boolean attachment=(flags&Constants.CHANGE_OPERATION_ATTACH)!=0;int value=attachment?attachmentFlags:flags;
        String name=(value&Constants.CHANGE_OPERATION_DELETE)!=0?"delete":(value&Constants.CHANGE_OPERATION_NEW)!=0?"create"
                :(value&Constants.CHANGE_OPERATION_CHANGED)!=0?"update":"unknown";
        return attachment?"attachment_"+name:name;
    }
    private static String label(SQLiteDatabase db,LayerInfo layer,long feature) {
        if(!tableExists(db,layer.table)||!columns(db,layer.table).contains("name"))return "";
        try(Cursor c=db.rawQuery("SELECT substr(CAST(name AS TEXT),1,120) FROM "+quoted(layer.table)+" WHERE _id=?",new String[]{Long.toString(feature)})) {
            return c.moveToFirst()&&!c.isNull(0)?c.getString(0):"";
        }
    }
    private static String attachmentName(LayerInfo layer,long feature,long attachment) {
        if(layer.directory==null||attachment<0)return "";
        try {
            File file=new File(layer.directory,feature+"/meta.json");UnderlayFiles.requireChild(layer.directory,file);
            if(!file.isFile()||Files.isSymbolicLink(file.toPath()))return "";
            JSONArray items;
            try(InputStream input=new FileInputStream(file)) {items=new JSONArray(new String(UnderlayFiles.readBounded(input,4*1024*1024),StandardCharsets.UTF_8));}
            for(int i=0;i<items.length();i++) {
                JSONObject item=items.getJSONObject(i);
                if(Long.toString(attachment).equals(item.optString("id"))) {
                    String value=item.optString("display_name","");
                    return value.codePointCount(0,value.length())>120?value.substring(0,value.offsetByCodePoints(0,120))+"…":value;
                }
            }
        }catch(IOException|JSONException ignored) { }
        return "";
    }
    private static boolean featureExists(SQLiteDatabase db,LayerInfo layer,long feature) {
        if(!tableExists(db,layer.table))return false;
        try(Cursor c=db.rawQuery("SELECT 1 FROM "+quoted(layer.table)+" WHERE _id=?",new String[]{Long.toString(feature)})) {return c.moveToFirst();}
    }
    private static Row queued(Context context,String uid,SQLiteDatabase db,LayerInfo layer,String changes,Cursor c,boolean held,boolean blocked) {
        long id=c.getLong(c.getColumnIndexOrThrow(Constants.FIELD_ID)),feature=c.getLong(c.getColumnIndexOrThrow(Constants.FIELD_FEATURE_ID));
        long attachment=c.getLong(c.getColumnIndexOrThrow(Constants.FIELD_ATTACH_ID));
        String operation=operation(c.getInt(c.getColumnIndexOrThrow(Constants.FIELD_OPERATION)),c.getInt(c.getColumnIndexOrThrow(Constants.FIELD_ATTACH_OPERATION)));
        String identity=c.isNull(c.getColumnIndexOrThrow(FeatureChanges.OPERATION_UUID))?null:c.getString(c.getColumnIndexOrThrow(FeatureChanges.OPERATION_UUID));
        UploadOperationJournal.Entry receipt=UploadOperationJournal.metadata(db,layer.table,identity);
        State state=State.QUEUED;Action action=Action.SEND;String reason="awaiting_upload";
        if(receipt!=null&&UploadOperationJournal.isInFlight(db,layer.table,receipt.id)) {state=State.SENDING;action=Action.NONE;reason="request_in_progress";}
        else if(layer.directory==null) {state=State.MISSING;action=Action.RECOVERY;reason="layer_missing";}
        else if(FeatureSaveJournal.isUploadHeld(db,layer.table,feature)||FeatureChanges.hasFeatureTempFlag(db,changes,feature)
                ||attachment>=0&&FeatureChanges.hasAttachTempFlag(db,changes,feature,attachment)) {
            state=State.LOCAL;action=Action.FORM;reason="finish_form";
        } else if(FeatureChanges.hasFeatureNotSyncFlag(db,changes,feature)
                ||attachment>=0&&FeatureChanges.hasAttachNotSyncFlag(db,changes,feature,attachment)) {
            state=State.LOCAL;action=Action.NONE;reason="local_only";
        } else if(blocked) {state=State.LOCAL;action=Action.NONE;reason="recovered_source";
        } else if(held) {
            state=State.REVIEW;action=Action.REVIEW;reason=receipt!=null&&UploadOperationJournal.RETRY_ALLOWED.equals(receipt.state)
                    &&layer.target!=null&&layer.target.equals(receipt.target)?"review_approved":"restored_project";
        } else if(!layer.outbound) {state=State.LOCAL;action=Action.NONE;reason=layer.unavailable;}
        else if(receipt!=null&&layer.target!=null&&!layer.target.equals(receipt.target)) {state=State.REVIEW;action=Action.RECOVERY;reason="target_changed";}
        else if(receipt!=null&&UploadOperationJournal.LOCAL_MISSING.equals(receipt.state)
                ||!operation.endsWith("delete")&&!featureExists(db,layer,feature)) {state=State.MISSING;action=Action.RECOVERY;reason="local_row_missing";}
        else if(receipt!=null&&UploadOperationJournal.READ_FAILED.equals(receipt.state)) {state=State.READ_ERROR;reason=receipt.reason;}
        else if(receipt!=null&&receipt.needsReconciliation()) {
            state=State.REVIEW;action=receipt.remoteId>=0?Action.FINISH:Action.REVIEW;reason=receipt.reason;
        } else if(receipt!=null&&UploadOperationJournal.CONFIRMED.equals(receipt.state)) {state=State.REVIEW;action=Action.RECOVERY;reason="inconsistent_acknowledgement";}
        else if(identity==null&&UploadOperationJournal.nonIdempotent(operation)) {state=State.REVIEW;action=Action.REVIEW;reason="legacy_creation_outcome_unknown";}
        else if(receipt!=null)reason=receipt.reason;
        return new Row(uid,layer,id,feature,attachment,operation,identity,label(db,layer,feature),attachmentName(layer,feature,attachment),reason,
                receipt==null?-1:receipt.remoteId,receipt==null?0:receipt.updated,state,action,true);
    }
    private static Row history(String uid,LayerInfo layer,UploadOperationJournal.Entry receipt) {
        State state=State.LOCAL;Action action=Action.NONE;String reason="retired_operation";
        if(UploadOperationJournal.CONFIRMED.equals(receipt.state)) {state=State.CONFIRMED;reason="server_confirmed";}
        else if(receipt.needsReconciliation()||UploadOperationJournal.LOCAL_MISSING.equals(receipt.state)||UploadOperationJournal.READ_FAILED.equals(receipt.state)) {
            state=State.REVIEW;action=Action.RECOVERY;reason="unbound_operation";
        }
        return new Row(uid,layer,receipt.changeId,receipt.featureId,receipt.attachId,receipt.operation,receipt.id,"","",reason,
                receipt.remoteId,receipt.updated,state,action,false);
    }
    public static Page load(Context context,String uid,int limit,long offset) throws Exception {
        if(limit<1||limit>200||offset<0)throw new IllegalArgumentException("Invalid changes page");
        CollectorProjectRegistry.ProjectInfo project=project(context,uid);File workspace=new File(project.getMapPath()).getCanonicalFile();
        File database=new File(workspace,"layers.db");if(!database.isFile())throw new IOException("Project database is unavailable");
        boolean held=ProjectRecoveryGuard.isHeld(workspace),blocked=ProjectRecoveryGuard.isDeliveryBlocked(workspace);List<Row> rows=new ArrayList<>();
        long total=0,queued=0,confirmed=0,review=0,skip=offset;
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(database.getPath(),null,SQLiteDatabase.OPEN_READONLY)) {
            TreeMap<String,LayerInfo> layers=new TreeMap<>();
            for(UnderlayWorkspaceIndex.Link link:UnderlayWorkspaceIndex.layers(new File(workspace,project.getMapName()+Constants.MAP_EXT)))
                if(link.config.optInt("type")==Constants.LAYERTYPE_NGW_VECTOR) {
                    UnderlayFiles.requireChild(workspace,link.directory);
                    layers.put(link.directory.getName(),new LayerInfo(context,workspace,link.directory.getName(),link.directory,link.config));
                }
            try(Cursor tables=db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'",null)) {
                while(tables.moveToNext()) {
                    String table=tables.getString(0);
                    if(table.endsWith("_changes")&&changesTable(db,table)) {
                        String layer=table.substring(0,table.length()-"_changes".length());
                        if(!layers.containsKey(layer))layers.put(layer,new LayerInfo(context,workspace,layer,null,null));
                    }
                }
            }
            for(String layer:UploadOperationJournal.layers(db))
                if(!layers.containsKey(layer))layers.put(layer,new LayerInfo(context,workspace,layer,null,null));
            if(layers.size()>2000)throw new IOException("Too many layers for this changes page");
            for(LayerInfo layer:layers.values()) {
                String changes=layer.table+"_changes";boolean exists=tableExists(db,changes);
                long count=exists?DatabaseUtils.longForQuery(db,"SELECT count(*) FROM "+quoted(changes)+" WHERE "+operations(),null):0;
                long old=UploadOperationJournal.historyCount(db,layer.table,changes);
                total=Math.addExact(total,Math.addExact(count,old));queued=Math.addExact(queued,count);
                confirmed=Math.addExact(confirmed,UploadOperationJournal.count(db,layer.table,UploadOperationJournal.CONFIRMED));
                for(String state:new String[]{UploadOperationJournal.UNCERTAIN,UploadOperationJournal.SENDING,UploadOperationJournal.ACCEPTED,UploadOperationJournal.LOCAL_MISSING,UploadOperationJournal.READ_FAILED})
                    review=Math.addExact(review,UploadOperationJournal.count(db,layer.table,state));
                if(skip>=count)skip-=count;
                else if(rows.size()<limit) {
                    String identity=FeatureChanges.hasOperationIdentity(db,changes)?FeatureChanges.OPERATION_UUID:"NULL AS "+FeatureChanges.OPERATION_UUID;
                    String[] fields={Constants.FIELD_ID,Constants.FIELD_FEATURE_ID,Constants.FIELD_OPERATION,Constants.FIELD_ATTACH_ID,Constants.FIELD_ATTACH_OPERATION,identity};
                    try(Cursor c=db.query(changes,fields,operations(),null,null,null,Constants.FIELD_ID,skip+","+(limit-rows.size()))) {
                        while(c.moveToNext())rows.add(queued(context,uid,db,layer,changes,c,held,blocked));
                    }
                    skip=0;
                }
                if(skip>=old)skip-=old;
                else if(rows.size()<limit) {
                    for(UploadOperationJournal.Entry receipt:UploadOperationJournal.historyPage(db,layer.table,changes,limit-rows.size(),skip))rows.add(history(uid,layer,receipt));
                    skip=0;
                }
            }
        }
        return new Page(project,rows,total,queued,confirmed,review,ProjectSyncState.lastComplete(context,workspace.getPath()),offset,held,blocked);
    }

    public interface LayerAction<T> {T run(NGWVectorLayer layer) throws Exception;}
    /** Review runs against a newly resolved registry identity under an exclusive project lease. */
    public static <T> T withLayer(Context context,String uid,String layerPath,LayerAction<T> action) throws Exception {
        CollectorProjectRegistry.ProjectInfo project=project(context,uid);
        if(layerPath==null)throw new IOException("Layer identity is unavailable");
        File workspace=new File(project.getMapPath()).getCanonicalFile(),requested=new File(workspace,layerPath).getCanonicalFile();
        UnderlayFiles.requireChild(workspace,requested);
        return withWorkspace(context,uid,map->{
            com.nextgis.maplib.api.ILayer found=map.getLayerByPathName(requested.getName());
            if(!(found instanceof NGWVectorLayer)||!requested.equals(((NGWVectorLayer)found).getPath().getCanonicalFile()))
                throw new IOException("Layer identity changed");
            return action.run((NGWVectorLayer)found);
        });
    }
    private interface WorkspaceAction<T> {T run(MapContentProviderHelper map) throws Exception;}
    private static <T> T withWorkspace(Context context,String uid,WorkspaceAction<T> action) throws Exception {
        CollectorProjectRegistry.ProjectInfo project=project(context,uid);
        File workspace=new File(project.getMapPath()).getCanonicalFile();
        ProjectOperationCoordinator.Lease lease=ProjectOperationCoordinator.tryBegin(context,ProjectOperationCoordinator.Kind.CHANGE_REVIEW);
        if(lease==null)throw new IOException("Project is busy");
        MapContentProviderHelper map=null;SyncWorkspaceSession session=null;Bitmap image=null;boolean background=false;
        try {
            IGISApplication app=(IGISApplication)context.getApplicationContext();MapBase active=MapBase.getActiveInstance();
            background=active==null||!workspace.equals(active.getPath().getCanonicalFile());
            if(background) {image=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);
                map=new MapDrawable(image,context,new File(workspace,project.getMapName()+Constants.MAP_EXT),app.getLayerFactory(),false);}
            else map=(MapContentProviderHelper)active;
            // Keep the exclusive identity reservation until the same lease becomes the
            // normal scoped sync owner; dependent callbacks must be able to drain safely.
            if(!lease.transitionTo(ProjectOperationCoordinator.Kind.DATA_SYNC))throw new IOException("Cannot reserve review scope");
            ProjectOperationCoordinator.requireScopedSyncDependents();session=new SyncWorkspaceSession(map);
            try(SyncWorkspaceSession.Scope ignored=session.enter()) {
                if(background&&!map.load())throw new IOException("Cannot open project for review");
                return action.run(map);
            }
        } finally {
            try {
                try {
                    if(session!=null) {
                        boolean interrupted=Thread.interrupted();
                        try(AutoCloseable ignored=ProjectOperationCoordinator.allowSyncDependents(session.getToken())) {
                            while(!session.isQuiet()) {lease.heartbeat();session.expireUndelivered();
                                try {session.waitForProgress();}catch(InterruptedException error) {interrupted=true;}}
                        }finally {session.close();if(interrupted)Thread.currentThread().interrupt();}
                    }
                }finally {
                    try {if(background&&map!=null)map.closeSyncWorkspace();}
                    finally {if(image!=null&&!image.isRecycled())image.recycle();}
                }
            } finally {lease.close();}
        }
    }
    public static boolean request(Context context,String uid) throws IOException {
        if(ProjectRecoveryGuard.isSyncBlocked(new File(project(context,uid).getMapPath())))return false;
        return LocalChangesWorker.requestProject(context,uid);
    }
    public static final class SiblingAcknowledgementException extends RuntimeException {
        public SiblingAcknowledgementException() {super("Another project copy already has a server acknowledgement");}
    }
    private static void checkSiblingAcknowledgements(SQLiteDatabase chosen,File sibling) throws IOException {
        File database=new File(sibling,"layers.db");
        if(!database.isFile())throw new IOException("Related project database is unavailable");
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(database.getPath(),null,SQLiteDatabase.OPEN_READONLY)) {
            for(String layer:UploadOperationJournal.layers(db))for(int offset=0;;offset+=200) {
                List<UploadOperationJournal.Entry> entries=UploadOperationJournal.page(db,layer,200,offset);
                for(UploadOperationJournal.Entry entry:entries) {
                    if(!UploadOperationJournal.nonIdempotent(entry.operation)||entry.remoteId<0
                            ||!(UploadOperationJournal.ACCEPTED.equals(entry.state)||UploadOperationJournal.CONFIRMED.equals(entry.state)))continue;
                    UploadOperationJournal.Entry own=UploadOperationJournal.metadata(chosen,layer,entry.id);
                    String changes=layer+"_changes";
                    boolean queued=tableExists(chosen,changes)&&FeatureChanges.hasOperationIdentity(chosen,changes)
                            &&DatabaseUtils.longForQuery(chosen,"SELECT count(*) FROM "+quoted(changes)+" WHERE "+FeatureChanges.OPERATION_UUID+"=?",new String[]{entry.id})>0;
                    // An ACK for a later source-only operation has no copied identity to reconcile.
                    if(own==null&&!queued)continue;
                    if(own==null||!UploadOperationJournal.CONFIRMED.equals(own.state)||own.remoteId!=entry.remoteId)
                        throw new SiblingAcknowledgementException();
                }
                if(entries.size()<200)break;
                if(offset>1_000_000)throw new IOException("Related project journal is too large");
            }
        }
    }
    public static boolean releaseReviewedCopy(Context context,String uid) throws Exception {
        return withWorkspace(context,uid,map->{
            File workspace=map.getPath();if(!ProjectRecoveryGuard.isHeld(workspace)||ProjectRecoveryGuard.isDeliveryBlocked(workspace))return false;
            JSONObject identity=ProjectRecoveryGuard.heldIdentity(workspace);
            SQLiteDatabase db=map.getDatabase(false);
            Set<String> family=ProjectRecoveryGuard.family(workspace,uid);
            Map<String,File> siblings=new LinkedHashMap<>();
            for(CollectorProjectRegistry.ProjectInfo other:CollectorProjectRegistry.listProjects(context)) {
                if(other.getProjectUid().equals(uid))continue;
                File directory=new File(other.getMapPath()).getCanonicalFile();
                if(directory.equals(workspace.getCanonicalFile()))throw new IOException("Duplicate project workspace identity");
                if(!Collections.disjoint(family,ProjectRecoveryGuard.family(directory,other.getProjectUid())))
                    siblings.put(other.getProjectUid(),directory);
            }
            return LayerDatabaseTransaction.run(db,()->{
                List<com.nextgis.maplib.api.ILayer> layers=new ArrayList<>();
                LayerGroup.getLayersByType(map,Constants.LAYERTYPE_NGW_VECTOR,layers);
                Set<String> known=new HashSet<>();
                for(com.nextgis.maplib.api.ILayer layer:layers) {
                    NGWVectorLayer vector=(NGWVectorLayer)layer;known.add(vector.getPath().getName());
                    if(!vector.isRecoveryQueueReviewed())return false;
                }
                try(Cursor tables=db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'",null)) {
                    while(tables.moveToNext()) {
                        String table=tables.getString(0);
                        if(table.endsWith("_changes")&&changesTable(db,table)
                                &&!known.contains(table.substring(0,table.length()-"_changes".length()))
                                &&DatabaseUtils.longForQuery(db,"SELECT count(*) FROM "+quoted(table),null)>0)return false;
                    }
                }
                for(String layer:UploadOperationJournal.layers(db))if(!known.contains(layer))return false;
                try {
                    for(File sibling:siblings.values())checkSiblingAcknowledgements(db,sibling);
                    for(Map.Entry<String,File> sibling:siblings.entrySet())ProjectRecoveryGuard.blockDelivery(
                            sibling.getValue(),sibling.getKey(),uid,identity.getString("operation"));
                    ProjectRecoveryGuard.release(workspace,identity);
                }
                catch(IOException error) {throw new android.database.sqlite.SQLiteException("Cannot release recovered project",error);}
                catch(JSONException error) {throw new android.database.sqlite.SQLiteException("Invalid recovery identity",error);}
                return true;
            });
        });
    }
}
