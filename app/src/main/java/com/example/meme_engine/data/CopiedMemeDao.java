package com.example.meme_engine.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CopiedMemeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CopiedMeme meme);

    @Query("DELETE FROM copied_memes WHERE destImageUri = :destImageUri")
    void deleteByDestUri(String destImageUri);

    @Query("SELECT * FROM copied_memes WHERE copyTimestamp < :thresholdTimestamp")
    List<CopiedMeme> getExpiredCopied(long thresholdTimestamp);

    @Query("SELECT * FROM copied_memes")
    List<CopiedMeme> getAll();
}
