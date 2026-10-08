package com.example.meme_engine.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class MemeDaoTest {
    private MemeDatabase db;
    private MemeDao dao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, MemeDatabase.class).build();
        dao = db.memeDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    @Test
    public void testInsertAndGetByUri() {
        dao.insert(new Meme("uri1", "wasted", 100, 100));
        Meme result = dao.getByUri("uri1");
        assertNotNull(result);
        assertEquals("wasted", result.tags);
    }

    @Test
    public void testInsertAllAndGetAll() {
        List<Meme> memes = new ArrayList<>();
        memes.add(new Meme("uri1", "wasted", 100, 100));
        memes.add(new Meme("uri2", "funny", 200, 200));

        dao.insertAll(memes);
        List<Meme> all = dao.getAll();
        assertEquals(2, all.size());
    }
}
