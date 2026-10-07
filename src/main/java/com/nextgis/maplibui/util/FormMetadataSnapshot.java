package com.nextgis.maplibui.util;

import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Shared immutable metadata storage; Activity/durable drafts keep only a verified hash. */
final class FormMetadataSnapshot {
    private FormMetadataSnapshot() { }
    static String hash(byte[] bytes) {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder text=new StringBuilder(64);
            for (byte value:digest) text.append(Character.forDigit((value>>>4)&15,16)).append(Character.forDigit(value&15,16));
            return text.toString();
        } catch (NoSuchAlgorithmException error) { throw new AssertionError(error); }
    }
    static String read(File layer, String directory, String pin, int maxBytes) throws IOException {
        if (!pin.matches("sha256:[0-9a-f]{64}")) throw new IOException("Invalid form metadata reference");
        File file=new File(new File(layer,directory),pin.substring(7)+".json");
        if (file.length()>maxBytes) throw new IOException("Form metadata exceeds limit");
        byte[] bytes=new AtomicFile(file).readFully();
        if (bytes.length>maxBytes || !pin.substring(7).equals(hash(bytes))) throw new IOException("Form metadata hash mismatch");
        return new String(bytes,StandardCharsets.UTF_8);
    }
    static synchronized String persist(File layer,String directory,String definition,int maxBytes) throws IOException {
        byte[] bytes=definition.getBytes(StandardCharsets.UTF_8);
        if (bytes.length>maxBytes) throw new IOException("Form metadata exceeds limit");
        String pin="sha256:"+hash(bytes);
        File folder=new File(layer,directory),file=new File(folder,pin.substring(7)+".json");
        if (file.isFile()) {
            try { read(layer,directory,pin,maxBytes);return pin; } catch (IOException ignored) { }
        }
        if (!folder.isDirectory()&&!folder.mkdirs()) throw new IOException("Cannot create form metadata directory");
        AtomicFile atomic=new AtomicFile(file);FileOutputStream output=null;
        try {
            output=atomic.startWrite();output.write(bytes);atomic.finishWrite(output);output=null;
            read(layer,directory,pin,maxBytes);
        } catch (IOException error) {
            if (output!=null) atomic.failWrite(output);
            throw error;
        }
        return pin;
    }
}
