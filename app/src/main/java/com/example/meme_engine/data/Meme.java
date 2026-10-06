package com.example.meme_engine.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "memes")
public class Meme {
    @PrimaryKey
    @NonNull
    public String imageUri;   // content:// URI string, unique per image

    public String tags;       // free text, comma separated
    public long dateAdded;

    public Meme(@NonNull String imageUri, String tags, long dateAdded) {
        this.imageUri = imageUri;
        this.tags = tags;
        this.dateAdded = dateAdded;
    }
}
