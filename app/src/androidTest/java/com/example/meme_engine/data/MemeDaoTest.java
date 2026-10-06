package com.example.meme_engine.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
    public void testSearchByTag() {
        dao.insert(new Meme("uri1", "wasted", 0));
        dao.insert(new Meme("uri2", "wastedd", 0));
        dao.insert(new Meme("uri3", "wasteddd", 0));

        List<Meme> results = dao.searchByTag("wasted");
        assertEquals(3, results.size());

        results = dao.searchByTag("wastedd");
        assertEquals(2, results.size());

        results = dao.searchByTag("wasteddd");
        assertEquals(1, results.size());
        assertEquals("uri3", results.get(0).imageUri);

        results = dao.searchByTag("GTA");
        assertEquals(0, results.size());
    }

    @Test
    public void testCaseInsensitivity() {
        dao.insert(new Meme("uri1", "WASTED", 0));
        List<Meme> results = dao.searchByTag("wasted");
        assertEquals(1, results.size());
    }
}
