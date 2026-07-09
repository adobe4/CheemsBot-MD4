# Vin Play M3U

A fully on-device IPTV playlist manager and player for Android. No backend, no cloud, no
accounts — parsing, storage, playback, link testing and export all run locally on the phone.

## Tech stack

- **Kotlin + Jetpack Compose** (Material 3, dark theme by default, Material You dynamic color)
- **MVVM + Hilt** for dependency injection
- **Room** for local persistence
- **Coroutines + Flow**
- **OkHttp** for streamed remote M3U download and link testing
- **Media3 / ExoPlayer** for playback (HLS, DASH, progressive, MPEG-TS)
- **DataStore** for preferences
- Min SDK 24, target/compile SDK 35

## Feature map

| Area | Where |
| --- | --- |
| Playlists CRUD | `ui/playlists/*`, `data/repository/PlaylistRepository.kt` |
| Streaming import (file / URL / paste, merge, batches of 500) | `data/repository/ImportManager.kt`, `ui/importer/*` |
| Streaming M3U parser (`#EXTINF` attrs, kind detection) | `data/parser/M3uParser.kt` |
| Channel editor (search, filter, paginate, edit, reorder, move) | `ui/channels/*`, `data/local/dao/ChannelDao.kt` |
| Soft-delete + Trash + restore + undo | `ChannelDao` (`deletedAt`), `ui/trash/*` |
| Bulk link testing (parallel HEAD → Range GET, limit 10, 8s) | `data/net/LinkTester.kt`, `ChannelRepository.testFiltered` |
| Player (ExoPlayer, PiP, orientation lock) | `player/PlayerManager.kt`, `ui/player/*` |
| Export to M3U via SAF / share | `data/repository/ExportManager.kt`, `data/parser/M3uWriter.kt` |

## Memory strategy for very large playlists (100k+ channels)

- **Import** never materializes the whole file: `M3uParser` reads the source line-by-line and
  emits one `ParsedChannel` at a time; `ImportManager` buffers them into 500-row batches, each
  inserted in a single Room transaction, so peak heap ≈ one batch.
- **Listing** is LIMIT/OFFSET paged (`ChannelDao.pageFiltered`) — the UI holds ~200 rows and
  grows on scroll, never the full table.
- **Export** pages the DB (1,000 rows at a time) straight into the output stream.
- **Link testing** streams ids and gates concurrency with a semaphore (10) so a "test all" over
  a huge playlist stays bounded.

## Building

This project uses the Gradle wrapper. If `gradle/wrapper/gradle-wrapper.jar` is not present in
your checkout, generate it once with a locally installed Gradle:

```bash
cd VinPlayM3U
gradle wrapper --gradle-version 8.11.1
```

Then build/install:

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Open the `VinPlayM3U` directory in Android Studio (Ladybug or newer) to run from the IDE.
