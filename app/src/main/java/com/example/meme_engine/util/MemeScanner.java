package com.example.meme_engine.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class MemeScanner {

    private static final String TAG = "PerformanceTest";

    public interface ScanCallback {
        void onScanComplete(List<Uri> imageUris);
    }

    public static void scanFolder(Context context, Uri folderUri, ScanCallback callback) {
        new Thread(() -> {
            long scanStart = System.nanoTime();
            List<Uri> imageUris = new ArrayList<>();

            if (folderUri != null) {
                try {
                    String treeDocId = DocumentsContract.getTreeDocumentId(folderUri);
                    Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, treeDocId);

                    String[] projection = new String[]{
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED
                    };

                    try (Cursor cursor = context.getContentResolver().query(childrenUri, projection, null, null, null)) {
                        if (cursor != null) {
                            int idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                            int mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);

                            while (cursor.moveToNext()) {
                                if (idIndex != -1 && mimeIndex != -1) {
                                    String docId = cursor.getString(idIndex);
                                    String mimeType = cursor.getString(mimeIndex);

                                    if (isImage(mimeType)) {
                                        Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId);
                                        imageUris.add(fileUri);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error performing fast scan via ContentResolver", e);
                }
            }

            long scanTimeMs = (System.nanoTime() - scanStart) / 1_000_000;
            Log.d(TAG, "(a) Folder scan completed in " + scanTimeMs + " ms. Scanned " + imageUris.size() + " images.");

            for (int i = 0; i < Math.min(3, imageUris.size()); i++) {
                Log.d(TAG, "Scanned URI [" + i + "]: " + imageUris.get(i).toString());
            }

            callback.onScanComplete(imageUris);
        }).start();
    }

    private static boolean isImage(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }
}
