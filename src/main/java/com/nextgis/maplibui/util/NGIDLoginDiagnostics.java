package com.nextgis.maplibui.util;

import android.os.SystemClock;
import com.hypertrack.hyperlog.HyperLog;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import javax.net.ssl.SSLException;

/** Bounded local diagnostics for login; never accepts credentials, payloads or exception text. */
final class NGIDLoginDiagnostics {
    interface Clock { long millis(); }
    interface Sink { void write(String line); }
    private static final Clock CLOCK = SystemClock::elapsedRealtime;
    private static final Sink LOG = line -> HyperLog.i("NGIDLogin", line);
    private NGIDLoginDiagnostics() { }

    static Attempt begin(String base) { return new Attempt(base, CLOCK, LOG); }

    static String host(String base) {
        try {
            String host = new URI(base).getHost();
            if (host == null) return "unknown";
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length()-1);
            return host.matches("[a-z0-9.:_-]{1,253}") ? host : "unknown";
        } catch (Exception malformed) { return "unknown"; }
    }

    private static String stageName(String value) {
        return value != null && value.matches("token|profile|settings|support") ? value : "unknown";
    }

    static String category(int status, Throwable failure) {
        // Inspect types only. Messages, URLs and server bodies never reach the sink.
        Throwable current = failure;
        for (int depth=0; current != null && depth<8; depth++, current=current.getCause()) {
            if (current instanceof UnknownHostException) return "dns";
            if (current instanceof SSLException) return "tls";
            if (current instanceof SocketTimeoutException) return "timeout";
        }
        if (failure != null) return failure instanceof IOException ? "io" : "internal";
        if (status == 401 || status == 403) return "authorization";
        if (status >= 500 && status <= 599) return "server";
        if (status >= 200 && status <= 299) return "success";
        return status >= 100 && status <= 599 ? "http" : "unknown";
    }

    static final class Attempt {
        final String id = UUID.randomUUID().toString();
        final String host;
        final Clock clock;
        final Sink sink;
        final long started;
        private int sequence;
        private boolean ended;
        private int lastStatus = -1;
        private String lastFailure = "unknown";
        private final Set<String> invalidStages = new HashSet<>();

        Attempt(String base, Clock clock, Sink sink) {
            this.host=host(base); this.clock=clock; this.sink=sink; this.started=clock.millis();
            emit("attempt",0,"start",0,-1,"pending");
        }
        synchronized Stage stage(String kind) {
            kind=stageName(kind);
            return new Stage(this,kind,++sequence);
        }
        synchronized void invalidResponse(String kind) {
            lastFailure="invalid_response";
            invalidStages.add(stageName(kind));
            emit(stageName(kind),sequence,"invalid_response",elapsed(started),lastStatus,lastFailure);
        }
        synchronized void validResponse(String kind) { invalidStages.remove(stageName(kind)); }
        synchronized void failed(Throwable failure) { lastStatus=-1;lastFailure=category(-1,failure); }
        synchronized void end(boolean success) {
            if (ended) return;
            ended=true;
            emit("attempt",0,"result",elapsed(started),lastStatus,success?(invalidStages.isEmpty()?"success":"invalid_response"):lastFailure);
        }
        private long elapsed(long begin) { return Math.max(0,clock.millis()-begin); }
        private void emit(String stage,int number,String event,long elapsed,int status,String category) {
            String line="NGID_LOGIN attempt="+id+" stage="+stage+" request="+number+" event="+event
                    +" host="+host+" elapsed_ms="+elapsed+" http_status="+(status>=100&&status<=599?status:"unknown")
                    +" category="+category;
            try { sink.write(line); } catch (RuntimeException unavailable) { /* Diagnostics cannot change login. */ }
        }
    }

    static final class Stage {
        final Attempt attempt;
        final String kind;
        final int number;
        final long started;
        private boolean ended;
        Stage(Attempt attempt,String kind,int number) {
            this.attempt=attempt;this.kind=kind;this.number=number;this.started=attempt.clock.millis();
            attempt.emit(kind,number,"start",0,-1,"pending");
        }
        void end(int status,Throwable failure) {
            synchronized (attempt) {
                if (ended) return;
                ended=true;
                String category=category(status,failure);
                attempt.lastStatus=status>=100&&status<=599?status:-1;
                if (!"success".equals(category)) attempt.lastFailure=category;
                attempt.emit(kind,number,"result",attempt.elapsed(started),status,category);
            }
        }
    }
}
