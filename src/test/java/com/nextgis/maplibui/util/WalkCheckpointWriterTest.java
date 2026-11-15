package com.nextgis.maplibui.util;

import android.content.SharedPreferences;
import com.nextgis.maplib.datasource.*;
import com.nextgis.maplib.util.GeoConstants;
import org.json.JSONObject;
import org.junit.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import static org.junit.Assert.*;

/** Filesystem/ordering models; the default Android AtomicFile and prefs path has native coverage. */
public class WalkCheckpointWriterTest {
    private Path root;
    private final List<WalkCheckpointWriter> writers=new ArrayList<>();
    private final List<CountDownLatch> releases=new ArrayList<>();
    private final WalkCheckpointWriter.JournalStorage files=(target,bytes)-> {
        Path fresh=Path.of(target+".new");
        try(FileOutputStream out=new FileOutputStream(fresh.toFile())) {out.write(bytes);out.getFD().sync();}
        Files.move(fresh,target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    };
    private static GeoLineString line() {
        GeoLineString line=new GeoLineString();line.setCRS(GeoConstants.CRS_WEB_MERCATOR);
        line.add(new GeoPoint(0,0));return line;
    }
    private WalkCheckpointWriter writer(String id,GeoLineString seed,long sequence,
            WalkCheckpointWriter.Sink sink,WalkCheckpointWriter.JournalStorage journal) throws Exception {
        WalkCheckpointWriter writer=new WalkCheckpointWriter(root.toFile(),id,seed,seed.getPointCount(),false,
                sequence,sink,null,journal);writers.add(writer);return writer;
    }
    private void stop(WalkCheckpointWriter writer) throws Exception {writer.close();assertTrue(writer.awaitClosed(5000));}
    private Path directory() throws Exception {
        try(Stream<Path> paths=Files.list(root.resolve(".walk-checkpoints"))) {return paths.findFirst().orElseThrow();}
    }
    @Before public void setup() throws Exception {root=Files.createTempDirectory("rel03-walk-");}
    @After public void cleanup() throws Exception {
        for(CountDownLatch release:releases)release.countDown();
        for(WalkCheckpointWriter writer:writers)stop(writer);
        try(Stream<Path> paths=Files.walk(root)) {
            for(Path path:paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
                assertFalse(Files.isSymbolicLink(path));Files.delete(path);
            }
        }
    }
    @Test public void producerDoesNotWaitForAFullCheckpointAndFinishIsAnOrderedBarrier() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(1);
        releases.add(release);AtomicReference<GeoLineString> saved=new AtomicReference<>();
        AtomicBoolean paused=new AtomicBoolean(),finished=new AtomicBoolean();
        long producer=Thread.currentThread().getId();
        WalkCheckpointWriter writer=writer("slow",line(),0,(part,index,pause,sequence)-> {
            assertNotEquals(producer,Thread.currentThread().getId());entered.countDown();
            try {if(!release.await(5,TimeUnit.SECONDS))return false;}catch(InterruptedException e){return false;}
            saved.set((GeoLineString)part.copy());paused.set(pause);return true;
        },files);
        assertEquals(WalkCheckpointWriter.Admission.DURABLE,writer.insert(1,new GeoPoint(1,1),2,false));
        assertTrue(entered.await(5,TimeUnit.SECONDS));
        ExecutorService caller=Executors.newSingleThreadExecutor();
        try {
            GeoPoint mutable=new GeoPoint(2,2);
            assertEquals(WalkCheckpointWriter.Admission.DURABLE,
                    caller.submit(()->writer.insert(2,mutable,3,false)).get(2,TimeUnit.SECONDS));
            mutable.setCoordinates(999,999);
            assertEquals(WalkCheckpointWriter.Admission.DURABLE,writer.correct(1,new GeoPoint(5,6),3,false));
            assertTrue(writer.checkpoint(3,true));
            writer.finishAsync(false,ok->{finished.set(ok);done.countDown();});
            assertFalse(done.await(50,TimeUnit.MILLISECONDS));
            release.countDown();assertTrue(done.await(5,TimeUnit.SECONDS));assertTrue(finished.get());
            assertEquals(3,saved.get().getPointCount());assertEquals(5,saved.get().getPoint(1).getX(),0);
            assertEquals(2,saved.get().getPoint(2).getX(),0);assertTrue(paused.get());
        } finally {release.countDown();caller.shutdownNow();}
    }
    @Test public void aFailedFullCheckpointKeepsTheJournalAndColdReplayIsExactlyOnce() throws Exception {
        AtomicReference<GeoLineString> base=new AtomicReference<>(line());AtomicLong sequence=new AtomicLong();
        WalkCheckpointWriter first=writer("restart",base.get(),0,(part,index,pause,seq)->false,files);
        assertEquals(WalkCheckpointWriter.Admission.DURABLE,first.insert(1,new GeoPoint(7,8),2,false));
        assertFalse(first.flush(5000));stop(first);
        assertEquals(1,base.get().getPointCount());
        WalkCheckpointWriter recovered=writer("restart",base.get(),0,(part,index,pause,seq)-> {
            base.set((GeoLineString)part.copy());sequence.set(seq);return true;
        },files);
        assertEquals(2,recovered.initialState().part.getPointCount());assertTrue(recovered.flush(5000));stop(recovered);
        WalkCheckpointWriter reopened=writer("restart",base.get(),sequence.get(),(part,index,pause,seq)->true,files);
        assertEquals(2,reopened.initialState().part.getPointCount());assertEquals(7,reopened.initialState().part.getPoint(1).getX(),0);
    }
    @Test public void journalWriteFailureRetainsABoundedRamTailAndNeverAcknowledgesFinish() throws Exception {
        AtomicBoolean full=new AtomicBoolean(true),prefsFail=new AtomicBoolean(true);
        AtomicReference<GeoLineString> base=new AtomicReference<>(line());
        WalkCheckpointWriter writer=writer("disk-pressure",line(),0,(part,index,pause,seq)-> {
            if(prefsFail.get())return false;base.set((GeoLineString)part.copy());return true;
        },(target,bytes)-> {if(full.get())throw new IOException("technical ENOSPC");files.write(target,bytes);});
        assertEquals(WalkCheckpointWriter.Admission.PENDING,writer.insert(1,new GeoPoint(8,9),2,false));
        assertEquals(1,WalkCheckpointWriter.capture(root.toFile(),"disk-pressure",0).size());
        assertFalse(writer.flush(5000));assertEquals(1,base.get().getPointCount());
        full.set(false);prefsFail.set(false);assertTrue(writer.flush(5000));
        assertEquals(2,base.get().getPointCount());assertEquals(8,base.get().getPoint(1).getX(),0);
    }
    @Test public void acknowledgedButNotDeletedRecordCannotDuplicateAfterAProcessRestart() throws Exception {
        AtomicReference<GeoLineString> base=new AtomicReference<>(line());AtomicLong sequence=new AtomicLong();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);releases.add(release);
        WalkCheckpointWriter writer=writer("ack",line(),0,(part,index,pause,seq)-> {
            entered.countDown();try {release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){return false;}
            base.set((GeoLineString)part.copy());sequence.set(seq);return true;
        },files);
        writer.insert(1,new GeoPoint(1,2),2,false);assertTrue(entered.await(5,TimeUnit.SECONDS));
        Path record=directory().resolve("00000000000000000001.json");byte[] bytes=Files.readAllBytes(record);
        release.countDown();assertTrue(writer.flush(5000));stop(writer);Files.write(record,bytes);
        WalkCheckpointWriter restarted=writer("ack",base.get(),sequence.get(),(part,index,pause,seq)->true,files);
        assertEquals(2,restarted.initialState().part.getPointCount());assertFalse(Files.exists(record));
    }
    @Test public void capacityRejectsAdditionalFixesWhileTheWorkerIsBlocked() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);releases.add(release);
        WalkCheckpointWriter writer=writer("capacity",line(),0,(part,index,pause,seq)-> {
            entered.countDown();try{return release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){return false;}
        },(target,bytes)->{ /* Capacity model only; no durability claim for this sink. */ });
        assertEquals(WalkCheckpointWriter.Admission.DURABLE,writer.insert(1,new GeoPoint(1,1),2,false));
        assertTrue(entered.await(5,TimeUnit.SECONDS));
        for(int i=2;i<=WalkCheckpointWriter.CAPACITY;i++)
            assertEquals(WalkCheckpointWriter.Admission.DURABLE,writer.insert(i,new GeoPoint(i,i),i+1,false));
        assertEquals(WalkCheckpointWriter.Admission.REJECTED,writer.insert(2049,new GeoPoint(9,9),2050,false));
        assertEquals(WalkCheckpointWriter.CAPACITY,WalkCheckpointWriter.capture(root.toFile(),"capacity",0).size());
        release.countDown();assertTrue(writer.flush(5000));
    }
    @Test public void aCorruptCommittedRecordIsRetainedAndCannotBecomeAnEmptySuccessfulDraft() throws Exception {
        WalkCheckpointWriter first=writer("corrupt",line(),0,(part,index,pause,seq)->false,files);
        first.insert(1,new GeoPoint(1,1),2,false);assertFalse(first.flush(5000));stop(first);
        Path record=directory().resolve("00000000000000000001.json");
        JSONObject changed=new JSONObject(new String(Files.readAllBytes(record),StandardCharsets.UTF_8));
        changed.put("index",1.25);Files.write(record,changed.toString().getBytes(StandardCharsets.UTF_8));
        byte[] original=Files.readAllBytes(record);
        try {writer("corrupt",line(),0,(part,index,pause,seq)->true,files);fail("Must retain ambiguous state");}
        catch(IOException expected){ }
        assertArrayEquals(original,Files.readAllBytes(record));
    }
    @Test public void interruptedUnadmittedFirstWriteDoesNotHideThePreviouslyCommittedDraft() throws Exception {
        WalkCheckpointWriter first=writer("partial",line(),0,(part,index,pause,seq)->true,files);stop(first);
        Files.write(directory().resolve("00000000000000000001.json.new"),"{".getBytes(StandardCharsets.UTF_8));
        WalkCheckpointWriter recovered=writer("partial",line(),0,(part,index,pause,seq)->true,files);
        assertEquals(1,recovered.initialState().part.getPointCount());
        assertEquals(WalkCheckpointWriter.Admission.DURABLE,recovered.insert(1,new GeoPoint(2,3),2,false));
        assertTrue(recovered.flush(5000));
    }
    @Test public void aMissingPredecessorIsNotSilentlyAppliedToTheWrongVertex() throws Exception {
        WalkCheckpointWriter first=writer("gap",line(),0,(part,index,pause,seq)->true,files);stop(first);
        JSONObject value=new JSONObject().put("format",1).put("owner","gap").put("sequence",2)
                .put("type",1).put("index",1).put("x",8).put("y",9).put("insertion",2).put("paused",false);
        Path record=directory().resolve("00000000000000000002.json");Files.write(record,value.toString().getBytes(StandardCharsets.UTF_8));
        try {writer("gap",line(),0,(part,index,pause,seq)->true,files);fail();}catch(IOException expected){ }
        assertTrue(Files.exists(record));
    }
    @Test public void explicitDiscardRemovesAnUnadmittedPartialWriteButRetainsUnknownFiles() throws Exception {
        WalkCheckpointWriter first=writer("discard",line(),0,(part,index,pause,seq)->true,files);stop(first);
        Path directory=directory();
        Files.write(directory.resolve("00000000000000000001.json.new"),new byte[]{'{'});
        Path unknown=directory.resolve("unknown.keep");Files.write(unknown,new byte[]{7});
        assertFalse(WalkCheckpointWriter.deleteFinished(root.toFile(),"discard"));
        assertTrue(Files.exists(unknown));Files.delete(unknown);
        assertTrue(WalkCheckpointWriter.deleteFinished(root.toFile(),"discard"));
        assertFalse(Files.exists(directory));
    }
    @Test public void replayPreservesRingTypeInsertionCorrectionAndPauseIntent() throws Exception {
        GeoLinearRing ring=new GeoLinearRing();ring.setCRS(GeoConstants.CRS_WEB_MERCATOR);
        ring.add(new GeoPoint(0,0));ring.add(new GeoPoint(10,0));ring.add(new GeoPoint(10,10));
        WalkCheckpointWriter first=writer("ring",ring,0,(part,index,pause,seq)->false,files);
        first.insert(1,new GeoPoint(2,2),2,false);first.correct(1,new GeoPoint(3,4),2,false);first.checkpoint(2,true);
        assertFalse(first.flush(5000));stop(first);
        WalkCheckpointWriter recovered=writer("ring",ring,0,(part,index,pause,seq)->true,files);
        WalkCheckpointWriter.State state=recovered.initialState();
        assertTrue(state.part instanceof GeoLinearRing);assertEquals(4,state.part.getPointCount());
        assertEquals(3,state.part.getPoint(1).getX(),0);assertEquals(10,state.part.getPoint(2).getX(),0);
        assertEquals(2,state.insertion);assertTrue(state.paused);assertEquals(3,state.sequence);
        assertTrue(recovered.flush(5000));
    }
    @Test public void namespacesAndLateWritersCannotOverwriteAReplacementOwner() throws Exception {
        AtomicReference<String> current=new AtomicReference<>("old");
        WalkCheckpointWriter old=writer("old",line(),0,(part,index,pause,seq)->"old".equals(current.get()),files);
        current.set("new");old.insert(1,new GeoPoint(1,1),2,false);assertFalse(old.flush(5000));
        WalkCheckpointWriter replacement=writer("new",line(),0,(part,index,pause,seq)->"new".equals(current.get()),files);
        replacement.insert(1,new GeoPoint(9,9),2,false);assertTrue(replacement.flush(5000));
        assertEquals(1,WalkCheckpointWriter.capture(root.toFile(),"old",0).size());
        assertEquals(0,WalkCheckpointWriter.capture(root.toFile(),"new",0).size());
    }
    @Test public void failedPreferenceCommitRestoresEveryOldValueIncludingUnknownTypedKeys() {
        MemoryPreferences prefs=new MemoryPreferences();
        prefs.values.put("geometry","original");prefs.values.put("sequence",7L);
        prefs.values.put("future",new HashSet<>(Arrays.asList("x","y")));prefs.values.put("point",true);
        Map<String,?> original=prefs.getAll();prefs.failNext=true;
        assertFalse(WalkSessionStore.commitDraftPreferences(prefs,prefs.edit().clear().putString("geometry","unconfirmed")));
        assertEquals(original,prefs.getAll());
    }
    private static final class MemoryPreferences implements SharedPreferences {
        final Map<String,Object> values=new HashMap<>();boolean failNext;
        @Override public Map<String,?> getAll(){return new HashMap<>(values);}
        @Override public boolean contains(String k){return values.containsKey(k);}
        @Override public String getString(String k,String d){return (String)values.getOrDefault(k,d);}
        @Override public Set<String> getStringSet(String k,Set<String>d){return (Set<String>)values.getOrDefault(k,d);}
        @Override public int getInt(String k,int d){return (Integer)values.getOrDefault(k,d);}
        @Override public long getLong(String k,long d){return (Long)values.getOrDefault(k,d);}
        @Override public float getFloat(String k,float d){return (Float)values.getOrDefault(k,d);}
        @Override public boolean getBoolean(String k,boolean d){return (Boolean)values.getOrDefault(k,d);}
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
        @Override public Editor edit(){return new Editor(){
            final Map<String,Object> changed=new HashMap<>();boolean clear;
            public Editor putString(String k,String v){changed.put(k,v);return this;}
            public Editor putStringSet(String k,Set<String>v){changed.put(k,new HashSet<>(v));return this;}
            public Editor putInt(String k,int v){changed.put(k,v);return this;}
            public Editor putLong(String k,long v){changed.put(k,v);return this;}
            public Editor putFloat(String k,float v){changed.put(k,v);return this;}
            public Editor putBoolean(String k,boolean v){changed.put(k,v);return this;}
            public Editor remove(String k){changed.put(k,null);return this;}
            public Editor clear(){clear=true;return this;}
            public boolean commit(){if(clear)values.clear();for(Map.Entry<String,Object> e:changed.entrySet())
                if(e.getValue()==null)values.remove(e.getKey());else values.put(e.getKey(),e.getValue());
                boolean ok=!failNext;failNext=false;return ok;}
            public void apply(){commit();}
        };}
    }
}
