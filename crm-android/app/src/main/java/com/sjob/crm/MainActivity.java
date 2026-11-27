package com.sjob.crm;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * 화면(UI)은 assets/index.html 이 담당하고, 이 액티비티는
 * 통화기록 읽기 · 파일 저장 · 전화/문자 연결만 JS에 제공한다.
 * 모든 데이터는 폰 안(WebView 저장소)에만 있고 외부로 전송하지 않는다.
 */
public class MainActivity extends Activity {

    private static final int REQ_PERM = 1;
    private static final int REQ_FILE = 2;
    private static final int REQ_AUTH = 3;
    private static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file";

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#2F5BEA"));

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri uri = req.getUrl();
                String scheme = uri.getScheme();
                if ("tel".equals(scheme)) {
                    startSafe(new Intent(Intent.ACTION_DIAL, uri));
                    return true;
                }
                if ("sms".equals(scheme) || "smsto".equals(scheme) || "http".equals(scheme) || "https".equals(scheme)) {
                    startSafe(new Intent(Intent.ACTION_VIEW, uri));
                    return true;
                }
                return false;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "백업 파일 선택"), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    private void startSafe(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "열 수 있는 앱이 없습니다", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasCallLog() {
        return checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED;
    }

    private void js(String code) {
        if (web != null) web.post(() -> web.evaluateJavascript(code, null));
    }

    @Override
    protected void onResume() {
        super.onResume();
        js("window.onAppResume && window.onAppResume()");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == REQ_PERM) {
            boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            js("window.onPermissionResult && window.onPermissionResult(" + ok + ")");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_AUTH) {
            try {
                AuthorizationResult r = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data);
                sendToken(r.getAccessToken(), null);
            } catch (Exception e) {
                sendToken(null, "cancelled");
            }
            return;
        }
        if (requestCode == REQ_FILE && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.onBack ? window.onBack() : false", value -> {
            if (!"true".equals(value)) MainActivity.super.onBackPressed();
        });
    }

    private void sendToken(String token, String err) {
        js("window.__driveToken && window.__driveToken(" + JSONObject.quote(token == null ? "" : token)
                + "," + JSONObject.quote(err == null ? "" : err) + ")");
    }

    /** 구글 드라이브(drive.file) 접근 토큰 요청. interactive=false 면 동의 화면을 띄우지 않음 */
    private void authorizeDrive(boolean interactive) {
        AuthorizationRequest req = AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(new Scope(DRIVE_SCOPE)))
                .build();
        Identity.getAuthorizationClient(this).authorize(req)
                .addOnSuccessListener(r -> {
                    if (r.hasResolution()) {
                        if (!interactive) { sendToken(null, "need_consent"); return; }
                        try {
                            startIntentSenderForResult(r.getPendingIntent().getIntentSender(), REQ_AUTH, null, 0, 0, 0);
                        } catch (Exception e) {
                            sendToken(null, "launch_failed");
                        }
                    } else {
                        sendToken(r.getAccessToken(), null);
                    }
                })
                .addOnFailureListener(e -> sendToken(null, "error: " + e.getMessage()));
    }

    /** googleapis.com 으로만 HTTP 요청을 대신 보냄 (file:// 페이지의 CORS 제약 회피) */
    private void httpRequest(String id, String method, String url, String token, String contentType, String body) {
        new Thread(() -> {
            int status = 0;
            String text;
            try {
                URL u = new URL(url);
                if (!"https".equals(u.getProtocol()) || !"www.googleapis.com".equals(u.getHost())) {
                    throw new SecurityException("blocked host");
                }
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                if ("PATCH".equals(method)) {
                    c.setRequestMethod("POST");
                    c.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                } else {
                    c.setRequestMethod(method);
                }
                if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
                if (body != null && !body.isEmpty()) {
                    c.setDoOutput(true);
                    if (contentType != null && !contentType.isEmpty()) c.setRequestProperty("Content-Type", contentType);
                    try (OutputStream os = c.getOutputStream()) {
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                }
                status = c.getResponseCode();
                InputStream is = status >= 400 ? c.getErrorStream() : c.getInputStream();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                if (is != null) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                    is.close();
                }
                text = bo.toString("UTF-8");
            } catch (Exception e) {
                text = String.valueOf(e.getMessage());
            }
            js("window.__httpDone && window.__httpDone(" + JSONObject.quote(id) + "," + status + "," + JSONObject.quote(text) + ")");
        }).start();
    }

    /** JS에서 window.AndroidBridge 로 호출 */
    private class Bridge {

        @JavascriptInterface
        public boolean hasCallLogPermission() {
            return hasCallLog();
        }

        @JavascriptInterface
        public void requestCallLogPermission() {
            runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.READ_CALL_LOG}, REQ_PERM));
        }

        /** sinceMs 이후의 통화기록을 JSON 배열 문자열로 반환 */
        @JavascriptInterface
        public String getCallLog(String sinceMs) {
            JSONArray arr = new JSONArray();
            if (!hasCallLog()) return arr.toString();
            long since = 0;
            try { since = Long.parseLong(sinceMs); } catch (Exception ignored) {}
            String[] cols = {
                    CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE,
                    CallLog.Calls.DURATION, CallLog.Calls.CACHED_NAME
            };
            ContentResolver cr = getContentResolver();
            try (Cursor c = cr.query(CallLog.Calls.CONTENT_URI, cols,
                    CallLog.Calls.DATE + " > ?", new String[]{String.valueOf(since)},
                    CallLog.Calls.DATE + " DESC")) {
                if (c != null) {
                    while (c.moveToNext()) {
                        JSONObject o = new JSONObject();
                        o.put("n", c.getString(0) == null ? "" : c.getString(0));
                        o.put("t", c.getInt(1));
                        o.put("d", c.getLong(2));
                        o.put("dur", c.getLong(3));
                        String name = c.getString(4);
                        if (name != null) o.put("nm", name);
                        arr.put(o);
                    }
                }
            } catch (Exception e) {
                return "[]";
            }
            return arr.toString();
        }

        /** 다운로드 폴더에 텍스트 파일 저장 */
        @JavascriptInterface
        public boolean saveFile(String name, String content) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return false;
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) return false;
                    os.write(content.getBytes(StandardCharsets.UTF_8));
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void driveAuthorize(boolean interactive) {
            runOnUiThread(() -> authorizeDrive(interactive));
        }

        @JavascriptInterface
        public void http(String id, String method, String url, String token, String contentType, String body) {
            httpRequest(id, method, url, token, contentType, body);
        }

        @JavascriptInterface
        public void toast(String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public void openAppSettings() {
            Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            runOnUiThread(() -> startSafe(i));
        }
    }
}
