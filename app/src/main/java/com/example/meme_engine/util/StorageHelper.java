package com.example.meme_engine.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;

public class StorageHelper {
    private static final String PREFS_NAME = "meme_engine_prefs";
    private static final String KEY_ROOT_FOLDER_URI = "root_folder_uri";
    private static final String KEY_DESTINATION_FOLDER_URI = "destination_folder_uri";

    public static void setSourceFolderUri(Context context, Uri uri) {
        setRootFolderUri(context, uri);
    }

    public static Uri getSourceFolderUri(Context context) {
        return getRootFolderUri(context);
    }

    public static void setRootFolderUri(Context context, Uri uri) {
        if (uri == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_ROOT_FOLDER_URI, uri.toString()).apply();

        try {
            int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            context.getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Uri getRootFolderUri(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String uriString = prefs.getString(KEY_ROOT_FOLDER_URI, null);
        return uriString != null ? Uri.parse(uriString) : null;
    }

    public static void setDestinationFolderUri(Context context, Uri uri) {
        if (uri == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_DESTINATION_FOLDER_URI, uri.toString()).apply();

        try {
            int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            context.getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Uri getDestinationFolderUri(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String uriString = prefs.getString(KEY_DESTINATION_FOLDER_URI, null);
        return uriString != null ? Uri.parse(uriString) : null;
    }

    public static String getFolderName(Context context, Uri folderUri) {
        if (folderUri == null) return "Not Selected";
        try {
            DocumentFile file = DocumentFile.fromTreeUri(context, folderUri);
            if (file != null && file.getName() != null) {
                return file.getName();
            }
        } catch (Exception ignored) {}
        String path = folderUri.getPath();
        if (path != null) {
            int lastIdx = path.lastIndexOf(':');
            if (lastIdx != -1) {
                return path.substring(lastIdx + 1);
            }
        }
        return "Selected Folder";
    }
}
