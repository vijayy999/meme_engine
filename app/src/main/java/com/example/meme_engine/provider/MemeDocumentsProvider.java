package com.example.meme_engine.provider;

import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Point;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import androidx.annotation.Nullable;

import com.example.meme_engine.R;
import com.example.meme_engine.data.Meme;
import com.example.meme_engine.data.MemeDatabase;

import java.io.FileNotFoundException;
import java.util.List;

public class MemeDocumentsProvider extends DocumentsProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(projection != null ? projection : new String[]{
                DocumentsContract.Root.COLUMN_ROOT_ID,
                DocumentsContract.Root.COLUMN_ICON,
                DocumentsContract.Root.COLUMN_TITLE,
                DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.COLUMN_DOCUMENT_ID
        });

        result.addRow(new Object[]{
                "meme_root",
                R.mipmap.ic_launcher,
                getContext().getString(R.string.app_name),
                DocumentsContract.Root.FLAG_LOCAL_ONLY | DocumentsContract.Root.FLAG_SUPPORTS_SEARCH,
                "root_doc"
        });

        return result;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(projection != null ? projection : new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_FLAGS,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        });

        if ("root_doc".equals(documentId)) {
            result.addRow(new Object[]{
                    "root_doc",
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    getContext().getString(R.string.app_name),
                    DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE,
                    0,
                    System.currentTimeMillis()
            });
        } else {
            // It's likely a meme URI as ID
            Meme meme = MemeDatabase.getDatabase(getContext()).memeDao().getByUri(documentId);
            if (meme != null) {
                result.addRow(new Object[]{
                        meme.imageUri,
                        "image/*",
                        meme.imageUri.substring(meme.imageUri.lastIndexOf('/') + 1),
                        DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL,
                        0,
                        meme.dateAdded
                });
            }
        }
        return result;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(projection != null ? projection : new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_FLAGS,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        });

        if ("root_doc".equals(parentDocumentId)) {
            // Query the database for memes
            List<Meme> memes = MemeDatabase.getDatabase(getContext()).memeDao().getAll();
            if (memes != null) {
                for (Meme meme : memes) {
                    result.addRow(new Object[]{
                            meme.imageUri, // Use URI as ID
                            "image/*",
                            meme.imageUri.substring(meme.imageUri.lastIndexOf('/') + 1),
                            DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL,
                            0,
                            meme.dateAdded
                    });
                }
            }
        }
        return result;
    }

    @Override
    public AssetFileDescriptor openDocumentThumbnail(String documentId, Point sizeHint, CancellationSignal signal) throws FileNotFoundException {
        try {
            Uri uri = Uri.parse(documentId);
            return getContext().getContentResolver().openAssetFileDescriptor(uri, "r", null);
        } catch (Exception e) {
            throw new FileNotFoundException("Failed to open thumbnail for " + documentId);
        }
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, @Nullable CancellationSignal signal) throws FileNotFoundException {
        try {
            Uri uri = Uri.parse(documentId);
            return getContext().getContentResolver().openFileDescriptor(uri, mode);
        } catch (Exception e) {
            throw new FileNotFoundException("Failed to open " + documentId);
        }
    }
}
