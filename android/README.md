# Coucou for Android

The Android app of [Coucou](https://github.com/Louis-CFM/coucou) by Louis Raillé, by Rhytam Biswas. It shows your coding
agents as Mochi on your phone and lets you approve or deny their permission requests, answer their questions and chat
through your computer's AI providers.

The name, the Mochi character, the icon and the sounds are © Louis Raillé and used with permission (see
[PERMISSION.md](PERMISSION.md)). The Android code is © Rhytam Biswas under the MIT License (see [LICENSE](LICENSE)). The app is free: no ads, no billing, no analytics.
It is English only and dark only. Download the signed APK from the [Releases](https://github.com/Louis-CFM/coucou/releases/tag/android-v0.2.0) (Android 11 or later; it needs Coucou 0.4.0 or later on Windows or Linux).

## Screenshots

Real screenshots from a Galaxy A12s. The pairing, chat and approval screens are protected on purpose (Android shows them black in a screenshot), so they are not shown here. Other apps’ notifications and the location are blurred.

| Home | The island over other apps | Alert on a locked phone | Allow or Deny |
|---|---|---|---|
| <img src="../docs/media/android-home.jpg" width="180" alt="Home: Mochi, the agent list, plan usage and a service"> | <img src="../docs/media/android-island.jpg" width="180" alt="The island over the home screen while an agent works"> | <img src="../docs/media/android-lock-alert.jpg" width="180" alt="The lock screen: Approval needed, the command hidden"> | <img src="../docs/media/android-allow-deny.jpg" width="180" alt="A notification with Deny and Allow"> |

## Build and test

Needs JDK 17 and the Android SDK (platform 36). From this folder:

```bash
./gradlew test assembleDebug lint
node scripts/gen-strings.mjs --check      # res/values/strings.xml is generated from i18n/app-strings.json
```

A release build (R8 shrinking and obfuscation, resources shrunk, `android.util.Log` calls removed, not debuggable) is `./gradlew assembleRelease`;
`lint` already depends on it in CI, with a check that no debug log text is left in the APK. Signing keys are never in the repository: set
`COUCOU_KEYSTORE_FILE`, `COUCOU_KEYSTORE_PASSWORD`, `COUCOU_KEY_ALIAS` (and `COUCOU_KEY_PASSWORD` if it differs) in the environment or in
`~/.gradle/gradle.properties`. Without them the release APK is unsigned (it cannot be installed), never signed with a debug key. Keep
`app/build/outputs/mapping/release/mapping.txt` with the build you ship. Nothing is published from here.

Without a computer: open the app and tap *Try the demo*. Or run the pretend computer (fake sessions, a fake approval; a test tool):

```bash
node tools/dev-desktop.mjs --host <LAN IP>     # prints a pairing link; flags: --details --answers --diffs --usage --services --fake-chat
```

The desktop side is in `windows/src-tauri/src/phone_link`; the protocol is `docs/ANDROID_LINK.md`, the internet relay
`docs/RELAY_LINK.md` (deploy and test it with `docs/RELAY_DEPLOY.md`).

## What it does

- **Home**: the focused agent as a black hero panel (Mochi in the agent's colour, a plain sentence), the other agents below, plan
  usage and service cards when the computer offers them, and *Recent* (your own Allow/Deny decisions, kept only on the phone).
  Raw tool names become plain words (`core/ToolLabels.kt`); a command or path is never shown without a deliberate tap (`core/SafeText.kt`).
- **Requests**: a sheet rises over any screen. Allow needs the fingerprint or screen lock, Deny does not, taps in the first 600 ms
  are ignored, and each request expires after 120 s. Questions from Claude Code can be answered the same way (the computer must allow it).
- **Where agents show** (Settings > Display): **Now Bar and Live Updates**, **Show Mochi over other apps** (the island), **Both**,
  or **Notifications only**. Every choice keeps the ordinary notifications (requests with Deny and Allow, finished or failed agents).
  An upgrade keeps what the old island switch said (on: the island; off: notifications only). Rules: `core/Surfaces.kt` (pure, tested).
- **Now Bar and Live Updates** (Android 16 or later): an Android Live Update about the agent the island would show, with its name,
  its state (working, using tools, thinking, waiting for your approval, has a question, finished, error, limit) and what it does in
  plain words, a status bar chip ("Working", "Approve", "Done"…) and a bar that follows the computer's steps. Details and limits in
  [Now Bar and Live Updates](#now-bar-and-live-updates) below.
- **Island over other apps** (optional, off by default): a black island from the camera cut-out drops while an agent works, shows
  the result for 10 s and goes back up with the computer's motion. Needs Android's "Display over other apps". Its **width, height and
  text size** can be changed (70 to 115 %, 70 to 130 %, 80 to 140 %, with a 10 s preview over the app); a lower height gives a
  compact island on one line, and a permission request always keeps its full height so Deny and Allow stay 48 dp. At 100 % it is
  exactly the island as it shipped. Rules are pure and tested (`core/Island*.kt`); `ui/IslandOverlay.kt` draws.
- **Chat** (a tab, when the computer offers it), **session details**, **file changes** and **plan usage** are opt-in switches on the
  computer (Settings > Android phone), all off by default; the phone is told only what it asks for and the computer allows.
- **Pairing**: scan the QR code with the in-app camera (CameraX + zxing, offline) or paste the link; a scan or a `coucou://pair`
  link always asks to confirm. The certificate is pinned. If the computer's address changes the phone finds it again by itself (mDNS,
  checked against the pinned certificate before the token is sent), or you can type the address.
- **Away from home Wi-Fi** (optional): through your own Cloudflare relay, end-to-end encrypted; the direct link is always tried first.
- **Keeping the link alive with the screen off**: an alarm every few minutes checks that the computer still answers and reconnects if not (Doze allows nothing more); Home says when and why the link was lost; Settings > Computer > *Can't connect?* checks the network, address, discovery, certificate, pairing code and relay; a calm hint suggests the battery setting ("Unrestricted", with the Samsung steps) only if it would help. Details and limits: `docs/ANDROID_LINK.md`, "Keeping the link alive".
- **Widget, quick-settings tile, ongoing notification**: agent names and state words only. Updated when something changes, never on a timer.
- **Widgets**: nine to pick from on the home screen, all on one card (24dp corners, a thin border, dark by default and light when the system is in day mode) with a gradient, a 1dp border and a glow in the colour of the state, the same palette for every Widget (`WidgetTone` in `core/WidgetData.kt`, `res/values/colors.xml`): green when an agent has finished, blue while it works, orange while something waits for you, soft grey when all is calm, and a muted grey with the "not connected" icon when the computer is not there. With several agents the most urgent one sets the colour (waiting, then working, then happy, then idle). Plan usage has a palette of its own so it never looks like a state: teal, yellow from 75% and red from 90% (`PlanUsageLook`); only from 90% is the percentage big and bold, with "Near limit" and the clock time it resets at ("resets at 14:30"). In the Widget picker each one shows a large, realistic preview with sample data (Android 12 and later; Android 11 shows a still picture of the card with Mochi), its name and a one-line description. The previews are static: vector Mochi faces in `res/drawable/preview_*`, layouts in `res/layout/widget_preview_*`.
  - *Agents summary* (3x2, the first Widget, same receiver as before): its text and counts are unchanged, now on the shared card with a small Mochi in the state of your most urgent agent, a headline with a badge for what waits for you and up to three short lines with a state dot each. A tap anywhere opens the app; it has no buttons.
  - *Mochi at a glance* (Solo, 2x2): Mochi in the state of your most urgent agent, its name and a state word.
  - *All your agents* (List, 4x3): one row of at least 48dp per agent (Mochi's face, name, state word, dot), up to five, with a badge for what waits for you; with no agent, a centred empty state ("Waiting for your computer" / "No agents running").
  - *Mochi, big* (4x4): no speech bubble any more. On the left Mochi with his glow and badge, then the agent's name and state word; on the right your plan usage (Claude and Codex, each with a thin bar that is teal, yellow from 75% and red from 90%, and a small percentage that becomes big and bold with "Near limit" from 90%), or "Plan usage is off" (a tap opens Settings) or "Not connected". Under them rounded buttons: two rows of three (Home, Chat, Settings, Recent, Top agent, Pair again; tinted orange while something waits), one row of four when medium, none when small.
  - *One-line status* (Status Pill, 4x1): one slim line with a dot, the agent's name and its state word, and four round buttons that open Home, Chat, Settings and Recent decisions. Narrower, the state word goes first, then the name; the text never runs under the buttons.
  - *Approvals waiting* (Waiting Alert, 2x2): calm when nothing waits, a bright orange "N waiting" when something does. A tap opens the app and the sheet rises by itself.
  - *Plan usage* (Usage Ring, 2x2): a ring for your Claude or Codex plan usage; "Turn on plan usage in the app" when it is off.
  - *Finished today* (Today, 2x2): agent tasks finished today, counted on this phone only and started again at local midnight by one inexact alarm (no exact-alarm permission, no wake-ups).
  - *Mochi's mood* (Mood, 1x1): just his face.

  The buttons only open a screen of the app, from a closed list (`core/LaunchScreen.kt`) through the non-exported `LaunchActivity`; none approves, denies or sends anything, and `MainActivity` still reads no intent extra. A tap on Solo, Mochi Big, Status Pill, Mochi Mood or a List row opens that agent in the app. A request is still decided in the app, behind the fingerprint, never from a Widget. Widgets show agent names, state words and colours only, are redrawn when something changes (`updatePeriodMillis` stays 0) and a redraw that would look the same is skipped. On Android 12 and later they adapt to their size (small, medium, large). Code: `core/WidgetData.kt` (pure, tested), `app/WidgetHub.kt`, one class per Widget in `app/`, `app/MochiBitmap.kt`, `app/RingBitmap.kt`.
- **Looking at every state without a computer** (debug builds only): `adb shell am broadcast -n com.coucou.android/.app.WidgetPreviewReceiver -a com.coucou.android.debug.WIDGET_STATE --es state <disconnected|empty|working|waiting|done|live>`; `live` goes back to the real state. The receiver is in `app/src/debug` and does not exist in a release build.
- Mochi and his 11 outfits, sounds, wardrobe (Settings), a tap on Mochi slaps him, a long press pets him.

## Now Bar and Live Updates

What Coucou does, with public and documented Android APIs only:

- It posts one **promoted ongoing notification**, which Android calls a Live Update (`app/Notifications.kt`, `showLive`). It asks
  for promotion with the extra that `NotificationCompat.Builder#setRequestPromotedOngoing` writes (`EXTRA_REQUEST_PROMOTED_ONGOING`,
  public in API 36.1), sets the chip text with `setShortCriticalText` and uses `Notification.ProgressStyle` (API 36). It keeps every
  rule Android sets for promotion: ongoing, a title, the standard or progress style, not colorized, no custom view, not a group
  summary, and its own silent channel *Live Updates* (default importance, never minimum). The manifest declares
  `POST_PROMOTED_NOTIFICATIONS` (a normal permission, granted at install).
- It says what the island would, from the same plan (`IslandPlan` → `core/LiveUpdatePlan.kt`) and the same timeline
  (`IslandTimeline`): it appears while an agent works, waits for your approval or has a question, shows how it ended for 10 s, and goes.
  The data is the link the app already has with the computer (the `sessions` and `approval` messages): nothing changed on the computer.
- Names and plain words only, as on the island and the widgets: never a command, a path or an answer. On a lock screen that hides
  notification content it shows the agent's name and a state word. Tapping it opens Coucou; a request is still decided in the app,
  behind the fingerprint, or with Deny and Allow on the request's own notification.
- If you swipe it away it stays away until what it was about has ended, as Android asks. Nothing runs while it is hidden.

What Coucou cannot promise, because it is the system's decision:

- **Android 16 or later** (API 36) is needed. On Android 15 and older, or when Live Updates are turned off for Coucou in Android's
  settings (Settings > Display > *Open settings* goes there), **Now Bar** falls back to the island (if "display over other apps" is
  allowed) and the ordinary notifications. **Both** then shows the island only.
- **Samsung's Now Bar is One UI's own surface.** One UI 8 (Android 16) started to show Android Live Updates from other apps in the Now
  Bar, and One UI 9 (Android 17, now on the Galaxy S24 series) builds on them; Samsung publishes no API of its own for third-party
  apps, and Google notes that phone makers may add criteria of their own. So Coucou asks Android for a Live Update, and One UI decides
  whether it goes to the Now Bar, the status bar chip, the lock screen or only the shade. Coucou does not use the undocumented extras
  that One UI 7's "Live notifications" read (a test fails if they appear in the code).
- Settings tells you what happened on your phone: whether Live Updates are allowed for Coucou, and whether Android really promoted the
  one that is posted (`FLAG_PROMOTED_ONGOING`, which the posting app may read). It cannot see the Now Bar itself.
- Not checked on a real Galaxy S24 Ultra in this change: it was written against Android's documentation and the API reference, and
  compiled and unit-tested on a computer. If One UI keeps it out of the Now Bar while Settings says it is promoted, that is One UI's
  rule, not something the app can change; please report the One UI version.

To see it: Settings > Display > **Now Bar and Live Updates**, then start an agent on the computer (or *Try the demo*) and leave the app.

## A test APK from GitHub Actions

Every pull request that touches `android/` (and Actions > android > **Run workflow**, on any branch) builds the app, runs the tests
and lint, and uploads an artifact named `Coucou-Android-<version>-<commit>` with:

- `Coucou-Android-<version>-<commit>.apk`: the release build (R8, no debug logs), to install on the phone;
- `Coucou-Android-<version>-<commit>-debug.apk`: the same app with its debug logs (`adb logcat -s CoucouLive CoucouIsland`);
- `SHA256SUMS`.

Both are signed with one test key, so a new build installs over the last one and keeps the pairing. With the repository secrets
`ANDROID_CI_KEYSTORE_BASE64` (a .jks or .p12, base64), `ANDROID_CI_KEYSTORE_PASSWORD` and, if it is not `coucou`, `ANDROID_CI_KEY_ALIAS`,
that key is yours alone; without them the workflow makes one and keeps it in the Actions cache, which anyone who can run workflows in
the repository can read, so it is for testing only. They cannot update an APK from the Releases page (another key): uninstall that one
first. Release builds are still made by `android-release.yml`, signed with the release key.

## Getting the phone link running on Windows

1. Download the portable build from the repository's **Windows** workflow run (Actions, Windows, the `Coucou-Windows-portable`
   artifact). It holds `coucou.exe` and `coucou-hook.exe`.
2. **Close any other Coucou first.** An installed copy (for example in `C:\Program Files\Coucou`) starts at login and has no
   phone link, so the phone would say "Connecting…" forever. Then start the portable `coucou.exe`.
3. Settings > **Android phone** > turn **Phone link** on. It is off by default and listens on your local network only. If Windows
   asks about the firewall, allow Coucou on **private** networks (a network marked *Public* blocks the phone).
4. **Show pairing code**, then scan the QR code with the app (or paste the link). The phone and the computer must be on the same Wi-Fi.
5. Optional, each with its own switch on the same page: session details, answers to questions, file changes, chat through the
   computer's own keys, plan usage, services.

What to know when you try approvals from the phone:

- Only **Claude Code** (the command-line tool), Codex, Copilot CLI and Muse Code can be approved from Coucou or the phone. Requests
  from the **Claude Desktop app** are asked in that app only: the computer declines them at once (`windows/src/island/agents.ts`,
  `APPROVAL_AGENTS`; the same rule as on the Mac).
- Claude Code must be in a mode that **asks**. In auto or bypass mode it never asks, so nothing reaches the phone. To test:
  `claude --permission-mode default "…"`, or press Shift+Tab until it says manual mode.
- The plan usage numbers also come from Claude Code's status line, so they update from the command-line tool and not from the
  Claude Desktop app.
- In Windows PowerShell, `claude` can be blocked by the script policy (`claude.ps1 cannot be loaded`). Run `claude.cmd`, use
  Command Prompt, or allow local scripts for your user with `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`.

For testing without a cable once the phone has been connected by USB once: `adb tcpip 5555`, then
`adb connect <phone address>:5555`. It resets when the phone restarts or its address changes.

## Security rules the tests guard

Certificate pinning and the pairing token; approval fingerprints, 120 s expiry and decide-once; the biometric gate for Allow;
`MainActivity` ignores intent extras (only the non-exported `LaunchActivity` can ask for the Allow prompt); `FLAG_SECURE` on pairing,
chat, approval and question screens; secrets only in the Android Keystore blob; no logging of tokens, answers or relay keys; no ad,
billing or analytics library.

## Layout

- `app/src/main/kotlin/.../mochi` the engine (pure Kotlin) and the Compose painter; `mochi/outfit` the outfits (ported line by line)
- `.../link` protocol, pairing, TLS client, WebSocket client, relay, transport choice, demo link
- `.../core` pure rules (island, navigation, safe text, home text, glance); `.../ui` Compose screens; `.../app` model, notifications, service, storage
- `relay/` the relay (Cloudflare Worker + Node twin); `tools/` the pretend computer and the outfit trace; `i18n/` the English strings
- Tests compare pill IDs, colours, outfits and sounds with the desktop sources, so a drift fails the build
