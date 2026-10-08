package com.example.meme_engine;

import com.example.meme_engine.util.FuzzySearchMatcher;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FuzzySearchMatcherTest {

    @Test
    public void testTypoTolerance_hapy_matches_happy() {
        FuzzySearchMatcher.MatchResult result = FuzzySearchMatcher.match("happy cat", "hapy");
        assertTrue(result.isMatch);
        assertTrue(result.score > 0);
    }

    @Test
    public void testPrefixTypoTolerance_ofic_matches_office() {
        FuzzySearchMatcher.MatchResult result = FuzzySearchMatcher.match("office worker", "ofic");
        assertTrue(result.isMatch);
        assertTrue(result.score > 0);
    }

    @Test
    public void testShortWordStrictness_1or2Letters_noTypo() {
        FuzzySearchMatcher.MatchResult result1 = FuzzySearchMatcher.match("hi cat", "ho");
        assertFalse(result1.isMatch);

        FuzzySearchMatcher.MatchResult result2 = FuzzySearchMatcher.match("hi cat", "hi");
        assertTrue(result2.isMatch);
    }

    @Test
    public void testHashtagIsolation() {
        FuzzySearchMatcher.MatchResult hashtagResult = FuzzySearchMatcher.match("happy cat #funny", "#funi");
        assertTrue(hashtagResult.isMatch);

        FuzzySearchMatcher.MatchResult plainResult = FuzzySearchMatcher.match("happy cat #funny", "#happy");
        assertFalse(plainResult.isMatch);
    }

    @Test
    public void testMultiWordAndMatching() {
        FuzzySearchMatcher.MatchResult matchBoth = FuzzySearchMatcher.match("happy cat", "hapy cat");
        assertTrue(matchBoth.isMatch);

        FuzzySearchMatcher.MatchResult failOne = FuzzySearchMatcher.match("happy cat", "hapy dog");
        assertFalse(failOne.isMatch);
    }
}
