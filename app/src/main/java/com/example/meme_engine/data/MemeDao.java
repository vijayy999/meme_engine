package com.example.meme_engine.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface MemeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Meme meme);

    @Update
    void update(Meme meme);

    @Query("SELECT * FROM memes WHERE imageUri = :imageUri LIMIT 1")
    Meme getByUri(String imageUri);

    @Query("SELECT * FROM memes WHERE tags LIKE '%' || :query || '%' COLLATE NOCASE")
    List<Meme> searchByTag(String query);

    @Query("SELECT * FROM memes")
    List<Meme> getAll();
}
