package com.nextgis.maplibui.util;

import com.nextgis.maplib.util.Constants;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CollectorImportJournalTest {
    @Test
    public void roundTripPreservesResumeState() throws Exception {
        CollectorImportJournal.Snapshot source = validSnapshot();

        CollectorImportJournal.Snapshot restored = CollectorImportJournal.decode(
                new JSONObject(CollectorImportJournal.encode(source).toString()));

        assertEquals(73, restored.groupId);
        assertEquals("collector", restored.accountName);
        assertEquals("project-uid", restored.projectUid);
        assertArrayEquals(source.remoteIds, restored.remoteIds);
        assertArrayEquals(source.names, restored.names);
        assertArrayEquals(source.configJsons, restored.configJsons);
        assertArrayEquals(source.formIds, restored.formIds);
        assertArrayEquals(source.editables, restored.editables);
        assertArrayEquals(source.fullProjectRemoteIds, restored.fullProjectRemoteIds);
        assertEquals(2, restored.repairPassesRemaining);
    }

    @Test
    public void rejectsMismatchedArrays() throws Exception {
        CollectorImportJournal.Snapshot source = validSnapshot();
        JSONObject json = CollectorImportJournal.encode(source);
        json.put("names", new org.json.JSONArray().put("only one"));

        assertNull(CollectorImportJournal.decode(json));
    }

    @Test
    public void rejectsUnknownVersionAndMissingProjectMembership() throws Exception {
        CollectorImportJournal.Snapshot source = validSnapshot();
        JSONObject json = CollectorImportJournal.encode(source);
        json.put("version", 999);
        assertNull(CollectorImportJournal.decode(json));

        source.fullProjectRemoteIds = new long[]{999L};
        assertEquals(false, source.isValid());
        source.groupId = Constants.NOT_FOUND;
        assertEquals(false, source.isValid());
    }

    private static CollectorImportJournal.Snapshot validSnapshot() {
        CollectorImportJournal.Snapshot snapshot = new CollectorImportJournal.Snapshot();
        snapshot.groupId = 73;
        snapshot.accountName = "collector";
        snapshot.projectUid = "project-uid";
        snapshot.remoteIds = new long[]{101L, 102L};
        snapshot.names = new String[]{"Trees", "Roads"};
        snapshot.configJsons = new String[]{"{\"a\":1}", null};
        snapshot.formIds = new long[]{201L, 0L};
        snapshot.editables = new boolean[]{true, false};
        snapshot.fullProjectRemoteIds = new long[]{100L, 101L, 102L, 103L};
        snapshot.repairPassesRemaining = 2;
        return snapshot;
    }
}
