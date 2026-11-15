package com.nextgis.maplibui.util;

import android.util.AtomicFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.nextgis.maplib.datasource.GeoLineString;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.util.NgwSyncResourceBudget;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Durable small deltas on the producer, ordered full checkpoints on one private worker. */
public final class WalkCheckpointWriter implements AutoCloseable {
    public static final String KEY_OWNER = "walk_checkpoint_owner";
    public static final String KEY_SEQUENCE = "walk_checkpoint_sequence";
    public static final String KEY_ROOT = "walk_checkpoint_root";
    public static final int CAPACITY = 2048;
    private static final int BATCH = 128;
    private static final Logger LOG = Logger.getLogger(WalkCheckpointWriter.class.getName());
    interface JournalStorage { void write(File target, byte[] bytes) throws IOException; }
    private final JournalStorage storage;
    private static final ConcurrentHashMap<String, WalkCheckpointWriter> ACTIVE = new ConcurrentHashMap<>();
    public interface Sink { boolean save(GeoLineString part, int insertion, boolean paused, long sequence); }
    public interface Result { void complete(boolean success); }
    public enum Admission { DURABLE, PENDING, REJECTED }

    public static final class Edit {
        public final long sequence;
        volatile boolean durable = true;
        final int type, index, insertion;
        final double x, y;
        final boolean paused;
        Edit(long sequence, int type, int index, double x, double y, int insertion, boolean paused) {
            this.sequence=sequence; this.type=type; this.index=index; this.x=x; this.y=y;
            this.insertion=insertion; this.paused=paused;
        }
        void apply(GeoLineString part) throws IOException {
            if (type==0) return;
            GeoPoint point=new GeoPoint(x,y); point.setCRS(GeoConstants.CRS_WEB_MERCATOR);
            if (type==1 && index>=0 && index<=part.getPointCount()) part.getPoints().add(index,point);
            else if (type==2 && index>=0 && index<part.getPointCount())
                part.getPoint(index).setCoordinates(x,y);
            else throw new IOException("Walk checkpoint geometry no longer matches its owner");
        }
    }
    public static final class State {
        public final GeoLineString part;
        public final int insertion;
        public final boolean paused;
        public final long sequence;
        State(GeoLineString part,int insertion,boolean paused,long sequence) {
            this.part=part;this.insertion=insertion;this.paused=paused;this.sequence=sequence;
        }
    }

    private final File directory;
    private final String owner, key;
    private final Sink sink;
    private final Result listener;
    private final ExecutorService executor;
    private final ArrayDeque<Edit> pending=new ArrayDeque<>();
    private final GeoLineString part;
    private final State initial;
    private long sequence, applied, persisted;
    private int insertion, acceptedInsertion, acceptedPoints;
    private boolean paused, acceptedPaused, scheduled, failed, closing, closed;

    public WalkCheckpointWriter(File workspace,String owner,GeoLineString seed,int insertion,
            boolean paused,long persisted,Sink sink,Result listener) throws IOException {
        this(workspace,owner,seed,insertion,paused,persisted,sink,listener,WalkCheckpointWriter::writeAtomic);
    }
    WalkCheckpointWriter(File workspace,String owner,GeoLineString seed,int insertion,
            boolean paused,long persisted,Sink sink,Result listener,JournalStorage storage) throws IOException {
        this.storage=storage;
        if (owner==null || owner.isEmpty() || seed==null || persisted<0) throw new IOException("Invalid walk owner");
        requirePointBudget(seed.getPointCount());
        this.owner=owner;this.sink=sink;this.listener=listener;
        directory=directory(workspace,owner); key=directory.getCanonicalPath();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create walk checkpoint journal");
        this.part=(GeoLineString)seed.copy(); this.part.setCRS(seed.getCRS());
        this.insertion=insertion;this.paused=paused;this.persisted=persisted;this.applied=persisted;
        for (Edit edit:read(directory,owner)) {
            sequence=Math.max(sequence,edit.sequence);
            if (edit.sequence<=persisted) { if (!remove(edit)) throw new IOException("Cannot finalize old walk checkpoint"); }
            else { if(edit.sequence!=applied+1)throw new IOException("Walk checkpoint sequence has a gap");
                requirePointBudget(part.getPointCount()+(edit.type==1?1:0));edit.apply(part);
                if(edit.insertion>part.getPointCount())throw new IOException("Invalid walk insertion");applied=edit.sequence;
                this.insertion=edit.insertion;this.paused=edit.paused;pending.add(edit); }
        }
        sequence=Math.max(sequence,persisted);
        acceptedInsertion=this.insertion;acceptedPaused=this.paused;acceptedPoints=part.getPointCount();
        GeoLineString copy=(GeoLineString)part.copy();copy.setCRS(part.getCRS());
        initial=new State(copy,this.insertion,this.paused,applied);
        executor=Executors.newSingleThreadExecutor(r -> { Thread t=new Thread(r,"lisa-walk-checkpoint");t.setDaemon(true);return t; });
        if (ACTIVE.putIfAbsent(key,this)!=null) { executor.shutdown();throw new IOException("Walk checkpoint writer is still closing"); }
        if (!pending.isEmpty()) schedule();
    }

