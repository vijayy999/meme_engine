# Implementation Plan - Main Grid Search Hashtag Matching & Fallback Penalty

Make the main grid search also match hashtags with fallback penalty and updated unit tests.

## User Review Required

> [!IMPORTANT]
> Please review the requirements and the proposed design before we proceed.

## Proposed Changes

### [util]

#### [MODIFY] [FuzzySearchMatcher.java](file:///C:/Users/Lcode/AndroidStudioProjects/meme_engine/app/src/main/java/com/example/meme_engine/util/FuzzySearchMatcher.java)
- Add constant `public static final int HASHTAG_FALLBACK_PENALTY = 1000;`.
- Update `match(String tagsStr, String query)` logic:
  - For a plain query word (does not start with '#'):
    - First check plain tags (`plainTagTokens`). If any match, use the best plain tag score (normal score).
    - If no plain tag matches, check hashtag tokens (`hashtagTokens`) with the `'#'` stripped. If a hashtag matches, compute score and subtract `HASHTAG_FALLBACK_PENALTY`.
  - For a hashtag query (starts with '#'):
    - Remains hashtag-only, exactly as today.
  - Maintain AND logic across multi-word queries.
  - Ensure typo tolerance works on the hashtag fallback too.

### [test]

#### [MODIFY] [FuzzySearchMatcherTest.java](file:///C:/Users/Lcode/AndroidStudioProjects/meme_engine/app/src/test/java/com/example/meme_engine/FuzzySearchMatcherTest.java)
- Add unit test 1: `"wasted #drowned #gtavc"` matches `"drowned"`; `"wasted"` does not match `"drowned"`.
- Add unit test 2: A plain-tag match scores higher than a hashtag-only match.
- Add unit test 3: `"drowned wasted"` does NOT match `"#drowned"`.

## Verification Plan

### Automated Tests
- Run Gradle unit tests (`app:testDebugUnitTest`).
