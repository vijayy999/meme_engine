package com.example.meme_engine.util;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.util.ArrayList;
import java.util.List;

public class MemeScanner {

    public interface ScanCallback {
        void onScanComplete(List<Uri> imageUris);
    }

    public static void scanFolder(Context context, Uri folderUri, ScanCallback callback) {
        new Thread(() -> {
            List<Uri> imageUris = new ArrayList<>();
            DocumentFile root = DocumentFile.fromTreeUri(context, folderUri);
            if (root != null && root.isDirectory()) {
                for (DocumentFile file : root.listFiles()) {
                    if (file.isFile() && isImage(file.getType())) {
                        imageUris.add(file.getUri());
                    }
                }
            }
            callback.onScanComplete(imageUris);
        }).start();
    }

    private static boolean isImage(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }
}
