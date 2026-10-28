package com.apexlauncher.preview;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;

/** Offline, landscape-only wrapper for the dashboard prototype. */
public final class MainActivity extends Activity {
    private WebView dashboard;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        dashboard = new WebView(this);
        dashboard.setBackgroundColor(0xff070b1c);
        dashboard.getSettings().setJavaScriptEnabled(true);
        dashboard.getSettings().setAllowFileAccess(true);
        dashboard.getSettings().setAllowFileAccessFromFileURLs(false);
        dashboard.getSettings().setAllowUniversalAccessFromFileURLs(false);
        setContentView(dashboard);
        dashboard.loadUrl("file:///android_asset/index.html");
    }

    @Override public void onDestroy() {
        if (dashboard != null) dashboard.destroy();
        super.onDestroy();
    }
}
