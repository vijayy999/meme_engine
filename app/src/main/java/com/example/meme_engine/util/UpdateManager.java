package com.example.meme_engine.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import com.example.meme_engine.BuildConfig;
import com.example.meme_engine.R;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateManager {

    private static final String TAG = "UpdateManager";
    private static final String GITHUB_RELEASE_API = "https://api.github.com/repos/vijayy999/meme_engine/releases/latest";
    private static final String PREFS_NAME = "meme_engine_update_prefs";
    private static final String KEY_LAST_CHECK_TIME = "last_check_time";
    private static final long CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L; // 24 hours

    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public static class UpdateInfo {
        public int latestVersionCode;
        public int minVersionCode;
        public String apkUrl;
        public String message;
        public String tagName;
    }

    /**
     * Automatic check: runs at most once every 24 hours unless forced. Silent on failure or no update.
     */
    public static void checkAutomaticUpdate(final Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L);
        long now = System.currentTimeMillis();

        // 24-hour throttling for automatic checks
        if (now - lastCheck < CHECK_INTERVAL_MS) {
            return;
        }

        prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply();

        fetchUpdateInfo(activity, false);
    }

    /**
     * Manual check: user initiated via toolbar icon. Ignores 24h limit and provides feedback in all cases.
     */
    public static void checkForUpdates(final Activity activity, final boolean showUpToDateToast) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        fetchUpdateInfo(activity, showUpToDateToast);
    }

    private static void fetchUpdateInfo(final Activity activity, final boolean manualCheck) {
        executorService.execute(() -> {
            try {
                URL url = new URL(GITHUB_RELEASE_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream in = conn.getInputStream();
                    StringBuilder builder = new StringBuilder();
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        builder.append(new String(buffer, 0, bytesRead));
                    }
                    in.close();

                    JSONObject jsonResponse = new JSONObject(builder.toString());
                    UpdateInfo info = parseUpdateInfo(jsonResponse);

                    int currentVersionCode = getCurrentVersionCode(activity);

                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (activity.isFinishing() || activity.isDestroyed()) return;

                        if (currentVersionCode < info.minVersionCode) {
                            // FORCED UPDATE: Cannot be dismissed
                            showForcedUpdateDialog(activity, info);
                        } else if (currentVersionCode < info.latestVersionCode) {
                            // OPTIONAL UPDATE
                            showOptionalUpdateDialog(activity, info);
                        } else if (manualCheck) {
                            Toast.makeText(activity, "Meme Engine is up to date! (v" + BuildConfig.VERSION_NAME + ")", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    if (manualCheck) {
                        new Handler(Looper.getMainLooper()).post(() ->
                                Toast.makeText(activity, "Couldn't check for updates (Server error).", Toast.LENGTH_SHORT).show()
                        );
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking updates: " + e.getMessage(), e);
                if (manualCheck) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(activity, "Couldn't check for updates (Network error).", Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }

    private static UpdateInfo parseUpdateInfo(JSONObject jsonResponse) {
        UpdateInfo info = new UpdateInfo();
        info.tagName = jsonResponse.optString("tag_name", "v1.0");
        info.message = jsonResponse.optString("body", "A new version of Meme Engine is available!");

        // Parse apkUrl from assets
        String apkUrl = null;
        JSONArray assets = jsonResponse.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset != null) {
                    String name = asset.optString("name", "");
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", null);
                        break;
                    }
                }
            }
        }
        info.apkUrl = apkUrl;

        // Try parsing version codes from json properties or fallback to tag parsing (e.g. v1.1 -> 2 or 11)
        info.latestVersionCode = jsonResponse.optInt("latestVersionCode", parseVersionCodeFromTag(info.tagName));
        info.minVersionCode = jsonResponse.optInt("minVersionCode", 0); // Default to 0 if absent

        return info;
    }

    private static int parseVersionCodeFromTag(String tag) {
        if (tag == null) return 1;
        String clean = tag.replaceAll("[^0-9]", "");
        if (clean.isEmpty()) return 1;
        try {
            // e.g. "1.1" -> 11 or "2" -> 2. Let's parse float/int or parse dotted version to integer code.
            String[] parts = tag.replaceAll("[^0-9.]", "").split("\\.");
            if (parts.length >= 2) {
                int major = Integer.parseInt(parts[0]);
                int minor = Integer.parseInt(parts[1]);
                return major * 10 + minor; // e.g. 1.1 -> 11, 1.0 -> 10, 2.0 -> 20
            } else if (parts.length == 1 && !parts[0].isEmpty()) {
                return Integer.parseInt(parts[0]);
            }
        } catch (Exception ignored) {}
        return 1;
    }

    private static int getCurrentVersionCode(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) pInfo.getLongVersionCode();
            } else {
                return pInfo.versionCode;
            }
        } catch (Exception e) {
            return BuildConfig.VERSION_CODE;
        }
    }

    private static void showOptionalUpdateDialog(Activity activity, UpdateInfo info) {
        new AlertDialog.Builder(activity)
                .setTitle("🎉 New Update Available (" + info.tagName + ")")
                .setMessage(info.message)
                .setPositiveButton("Update", (dialog, which) -> openUpdateUrl(activity, info.apkUrl))
                .setNegativeButton("Later", null)
                .show();
    }

    private static void showForcedUpdateDialog(Activity activity, UpdateInfo info) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("⚠️ Mandatory Update Required (" + info.tagName + ")")
                .setMessage("A critical update is required to continue using Meme Engine.\n\n" + info.message)
                .setPositiveButton("Update Now", (d, w) -> {
                    openUpdateUrl(activity, info.apkUrl);
                    // Re-show forced dialog if user tries to bypass
                    activity.finishAffinity();
                })
                .setCancelable(false)
                .create();

        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private static void openUpdateUrl(Activity activity, String apkUrl) {
        if (apkUrl == null || apkUrl.isEmpty()) {
            apkUrl = GITHUB_RELEASE_API.replace("/releases/latest", "/releases");
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "Unable to open update link.", Toast.LENGTH_SHORT).show();
        }
    }
}
