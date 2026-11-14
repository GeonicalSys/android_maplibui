package com.nextgis.maplibui.util;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Original checkpoints are retained until an explicit user action; loading never deletes them. */
public final class DraftQuarantineStore {
    private static final long MAX_CHECKPOINT=16L*1024*1024;
    private DraftQuarantineStore() { }
    public static final class Entry {
        public final String id,reason;
        public final File original;
        public final long created,bytes;
        public final boolean recovered;
        Entry(String id,File directory) throws IOException {
            this.id=id;File json=file(directory,"original.json");
            original=json.isFile()?json:file(directory,"original.xml");bytes=original.length();
            JSONObject meta;
            try {meta=metadata(directory);}catch(IOException error) {meta=new JSONObject();}
            reason=meta.optString("reason","interrupted_checkpoint");
            created=meta.optLong("created",original.lastModified());recovered=meta.optBoolean("recovered",false);
        }
    }
    private static File root(Context context) {return new File(context.getFilesDir(),"form-draft-quarantine");}
    private static File file(File directory,String name) throws IOException {
        File result=new File(directory,name);
        if(Files.isSymbolicLink(result.toPath())||!result.getCanonicalPath().startsWith(directory.getCanonicalPath()+File.separator))
            throw new IOException("Invalid checkpoint file");
        return result;
    }
    private static File directory(Context context,String id) throws IOException {
        if(id==null||!id.matches("[0-9a-f]{64}"))throw new IOException("Invalid quarantine identity");
        File parent=root(context),dir=new File(parent,id);
        if(Files.isSymbolicLink(parent.toPath())||Files.isSymbolicLink(dir.toPath())
                ||!dir.getCanonicalPath().startsWith(parent.getCanonicalPath()+File.separator))
            throw new IOException("Invalid quarantine directory");
        return dir;
    }
    private static String sha(byte[] bytes) throws Exception {
        StringBuilder out=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format(Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
    private static JSONObject metadata(File dir) throws IOException {
        File file=file(dir,"metadata.json");
        if(!file.isFile())return new JSONObject();
        if(file.length()>65536)throw new IOException("Invalid quarantine metadata");
        try{return new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));}
        catch(org.json.JSONException error){throw new IOException("Unreadable quarantine metadata",error);}
    }
    private static void atomic(File file,byte[] bytes,boolean replace) throws IOException {
        File temporary=new File(file.getParentFile(),".writing-"+UUID.randomUUID());
        try {
            try(FileOutputStream out=new FileOutputStream(temporary)) {out.write(bytes);out.getFD().sync();}
            if(!replace&&file.exists()) {
                if(!Arrays.equals(bytes,Files.readAllBytes(file.toPath())))throw new IOException("Original checkpoint differs");
                return;
            }
            Files.move(temporary.toPath(),file.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {if(temporary.exists()&&!temporary.delete())throw new IOException("Cannot remove checkpoint staging file");}
    }
    public static synchronized boolean preserve(Context context,String original,String reason) {
        if(context==null||original==null)return false;
        return preserveBytes(context,original.getBytes(StandardCharsets.UTF_8),reason,"json");
    }
    /** A corrupt preference type/container also retains its exact source bytes. */
    public static synchronized boolean preservePreferences(Context context,String reason) {
        try {
            File source=preferencesFile(context);
            if(!source.isFile()||Files.isSymbolicLink(source.toPath())||source.length()>MAX_CHECKPOINT)return false;
            return preserveBytes(context,Files.readAllBytes(source.toPath()),reason,"xml");
        }catch(IOException error) {return false;}
    }
    private static File preferencesFile(Context context) {
        return new File(context.getApplicationInfo().dataDir,"shared_prefs/feature_form_draft.xml");
    }
    private static boolean preserveBytes(Context context,byte[] bytes,String reason,String format) {
        try {
            if(bytes.length>MAX_CHECKPOINT)throw new IOException("Checkpoint exceeds preservation budget");
            String id=sha(bytes);File dir=directory(context,id);
            if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot create quarantine directory");
            File original=file(dir,"original."+format);atomic(original,bytes,false);
            if(!id.equals(sha(Files.readAllBytes(original.toPath()))))
                throw new IOException("Checkpoint verification failed");
            File preferences=preferencesFile(context);
            File savedPreferences=file(dir,"original-preferences.xml");
            if(preferences.isFile()&&!Files.isSymbolicLink(preferences.toPath())&&!savedPreferences.exists()&&preferences.length()<=MAX_CHECKPOINT)
                atomic(savedPreferences,Files.readAllBytes(preferences.toPath()),false);
            if(!file(dir,"metadata.json").exists()) {
                JSONObject meta=new JSONObject().put("schema",1).put("id",id).put("reason",reason)
                        .put("created",System.currentTimeMillis()).put("bytes",bytes.length).put("format",format).put("recovered",false);
                atomic(file(dir,"metadata.json"),meta.toString().getBytes(StandardCharsets.UTF_8),false);
            }
            return true;
        } catch(Exception error) {
            android.util.Log.w("DraftQuarantine","Original draft retained in its source; quarantine failed",error);
            return false;
        }
    }
    public static List<Entry> list(Context context) throws IOException {
        List<Entry> result=new ArrayList<>();File[] dirs=root(context).listFiles();
        if(dirs!=null)for(File dir:dirs) if(dir.getName().matches("[0-9a-f]{64}")) {
            File checked=directory(context,dir.getName());
            if(file(checked,"original.json").isFile()||file(checked,"original.xml").isFile())result.add(new Entry(dir.getName(),checked));
        }
        result.sort((a,b)->Long.compare(b.created,a.created));return result;
    }
    public static String original(Context context,String id) throws IOException {
        File dir=directory(context,id),original=file(dir,"original.json");
        if(!original.isFile())original=file(dir,"original.xml");
        if(!original.isFile()||original.length()>MAX_CHECKPOINT)throw new IOException("Original checkpoint unavailable");
        byte[] bytes=Files.readAllBytes(original.toPath());
        try {if(!id.equals(sha(bytes)))throw new IOException("Original checkpoint hash differs");}
        catch(IOException error){throw error;}catch(Exception error){throw new IOException(error);}
        return new String(bytes,StandardCharsets.UTF_8);
    }
    public static void recovered(Context context,String id) throws Exception {
        File dir=directory(context,id);JSONObject meta=metadata(dir).put("recovered",true);
        atomic(file(dir,"metadata.json"),meta.toString().getBytes(StandardCharsets.UTF_8),true);
    }
    public static void export(Context context,String id,OutputStream destination) throws IOException {
        File dir=directory(context,id);original(context,id);
        try(ZipOutputStream zip=new ZipOutputStream(destination)) {
            for(String name:new String[]{"original.json","original.xml","original-preferences.xml","metadata.json"}) {
                File file=file(dir,name);if(!file.isFile())continue;
                zip.putNextEntry(new ZipEntry(name));Files.copy(file.toPath(),zip);zip.closeEntry();
            }
            zip.finish();
        }
    }
}
