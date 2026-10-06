package com.example.meme_engine.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "memes")
public class Meme {
    @PrimaryKey
    @NonNull
    public String imageUri;   // content:// URI string, unique per image

    public String tags;       // free text, comma separated
    public long dateAdded;
    public long firstSeen;    // timestamp when first scanned in source folder

    public Meme(@NonNull String imageUri, String tags, long dateAdded, long firstSeen) {
        this.imageUri = imageUri;
        this.tags = tags;
        this.dateAdded = dateAdded;
        this.firstSeen = firstSeen;
    }

    @Ignore
    public Meme(@NonNull String imageUri, String tags, long dateAdded) {
        this(imageUri, tags, dateAdded, 0L);
    }
}
