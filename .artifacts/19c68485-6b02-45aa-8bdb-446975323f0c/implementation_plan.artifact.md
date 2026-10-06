# Meme Engine Implementation Plan

Implementation of a personal meme-search tool that allows users to tag images in a local folder and search them later. The app will also integrate as an image picker for other applications.

## User Review Required

> [!IMPORTANT]
> The app will use `ACTION_OPEN_DOCUMENT_TREE` for folder selection. The user will need to grant persistable URI permissions for the app to access the folder across restarts.

> [!NOTE]
> The app will use Glide for efficient thumbnail loading and Room for metadata storage.

## Proposed Changes

### Dependencies & Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Users/Lcode/AndroidStudioProjects/meme_engine/gradle/libs.versions.toml)
- Add Room and Glide versions and libraries.

#### [MODIFY] [build.gradle.kts](file:///C:/Users/Lcode/AndroidStudioProjects/meme_engine/app/build.gradle.kts)
- Add Room and Glide dependencies.
- Enable annotation processing.

---

### Data Layer (Room)

#### [NEW] [Meme.java](file:///C:/Users/Lcode/AndroidStudioProjects/meme_engine/app/src/main/java/com/example/meme_engine/data/Meme.java)
- Room entity with `imageUri`, `tags`, and `dateAdded`.

#### [NEW] [MemeDao.java](file:///C:/Users/Lcode/AndroidStudioProjects/meme_engine/app/src/main/java/com/example/meme_engine/data/MemeDao.java)
- CRUD operations and search query.

#### [NEW] [MemeDatabase.java](file:///C:/Users/Lcode/AndroidStudioProjects/meme_engine/app/src/main/java/com/example/meme_engine/data/MemeDatabase.java)
- Room database singleton.

---

### Folder Scanning & Helpers

#### [NEW] [MemeScanner.java](file:///C:/Users/Lcode/AndroidStudioProjects/meme_engine/app/src/main/java/com/example/meme_engine/util/MemeScanner.java)
- Helper class to query `MediaStore.Images.Media` for a given folder URI/path.

#### [NEW] [StorageHelper.java](file:///C:/Users/Lcode/AndroidStudioProjects/meme_engine/app/src/main/java/com/example/meme_engine/util/StorageHelper.java)
- Manages `SharedPreferences` for the root folder URI and persistable URI permissions.

---

### UI Components

#### [MODIFY] [MainActivity.java](file:///C:/Users/Lcode/AndroidStudioProjects/meme_engine/app/src/main/java/com/example/meme_engine/MainActivity.java)
- Rename to `MainGridActivity` or refactor as the home screen.
- Implement `RecyclerView` for meme grid.
- Handle folder picker result.

#### [NEW] [MemeAdapter.java](file:///C:/Users/Lcode/AndroidStudioProjects/meme_engine/app/src/main/java/com/example/meme_engine/ui/MemeAdapter.java)
- Adapter for the grid, showing thumbnails and X/✓ badges.

#### [NEW] [TagMemeDialogFragment.java](file:///C:/Users/Lcode/AndroidStudioProjects/meme_engine/app/src/main/java/com/example/meme_engine/ui/TagMemeDialogFragment.java)
- `DialogFragment` for adding/editing tags.

#### [NEW] [SearchActivity.java](file:///C:/Users/Lcode/AndroidStudioProjects/meme_engine/app/src/main/java/com/example/meme_engine/ui/SearchActivity.java)
- Search interface with live filtering.
- Handles `GET_CONTENT` intent.

---

### Resources

#### [NEW] [item_meme.xml](file:///C:/Users/Lcode/AndroidStudioProjects/meme_engine/app/src/main/res/layout/item_meme.xml)
- Layout for a single meme item in the grid (ImageView + Badge).

#### [NEW] [dialog_tag_meme.xml](file:///C:/Users/Lcode/AndroidStudioProjects/meme_engine/app/src/main/res/layout/dialog_tag_meme.xml)
- Layout for the tag dialog.

#### [MODIFY] [activity_main.xml](file:///C:/Users/Lcode/AndroidStudioProjects/meme_engine/app/src/main/res/layout/activity_main.xml)
- Add `RecyclerView` and toolbar.

---

### Manifest & Integration

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/Lcode/AndroidStudioProjects/meme_engine/app/src/main/AndroidManifest.xml)
- Declare `SearchActivity` with `GET_CONTENT` intent filter.
- Add necessary permissions.

## Verification Plan

### Automated Tests
- Room DAO tests for `Meme` entities and search logic.

### Manual Verification
- Verify grid displays images from a selected folder.
- Verify tagging an image updates its badge.
- Verify search filters results correctly.
- Verify app appears in "More apps" when using "Attach" in WhatsApp/Telegram and correctly returns the selected image.
