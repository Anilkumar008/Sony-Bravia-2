package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** Handles market://launch?id=<package> (Android TV Remote "launch app" by package name), which the
 *  stock launcher normally serves: opens the app's TV launch intent, then the phone launch intent. */
public class AppLaunchActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            Uri data = getIntent().getData();
            String pkg = data == null ? null : data.getQueryParameter("id");
            if (pkg != null) {
                boolean fresh = LastOpened.isClosed(this, pkg);
                Intent launch = null;
                if ("com.google.android.youtube.tv".equals(pkg) && fresh) {
                    launch = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube://")).setPackage(pkg);
                } else {
                    launch = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                    if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(pkg);
                }
                if (launch != null) {
                    if (fresh) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        LastOpened.unmarkClosed(this, pkg);
                    } else {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    startActivity(launch);
                    LastOpened.record(this, pkg);
                }
            }
        } catch (ActivityNotFoundException | SecurityException ignored) {
        } finally {
            finish();
        }
    }
}
