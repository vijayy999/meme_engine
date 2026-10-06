package com.example.meme_engine.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public static void checkForUpdates(final Activity activity, final boolean showUpToDateToast) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        final String currentVersionName = getCurrentVersionName(activity);

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
                    String tagName = jsonResponse.optString("tag_name", "");
                    String releaseTitle = jsonResponse.optString("name", "New Release");
                    String releaseNotes = jsonResponse.optString("body", "A new version of Meme Engine is available!");

                    String downloadUrl = null;
                    JSONArray assets = jsonResponse.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String name = asset.optString("name", "");
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url", null);
                                break;
                            }
                        }
                    }

                    boolean isNewer = isVersionNewer(tagName, currentVersionName);

                    String finalDownloadUrl = downloadUrl;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (activity.isFinishing() || activity.isDestroyed()) return;

                        if (isNewer && finalDownloadUrl != null) {
                            showUpdateDialog(activity, tagName, releaseTitle, releaseNotes, finalDownloadUrl);
                        } else if (showUpToDateToast) {
                            Toast.makeText(activity, "Meme Engine is up to date! (v" + currentVersionName + ")", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    if (showUpToDateToast) {
                        new Handler(Looper.getMainLooper()).post(() ->
                                Toast.makeText(activity, "Unable to check for updates right now.", Toast.LENGTH_SHORT).show()
                        );
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking for updates: " + e.getMessage(), e);
                if (showUpToDateToast) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(activity, "No releases found on GitHub yet.", Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }

    public static String getCurrentVersionName(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName != null ? pInfo.versionName : "1.0";
        } catch (Exception e) {
            return "1.0";
        }
    }

    private static boolean isVersionNewer(String remoteTag, String currentVersionName) {
        if (remoteTag == null || remoteTag.trim().isEmpty()) return false;

        String cleanRemote = remoteTag.replaceAll("[^0-9.]", "").trim();
        String cleanCurrent = currentVersionName != null ? currentVersionName.replaceAll("[^0-9.]", "").trim() : "1.0";

        String[] remoteParts = cleanRemote.split("\\.");
        String[] currentParts = cleanCurrent.split("\\.");

        int length = Math.max(remoteParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            int remoteNum = i < remoteParts.length && !remoteParts[i].isEmpty() ? Integer.parseInt(remoteParts[i]) : 0;
            int currentNum = i < currentParts.length && !currentParts[i].isEmpty() ? Integer.parseInt(currentParts[i]) : 0;

            if (remoteNum > currentNum) return true;
            if (remoteNum < currentNum) return false;
        }
        return false;
    }

    private static void showUpdateDialog(Activity activity, String tagName, String title, String notes, String downloadUrl) {
        new AlertDialog.Builder(activity)
                .setTitle("🎉 New Update " + tagName)
                .setMessage(title + "\n\n" + notes)
                .setPositiveButton("Update Now", (dialog, which) -> checkPermissionAndDownload(activity, downloadUrl))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void checkPermissionAndDownload(Activity activity, String downloadUrl) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity)
                        .setTitle("Permission Needed")
                        .setMessage("To install the new update, please allow Meme Engine to install unknown apps in settings.")
                        .setPositiveButton("Go to Settings", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
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
                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.connect();

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

                new Handler(Looper.getMainLooper()).post(() -> installApk(activity, apkFile));

            } catch (Exception e) {
                Log.e(TAG, "Failed to download update APK: " + e.getMessage(), e);
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(activity, "Failed to download update.", Toast.LENGTH_SHORT).show()
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
