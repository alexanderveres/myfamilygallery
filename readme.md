# MyFamilyGallery

## Intorduction
MyFamilyGallery is a application for organizing and archiving family photos and videos.

## Features
Importing photos and videos from folders.
Organizing assets by date-time taken, location...
Detection and prevention of storing duplicates.
REST api for importing, browsing and search

## Usecases

User performs backup od digital assets (photos and videos) from phone or camera into a folder.
User opens a app, selects a local folder and performs import.
Application will import assests (which already does not exist), read metadata from asset (date-time taken, location, device id...), store files into a internal local folder and metadata (local database).

User when opening a app can browse assets by different criteria (year, month, location, orientation...).
User can also browse application local folder and bake additional backup with assets metadata.

## Techical stack
Core application: Java, spring boot, maven
UI: Vue.js
Storage: NoSQL database or internal JSON files.

## Running the application

Prerequisites:
- Java 21+
- Maven 3.9+
- Node.js 20+ and npm

Build the UI and backend:

```powershell
npm install
npm run build
mvn package
```

Start the application:

```powershell
mvn spring-boot:run
```

Open `http://localhost:8080`.

Use **Upload files** to import selected photos or videos, or enter a server-side folder path in **Import from folder**. Imported files are copied into `data/gallery/assets/<year>`, and metadata and duplicate hashes are stored in `data/gallery/assets.json`. Set `GALLERY_STORAGE` or `gallery.storage` to use another location.

## REST API

- `GET /api/assets` - browse and filter assets; accepts `page` (zero-based, default `0`) and `size` (default `50`, maximum `100`) and returns a page containing `content`, `page`, `size`, `totalElements`, and `totalPages`
- `GET /api/assets/{id}` - get asset metadata
- `GET /api/assets/{id}/content` - retrieve an asset
- `POST /api/import/folder` - import a server folder with body `{"folder":"C:\\path\\to\\photos"}`
- `POST /api/import/files` - multipart upload with `files`
- `GET /api/storage` - get the configured storage path
