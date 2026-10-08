package com.example.meme_engine.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure Java utility for typo-tolerant (fuzzy) search matching and relevance scoring.
 * Zero Android dependencies so it can be unit tested cleanly.
 */
public class FuzzySearchMatcher {

    public static class MatchResult {
        public final boolean isMatch;
        public final int score;

        public MatchResult(boolean isMatch, int score) {
            this.isMatch = isMatch;
            this.score = score;
        }
    }

    /**
     * Evaluates a search query against a meme's tags string.
     *
     * @param tagsStr Space-separated string of meme tags (e.g. "happy cat #funny")
     * @param query   User search input query (e.g. "hapy cat")
     * @return MatchResult indicating if all query terms matched and the cumulative score.
     */
    public static MatchResult match(String tagsStr, String query) {
        if (query == null || query.trim().isEmpty()) {
            return new MatchResult(true, 0);
        }
        if (tagsStr == null || tagsStr.trim().isEmpty()) {
            return new MatchResult(false, 0);
        }

        String[] queryWords = query.trim().toLowerCase().split("\\s+");
        String[] tagTokens = tagsStr.trim().toLowerCase().split("\\s+");

        // Separate tag tokens into plain tags and hashtags
        List<String> plainTagTokens = new ArrayList<>();
        List<String> hashtagTokens = new ArrayList<>();

        for (String token : tagTokens) {
            if (token.startsWith("#")) {
                hashtagTokens.add(token);
            } else {
                plainTagTokens.add(token);
            }
        }

        int totalScore = 0;

        // Every word in the query must match at least one candidate tag token (AND logic)
        for (String qw : queryWords) {
            boolean isHashtagQuery = qw.startsWith("#");
            List<String> candidates = isHashtagQuery ? hashtagTokens : plainTagTokens;

            if (candidates.isEmpty()) {
                return new MatchResult(false, 0);
            }

            int bestWordScore = evaluateWordMatch(qw, candidates);
            if (bestWordScore <= 0) {
                return new MatchResult(false, 0); // Query word failed to match any tag token
            }

            totalScore += bestWordScore;
        }

        return new MatchResult(true, totalScore);
    }

    /**
     * Evaluates a single query word against a list of candidate tag tokens.
     */
    private static int evaluateWordMatch(String queryWord, List<String> candidateTokens) {
        int maxScore = 0;

        for (String token : candidateTokens) {
            int score = scoreSingleToken(queryWord, token);
            if (score > maxScore) {
                maxScore = score;
            }
        }

        return maxScore;
    }

    /**
     * Scores a single query word against a single candidate token.
     */
    public static int scoreSingleToken(String queryWord, String token) {
        if (queryWord == null || token == null || queryWord.isEmpty() || token.isEmpty()) {
            return 0;
        }

        String q = queryWord.toLowerCase();
        String t = token.toLowerCase();

        // 1. Direct exact match
        if (t.equals(q)) {
            return 100;
        }

        // 2. Starts-with match
        if (t.startsWith(q)) {
            return 80;
        }

        // 3. Contains substring match
        if (t.contains(q)) {
            return 60;
        }

        // Strip leading '#' for typo length evaluation and distance comparison
        String cleanQ = q.startsWith("#") ? q.substring(1) : q;
        String cleanT = t.startsWith("#") ? t.substring(1) : t;

        int cleanQLen = cleanQ.length();
        int cleanTLen = cleanT.length();

        // Rule: 1-2 letters = 0 typos; 3-4 letters = 1 typo; 5+ letters = 2 typos
        int maxAllowedTypos;
        if (cleanQLen <= 2) {
            maxAllowedTypos = 0;
        } else if (cleanQLen <= 4) {
            maxAllowedTypos = 1;
        } else {
            maxAllowedTypos = 2;
        }

        if (maxAllowedTypos == 0) {
            return 0; // No typos allowed for 1-2 letter terms
        }

        // Compare query against full tag token AND prefixes of tag token
        int minDistance = computeLevenshteinDistance(cleanQ, cleanT);

        // Compare against prefix of length = cleanQLen
        if (cleanTLen >= cleanQLen) {
            String prefix = cleanT.substring(0, cleanQLen);
            int dist = computeLevenshteinDistance(cleanQ, prefix);
            if (dist < minDistance) {
                minDistance = dist;
            }
        }

        // Compare against prefix of length = cleanQLen + 1
        if (cleanTLen >= cleanQLen + 1) {
            String prefix = cleanT.substring(0, cleanQLen + 1);
            int dist = computeLevenshteinDistance(cleanQ, prefix);
            if (dist < minDistance) {
                minDistance = dist;
            }
        }

        // Compare against prefix of length = cleanQLen - 1
        if (cleanQLen > 1 && cleanTLen >= cleanQLen - 1) {
            String prefix = cleanT.substring(0, cleanQLen - 1);
            int dist = computeLevenshteinDistance(cleanQ, prefix);
            if (dist < minDistance) {
                minDistance = dist;
            }
        }

        if (minDistance <= maxAllowedTypos) {
            return minDistance == 1 ? 40 : 20;
        }

        return 0;
    }

    /**
     * Standard Levenshtein edit distance algorithm (pure Java).
     */
    public static int computeLevenshteinDistance(String s1, String s2) {
        if (s1.equals(s2)) return 0;
        if (s1.isEmpty()) return s2.length();
        if (s2.isEmpty()) return s1.length();

        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else if (j > 0) {
                    int newValue = costs[j - 1];
                    if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                        newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                    }
                    costs[j - 1] = lastValue;
                    lastValue = newValue;
                }
            }
            if (i > 0) {
                costs[s2.length()] = lastValue;
            }
        }
        return costs[s2.length()];
    }
}
