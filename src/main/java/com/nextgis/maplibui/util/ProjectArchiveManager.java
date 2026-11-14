package com.nextgis.maplibui.util;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.map.*;
import com.nextgis.maplib.util.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Complete saved-project archives. Source files and Android account credentials are never rewritten. */
public final class ProjectArchiveManager {
    private static final String FORMAT="lisa.project-archive", MANIFEST="manifest.json";
    private static final int SCHEMA=1, MAX_FILES=100000, MAX_MANIFEST=16*1024*1024;
    private static final long MARGIN=16L*1024*1024;
    private ProjectArchiveManager() { }
    public interface Budget { boolean permits(File directory,long bytes); }
    private static final Budget DISK=(dir,bytes)->dir.getUsableSpace()>bytes;

    public static File archiveDirectory(Context context) throws IOException {
        // The legacy workspace can be the map root itself. Its archive must never be
        // created inside that root, otherwise inventory would include its own output.
        File archives=context.getExternalFilesDir("project_archives");
        if(archives==null)archives=new File(context.getFilesDir(),"project_archives");
        UnderlayFiles.directory(archives);return archives;
    }
    private static void checkCancelled() throws IOException {
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Project archive cancelled");
    }
    private static String normalized(String name) throws IOException {
        if(name==null||name.length()>2048||name.startsWith("/")||name.contains("\\")||name.contains(":")||name.indexOf('\0')>=0)
            throw new IOException("Unsafe archive path");
        for(String part:name.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))throw new IOException("Unsafe archive path");
        return name;
    }
    private static File child(File directory,String name) throws IOException {
        File file=new File(directory,normalized(name));UnderlayFiles.requireChild(directory,file);
        if(Files.isSymbolicLink(file.toPath()))throw new IOException("Archive path is a link");return file;
    }
    private static String hash(File file) throws IOException {
        MessageDigest digest=UnderlayFiles.digest();byte[] bytes=new byte[64*1024];
        try(InputStream in=new FileInputStream(file)) {
            int count;while((count=in.read(bytes))!=-1) {checkCancelled();digest.update(bytes,0,count);}
        }
        return UnderlayFiles.hex(digest.digest());
    }
    private static String textHash(String value) {
        return UnderlayFiles.hex(UnderlayFiles.digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }
    private static List<File> files(File root) throws IOException {
        return files(root,Collections.emptySet());
    }
    private static List<File> files(File root,Set<File> excluded) throws IOException {
        List<File> result=new ArrayList<>();
        if(!root.isDirectory())throw new IOException("Project directory is unavailable");
        Files.walkFileTree(root.toPath(),new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path path,java.nio.file.attribute.BasicFileAttributes attr) throws IOException {
                checkCancelled();
                return excluded.contains(path.toFile().getCanonicalFile())?FileVisitResult.SKIP_SUBTREE:FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path,java.nio.file.attribute.BasicFileAttributes attr) throws IOException {
                checkCancelled();File file=path.toFile();UnderlayFiles.requireChild(root,file);
                if(!attr.isRegularFile()||Files.isSymbolicLink(path))throw new IOException("Unsupported project file");
                if(excluded.contains(file.getCanonicalFile()))return FileVisitResult.CONTINUE;
                result.add(file);if(result.size()>MAX_FILES)throw new IOException("Too many project files");return FileVisitResult.CONTINUE;
            }
        });
        result.sort(Comparator.comparing(File::getPath));return result;
    }
    private static List<File> workspaceFiles(Context context,File workspace) throws IOException {
        File common=context.getExternalFilesDir(SettingsConstants.KEY_PREF_MAP);
        if(common==null)common=new File(context.getFilesDir(),SettingsConstants.KEY_PREF_MAP);
        Set<File> excluded=new HashSet<>();
        // These are global namespaces, not part of the legacy workspace. Referenced
        // immutable underlays are copied explicitly, without other projects or registry.
        for(String name:new String[]{"collector_projects","shared_underlays","ProjectArchives",
                "collector_projects_registry.json","collector_projects_registry.json.bak"})
            excluded.add(new File(common,name).getCanonicalFile());
        excluded.add(archiveDirectory(context).getCanonicalFile());excluded.remove(workspace.getCanonicalFile());
        return files(workspace,excluded);
    }
    private static boolean liveDatabaseFile(String path) {
        return path.equals("layers.db")||path.equals("layers.db-wal")||path.equals("layers.db-shm")||path.equals("layers.db-journal");
    }
    private static void unchangedWorkspace(Context context,File workspace,JSONArray records) throws Exception {
        java.util.Map<String,JSONObject> expected=new HashMap<>();
        for(int i=0;i<records.length();i++) {
            JSONObject record=records.getJSONObject(i);String path=record.getString("path");
            if(path.startsWith("workspace/")&&!liveDatabaseFile(path.substring(10)))expected.put(path.substring(10),record);
        }
        for(File file:workspaceFiles(context,workspace)) {
            String path=relative(workspace,file);if(liveDatabaseFile(path))continue;
            JSONObject record=expected.remove(path);
            if(record==null||record.getLong("bytes")!=file.length()||!record.getString("sha256").equals(hash(file)))
                throw new IOException("Project changed while archiving; retry after editing finishes");
        }
        if(!expected.isEmpty())throw new IOException("Project files disappeared while archiving");
    }
    private static String relative(File root,File file) {return root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar,'/');}
    private static JSONObject identity(Context context,CollectorProjectRegistry.ProjectInfo project,MapContentProviderHelper map) throws Exception {
        JSONObject source=new JSONObject().put("uid",project.getProjectUid()).put("name",project.getName())
                .put("type",project.getProjectType().name()).put("account",project.getAccountName()==null?JSONObject.NULL:project.getAccountName())
                .put("remote_id",project.getProjectRemoteId()).put("map_name",project.getMapName())
                .put("workspace_path",map.getPath().getCanonicalPath());
        TreeSet<String> names=new TreeSet<>();
        if(project.getAccountName()!=null&&!project.getAccountName().isEmpty())names.add(project.getAccountName());
        List<ILayer> layers=new ArrayList<>();LayerGroup.getLayersByType(map,Constants.LAYERTYPE_NGW_VECTOR,layers);
        for(ILayer layer:layers) {
            String name=((NGWVectorLayer)layer).getAccountName();if(name!=null&&!name.isEmpty())names.add(name);
        }
        IGISApplication app=(IGISApplication)context.getApplicationContext();JSONArray accounts=new JSONArray();
        for(String name:names) {
            android.accounts.Account account=app.getAccount(name);
            if(account==null||app.getAccountUrl(account)==null||app.getAccountLogin(account)==null)throw new IOException("Project account is unavailable");
            accounts.put(new JSONObject().put("name",name).put("server_sha256",textHash(app.getAccountUrl(account)))
                    .put("login_sha256",textHash(app.getAccountLogin(account))));
        }
        return source.put("accounts",accounts);
    }
    private static void checkAccounts(Context context,JSONObject source) throws Exception {
        IGISApplication app=(IGISApplication)context.getApplicationContext();JSONArray accounts=source.getJSONArray("accounts");
        if(accounts.length()>1000)throw new IOException("Invalid project accounts");
        for(int i=0;i<accounts.length();i++) {
            JSONObject binding=accounts.getJSONObject(i);android.accounts.Account account=app.getAccount(binding.getString("name"));
            if(account==null||app.getAccountUrl(account)==null||app.getAccountLogin(account)==null
                    ||!textHash(app.getAccountUrl(account)).equals(binding.getString("server_sha256"))
                    ||!textHash(app.getAccountLogin(account)).equals(binding.getString("login_sha256")))
                throw new IOException("Archive belongs to another account or server");
        }
    }
    private static void add(ZipOutputStream zip,JSONArray records,String name,File file) throws Exception {
        checkCancelled();if(records.length()>=MAX_FILES)throw new IOException("Too many archive files");
        normalized(name);String before=hash(file);long size=file.length();
        zip.putNextEntry(new ZipEntry(name));
        MessageDigest digest=UnderlayFiles.digest();long read=0;byte[] bytes=new byte[64*1024];
        try(InputStream in=new FileInputStream(file)) {
            int count;while((count=in.read(bytes))!=-1) {checkCancelled();read+=count;zip.write(bytes,0,count);digest.update(bytes,0,count);}
        }
        zip.closeEntry();
        if(read!=size||!before.equals(UnderlayFiles.hex(digest.digest()))||!before.equals(hash(file)))
            throw new IOException("Project file changed while archiving");
        records.put(new JSONObject().put("path",name).put("bytes",size).put("sha256",before));
    }

    public static File create(Context context,MapContentProviderHelper map,CollectorProjectRegistry.ProjectInfo project) throws Exception {
        return create(context,map,project,DISK);
    }
    static File create(Context context,MapContentProviderHelper map,CollectorProjectRegistry.ProjectInfo project,Budget budget) throws Exception {
        if(project==null||!map.getPath().getCanonicalPath().equals(new File(project.getMapPath()).getCanonicalPath()))
            throw new IOException("Project identity changed");
        ProjectOperationCoordinator.Lease lease=ProjectOperationCoordinator.tryBegin(context,ProjectOperationCoordinator.Kind.PROJECT_BACKUP);
        if(lease==null)throw new IOException("Project is busy with an active operation");
        File archives=null,work=null;
        try {
            archives=archiveDirectory(context);work=new File(archives,".archive-"+UUID.randomUUID());
            File result=new File(archives,"project-"+System.currentTimeMillis()+"-"+UUID.randomUUID()+".zip");
            UnderlayFiles.directory(work);
            SQLiteDatabase db=map.getDatabase(false);
            db.beginTransaction();
            try {
            JSONObject source=identity(context,project,map);
            File mapFile=child(map.getPath(),project.getMapName()+Constants.MAP_EXT);
            if(!mapFile.isFile())throw new IOException("Saved project configuration is missing");
            List<File> projectFiles=workspaceFiles(context,map.getPath());long estimate=MARGIN;
            for(File file:projectFiles)estimate=Math.addExact(estimate,file.length());
            SharedUnderlayCatalog catalog=SharedUnderlayStore.catalog(context);JSONArray underlays=new JSONArray();
            LinkedHashMap<String,File> payloads=new LinkedHashMap<>();
            for(UnderlayWorkspaceIndex.Link link:UnderlayWorkspaceIndex.layers(mapFile)) if(!link.assetId().isEmpty()) {
                SharedUnderlayCatalog.Asset asset=catalog.get(link.assetId());
                if(asset==null||!asset.isReady())throw new IOException("An offline underlay is unavailable");
                File payload=catalog.payload(asset.id);payloads.put(asset.id,payload);
                underlays.put(new JSONObject().put("id",asset.id).put("layer_path",relative(map.getPath(),link.directory))
                        .put("layer_config",asset.layerConfig()).put("kind",asset.kind));
            }
            for(File payload:payloads.values())for(File file:files(payload))estimate=Math.addExact(estimate,file.length());
            if(estimate>Long.MAX_VALUE/3||!budget.permits(archives,estimate*2))throw new IOException("Not enough free space for the complete archive");
            JSONObject manifest=new JSONObject();
                File snapshot=new File(work,"layers.db");WorkspaceSqliteSnapshot.copy(db,snapshot);
                File temporary=new File(work,"archive.zip");JSONArray records=new JSONArray();
                try(FileOutputStream output=new FileOutputStream(temporary);ZipOutputStream zip=new ZipOutputStream(output)) {
                    for(File file:projectFiles) {
                        String path=relative(map.getPath(),file);
                        if(liveDatabaseFile(path))continue;
                        add(zip,records,"workspace/"+path,file);
                    }
                    add(zip,records,"workspace/layers.db",snapshot);
                    for(java.util.Map.Entry<String,File> payload:payloads.entrySet())for(File file:files(payload.getValue()))
                        add(zip,records,"underlays/"+payload.getKey()+"/"+relative(payload.getValue(),file),file);
                    manifest.put("format",FORMAT).put("schema",SCHEMA).put("source",source)
                            .put("sqlite_version",db.getVersion()).put("created",System.currentTimeMillis())
                            .put("files",records).put("underlays",underlays);
                    byte[] encoded=manifest.toString().getBytes(StandardCharsets.UTF_8);
                    if(encoded.length>MAX_MANIFEST)throw new IOException("Archive manifest is too large");
                    zip.putNextEntry(new ZipEntry(MANIFEST));zip.write(encoded);zip.closeEntry();zip.finish();output.getFD().sync();
                }
                unchangedWorkspace(context,map.getPath(),records);
                verify(temporary,project.getProjectUid(),db.getVersion(),Long.MAX_VALUE);checkAccounts(context,source);
                Files.move(temporary.toPath(),result.toPath(),StandardCopyOption.ATOMIC_MOVE);
                db.setTransactionSuccessful();
            } finally {db.endTransaction();}
            return result;
        } finally {
            try {if(work!=null&&work.exists())UnderlayFiles.deleteChildTree(archives,work);}
            finally {lease.close();}
        }
    }

    /** Verify every byte before creating a recovery workspace or changing its registry. */
    public static JSONObject verify(File archive,String expectedUid,int databaseVersion,long freeBytes) throws Exception {
        try(ZipFile zip=new ZipFile(archive)) {
            ZipEntry header=zip.getEntry(MANIFEST);if(header==null||header.getSize()>MAX_MANIFEST)throw new IOException("Archive manifest is missing");
            JSONObject manifest;
            try(InputStream in=zip.getInputStream(header)) {
                byte[] encoded=UnderlayFiles.readBounded(in,MAX_MANIFEST);CRC32 crc=new CRC32();crc.update(encoded);
                if(crc.getValue()!=header.getCrc())throw new IOException("Corrupt archive manifest");
                manifest=new JSONObject(new String(encoded,StandardCharsets.UTF_8));
            }
            if(!FORMAT.equals(manifest.optString("format"))||manifest.optInt("schema",-1)!=SCHEMA
                    ||manifest.getInt("sqlite_version")!=databaseVersion)throw new IOException("Incompatible project archive version");
            JSONObject source=manifest.getJSONObject("source");
            if(expectedUid==null||!expectedUid.equals(source.getString("uid")))throw new IOException("Archive belongs to another project");
            String mapName=source.getString("map_name");normalized(mapName);
            if(mapName.contains("/"))throw new IOException("Invalid map name");
            JSONArray records=manifest.getJSONArray("files");
            if(records.length()<2||records.length()>MAX_FILES)throw new IOException("Invalid archive file count");
            java.util.Map<String,JSONObject> declared=new HashMap<>();long total=0;
            for(int i=0;i<records.length();i++) {
                checkCancelled();
                JSONObject record=records.getJSONObject(i);String path=normalized(record.getString("path"));
                if(!(path.startsWith("workspace/")||path.startsWith("underlays/"))||declared.put(path,record)!=null)
                    throw new IOException("Duplicate or unexpected archive path");
                long size=record.getLong("bytes");if(size<0||!record.getString("sha256").matches("[a-f0-9]{64}"))throw new IOException("Invalid archive file metadata");
                total=Math.addExact(total,size);
            }
            java.util.Map<String,Integer> payloads=new HashMap<>();
            for(String path:declared.keySet()) {
                checkCancelled();
                for(int slash=path.indexOf('/');slash>=0;slash=path.indexOf('/',slash+1))
                    if(declared.containsKey(path.substring(0,slash)))throw new IOException("Conflicting archive file paths");
                if(path.startsWith("underlays/")) {
                    String[] parts=path.split("/",3);
                    if(parts.length!=3)throw new IOException("Invalid underlay payload path");
                    payloads.put(parts[1],payloads.getOrDefault(parts[1],0)+1);
                }
            }
            if(!declared.containsKey("workspace/layers.db")||!declared.containsKey("workspace/"+mapName+Constants.MAP_EXT))
                throw new IOException("Archive does not contain the complete project");
            JSONArray underlays=manifest.getJSONArray("underlays");Set<String> boundLayers=new HashSet<>(),assets=new HashSet<>();
            for(int i=0;i<underlays.length();i++) {
                checkCancelled();
                JSONObject binding=underlays.getJSONObject(i);String id=normalized(binding.getString("id"));
                String layer=normalized(binding.getString("layer_path")),kind=binding.getString("kind");
                if(id.contains("/")||!boundLayers.add(layer)||!(SharedUnderlayCatalog.MBTILES.equals(kind)||SharedUnderlayCatalog.LEGACY_TILES.equals(kind)))
                    throw new IOException("Invalid underlay binding");
                binding.getJSONObject("layer_config");assets.add(id);boolean payload=payloads.containsKey(id);
                String configPath="workspace/"+layer+"/config.json";
                if(!payload||!declared.containsKey(configPath))throw new IOException("Incomplete offline underlay");
                ZipEntry configEntry=zip.getEntry(configPath);
                if(configEntry==null)throw new IOException("Missing underlay configuration");
                try(InputStream in=zip.getInputStream(configEntry)) {
                    JSONObject config=new JSONObject(new String(UnderlayFiles.readBounded(in,1024*1024),StandardCharsets.UTF_8));
                    if(config.optInt("type")!=Constants.LAYERTYPE_LOCAL_TMS||!id.equals(config.optString(SharedUnderlayCatalog.LAYER_KEY)))
                        throw new IOException("Underlay binding differs from the project");
                }
            }
            for(String path:declared.keySet())if(path.startsWith("underlays/")&&!assets.contains(path.split("/",3)[1]))
                throw new IOException("Unbound underlay payload");
            if(total>Long.MAX_VALUE-MARGIN||total+MARGIN>freeBytes)throw new IOException("Not enough free space to restore the project");
            Set<String> seen=new HashSet<>();Enumeration<? extends ZipEntry> entries=zip.entries();
            byte[] bytes=new byte[64*1024];
            while(entries.hasMoreElements()) {
                checkCancelled();ZipEntry entry=entries.nextElement();String path=normalized(entry.getName());
                if(!seen.add(path)||entry.isDirectory())throw new IOException("Duplicate or unsupported ZIP entry");
                if(MANIFEST.equals(path))continue;
                JSONObject record=declared.get(path);if(record==null||entry.getSize()!=record.getLong("bytes"))throw new IOException("Unexpected ZIP content");
                MessageDigest digest=UnderlayFiles.digest();CRC32 crc=new CRC32();long count=0;
                try(InputStream in=zip.getInputStream(entry)) {
                    int n;while((n=in.read(bytes))!=-1) {checkCancelled();count+=n;
                        if(count>record.getLong("bytes"))throw new IOException("ZIP entry exceeds declared size");digest.update(bytes,0,n);crc.update(bytes,0,n);}
                }
                if(count!=record.getLong("bytes")||crc.getValue()!=entry.getCrc()
                        ||!record.getString("sha256").equals(UnderlayFiles.hex(digest.digest())))throw new IOException("Corrupt archive file");
            }
            if(seen.size()!=declared.size()+1)throw new IOException("Archive is incomplete");
            return manifest;
        }
    }

    public static CollectorProjectRegistry.ProjectInfo restore(Context context,File archive,
            CollectorProjectRegistry.ProjectInfo expected,String name,int databaseVersion) throws Exception {
        ProjectOperationCoordinator.Lease lease=ProjectOperationCoordinator.tryBegin(context,ProjectOperationCoordinator.Kind.PROJECT_RESTORE);
        if(lease==null)throw new IOException("Project is busy with an active operation");
        File mapRoot=context.getExternalFilesDir(SettingsConstants.KEY_PREF_MAP);
        if(mapRoot==null)mapRoot=new File(context.getFilesDir(),SettingsConstants.KEY_PREF_MAP);
        File projects=new File(mapRoot,"collector_projects"),stage=new File(projects,".restore-"+UUID.randomUUID());
        try {
            UnderlayFiles.directory(projects);
            JSONObject manifest=verify(archive,expected.getProjectUid(),databaseVersion,projects.getUsableSpace());
            checkAccounts(context,manifest.getJSONObject("source"));
            UnderlayFiles.directory(stage);
            try(ZipFile zip=new ZipFile(archive)) {
                JSONArray records=manifest.getJSONArray("files");
                for(int i=0;i<records.length();i++) {
                    String path=records.getJSONObject(i).getString("path");File output=child(stage,path);
                    if(output.exists())throw new IOException("Archive paths alias an existing restored file");
                    UnderlayFiles.directory(output.getParentFile());
                    try(InputStream in=zip.getInputStream(zip.getEntry(path));FileOutputStream out=new FileOutputStream(output)) {
                        byte[] bytes=new byte[64*1024];int count;long copied=0,limit=records.getJSONObject(i).getLong("bytes");
                        while((count=in.read(bytes))!=-1) {checkCancelled();copied+=count;
                            if(copied>limit)throw new IOException("Archive changed during extraction");out.write(bytes,0,count);}
                        if(copied!=limit)throw new IOException("Archive changed during extraction");out.getFD().sync();
                    }
                    if(!hash(output).equals(records.getJSONObject(i).getString("sha256")))throw new IOException("Restored file differs from the archive");
                }
            }
            File workspace=child(stage,"workspace");
            try(SQLiteDatabase database=SQLiteDatabase.openDatabase(new File(workspace,"layers.db").getPath(),null,SQLiteDatabase.OPEN_READONLY)) {
                WorkspaceSqliteSnapshot.check(database);
                if(database.getVersion()!=databaseVersion)throw new IOException("Restored database version differs");
            }
            JSONArray underlays=manifest.getJSONArray("underlays");
            for(int i=0;i<underlays.length();i++) {
                JSONObject binding=underlays.getJSONObject(i);String id=binding.getString("id");
                File layer=child(workspace,binding.getString("layer_path"));File configFile=child(layer,"config.json");
                JSONObject config=UnderlayFiles.readJson(configFile);
                if(config.optInt("type")!=Constants.LAYERTYPE_LOCAL_TMS||!id.equals(config.optString(SharedUnderlayCatalog.LAYER_KEY)))
                    throw new IOException("Underlay binding differs from the project");
                File payload=child(stage,"underlays/"+id);
                for(File file:files(payload)) {
                    File output=child(layer,relative(payload,file));
                    if(output.exists())throw new IOException("Underlay restoration would replace a project file");
                    UnderlayFiles.directory(output.getParentFile());Files.copy(file.toPath(),output.toPath());
                    if(!hash(file).equals(hash(output)))throw new IOException("Restored underlay differs");
                }
                JSONObject raster=binding.getJSONObject("layer_config");
                for(String key:new String[]{"tms_type","levels","bbox_minx","bbox_miny","bbox_maxx","bbox_maxy"}) {
                    if(raster.has(key))config.put(key,raster.get(key));else config.remove(key);
                }
                config.remove(SharedUnderlayCatalog.LAYER_KEY);UnderlayFiles.writeJson(configFile,config);
            }
            JSONObject origin=new JSONObject(manifest.getJSONObject("source").toString())
                    .put("archive_sha256",hash(archive)).put("archive_created",manifest.getLong("created"));
            ProjectRecoveryGuard.mark(workspace,origin);
            checkAccounts(context,manifest.getJSONObject("source"));checkCancelled();
            return CollectorProjectRegistry.registerRecoveredProject(context,workspace,name,
                    manifest.getJSONObject("source").getString("map_name"),lease);
        } finally {
            lease.close();if(stage.exists())UnderlayFiles.deleteChildTree(projects,stage);
        }
    }
}
