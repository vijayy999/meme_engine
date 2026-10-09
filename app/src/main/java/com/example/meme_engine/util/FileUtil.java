package com.example.meme_engine.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.documentfile.provider.DocumentFile;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtil {
    private static final String TAG = "FileUtil";

    public static boolean copyFile(Context context, Uri sourceUri, Uri destFolderUri) {
        return copyFileAndGetUri(context, sourceUri, destFolderUri) != null;
    }

    public static Uri copyFileAndGetUri(Context context, Uri sourceUri, Uri destFolderUri) {
        if (context == null || sourceUri == null || destFolderUri == null) {
            return null;
        }
        try {
            DocumentFile sourceFile = DocumentFile.fromSingleUri(context, sourceUri);
            DocumentFile destFolder = DocumentFile.fromTreeUri(context, destFolderUri);

            if (destFolder == null || !destFolder.isDirectory()) {
                Log.e(TAG, "Destination folder is invalid or not a directory.");
                return null;
            }

            String fileName = (sourceFile != null && sourceFile.getName() != null)
                    ? sourceFile.getName()
                    : "meme_" + System.currentTimeMillis() + ".jpg";

            String mimeType = (sourceFile != null && sourceFile.getType() != null)
                    ? sourceFile.getType()
                    : "image/jpeg";

            DocumentFile newFile = destFolder.createFile(mimeType, fileName);
            if (newFile == null) {
                Log.e(TAG, "Failed to create new file in destination folder.");
                return null;
            }

            try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
                 OutputStream out = context.getContentResolver().openOutputStream(newFile.getUri())) {

                if (in == null || out == null) return null;

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
                return newFile.getUri();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error copying file: " + e.getMessage(), e);
            return null;
        }
    }

    public static boolean deleteFile(Context context, Uri fileUri) {
        if (context == null || fileUri == null) return false;
        try {
            DocumentFile file = DocumentFile.fromSingleUri(context, fileUri);
            if (file != null && file.exists()) {
                return file.delete();
            }
            int rows = context.getContentResolver().delete(fileUri, null, null);
            return rows > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting file: " + e.getMessage(), e);
            return false;
        }
    }
}
