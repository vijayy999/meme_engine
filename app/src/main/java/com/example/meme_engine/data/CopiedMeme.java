package com.example.meme_engine.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "copied_memes")
public class CopiedMeme {
    @PrimaryKey
    @NonNull
    public String destImageUri;

    public long copyTimestamp;
    public String sourceImageUri;

    public CopiedMeme(@NonNull String destImageUri, long copyTimestamp, String sourceImageUri) {
        this.destImageUri = destImageUri;
        this.copyTimestamp = copyTimestamp;
        this.sourceImageUri = sourceImageUri;
    }
}
