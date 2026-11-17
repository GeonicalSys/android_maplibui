package com.nextgis.maplibui.util;

import static org.junit.Assert.*;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLHandshakeException;
import org.junit.Test;

public class NGIDLoginDiagnosticsTest {
    private static final class Fixture {
        long now=100;
        final List<String> lines=new ArrayList<>();
        final NGIDLoginDiagnostics.Attempt attempt=new NGIDLoginDiagnostics.Attempt(
                "https://user-secret:password-secret@LOGIN.example:443/path-secret?token=token-secret",
                ()->now,lines::add);
    }
    @Test public void linkedStagesPreserveActualStatusHostAndDurationWithoutSecrets() {
        Fixture f=new Fixture();NGIDLoginDiagnostics.Stage token=f.attempt.stage("token");f.now+=25;token.end(200,null);
        NGIDLoginDiagnostics.Stage profile=f.attempt.stage("profile");f.now+=75;profile.end(401,null);f.attempt.end(false);
        assertEquals(6,f.lines.size());
        for(String line:f.lines) {
            assertTrue(line.contains("attempt="+f.attempt.id+" "));assertTrue(line.contains("host=login.example "));
            for(String secret:new String[]{"user-secret","password-secret","path-secret","token-secret","https://","?"})assertFalse(line,line.contains(secret));
        }
        assertTrue(f.lines.get(2).contains("elapsed_ms=25 http_status=200 category=success"));
        assertTrue(f.lines.get(4).contains("elapsed_ms=75 http_status=401 category=authorization"));
        assertTrue(f.lines.get(5).contains("elapsed_ms=100 http_status=401 category=authorization"));
    }
    @Test public void laterTransportFailureCannotReuseAnEarlierHttpStatus() {
        Fixture f=new Fixture();f.attempt.stage("token").end(200,null);
        f.attempt.stage("profile").end(-1,new UnknownHostException("secret.invalid"));f.attempt.end(false);
        assertTrue(f.lines.get(f.lines.size()-1).endsWith("http_status=unknown category=dns"));
        assertFalse(String.join("\n",f.lines).contains("secret.invalid"));
    }
    @Test public void malformedResponseIsDistinctAndSuccessfulRetryResolvesOnlyItsOwnStage() {
        Fixture f=new Fixture();f.attempt.stage("token").end(200,null);f.attempt.invalidResponse("token");
        f.attempt.stage("profile").end(200,null);f.attempt.validResponse("profile");f.attempt.end(true);
        assertTrue(f.lines.get(f.lines.size()-1).endsWith("category=invalid_response"));
        Fixture retried=new Fixture();retried.attempt.stage("profile").end(200,null);retried.attempt.invalidResponse("profile");
        retried.attempt.stage("profile").end(200,null);retried.attempt.validResponse("profile");retried.attempt.end(true);
        assertTrue(retried.lines.get(retried.lines.size()-1).endsWith("category=success"));
    }
    @Test public void statusAndTypedCausesRemainDistinctWithoutExceptionText() {
        assertEquals("authorization",NGIDLoginDiagnostics.category(403,null));
        assertEquals("server",NGIDLoginDiagnostics.category(503,null));
        assertEquals("http",NGIDLoginDiagnostics.category(302,null));
        assertEquals("unknown",NGIDLoginDiagnostics.category(-1,null));
        assertEquals("dns",NGIDLoginDiagnostics.category(-1,new IOException(new UnknownHostException("secret"))));
        assertEquals("tls",NGIDLoginDiagnostics.category(200,new SSLHandshakeException("secret")));
        assertEquals("timeout",NGIDLoginDiagnostics.category(-1,new SocketTimeoutException("secret")));
        assertEquals("io",NGIDLoginDiagnostics.category(-1,new IOException("secret")));
        assertEquals("internal",NGIDLoginDiagnostics.category(-1,new IllegalStateException("secret")));
    }
    @Test public void duplicateCompletionIsIdempotentAndClockRegressionNeverGoesNegative() {
        Fixture f=new Fixture();NGIDLoginDiagnostics.Stage stage=f.attempt.stage("token");f.now=50;
        stage.end(503,null);stage.end(200,null);f.attempt.end(false);f.attempt.end(true);
        assertEquals(4,f.lines.size());assertTrue(f.lines.get(2).contains("elapsed_ms=0 http_status=503"));
        assertTrue(f.lines.get(3).endsWith("category=server"));
    }
    @Test public void unsafeHostsAndUnknownStagesCannotInjectLogFields() {
        assertEquals("unknown",NGIDLoginDiagnostics.host("not a URI\npassword"));
        assertEquals("unknown",NGIDLoginDiagnostics.host("https://"));
        assertEquals("::1",NGIDLoginDiagnostics.host("http://[::1]:7777/secret"));
        Fixture f=new Fixture();f.attempt.stage("token\npassword").end(200,null);
        assertTrue(f.lines.get(1).contains("stage=unknown "));assertFalse(f.lines.get(1).contains("password"));
    }
    @Test public void localLogFailureCannotChangeLoginControlFlow() {
        NGIDLoginDiagnostics.Attempt attempt=new NGIDLoginDiagnostics.Attempt("https://example.com",()->1,line->{throw new IllegalStateException("unavailable");});
        attempt.stage("token").end(200,null);attempt.end(true);
    }
}
