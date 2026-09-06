# VinPlay Manager (Windows desktop)

A desktop companion to the Vin Play M3U Android app, built for **very large libraries** — millions
of channels across many playlists, where the phone becomes unusable.

## Why it's fast

The phone app searches with `name LIKE '%text%'`, which SQLite cannot serve from an index: every
search reads **every row** in the table. This app keeps an **FTS5 full-text index** (`channels_fts`,
kept in sync by triggers), so searching across all playlists is an index lookup instead of a full
scan. Typing is also debounced, and the row count is fetched *after* the rows are drawn so results
appear immediately.

## What it does

- **One library, all playlists** — browse and search everything at once, or filter to one playlist.
- **Search across every playlist** with prefix matching (`sky sp` finds `Sky Sports`).
- **Multi-select** → move to another playlist, move to a **new** playlist, or send to trash.
- **Link testing** with 16 parallel probes; results show as coloured dots
  (green OK, yellow unstable, red dead).
- **Remove duplicates** (same name + URL) across a playlist or the whole library.
- **Import** from a local `.m3u` file, a URL, or an **Xtream Codes login**
  (server + username + password, verified before importing).
- **Export** the current filtered view back out as `.m3u`.
- **Play** opens the stream in **VLC**, passing the channel's User-Agent/Referer so header-gated
  streams still work. Set the VLC path under Settings if it isn't auto-detected.

## Install

Grab the artifact from the **"Windows build (VinPlay Manager)"** GitHub Actions run:

- `VinPlayManager-windows-portable` — unzip anywhere and run `VinPlay Manager.exe`. No install.
- `VinPlayManager-windows-installer` — an `.msi` (only present when the build machine had the
  WiX toolset).

Your library lives in `%APPDATA%\VinPlayManager\library.db`.

## Build from source

```bash
cd VinPlayDesktop
./gradlew createDistributable   # portable app image
./gradlew packageMsi            # Windows installer (needs WiX)
./gradlew run                   # run it directly
```

Requires JDK 17+.
