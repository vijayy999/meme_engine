# 🎭 Meme Engine

**Meme Engine** is an intelligent, privacy-first Android application built for scanning, tagging, searching, organizing, and sharing memes locally on Android devices. It functions as both a standalone meme manager and a native **Android System Picker Provider** and **Documents Provider**, allowing users to seamlessly search and attach tagged memes directly within third-party apps like WhatsApp, Telegram, Discord, and Messages.

---

## 📑 Table of Contents
1. [Key Features](#-key-features)
2. [Architecture & System Flow Overview](#-architecture--system-flow-overview)
3. [Component & File Analysis](#-component--file-analysis)
   - [Data Layer](#1-data-layer-comexamplememe_enginedata)
   - [UI Layer](#2-ui-layer-comexamplememe_engineui)
   - [Utility Layer](#3-utility-layer-comexamplememe_engineutil)
   - [System Providers & System Integration](#4-system-providers--integration)
4. [Detailed End-to-End Execution Flows](#-detailed-end-to-end-execution-flows)
   - [Flow A: App Startup & Permission Flow](#flow-a-app-startup--permission-flow)
   - [Flow B: SAF Folder Selection & Persistable Permissions](#flow-b-saf-folder-selection--persistable-permissions)
   - [Flow C: Background Scanning & First-Seen Tracking](#flow-c-background-scanning--first-seen-tracking)
   - [Flow D: Tagging, Hashtag Processing & Destination Copy](#flow-d-tagging-hashtag-processing--destination-copy)
   - [Flow E: Unified Multi-Filter & Search Algorithm](#flow-e-unified-multi-filter--search-algorithm)
   - [Flow F: Destination Tab & Batch Multi-Deletion](#flow-f-destination-tab--batch-multi-deletion)
   - [Flow G: System Picker Mode (`GET_CONTENT` / `PICK`)](#flow-g-system-picker-mode-get_content--pick)
   - [Flow H: System Documents Provider Integration](#flow-h-system-documents-provider-integration)
   - [Flow I: OTA Software Update Engine](#flow-i-ota-software-update-engine)
5. [Database Architecture & Migrations](#-database-architecture--migrations)
6. [Tech Stack & Build System](#-tech-stack--build-system)
7. [How to Build & Install](#-how-to-build--install)

---

## 🌟 Key Features

- 📁 **Storage Access Framework (SAF) Folder Management**: Select source and destination directories on internal storage or SD cards with persistent, reboot-resilient read/write permissions.
- 🏷️ **Smart Tagging & Hashtags**: Dual-field tagging supporting plain descriptive keywords and categorized `#hashtags`. Auto-formats missing `#` tokens.
- 🚦 **Visual Tag Status Badges**: Source grid overlay indicates whether an image is tagged (`ic_tag_done` checkmark) or untagged (`ic_tag_none`).
- 🎛️ **Unified Filtering System**: Filter source memes simultaneously by Chip categories (`All`, `Tagged`, `Untagged`) and search queries.
- 🕒 **First-Seen Timestamp Sorting**: Automatically registers newly discovered images in Room DB on first scan, ensuring newest memes always appear at the top.
- 📁 **Dedicated Destination Storage**: Copy tagged memes to an organized output folder (e.g. `Pictures/OrganizedMemes`) with a single tap.
- 🗑️ **Multi-Select & Batch Deletion**: Long-press in the Destination tab to toggle selection overlays and perform batch file deletions.
- 📲 **System Intent Interception (`GET_CONTENT` / `PICK`)**: Pick tagged memes directly from within third-party apps (WhatsApp, Discord, Twitter, etc.).
- 📂 **Custom Documents Provider**: Exposes tagged memes as a system-recognized document root (`com.example.meme_engine.documents`) in the Android Files/Storage picker.
- 🔄 **In-App OTA Software Updates**: Automated GitHub Release API integration featuring 24-hour update throttling, forced updates for breaking versions, Amazon S3 redirect handling, and background APK installation.

---

## 🏗️ Architecture & System Flow Overview

```
                          ┌─────────────────────────────────────────┐
                          │            Android OS / SAF             │
                          └────────────────────┬────────────────────┘
                                               │
                                    Storage Access Framework
                                  (Tree URIs & Permissions)
                                               │
                                               ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                                   com.example.meme_engine                                   │
│                                                                                             │
│   ┌───────────────────────────┐                       ┌─────────────────────────────────┐   │
│   │     MainGridActivity      │                       │         SearchActivity          │   │
│   │   (Main Dashboard View)   │                       │      (System Picker Mode)       │   │
│   └─────────────┬─────────────┘                       └────────────────┬────────────────┘   │
│                 │                                                      │                    │
│        ┌────────┴────────┐                                             │                    │
│        ▼                 ▼                                             │                    │
│   Source Tab      Destination Tab                                      │                    │
│        │                 │                                             │                    │
│        │                 ├─ Multi-Select Batch Delete                  │                    │
│        │                 └─ Single File Delete                         │                    │
│        │                                                               │                    │
│        ├───────────────────────────────────────────────────────────────┘                    │
│        │                                                                                    │
│        ▼                                                                                    │
│   ┌───────────────────────────┐         ┌───────────────────────────┐                       │
│   │        MemeScanner        │         │   TagMemeDialogFragment   │                       │
│   │   (Background Thread)     │         │   (Tagging & Copying)     │                       │
│   └─────────────┬─────────────┘         └─────────────┬─────────────┘                       │
│                 │                                     │                                     │
│                 ▼                                     ▼                                     │
│   ┌─────────────────────────────────────────────────────────────────┐                       │
│   │                         MemeDatabase                            │                       │
│   │                     (Room Database v2)                          │                       │
│   └─────────────────────────────┬───────────────────────────────────┘                       │
│                                 │                                                           │
│                                 ▼                                                           │
│   ┌─────────────────────────────────────────────────────────────────┐                       │
│   │                     MemeDocumentsProvider                       │                       │
│   │            (android.content.action.DOCUMENTS_PROVIDER)          │                       │
│   └─────────────────────────────────────────────────────────────────┘                       │
│                                                                                             │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
                                               │
                                               ▼
                          ┌─────────────────────────────────────────┐
                          │           3rd-Party Messaging           │
                          │        (WhatsApp, Discord, etc.)        │
                          └─────────────────────────────────────────┘
```

---

## 📂 Component & File Analysis

### 1. Data Layer (`com.example.meme_engine.data`)

#### `Meme.java`
- **Role**: Room Database Entity representing a meme's metadata.
- **Table Name**: `memes`
- **Fields**:
  - `imageUri` (`@PrimaryKey`, `NonNull String`): SAF `content://` URI string representing the unique image identifier.
  - `tags` (`String`): Space-separated string containing normal text keywords and `#hashtags`.
  - `dateAdded` (`long`): Epoch timestamp when the meme was tagged or edited.
  - `firstSeen` (`long`): Epoch timestamp when the meme was first discovered during folder scan (added in DB v2).

#### `MemeDao.java`
- **Role**: Data Access Object for SQLite Room operations.
- **Key Methods**:
  - `insert(Meme meme)`: Inserts or replaces (`OnConflictStrategy.REPLACE`) a meme record.
  - `update(Meme meme)`: Updates an existing meme record.
  - `getByUri(String imageUri)`: Queries a single meme record by its URI string.
  - `searchByTag(String query)`: Executes a case-insensitive SQL `LIKE` query (`SELECT * FROM memes WHERE tags LIKE '%' || :query || '%' COLLATE NOCASE`).
  - `getAll()`: Retrieves all stored meme records.

#### `MemeDatabase.java`
- **Role**: Room Database Singleton instance (`version = 2`).
- **Migration**:
  - `MIGRATION_1_2`: Non-destructive schema migration executing `ALTER TABLE memes ADD COLUMN firstSeen INTEGER NOT NULL DEFAULT 0` to preserve existing user tags while adding arrival-time sorting support.

---

### 2. UI Layer (`com.example.meme_engine.ui`)

#### `MainGridActivity.java`
- **Role**: Primary application dashboard and launcher activity.
- **Key Responsibilities**:
  - Manages `TabLayout` for switching between **Source Tab** and **Destination Tab**.
  - Intercepts incoming picker intents (`GET_CONTENT`, `PICK`) and forwards them to `SearchActivity`.
  - Configures SAF folder selection launchers (`ACTION_OPEN_DOCUMENT_TREE`) for Source and Destination folders.
  - Handles runtime storage permission requests (`READ_MEDIA_IMAGES` for API 33+, `READ_EXTERNAL_STORAGE` for API 32 and below).
  - Executes background scanning via `MemeScanner` and populates `currentSourceScannedUris`.
  - Implements `applyFilters()` to merge search bar text queries and chip selections (`All`, `Tagged`, `Untagged`).
  - Triggers update checks via `UpdateManager` (both automatic 24-hour check and manual toolbar menu click).

#### `SearchActivity.java`
- **Role**: Dual-purpose search activity operating in standalone search mode or system picker mode.
- **Key Responsibilities**:
  - Detects picker intent actions (`GET_CONTENT`, `PICK`, `OPEN_DOCUMENT`, `PICK_IMAGES`).
  - Renders search result grid with live query filtering against `MemeDatabase`.
  - In picker mode (`isPickerMode = true`), selecting a meme returns its URI to the calling app with `FLAG_GRANT_READ_URI_PERMISSION`.
  - In normal mode, selecting a meme launches `TagMemeDialogFragment`.

#### `TagMemeDialogFragment.java`
- **Role**: Interactive dialog for tagging and copying memes.
- **Key Responsibilities**:
  - Displays thumbnail preview of selected image using Glide.
  - Splits existing tags into two fields: `etTags` (plain keywords) and `etHashtags` (`#hashtags`).
  - `combineTags()` helper ensures all hashtag tokens begin with `#` even if the user omitted the symbol.
  - **Save Button**: Persists updated tags to Room DB.
  - **Save & Copy to Destination Button**: Saves tags to Room DB **and** executes asynchronous file copying to the destination folder via `FileUtil.copyFile()`.

#### `DestinationAdapter.java`
- **Role**: Grid adapter for the Destination tab with selection management.
- **Key Responsibilities**:
  - Displays thumbnail grid of copied memes.
  - Supports single file deletion via an overlay `X` button (`ivDeleteSingle`).
  - Supports multi-select mode activated by item long-press.
  - Manages `selectedUris` set, toggles selection overlays and checkmarks (`vSelectionOverlay`, `ivCheckmark`), and fires `onSelectionChanged(count)` callbacks to update `MainGridActivity` selection bar.

#### `MemeAdapter.java`
- **Role**: Grid adapter for the Source tab.
- **Key Responsibilities**:
  - Loads thumbnail images asynchronously with Glide `.centerCrop()`.
  - Renders tag status badges: green checkmark (`ic_tag_done`) if tagged, empty badge (`ic_tag_none`) if untagged.
  - Handles item click listener to launch tagging dialog.

#### `MemePreviewActivity.java`
- **Role**: Fullscreen high-resolution preview and direct sharing.
- **Key Responsibilities**:
  - Displays full image using Glide.
  - Provides a **Share** button launching system share chooser (`ACTION_SEND`) with `FLAG_GRANT_READ_URI_PERMISSION`.

#### `PlusFlowDialogFragment.java`
- **Role**: Fullscreen dialog modal mirroring `MainGridActivity` dual-tab workflows for embedded UI contexts.

---

### 3. Utility Layer (`com.example.meme_engine.util`)

#### `FileUtil.java`
- **Role**: Helper for Storage Access Framework file operations.
- **Key Functions**:
  - `copyFile(Context, Uri sourceUri, Uri destFolderUri)`: Resolves source `DocumentFile` and destination folder `DocumentFile`. Streams data using an 8KB buffer (`byte[8192]`) and flushes output.
  - `deleteFile(Context, Uri fileUri)`: Deletes file via `DocumentFile.fromSingleUri().delete()` with `ContentResolver.delete()` fallback.

#### `MemeScanner.java`
- **Role**: Multithreaded SAF folder scanner.
- **Key Functions**:
  - `scanFolder(Context, Uri folderUri, ScanCallback)`: Spawns a background thread that traverses the SAF tree (`DocumentFile.fromTreeUri`), filtering files matching `image/*` MIME types, and returns list of image URIs via `ScanCallback`.

#### `StorageHelper.java`
- **Role**: Persistent store for SAF folder URIs and tree permissions.
- **Key Functions**:
  - Stores `root_folder_uri` and `destination_folder_uri` in `SharedPreferences` (`meme_engine_prefs`).
  - Executes `takePersistableUriPermission()` with `FLAG_GRANT_READ_URI_PERMISSION | FLAG_GRANT_WRITE_URI_PERMISSION` so SAF folder access survives device reboots.
  - Resolves human-readable folder names from URIs.

#### `UpdateManager.java`
- **Role**: Complete Over-The-Air (OTA) application update system.
- **Key Functions**:
  - `checkAutomaticUpdate(Activity)`: Automatic background update check throttled to once every 24 hours via `SharedPreferences` (`meme_engine_update_prefs`).
  - `checkForUpdates(Activity, boolean showUpToDateToast)`: Manual update check triggered from top bar menu.
  - Queries GitHub Releases API (`https://api.github.com/repos/vijayy999/meme_engine/releases/latest`).
  - Parses tag name, release release body notes, asset APK download URL, `latestVersionCode`, and `minVersionCode`.
  - **Version Code Evaluation**:
    - If `currentVersionCode < minVersionCode`: Displays **Mandatory Forced Update Dialog** (non-cancelable).
    - If `currentVersionCode < latestVersionCode`: Displays **Optional Update Dialog**.
    - If up to date: Displays toast message (in manual mode).
  - Handles GitHub release redirects (Amazon S3 bucket redirects) up to 5 hops.
  - Downloads APK to app cache (`meme_engine_update.apk`), verifies file size (>100KB), and launches package installation intent using `androidx.core.content.FileProvider`.

---

### 4. System Providers & Integration

#### `MemeDocumentsProvider.java` (`com.example.meme_engine.provider`)
- **Authority**: `com.example.meme_engine.documents`
- **Permission**: `android.permission.MANAGE_DOCUMENTS`
- **Role**: Custom `DocumentsProvider` exposing Meme Engine's tagged database to the Android system file browser.
- **Query Implementations**:
  - `queryRoots()`: Registers `"meme_root"` under display title **Meme Engine** with search support (`FLAG_SUPPORTS_SEARCH`).
  - `queryDocument()`: Resolves document metadata for root folder (`root_doc`) and individual meme URIs.
  - `queryChildDocuments()`: Queries `MemeDatabase.getDatabase().memeDao().getAll()` and returns all tagged memes as virtual documents.
  - `openDocumentThumbnail()`: Generates image thumbnails for system pickers via `ContentResolver.openAssetFileDescriptor()`.
  - `openDocument()`: Opens read/write file descriptors (`ParcelFileDescriptor`) for requested meme URIs.

---

## 🔄 Detailed End-to-End Execution Flows

### Flow A: App Startup & Permission Flow

```
[MainGridActivity.onCreate]
          │
          ▼
Check Intent Action
  ├─ If ACTION_GET_CONTENT or ACTION_PICK: Launch SearchActivity (Picker Mode)
  └─ Otherwise: Continue Main Dashboard Setup
          │
          ▼
Check SDK Version Permission:
  ├─ Android 13+ (API 33+): Manifest.permission.READ_MEDIA_IMAGES
  └─ Android 12- (API 24-32): Manifest.permission.READ_EXTERNAL_STORAGE
          │
          ├─► Granted ──► loadSourceTab() ──► Trigger UpdateManager.checkAutomaticUpdate()
          └─► Denied  ──► Request Permission Launcher ──► Toast on denial
```

---

### Flow B: SAF Folder Selection & Persistable Permissions

```
[User Clicks "Select Folder"]
          │
          ▼
Launch ACTION_OPEN_DOCUMENT_TREE Intent
          │
          ▼
[Android System Folder Picker] ──► User Selects Folder
          │
          ▼
[ActivityResultLauncher callback]
          │
          ▼
StorageHelper.setSourceFolderUri(context, uri) / setDestinationFolderUri(context, uri)
          │
          ▼
ContentResolver.takePersistableUriPermission(uri, READ | WRITE flags)
          │
          ▼
Save URI String in SharedPreferences ("meme_engine_prefs")
          │
          ▼
Reload Tab (loadSourceTab / loadDestinationTab)
```

---

### Flow C: Background Scanning & First-Seen Tracking

```
[loadSourceTab()]
          │
          ▼
Retrieve Saved Source Folder URI from StorageHelper
          │
          ▼
MemeScanner.scanFolder(context, sourceUri, callback)
          │
          ▼
[Background Thread] Traverse DocumentFile.fromTreeUri tree ──► Filter image/* MIME files
          │
          ▼
[ExecutorService Background Job]
  For each scanned image URI:
    Query MemeDatabase by URI string
    If URI does not exist in DB:
      Create new Meme record:
        imageUri = uri.toString()
        tags = null
        dateAdded = currentTimeMillis()
        firstSeen = currentTimeMillis()
      Insert into Room DB
          │
          ▼
[Main Thread UI Post] ──► Update currentSourceScannedUris ──► Invoke applyFilters()
```

---

### Flow D: Tagging, Hashtag Processing & Destination Copy

```
[User Taps Meme in Source Grid]
          │
          ▼
TagMemeDialogFragment.newInstance(uri, existingTags)
          │
          ▼
Parse existing tags into Normal Tags (etTags) & Hashtags (etHashtags)
          │
          ▼
User Modifies Text Fields
          │
          ├──────────────────────────────────────────────────────────┐
          ▼                                                          ▼
   [User Clicks "Save"]                     [User Clicks "Save & Copy to Destination"]
          │                                                          │
          ▼                                                          ▼
combineTags(etTags, etHashtags)                            combineTags(etTags, etHashtags)
  (Auto-prefix tokens with '#' if missing)                    (Auto-prefix tokens with '#' if missing)
          │                                                          │
          ▼                                                          ▼
Save to Room DB (Preserve original firstSeen)              Save to Room DB (Preserve original firstSeen)
          │                                                          │
          ▼                                                          ▼
Re-apply Source Grid Filters                               Retrieve Destination Folder URI
                                                                     │
                                                                     ▼
                                                           [ExecutorService Background Job]
                                                           FileUtil.copyFile(sourceUri, destUri)
                                                           Stream bytes (8KB buffer)
                                                                     │
                                                                     ▼
                                                           Show Toast ("Copied to destination")
```

---

### Flow E: Unified Multi-Filter & Search Algorithm

```
                  ┌─────────────────────────────────────────┐
                  │              applyFilters()             │
                  └────────────────────┬────────────────────┘
                                       │
                      Retrieve currentSourceScannedUris
                                       │
               ┌───────────────────────┴───────────────────────┐
               ▼                                               ▼
   Get Chip Filter State                           Get Search Query Text
  ("All", "Tagged", "Untagged")                   (etSourceSearch.getText())
               │                                               │
               └───────────────────────┬───────────────────────┘
                                       │
                         [ExecutorService Background Job]
                          Iterate over all scanned URIs
                                       │
  ┌────────────────────────────────────┴────────────────────────────────────┐
  │                                                                         │
  ▼                                                                         ▼
Evaluate Chip Filter Rule                                 Evaluate Search Query Rule
  ├─ All: Include all                                      ├─ Query starts with '#':
  ├─ Tagged: Include if tags != null                          Match against hashtag tokens
  └─ Untagged: Include if tags == null                     └─ Plain text query:
                                                              Match against non-hashtag tokens
  │                                                                         │
  └────────────────────────────────────┬────────────────────────────────────┘
                                       │
                         Both Rules Passed? (passesChip && passesSearch)
                                       │
                                       ▼
                       Add to displayUris & map firstSeen
                                       │
                                       ▼
                  Sort displayUris by firstSeen DESCENDING
                        (Newest scanned memes first)
                                       │
                                       ▼
                      [Main Thread UI Post]
                       MemeAdapter.setImages(displayUris)
                       MemeAdapter.setTags(tagsMap)
                       Toggle empty view visibility
```

---

### Flow F: Destination Tab & Batch Multi-Deletion

```
[Switch to Destination Tab] ──► Scan Destination Folder ──► Populate DestinationAdapter
                                                                      │
  ┌───────────────────────────────────────────────────────────────────┴───────────────────────────────────┐
  │                                                                                                       │
  ▼                                                                                                       ▼
[Single Delete (Click 'X' Icon)]                                                        [Multi-Select Mode (Long Press)]
  │                                                                                                       │
  ▼                                                                                                       ▼
FileUtil.deleteFile(uri)                                                                Toggle Selection Overlay & Checkmark
DocumentFile.delete() / ContentResolver.delete()                                        Update Selection Counter Bar
  │                                                                                                       │
  ▼                                                                                                       ▼
Reload Destination Grid                                                                 [User Clicks "Delete Selected"]
                                                                                                          │
                                                                                                          ▼
                                                                                        [ExecutorService Background Job]
                                                                                        Iterate selectedUris set ──► FileUtil.deleteFile()
                                                                                                          │
                                                                                                          ▼
                                                                                        Show Toast ("N file(s) deleted")
                                                                                        Clear selection & Reload Grid
```

---

### Flow G: System Picker Mode (`GET_CONTENT` / `PICK`)

```
3rd-Party App (WhatsApp, Discord, Twitter)
  │
  ▼
Fires Intent ACTION_GET_CONTENT / ACTION_PICK with mimeType="image/*"
  │
  ▼
Android System Chooser ──► User selects "Meme Engine"
  │
  ▼
SearchActivity launched in Picker Mode (isPickerMode = true)
  │
  ▼
User searches memes by keywords or hashtags
  │
  ▼
User taps desired meme
  │
  ▼
Set Result Intent:
  resultIntent.setData(selectedMemeUri)
  resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  setResult(RESULT_OK, resultIntent)
  finish()
  │
  ▼
Image returned directly to 3rd-Party App!
```

---

### Flow H: System Documents Provider Integration

```
Android OS "Files" App / System Document Chooser
  │
  ▼
Queries registered DOCUMENTS_PROVIDER authority: com.example.meme_engine.documents
  │
  ▼
MemeDocumentsProvider.queryRoots()
  │
  ▼
Displays Root Document: "Meme Engine"
  │
  ▼
User opens "Meme Engine" folder
  │
  ▼
MemeDocumentsProvider.queryChildDocuments("root_doc")
  │
  ▼
Queries MemeDatabase.getDatabase().memeDao().getAll()
  │
  ▼
Returns all tagged memes as virtual document files with thumbnail support (openDocumentThumbnail)
```

---

### Flow I: OTA Software Update Engine

```
[App Launch / Manual Menu Click]
          │
          ▼
UpdateManager.checkForUpdates() / checkAutomaticUpdate()
  (Auto mode enforced by 24h interval check via SharedPreferences)
          │
          ▼
[ExecutorService Background Job]
GET https://api.github.com/repos/vijayy999/meme_engine/releases/latest
          │
          ▼
Parse Response JSON (tag_name, body notes, asset apkUrl, latestVersionCode, minVersionCode)
          │
          ▼
Compare currentVersionCode against release version codes:
  │
  ├─► currentVersionCode < minVersionCode
  │     └─► Show FORCED UPDATE Dialog (Non-cancelable, closes app on skip)
  │
  ├─► currentVersionCode < latestVersionCode
  │     └─► Show OPTIONAL UPDATE Dialog ("Update Now" / "Later")
  │
  └─► currentVersionCode >= latestVersionCode
        └─► App is Up to Date (Toast shown in manual mode)
          │
          ▼ (If User Confirms Update)
Check Install Permission: Build.VERSION.SDK_INT >= 26 && !canRequestPackageInstalls()
  ├─ Denied  ──► Prompt user to enable "Install Unknown Apps" in System Settings
  └─ Granted ──► Proceed to download
          │
          ▼
[ExecutorService Background Job]
downloadAndInstallApk(apkUrl)
  ├─ Manual HttpURLConnection redirect handling (Amazon S3 asset bucket redirects, up to 5 hops)
  ├─ Download file to context.getCacheDir()/meme_engine_update.apk
  └─ Verify download file size (> 100 KB)
          │
          ▼
installApk(context, apkFile)
  ├─ Generate content URI using FileProvider (${applicationId}.fileprovider)
  └─ Launch Intent.ACTION_VIEW with MIME "application/vnd.android.package-archive"
```

---

## 💾 Database Architecture & Migrations

```
  ┌─────────────────────────────────────────────────────────────┐
  │                        TABLE: memes                         │
  ├───────────────────┬──────────────────────┬──────────────────┤
  │ COLUMN            │ TYPE                 │ CONSTRAINTS      │
  ├───────────────────┼──────────────────────┼──────────────────┤
  │ imageUri          │ TEXT                 │ PRIMARY KEY      │
  │ tags              │ TEXT                 │ NULLABLE         │
  │ dateAdded         │ INTEGER (long)       │ NOT NULL         │
  │ firstSeen         │ INTEGER (long)       │ DEFAULT 0 (v2)   │
  └───────────────────┴──────────────────────┴──────────────────┘
```

### Room Schema Version History
- **Version 1**: Initial release schema with `imageUri`, `tags`, and `dateAdded`.
- **Version 2**: Introduced `firstSeen` timestamp column.
  - **Migration Script (`MIGRATION_1_2`)**:
    ```sql
    ALTER TABLE memes ADD COLUMN firstSeen INTEGER NOT NULL DEFAULT 0;
    ```

---

## 🛠️ Tech Stack & Build System

- **Language**: Java 11 (Source & Target compatibility)
- **Min SDK**: 24 (Android 7.0 Nougat)
- **Target / Compile SDK**: 37 (Android 15)
- **Gradle Version**: 9.4.0 (Android Gradle Plugin 9.4.0, Kotlin DSL build scripts)
- **Database**: [Room Persistence Library](https://developer.android.com/training/data-storage/room) `2.6.1`
- **Image Loading**: [Glide](https://github.com/bumptech/glide) `4.16.0`
- **UI Components**: Material Components `1.14.0`, ConstraintLayout `2.2.2`, AppCompat `1.8.0`
- **Storage**: Android Storage Access Framework (`DocumentFile` `1.1.0`, `takePersistableUriPermission`)
- **File Sharing & Updates**: AndroidX `FileProvider`

---

## 🚀 How to Build & Install

### Prerequisites
1. **Android Studio**: Ladybug (2024.2.1+) or newer.
2. **JDK**: Java 11 or higher configured in Android Studio.
3. **Android Device / Emulator**: Running Android 7.0 (API level 24) or higher.

### Step-by-Step Instructions

1. **Clone & Open Project**:
   ```bash
   git clone https://github.com/vijayy999/meme_engine.git
   ```
   Open the root directory in Android Studio.

2. **Gradle Synchronization**:
   Allow Android Studio to sync dependencies via Gradle automatically.

3. **Assemble Debug Build**:
   Execute the assemble task via terminal or IDE build toolbar:
   ```bash
   ./gradlew assembleDebug
   ```

4. **Run Application**:
   Connect an Android device via USB debugging or start an emulator, then run `app` (**Shift + F10**).

---

## 📄 License

This project is licensed under the **MIT License**.
