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
                conn.setRequestProperty("User-Agent", "MemeEngine-App");
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
                    String currentVersionName = getCurrentVersionName(activity);

                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (activity.isFinishing() || activity.isDestroyed()) return;

                        // Strict version code comparison as instructed
                        if (currentVersionCode < info.minVersionCode) {
                            // FORCED UPDATE: Cannot be dismissed
                            showForcedUpdateDialog(activity, info);
                        } else if (currentVersionCode < info.latestVersionCode) {
                            // OPTIONAL UPDATE
                            showOptionalUpdateDialog(activity, info);
                        } else if (manualCheck) {
                            Toast.makeText(activity, "Meme Engine is up to date! (v" + currentVersionName + ", code " + currentVersionCode + ")", Toast.LENGTH_SHORT).show();
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

        // Parse latestVersionCode from JSON or fallback to parsing tag into integer code
        info.latestVersionCode = jsonResponse.optInt("latestVersionCode", parseVersionCodeFromTag(info.tagName));
        info.minVersionCode = jsonResponse.optInt("minVersionCode", 0);

        return info;
    }

    private static int parseVersionCodeFromTag(String tag) {
        if (tag == null) return 1;
        try {
            String[] parts = tag.replaceAll("[^0-9.]", "").split("\\.");
            if (parts.length >= 2) {
                int major = Integer.parseInt(parts[0]);
                int minor = Integer.parseInt(parts[1]);
                return major * 10 + minor; // e.g. "1.2" -> 12
            } else if (parts.length == 1 && !parts[0].isEmpty()) {
                return Integer.parseInt(parts[0]);
            }
        } catch (Exception ignored) {}
        return 1;
    }

    public static String getCurrentVersionName(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName != null ? pInfo.versionName : "1.2";
        } catch (Exception e) {
            return BuildConfig.VERSION_NAME;
        }
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
                .setPositiveButton("Update Now", (dialog, which) -> checkPermissionAndDownload(activity, info.apkUrl))
                .setNegativeButton("Later", null)
                .show();
    }

    private static void showForcedUpdateDialog(Activity activity, UpdateInfo info) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("⚠️ Mandatory Update Required (" + info.tagName + ")")
                .setMessage("A critical update is required to continue using Meme Engine.\n\n" + info.message)
                .setPositiveButton("Update Now", (d, w) -> {
                    checkPermissionAndDownload(activity, info.apkUrl);
                    activity.finishAffinity();
                })
                .setCancelable(false)
                .create();

        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private static void checkPermissionAndDownload(Activity activity, String downloadUrl) {
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            Toast.makeText(activity, "APK download link not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity)
                        .setTitle("Permission Needed")
                        .setMessage("To install the new update, please allow Meme Engine to install unknown apps in settings.")
                        .setPositiveButton("Go to Settings", (dialog, which) -> {
                            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                            intent.setData(Uri.parse("package:" + activity.getPackageName()));
                            activity.startActivity(intent);
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
                return;
            }
        }
        downloadAndInstallApk(activity, downloadUrl);
    }

    public static void downloadAndInstallApk(Activity activity, String downloadUrl) {
        Toast.makeText(activity, "Downloading update...", Toast.LENGTH_LONG).show();

        executorService.execute(() -> {
            try {
                String currentUrl = downloadUrl;
                HttpURLConnection conn = null;
                boolean redirected;
                int redirectsCount = 0;

                // Handle GitHub release asset redirects (Amazon S3) manually
                do {
                    redirected = false;
                    URL url = new URL(currentUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setInstanceFollowRedirects(false);
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "MemeEngine-App");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    conn.connect();

                    int status = conn.getResponseCode();
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                            status == HttpURLConnection.HTTP_MOVED_PERM ||
                            status == HttpURLConnection.HTTP_SEE_OTHER ||
                            status == 307 || status == 308) {
                        String newUrl = conn.getHeaderField("Location");
                        if (newUrl != null) {
                            currentUrl = newUrl;
                            redirected = true;
                            redirectsCount++;
                            conn.disconnect();
                        }
                    }
                } while (redirected && redirectsCount < 5);

                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new Exception("Server returned HTTP " + conn.getResponseCode());
                }

                File apkFile = new File(activity.getCacheDir(), "meme_engine_update.apk");
                if (apkFile.exists()) {
                    boolean deleted = apkFile.delete();
                    if (!deleted) Log.w(TAG, "Could not delete old apk file before downloading");
                }

                InputStream in = conn.getInputStream();
                FileOutputStream out = new FileOutputStream(apkFile);

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }

                out.flush();
                out.close();
                in.close();
                conn.disconnect();

                // Verify file size and content
                long fileSize = apkFile.length();
                Log.d(TAG, "Downloaded update APK size: " + fileSize + " bytes");

                if (fileSize < 100000) {
                    // Less than 100KB means it's an error/HTML page, not a real APK
                    throw new Exception("Downloaded file is invalid (size: " + fileSize + " bytes)");
                }

                new Handler(Looper.getMainLooper()).post(() -> installApk(activity, apkFile));

            } catch (Exception e) {
                Log.e(TAG, "Failed to download update APK: " + e.getMessage(), e);
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(activity, "Failed to download update: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private static void installApk(Context context, File apkFile) {
        try {
            Uri apkUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    apkFile
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error launching package installer: " + e.getMessage(), e);
            Toast.makeText(context, "Error opening installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
