package com.example.meme_engine.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Meme.class, CopiedMeme.class}, version = 3, exportSchema = false)
public abstract class MemeDatabase extends RoomDatabase {
    public abstract MemeDao memeDao();
    public abstract CopiedMemeDao copiedMemeDao();

    private static volatile MemeDatabase INSTANCE;

    // Non-destructive migration from version 1 to 2 to preserve existing tags while adding firstSeen support
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE memes ADD COLUMN firstSeen INTEGER NOT NULL DEFAULT 0");
        }
    };

    // Migration from version 2 to 3 to add copied_memes table for 24-hour destination auto-cleanup
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `copied_memes` (`destImageUri` TEXT NOT NULL, `copyTimestamp` INTEGER NOT NULL, `sourceImageUri` TEXT, PRIMARY KEY(`destImageUri`))");
        }
    };

    public static MemeDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (MemeDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    MemeDatabase.class, "meme_database")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
