# gb3 for Android

A native Android client for the [gb3](https://github.com/zorel/gb3) `gb2c` server. It talks directly
to the same HTTP/SSE API as `gb3tui` and `gc2`, and can also act as its own
tribune client: in **Direct boards** mode it downloads the TSV backend and posts
to each board URL itself, without a gb2c server in the middle. There is no
WebView for the tribune itself; a small WebView is only used to complete the
LinuxFR OAuth flow.

## Features

The client covers the feature set of `gb3tui` and `gc2`, adapted to touch:

- live posts from `/gb2c/poll` over server-sent events, with automatic
  reconnection and foreground-only streaming;
- **direct boards**: gb2c's predefined boards plus user-defined boards, polled
  every 30 seconds straight from their TSV backend and posted to directly.
  Malformed TSV rows, invalid ids, oversized lines and control characters are
  rejected or sanitized before they reach the UI;
- posting to any tribune through `/gb2c/post` or directly to the selected
  board, including LinuxFR (`dlfp`) OAuth login, token refresh and restoration
  of the message while authorizing;
- tribune switching with chips, horizontal swipes and unread/reply/bigorno
  badges;
- clock references (norloges): tap one to jump to the referenced post, tap a
  post clock to quote it in the composer, and see reverse references
  highlighted while a post is selected;
- conversation grouping with a stable, high-contrast color rail, plus the
  same conversation filter as `gc2`:
  - hide a login/user-agent and every direct or indirect reply,
  - either hide those posts or show them grayed with their message masked;
- server-side blocked identities from `/gb2c/config` merged with local
  settings;
- bigorno, "this is a reply to me" and "mine" indicators in a reserved leading
  slot, so clocks and author names stay aligned;
- message formatting: `<b>`, `<i>`, `<u>`, `<s>`, `<tt>`, `<code>`, tappable
  spoilers, links, `[:totoz]`, `nick<` bigornos and normal bouchot whitespace;
- totoz search with image grid and preview, either through gb2c or straight from a
  configurable totoz server (`https://nsfw.totoz.eu` by default), emoji search,
  attachment upload (4 MiB server limit) and the ALT formatting shortcuts;
- plop-list message replacement;
- English and French UI, following the Android system language;
- light/dark Material 3 theme following the system, with the gc2 palette;
- the alarm clock that counts seconds during the last minute of each hour.

## Requirements

- JDK 17 (a full JDK with `javac`, not only a Java runtime)
- Android SDK 35 (`compileSdk = 35`, Build-Tools 35.0.0)
- Android Studio Ladybug or newer, or the Gradle wrapper

The project includes a Gradle wrapper. Build from this directory:

```bash
./scripts/build.sh :app:assembleDebug
```

`build.sh` finds a JDK that actually contains `javac`, sets `JAVA_HOME` and
`ANDROID_HOME`, and forwards the arguments to Gradle. You can also call Gradle
directly after exporting a valid JDK:

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
```

If Gradle reports:

```text
Toolchain installation '/usr/lib/jvm/...' does not provide the required
capabilities: [JAVA_COMPILER]
```

the default `java` is a JRE, not a JDK. Install `openjdk-17-jdk` (or point
`JAVA_HOME` at a full JDK). The checked-in
`gradle/gradle-daemon-jvm.properties` selects JDK 17 for the Gradle daemon.

If the SDK is not installed at all, bootstrap it with:

```bash
./scripts/install-android-sdk.sh
```

The script installs the command-line tools, accepts the Android SDK licences,
installs `platform-tools`, `platforms;android-35` and `build-tools;35.0.0`, and
writes an ignored `local.properties` with `sdk.dir`. It supports Linux and macOS.

`buildToolsVersion` only selects a Build-Tools revision; it does not download
anything. AGP's `android.builder.sdkDownload=true` can install missing SDK
components when an SDK root already exists and licences are accepted, but it
cannot bootstrap a completely missing SDK. Use the script for that.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

For an installable release APK:

```bash
./gradlew :app:assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`.

Android refuses to install unsigned APKs. For local testing, the release build
falls back to the locally generated Android debug key, so it is installable but
must not be published. Provide a real release key through the environment for
distribution:

```bash
export GB3_RELEASE_KEYSTORE=/path/to/release.jks
export GB3_RELEASE_KEYSTORE_PASSWORD=...
export GB3_RELEASE_KEY_ALIAS=...
export GB3_RELEASE_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

Run the unit tests with:

```bash
./scripts/build.sh :app:testDebugUnitTest
```

The test suite includes an SSE burst regression test (gb2c replays its whole
history in one burst when a client connects; the client applies backpressure
instead of silently dropping events) and parser/conversation tests.

## Continuous integration

GitHub Actions runs the unit tests and builds both the debug and release APKs
for pushes and pull requests to `main` (`.github/workflows/android-ci.yml`).
Successful runs upload the APKs as the `gb3-android-debug` and
`gb3-android-release-*` workflow artifacts.

To sign the release APK in CI with a real release key, add these repository
secrets:

- `GB3_RELEASE_KEYSTORE_BASE64`: the keystore file as a single-line base64
  string (`base64 -w0 release.jks` on Linux, `base64 -i release.jks` on macOS);
- `GB3_RELEASE_KEYSTORE_PASSWORD`;
- `GB3_RELEASE_KEY_ALIAS`;
- `GB3_RELEASE_KEY_PASSWORD`.

Without them, the release APK uses the debug-key fallback described above and
its artifact name ends in `debug-key`; it must not be published.

Publishing a GitHub Release triggers `.github/workflows/release.yml`, which
builds a signed release APK and attaches it to the release as
`gb3android-<tag>.apk`. That workflow requires the four signing secrets and
fails instead of attaching a debug-signed APK when they are missing.

## First run

The app asks how to reach tribunes:

- **gb2c server** - enter the base URL of a `gb2c` server, for example
  `https://gb3.example.com` or `http://192.168.1.10:16666`. Posts stream over
  `/gb2c/poll` and are sent through `/gb2c/post`, as before.
- **Direct boards** - pick one or more boards and leave the server URL empty.
  The app downloads each board's TSV backend itself and posts to its post URL.
  A gb2c URL is still optional in this mode for emoji search, attachment uploads
  and LinuxFR OAuth. Totoz search is configured separately with its own direct
  server URL.

The nickname is sent as the post `info`; if it is left empty, a gc2-style random
nickname is generated. On an Android emulator, `localhost` is the emulator
itself: use `http://10.0.2.2:16666` to reach a gb2c process running on the host
machine.

## Custom boards

Direct mode ships the same default boards as gb2c:

| Board | Backend | Post field |
| --- | --- | --- |
| `euromussels` | `https://faab.euromussels.eu/data/backend.tsv` | `message` |
| `sveetch` | `http://sveetch.net/tribune/remote/tsv/` | `content` |
| `moules` | `https://moules.org/board/backend/tsv` | `message` |
| `dlfp` | `https://linuxfr.org/board/index.tsv` | `message` (OAuth2) |
| `ototu` | `https://www.ototu.me/goboard/backend/tsv` | `message` |
| `devnewton` | `https://gb3.devnewton.fr/gb0/tsv` | `message` |
| `gabuzomeu` | `https://gb3.plop.cc/gb0/tsv` | `message` |
| `reveildutroll` | `https://miaoli.im/%2B6VtcdSCI0Y/posts.tsv` | `message` |

Open **Settings -> Boards -> Add custom board** to add any other bouchot-style
board. A custom board needs:

- a unique name without spaces or commas (it becomes the tribune name);
- the backend URL returning five-column TSV (`id`, `time`, `info`, `login`,
  `message`);
- the post URL accepting an `application/x-www-form-urlencoded` POST;
- the post field name (defaults to `message`);
- an optional info field. When it is empty, the nickname is sent in the
  `User-Agent` header, exactly like gb2c;
- an optional LinuxFR OAuth2 flag for boards that require a Bearer token.

Custom boards are stored as JSON in `SharedPreferences` and can be removed from
the same section. Built-in boards are read-only.

### Totoz server

The **Services** section in Settings (and the first-run setup screen) contains
the totoz server URL. It defaults to `https://nsfw.totoz.eu`, the same search
server gb2c uses internally.

- Search requests go directly to `<server>/search.xml?terms=...`.
- Preview images are loaded from `<server>/img/<name>`.
- Totoz search therefore works in direct-board mode without a gb2c server.
- Both a base URL (`https://nsfw.totoz.eu`) and a full
  `https://nsfw.totoz.eu/search.xml` endpoint are accepted.
- Leaving the field empty falls back to gb2c's `/gb2c/totoz/search` endpoint
  when a gb2c URL is configured.

The XML response is parsed defensively: external entities and DOCTYPE
declarations are disabled, responses are bounded to 2 MiB, and blank names are
ignored. Image URLs are always rebuilt from the configured server, like gb2c
does, instead of trusting an image URL supplied by the server.

Direct mode is deliberately defensive: rows with fewer than five fields or with
an invalid/non-positive id are skipped, duplicate ids keep the latest row, extra
columns are dropped, control characters are removed, messages are capped at
64 KiB and backend responses are capped at 8 MiB.

## Touch gestures

- tap a tribune chip, or swipe the post list left/right, to change tribune;
- tap a post to select it and highlight every clock reference pointing to it;
- tap its clock to quote it, or tap its author to add `author<` to the message;
- tap a clock reference inside a message to jump to the referenced post;
- long-press a post to reply, call the author, copy the message or show its
  metadata;
- tap a spoiler to reveal or hide it, and tap a totoz to preview it;
- use the `+` button in the composer, or the top-right menu, for attachments,
  totoz, emoji, formatting shortcuts and settings.

Settings are stored locally in `SharedPreferences`:

- connection mode, server URL, totoz server URL and nickname;
- selected tribunes and custom board definitions;
- local blocked identities and hide/gray mode;
- plop list;
- last posted message and per-tribune last-read marker;
- LinuxFR access/refresh tokens.

## API endpoints used

In gb2c mode:

| Endpoint | Usage |
| --- | --- |
| `/gb2c/list` | tribune list |
| `/gb2c/config` | server-side blocked identities |
| `/gb2c/poll?filter=...` | SSE live posts |
| `/gb2c/post` | post a message |
| `/gb2c/totoz/search` | totoz search |
| `/gb2c/emoji/search` | emoji search |
| `/gb2c/attachment/` | multipart attachment upload |
| `/gb2c/linuxfr/authorize` | OAuth authorization page |
| `/gb2c/linuxfr/refresh` | refresh a LinuxFR token |

In direct board mode:

| Endpoint | Usage |
| --- | --- |
| `<board backendUrl>` | five-column TSV history |
| `<board postUrl>` | form-encoded post |
| `<totoz server>/search.xml?terms=...` | direct totoz search |
| `<totoz server>/img/<name>` | totoz preview image |

Each configured board's `backendUrl` and `postUrl` are contacted directly.
Totoz search and previews use the configured totoz server. The optional gb2c
server is only used for emoji search, attachments and LinuxFR OAuth.

The LinuxFR redirect configured on the gb2c server must be reachable from the
device. gb2c currently builds that redirect with `https://`, so a gb2c instance
using plain HTTP on a LAN may not be able to complete OAuth; the rest of the app
works normally. Direct `dlfp` posting works with an existing LinuxFR token, but
obtaining or refreshing that token still needs a gb2c server with OAuth
credentials configured.

## Project layout

```
app/src/main/java/org/zorel/gb3android/
  MainActivity.kt              single-activity Compose host
  data/                        models, preferences, HTTP/SSE and direct-board clients
  domain/MessageParser.kt      native port of the gc2 message scanner
  domain/Conversation.kt       norloge index, reply graph, filtering
  ui/Gb3ViewModel.kt           state, stream/board lifecycle, posting, settings
  ui/Gb3App.kt                 navigation, main screen, sheets, dialogs
  ui/components/               post list, rows, composer, search sheets
  ui/screens/                  setup, settings, LinuxFR OAuth
  ui/theme/                    Material 3 color schemes and palette
app/src/test/                  parser, conversation, TSV and board client tests
```

## Notes and limitations

- The stream runs while the app is in the foreground. There are no OS push
  notifications; unread/reply/bigorno indicators are in-app badges.
- Direct boards are refreshed every 30 seconds, like gb2c; there is no live
  push from the board itself.
- The client is read-only for server configuration. Blocked identities configured
  with `GB2C_BLOCKED_IDENTITIES` are displayed in Settings but can only be changed
  on the server. Local blocked identities are editable in the app.
- Attachments are inserted as their public URL in the composer, like gc2. They
  need a gb2c server URL even in direct board mode.
- Totoz images are loaded from the configured totoz server
  (`https://nsfw.totoz.eu` by default).
- LinuxFR is only needed to post to the `dlfp` tribune. OAuth uses a dedicated
  WebView so the token stays in the app and passwords are only entered on
  linuxfr.org. Connecting or refreshing a LinuxFR account requires a gb2c server
  with OAuth credentials; an existing token can be reused by an OAuth2 custom
  board.
