# 🎭 Meme Engine

**Meme Engine** is an intelligent, privacy-focused Android application designed for scanning, tagging, searching, organizing, and sharing memes locally on Android devices. It integrates seamlessly with the Android OS as a custom **System Picker Provider** and **Documents Provider**, allowing users to quickly pick and send tagged memes from inside third-party apps like WhatsApp, Telegram, Discord, and Messages.

---

## 🌟 Key Features

- 📁 **SAF Folder Management**: Pick any folder on device or SD card via Android's Storage Access Framework (SAF) with persistent read/write permissions.
- 🏷️ **Smart Tagging System**: Tag memes with free-text keywords and `#hashtags`. Visual badges indicate tagged vs. untagged images.
- 🔍 **Instant Search Engine**: Search memes in real-time by plain keywords or exact `#hashtags`.
- 📁 **Destination Folder & Copy**: Easily copy tagged memes into a dedicated destination folder for organized storage.
- 🗑️ **Multi-Select & Batch Deletion**: Select multiple memes in the destination folder for bulk deletion or single-item cleanup.
- 📱 **System Image Picker (`GET_CONTENT` / `PICK`)**: Pick tagged memes directly when attaching media in third-party apps.
- 📂 **Documents Provider Integration**: Exposes tagged memes as a system-recognized document root in the Android Files / Storage picker.
- 📤 **Direct Share & Preview**: High-res meme preview with built-in Android share chooser.

---

## 🚀 End-to-End User Journey (Start to Finish Flow)

```
[Launch App] ──► [Grant Permissions] ──► [Select Source Folder] ──► [Auto-Scan Folder]
                                                                          │
  ┌───────────────────────────────────────────────────────────────────────┴─────────────────────────────────────────┐
  │                                                                                                                 │
  ▼                                                                                                                 ▼
[Source Tab]                                                                                               [Destination Tab]
  ├─ View Memes & Badges (✓ Tagged / ◯ Untagged)                                                            ├─ View Copied Memes
  ├─ Real-time Tag & Hashtag Search                                                                        ├─ Single File Delete (X)
  └─ Tap Meme ──► [Tag Meme Dialog]                                                                         └─ Long-press ──► [Multi-Select]
                      ├─ Input Normal Tags & #Hashtags                                                                       └─ [Batch Delete]
                      ├─ Tap [Save] ──► Persist in Room DB
                      └─ Tap [Save & Copy to Destination] ──► Copy File to Output Folder
                                                                    │
                                                                    ▼
                                                 [3rd-Party App (WhatsApp/Discord)]
                                                    └─ Pick Image ──► [SearchActivity (Picker Mode)]
                                                                          ├─ Search by Tag/Hashtag
                                                                          └─ Tap Meme ──► Returned to 3rd-Party App
```

---

### Step 1: App Launch & Initial Permission Setup
1. On first launch, `MainGridActivity` requests runtime storage permissions:
   - `READ_MEDIA_IMAGES` on **Android 13+** (API 33+).
   - `READ_EXTERNAL_STORAGE` on **Android 12 and below**.
2. Once granted, the app initializes its two main tabs: **Source** and **Destination**.

---

### Step 2: Selecting Source & Destination Folders
1. **Source Folder**: Click **"Select Folder"** on the Source Tab to open the Android System Document Tree Picker (`ACTION_OPEN_DOCUMENT_TREE`). Select the folder where your memes are stored.
2. **Destination Folder**: Click **"Select Folder"** on the Destination Tab to choose an output folder (e.g., `Pictures/OrganizedMemes`).
3. **Persistable Permissions**: `StorageHelper` saves URI tree permissions with `takePersistableUriPermission()` into `SharedPreferences`, so permissions persist even after phone reboots.

---

### Step 3: Automated Folder Scanning & Indexing
1. `MemeScanner` runs a background thread scanning the selected SAF directory for files matching `image/*` MIME types.
2. Scanned image URIs are sent to `MemeAdapter` to render in a responsive grid (`rvSourceGrid`).
3. Each item checks against the local **Room Database** (`MemeDatabase`):
   - **Tagged Memes**: Display a green checkmark badge (`ic_tag_done`).
   - **Untagged Memes**: Display an empty tag badge (`ic_tag_none`).

---

### Step 4: Tagging & Organising Memes (`TagMemeDialogFragment`)
1. Click any meme in the Source grid to launch the **Tag Dialog**.
2. **Tag Input Fields**:
   - **Tags Field (`etTags`)**: Input plain descriptive keywords (e.g., `cat funny confused`).
   - **Hashtags Field (`etHashtags`)**: Input categorized hashtags (e.g., `#wholesome #work`). If the user forgets the `#` symbol, the app auto-formats tokens with `#`.