    public State initialState() { return initial; }

    /** One constant-sized atomic journal record, never serialization/copy of the walk. */
    public synchronized Admission insert(int index,GeoPoint point,int insertion,boolean paused) {
        if (acceptedPoints>=pointLimit()) return reject();
        return append(1,index,point.getX(),point.getY(),insertion,paused);
    }
    public synchronized Admission correct(int index,GeoPoint point,int insertion,boolean paused) {
        return append(2,index,point.getX(),point.getY(),insertion,paused);
    }
    public synchronized boolean checkpoint(int insertion,boolean paused) { return checkpoint(insertion,paused,false); }
    public synchronized boolean checkpoint(int insertion,boolean paused,boolean force) {
        if (closed||closing) return false;
        if (!force && acceptedInsertion==insertion && acceptedPaused==paused) { schedule();return true; }
        return append(0,0,0,0,insertion,paused)==Admission.DURABLE;
    }
    private Admission reject() { failed=true;publish(false);return Admission.REJECTED; }
    private void publish(boolean success) {
        try { if(listener!=null)listener.complete(success); }
        catch(RuntimeException error){ LOG.log(Level.WARNING,"Checkpoint observer failed",error); }
    }
    private Admission append(int type,int index,double x,double y,int insertion,boolean paused) {
        if (closed||closing||sequence==Long.MAX_VALUE||pending.size()>=CAPACITY||index<0||insertion<0
                ||type==1&&index>acceptedPoints||type==2&&index>=acceptedPoints
                ||insertion>acceptedPoints+(type==1?1:0)
                ||!Double.isFinite(x)||!Double.isFinite(y)) return reject();
        Edit edit=new Edit(sequence+1,type,index,x,y,insertion,paused);
        boolean prefixDurable=pending.isEmpty()||pending.peekLast().durable;
        edit.durable=false;
        pending.add(edit);sequence=edit.sequence;acceptedInsertion=insertion;acceptedPaused=paused;
        if(type==1)acceptedPoints++;
        if(prefixDurable) try { write(edit); }
        catch(IOException error) { LOG.log(Level.WARNING,"Walk delta remains in memory pending retry",error); }
        if(!edit.durable){failed=true;publish(false);}
        schedule();return edit.durable?Admission.DURABLE:Admission.PENDING;
    }
    private static long pointLimit() { return Math.max(1024,Runtime.getRuntime().maxMemory()/1024); }
    private static void requirePointBudget(int count) throws IOException {
        NgwSyncResourceBudget.requireHeap();
        if (count<0 || count>pointLimit()) throw new IOException("Walk exceeds the checkpoint memory budget");
    }
    private synchronized void schedule() {
        if (!scheduled&&!closed&&!closing&&!pending.isEmpty()) {
            scheduled=true;executor.execute(this::drain);
        }
    }
    private boolean drain() {
        while (true) {
            List<Edit> batch=new ArrayList<>(BATCH);
            synchronized(this) {
                if(closed)return false;
                for(Edit edit:pending) { batch.add(edit);if(batch.size()==BATCH)break; }
                if(batch.isEmpty()) { scheduled=false;failed=false;notifyAll();return true; }
            }
            boolean success=false;
            try {
                boolean journalWritable=true;
                for(Edit edit:batch) {
                    if(!edit.durable&&journalWritable) try { write(edit); }
                    catch(IOException unavailable){journalWritable=false;}
                    if(edit.sequence<=applied)continue;
                    requirePointBudget(part.getPointCount()+(edit.type==1?1:0));edit.apply(part);
                    insertion=edit.insertion;paused=edit.paused;applied=edit.sequence;
                }
                success=applied<=persisted || sink.save(part,insertion,paused,applied);
                if(success) {
                    synchronized(this) { persisted=applied; }
                    for(Edit edit:batch) if(!remove(edit)) { success=false;break; }
                }
            } catch(IOException|RuntimeException|OutOfMemoryError error) {
                LOG.log(Level.WARNING,"Ordered walk checkpoint is pending",error);
            }
            synchronized(this) {
                if(success) for(Edit edit:batch) { if(pending.peekFirst()==edit)pending.removeFirst(); }
                failed=!success;
                if(!success)scheduled=false;
                notifyAll();
            }
            publish(success);
            if(!success)return false;
        }
    }

