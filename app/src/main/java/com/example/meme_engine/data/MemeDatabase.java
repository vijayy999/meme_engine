package com.example.meme_engine.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {Meme.class}, version = 1, exportSchema = false)
public abstract class MemeDatabase extends RoomDatabase {
    public abstract MemeDao memeDao();

    private static volatile MemeDatabase INSTANCE;

    public static MemeDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (MemeDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    MemeDatabase.class, "meme_database")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