3. **Action Buttons**:
   - **Save**: Saves the URI, tags string, and timestamp into Room DB.
   - **Save & Copy to Destination**: Saves tags to Room DB **and** copies the image file into the configured Destination Folder using `FileUtil.copyFile()`.

---

### Step 5: Real-Time Tag & Hashtag Search
1. Type query terms into the top search bar (`etSourceSearch`).
2. **Smart Search Matching Logic**:
   - Queries starting with `#` filter specifically against `#hashtags`.
   - Queries without `#` match plain tag keywords.
   - Filtering works instantly on keypress without blocking the UI thread.

---

### Step 6: Managing the Destination Folder
1. Switch to the **Destination** tab to view all copied/organized memes.
2. **Single Delete**: Click the `X` icon on any item overlay to delete the file from storage via `DocumentFile.delete()`.
3. **Multi-Select & Batch Deletion**:
   - Long-press an item to enter multi-select mode.
   - Tap multiple items to select/unselect them.
   - Click **"Delete Selected"** in the bottom selection bar to perform batch deletion.

---

### Step 7: System Integration & Third-Party App Picking (`SearchActivity`)
When using messaging or social media apps (e.g., WhatsApp, Telegram, Discord, Twitter):
1. In the external app, tap **Attach Image** / **Pick File**.
2. Select **Meme Engine** from the system chooser (`ACTION_GET_CONTENT` or `ACTION_PICK`).
3. `MainGridActivity` forwards the intent to `SearchActivity` operating in **Picker Mode**.
4. Search your meme library by keywords or hashtags.
5. Tapping a meme grants read permission (`FLAG_GRANT_READ_URI_PERMISSION`) and passes the image URI straight back to the external app to send!

---

### Step 8: System Documents Provider (`MemeDocumentsProvider`)
- Meme Engine exposes authority `com.example.meme_engine.documents`.
- Registered under `android.content.action.DOCUMENTS_PROVIDER`.
- Appears directly in Android's built-in **Files / System Storage Picker** under document root `"Meme Engine"`.
- Allows browsing and selecting tagged memes natively across system file pickers with thumbnail support.

---

## 🛠️ Technology Stack & Architecture

- **Language**: Java 11
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 37 (Android 15)
- **Architecture**: Native Android (Activities, DialogFragments, Custom Provider, Storage Access Framework)
- **Local Persistence**: [Room Database](https://developer.android.com/training/data-storage/room)
- **Image Processing**: [Glide](https://github.com/bumptech/glide)
- **Storage System**: Android Storage Access Framework (`DocumentFile`, `takePersistableUriPermission`)
- **UI Toolkit**: Material Design Components (`TabLayout`, `RecyclerView`, `ConstraintLayout`)

---

## 📂 Project Component Directory

```
com.example.meme_engine/
│
├── MainGridActivity.java          # Primary dashboard (Source & Destination tabs, SAF launcher)
│
├── data/                          # Database & Entity Layer
│   ├── Meme.java                  # Room Entity (imageUri, tags, dateAdded)
│   ├── MemeDao.java               # Room Data Access Object (Insert, Update, Search)
│   └── MemeDatabase.java          # Room Database Singleton
│
├── ui/                            # User Interface Layer
│   ├── MemeAdapter.java           # Grid Adapter for Source tab memes (with status badges)
│   ├── DestinationAdapter.java    # Grid Adapter for Destination tab memes (multi-select & delete)
│   ├── SearchActivity.java        # Search view & Picker Mode interface for 3rd-party apps
│   ├── MemePreviewActivity.java   # Fullscreen meme preview & direct share
│   ├── TagMemeDialogFragment.java  # Dialog for tagging & copying memes to destination
│   └── PlusFlowDialogFragment.java # Fullscreen modal workflow interface
│
├── util/                          # Utility & Helper Layer
│   ├── FileUtil.java              # File copy & delete operations via DocumentFile
│   ├── MemeScanner.java           # Background SAF folder image scanner
│   └── StorageHelper.java         # Preference store for SAF URIs & permissions
│
└── provider/                      # Android System Integration
    └── MemeDocumentsProvider.java  # Custom DocumentsProvider for Android Files app
```

---

## 🛠️ How to Build and Run

1. **Open in Android Studio**: Open Android Studio (2024.1+ recommended).
2. **Gradle Sync**: Let Android Studio sync dependencies automatically.
3. **Run App**: Select an Android device or emulator running **Android 7.0 (API 24)** or higher and hit **Run (Shift + F10)**.

---

## 📄 License

This project is open-source under the MIT License.