    /** A finish barrier runs after accepted deltas. Failure keeps the owner and journal retryable. */
    public synchronized void finishAsync(boolean discard,Result completion) {
        if(closed||closing) { completion.complete(false);return; }
        closing=true;
        executor.execute(() -> {
            boolean ok=discard||drain();
            if(ok)ACTIVE.remove(key,this);
            synchronized(this) { if(ok)closed=true;else closing=false;notifyAll(); }
            if(ok)executor.shutdown();
            completion.complete(ok);
        });
    }

    /** Only for lifecycle shutdown/tests, never a GPS callback. The durable journal survives a timeout. */
    public synchronized boolean flush(long timeoutMillis) throws InterruptedException {
        if(closed)return pending.isEmpty();
        long target=sequence;failed=false;schedule();
        long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while(persisted<target||!pending.isEmpty()) {
            if(failed)return false;
            long remaining=end-System.nanoTime();if(remaining<=0)return false;
            TimeUnit.NANOSECONDS.timedWait(this,remaining);
        }
        return true;
    }
    @Override public synchronized void close() {
        if(closed||closing)return;
        closing=true;
        executor.execute(() -> { try { drain(); } finally {
            ACTIVE.remove(key,this);synchronized(this){closed=true;notifyAll();}
        }});
        executor.shutdown();
    }
    public synchronized boolean awaitClosed(long timeoutMillis) throws InterruptedException {
        long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while(!closed) { long left=end-System.nanoTime();if(left<=0)return false;TimeUnit.NANOSECONDS.timedWait(this,left); }
        return true;
    }
    public static boolean isActive(File workspace,String owner) {
        try { return ACTIVE.containsKey(directory(workspace,owner).getCanonicalPath()); }
        catch(IOException error){return false;}
    }
    public static List<Edit> capture(File workspace,String owner,long after) throws IOException {
        if(owner==null||owner.isEmpty())return Collections.emptyList();
        File directory=directory(workspace,owner);
        WalkCheckpointWriter active=ACTIVE.get(directory.getCanonicalPath());
        List<Edit> result=new ArrayList<>();
        if(active!=null) {
            synchronized(active){ for(Edit edit:active.pending)if(edit.sequence>after)result.add(edit); }
        } else for(Edit edit:read(directory,owner))if(edit.sequence>after)result.add(edit);
        return result;
    }
    public static State replay(GeoLineString part,int insertion,boolean paused,long sequence,List<Edit> edits) throws IOException {
        if(edits.size()>CAPACITY)throw new IOException("Walk replay exceeds its bounded tail");
        for(Edit edit:edits)if(edit.sequence>sequence) {
            if(edit.sequence!=sequence+1)throw new IOException("Walk checkpoint sequence has a gap");
            // Already accepted edits remain recoverable when a later device has a smaller write budget.
            // This overlays at most CAPACITY small records; admission limits apply to new writes.
            edit.apply(part);
            if(edit.insertion>part.getPointCount())throw new IOException("Invalid walk insertion");
            insertion=edit.insertion;paused=edit.paused;sequence=edit.sequence;
        }
        return new State(part,insertion,paused,sequence);
    }
    public static boolean deleteFinished(File workspace,String owner) {
        if(owner==null||owner.isEmpty())return true;
        try {
            if(isActive(workspace,owner))return false;
            File directory=directory(workspace,owner);
            read(directory,owner); // Validate the complete namespace before deleting any file.
            File[] files=directory.listFiles();
            if(files==null)return !directory.exists();
            for(File file:files)if(!file.delete())return false;
            return !directory.exists()||directory.delete();
        } catch(IOException error) { LOG.log(Level.WARNING,"Finished journal cleanup is pending",error);return false; }
    }
    private File file(Edit edit) { return new File(directory,String.format(Locale.ROOT,"%020d.json",edit.sequence)); }
    private void write(Edit edit) throws IOException {
        try {
            JSONObject json=new JSONObject().put("format",1).put("owner",owner).put("sequence",edit.sequence)
                    .put("type",edit.type).put("index",edit.index).put("x",edit.x).put("y",edit.y)
                    .put("insertion",edit.insertion).put("paused",edit.paused);
            byte[] bytes=json.toString().getBytes(StandardCharsets.UTF_8);
            NgwSyncResourceBudget.requireSpace(directory,bytes.length+4096);
            storage.write(file(edit),bytes);edit.durable=true;
        } catch(Exception error) { throw new IOException("Cannot retain walk delta",error); }
    }
    private static void writeAtomic(File target,byte[] bytes) throws IOException {
        AtomicFile atomic=new AtomicFile(target);FileOutputStream out=null;
        try { out=atomic.startWrite();out.write(bytes);out.getFD().sync();atomic.finishWrite(out); }
        catch(IOException|RuntimeException error) { if(out!=null)atomic.failWrite(out);throw new IOException("Cannot retain walk delta",error); }
    }
    private boolean remove(Edit edit) {
        boolean ok=true;
        for(String suffix:new String[]{"",".bak",".new"}) { File f=new File(file(edit)+suffix);if(f.exists()&&!f.delete())ok=false; }
        return ok;
    }
    private static File directory(File workspace,String owner) throws IOException {
        if(workspace==null||owner==null||owner.isEmpty())throw new IOException("Missing walk namespace");
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(owner.getBytes(StandardCharsets.UTF_8));
            StringBuilder name=new StringBuilder();for(byte b:hash)name.append(String.format(Locale.ROOT,"%02x",b&255));
            File parent=new File(workspace,".walk-checkpoints");File result=new File(parent,name.toString());
            if(Files.isSymbolicLink(parent.toPath())||Files.isSymbolicLink(result.toPath()))throw new IOException("Walk namespace is a link");
            if(!result.getCanonicalPath().startsWith(workspace.getCanonicalPath()+File.separator))throw new IOException("Walk namespace escaped its workspace");
            return result;
        } catch(java.security.NoSuchAlgorithmException error){throw new IOException(error);}
    }
    private static List<Edit> read(File directory,String owner) throws IOException {
        if(!directory.exists())return Collections.emptyList();
        File[] files=directory.listFiles();if(files==null)throw new IOException("Cannot enumerate walk journal");
        if(files.length>CAPACITY*3)throw new IOException("Walk journal exceeds its bounded capacity");
        TreeMap<Long,File> names=new TreeMap<>();
        for(File file:files) {
            String name=file.getName().replaceFirst("\\.(bak|new)$","");
            if(!name.matches("[0-9]{20}\\.json")||Files.isSymbolicLink(file.toPath())||!file.isFile())throw new IOException("Unknown walk checkpoint file");
            try { names.put(Long.parseLong(name.substring(0,20)),new File(directory,name)); }
            catch(NumberFormatException error){throw new IOException(error);}
        }
        if(names.size()>CAPACITY)throw new IOException("Walk journal is full");
        List<Edit> result=new ArrayList<>();
        for(Map.Entry<Long,File> item:names.entrySet()) {
            File base=item.getValue(),backup=new File(base+".bak"),fresh=new File(base+".new");
            File source=backup.isFile()?backup:base.isFile()?base:fresh;
            if(source.length()>4096)throw new IOException("Oversized walk checkpoint record");
            try {
                JSONObject json=new JSONObject(new String(Files.readAllBytes(source.toPath()),StandardCharsets.UTF_8));
                for(String field:new String[]{"format","sequence","type","index","insertion"}) {
                    Object value=json.get(field);
                    if(!(value instanceof Integer||value instanceof Long))throw new IOException("Invalid walk integer");
                }
                long sequence=json.getLong("sequence");int type=Math.toIntExact(json.getLong("type")),index=Math.toIntExact(json.getLong("index")),insertion=Math.toIntExact(json.getLong("insertion"));
                double x=json.getDouble("x"),y=json.getDouble("y");
                if(json.getInt("format")!=1||!owner.equals(json.getString("owner"))||sequence<=0||sequence!=item.getKey()
                        ||type<0||type>2||index<0||insertion<0||!Double.isFinite(x)||!Double.isFinite(y))throw new IOException("Invalid walk checkpoint identity");
                result.add(new Edit(sequence,type,index,x,y,insertion,json.getBoolean("paused")));
            } catch(org.json.JSONException|ArithmeticException error){
                // An interrupted first AtomicFile write was never admitted as durable.
                if(source.equals(fresh) && !base.exists() && !backup.exists() && item.getKey().equals(names.lastKey()))continue;
                throw new IOException("Corrupt walk checkpoint record",error);
            }
        }
        return result;
    }
}
