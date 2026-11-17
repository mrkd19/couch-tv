# Sony BRAVIA emergency recovery guide

**Use this page to put a Sony BRAVIA back to its original Google TV home** if couch-tv stops
working, crashes, gets stuck, makes the Home button misbehave, fails to install or update, or you
simply want it gone.

> **First, don't panic.** couch-tv never switches off Sony's Google TV home or the setup app, and
> it never deletes anything from the TV. Everything below only switches things back. A factory
> reset is the very last resort ([§3.6](#36-last-resort-factory-reset)), never the first.

| | |
|---|---|
| TV | Sony BRAVIA 3 (S30 series), internal model `BRAVIA BF1` |
| Software | Google TV, Android TV OS 12 (API 31), build `STT2.230811.001` |
| Original state recorded | 17 November 2026, read-only survey, couch-tv not installed |
| Checked against | couch-tv `main` @ `1b3e49a` (release 0.12): `tv-home/tools/apply.sh`, `undo.sh`, `remove-all.sh`, `tvadb.py`, `FireWatch.kt`, `CrashGuard.kt`, `AndroidManifest.xml` |
| Recovery commands tested on the TV? | **No.** They were read and checked against the scripts and the recorded TV state, and the Windows behaviour of the scripts was tested against an unused test address. They were never run on the TV. |

**Contents**

1. [Original TV configuration](#1-original-tv-configuration)
2. [Emergency restore instructions](#2-emergency-restore-instructions)
3. [Recovery without a working custom launcher](#3-recovery-without-a-working-custom-launcher)
4. [Before every future installation (mandatory checklist)](#4-before-every-future-installation-mandatory-checklist)
5. [Copy-and-paste command reference](#5-copy-and-paste-command-reference)
6. [Keeping this guide up to date](#6-keeping-this-guide-up-to-date)
7. [Where the backups live](#7-where-the-backups-live)

---

## 1. Original TV configuration

Recorded before couch-tv was ever installed. The full private survey is **not** in this repository
(see [§7](#7-where-the-backups-live)).

### 1.1 Home screen (launcher)

| What | Package / activity | Original state |
|---|---|---|
| **Google TV home** (the original launcher) | `com.google.android.apps.tv.launcherx/.home.HomeActivity` | **Enabled**. Android's resolved HOME and the default launcher. HOME priority 2 |
| **Google TV setup / recovery app** | `com.google.android.tungsten.setupwraith/.RecoveryActivity` | **Enabled**. A second HOME candidate, priority 1 |
| **Fallback home** (Android TV Settings) | `com.android.tv.settings/.system.FallbackHome` | Enabled. Last-resort HOME, priority −1000 |
| Older Android TV launcher | `com.google.android.tvlauncher` | Not installed on this TV |
| Ambient mode / screensaver (Google's idle adverts) | `com.google.android.apps.tv.dreamx` (`.service.Backdrop`) | Enabled, and set as the screensaver |
| Accessibility services | — | **None** (`accessibility_enabled` = 0) |
| couch-tv apps | `app.couchtv.launcher`, `dev.couchtv.ontv` | Not installed |

**Why the setup app matters:** on this TV the setup app is itself a home screen candidate. If the
Google TV home *and* the setup app are both switched off, the TV has nothing to start into and
can loop at every restart. That is the exact bug couch-tv 0.10 and older caused on a Sony BRAVIA
([TROUBLESHOOTING.md](TROUBLESHOOTING.md#if-your-tv-loops-after-a-restart)).

### 1.2 Packages that must never be disabled

Never switch these off, by hand, with ADB, or through `EXTRA_OFF` / `PRIVACY=1`:

| Package | What it is |
|---|---|
| `com.google.android.apps.tv.launcherx` | Google TV home |
| `com.google.android.tungsten.setupwraith` | Google TV setup / recovery home |
| `com.android.tv.settings` | TV Settings, including the fallback home |
| `com.sony.dtv.sonysystemservice` | Sony system service |
| `com.google.android.tv.remote.service` | Remote / pairing service |
| `com.google.android.katniss` | Google Assistant / voice |
| `com.google.android.webview` | WebView (couch-tv's home page and many apps need it) |
| `com.google.android.gms`, `com.google.android.gsf` | Google Play services |
| Anything `com.android.*`, `android` | Core Android |
| Anything with *bluetooth, remote, rcu, voice, cec, inputmethod, tvinput, providers.tv* in the name | Remote control, HDMI-CEC, keyboard, TV inputs, Keep watching |

`apply.sh` itself refuses all of these (`NEVER_OFF` and `needed()` in `tv-home/tools/apply.sh`).
The table is here so a human never does it by hand either.

### 1.3 Packages that were already switched off by Sony

These were **disabled before couch-tv**, most likely region-specific apps Sony ships switched off.
Leave them as they are; `undo.sh` doesn't touch them, because they aren't on its list:

`com.mediatek.dtv.tvinput.atsctuner`, `com.mediatek.dtv.tvinput.isdbtuner`,
`com.sony.dtv.all4launcher`, `com.sony.dtv.b2b.vendorprotocol`, `com.sony.dtv.iplayerlauncher`,
`com.sony.dtv.itvlauncher`, `com.sony.dtv.my5launcher`, `com.sony.dtv.soundslauncher`,
`com.youview.poa`

### 1.4 What couch-tv changes on install (Option A, `apply.sh`)

Recovery means undoing exactly this list (read from `tv-home/tools/apply.sh`):

| # | Change | Undone by |
|---|---|---|
| 1 | Installs `app.couchtv.launcher` (shows as **"Home"** in the app list) | `remove-all.sh`, or uninstall in Settings |
| 2 | `cmd package set-home-activity app.couchtv.launcher/.MainActivity` | `undo.sh` sets the original home back |
| 3 | Switches off ambient mode: `pm disable-user --user 0 com.google.android.apps.tv.dreamx` (plus anything in `EXTRA_OFF` / `PRIVACY=1`, if used) | `undo.sh` (`pm enable`) |
| 4 | Grants `READ_TV_LISTINGS`; sets app-op `WRITE_SETTINGS allow` (and `SYSTEM_ALERT_WINDOW` only with a doorbell) | `undo.sh` resets the app-ops; uninstalling removes the rest |
| 5 | Grants `WRITE_SECURE_SETTINGS` | `undo.sh` revokes it **first** |
| 6 | Turns on the accessibility service **"Home button watch"** (`app.couchtv.launcher/.FireWatch`) and `accessibility_enabled 1` | `undo.sh` |
| 7 | On the PC: writes `tv-home/tvs/<TV_NAME>.home` (the original home) and `tv-home/tvs/<TV_NAME>.disabled` (what it switched off) | Used by `undo.sh`. **Back these up** ([§4](#4-before-every-future-installation-mandatory-checklist)) |

It never switches off the Google TV home or the setup app. If it finds them switched off, it switches
them back **on**.

### 1.5 Two safety mechanisms built into couch-tv

- **Crash guard** (`CrashGuard.kt`): if couch-tv fails to start properly twice within 3 minutes (a
  start counts as healthy after 20 s), it **steps aside for 5 minutes**. The Google TV home stays
  on screen and the Home button watch stops pulling couch-tv back. Then it tries again.
- **The Home button watch re-enables itself.** While couch-tv holds `WRITE_SECURE_SETTINGS`, it
  switches the watch back on every time it starts, at every boot and after every update
  (`FireWatch.ensure()`, called from `MainActivity.kt` and `FireWatch.Restore`). So **switching
  "Home button watch" off in TV Settings is only temporary**. For a permanent fix, remove couch-tv
  (§2.4) or run `undo.sh` (§2.1), which revokes that permission first.

---

## 2. Emergency restore instructions

### 2.0 One-time setup for every PC command below

You need: the PC with this repository at `C:\dev\couch-tv` and its Python environment (`.venv`),
the TV switched on, the same home network, and the TV still allowing this PC (ADB).

Replace `<TV_IP>` everywhere with the TV's address: TV **Settings → Network & Internet →** your
network.

**Git Bash** (Start menu → *Git Bash*). Needed for the `.sh` scripts:

```bash
cd /c/dev/couch-tv
export TV_IP=<TV_IP>
export PY=/c/dev/couch-tv/.venv/Scripts/python.exe
```

> ⚠️ **On Windows, `PY` is required.** The scripts look for `.venv/bin/python`, which only exists on
> Mac/Linux. Without `PY` they print *"The computer cannot reach the TV"* even when the TV is fine
> (tested).

**Windows PowerShell**. For single commands with `tvadb.py`:

```powershell
cd C:\dev\couch-tv
$env:TV_IP = "<TV_IP>"
```

**Can the PC reach the TV?** Always do this first. Git Bash:

```bash
"$PY" tv-home/tools/tvadb.py ping
```

- **Expected:** `ok`
- **If it fails** (`cannot reach the TV …`): the TV is off or asleep, on a different network, has a
  new address, has debugging switched off, or no longer trusts this PC. Check the address, wake
  the TV, and try again. If the TV shows *"Allow USB debugging?"*, tick *Always allow* and press
  **Allow**. Still failing? Use the TV-remote route ([§3.4](#34-adb-is-unavailable-tv-remote-only)).

### 2.1 Restore the original Google TV home (keeps couch-tv installed)

**Where:** Git Bash (after §2.0)

```bash
sh tv-home/tools/undo.sh
```

**What it does** (from `undo.sh`), in this order:

1. Switches back on everything `apply.sh` switched off (its `.disabled` list on this PC; without
   one, just ambient mode). It always also runs `pm enable` on the Google TV home and the setup
   app.
2. Resets the doorbell and screensaver permissions.
3. **Revokes `WRITE_SECURE_SETTINGS` first**, then removes only couch-tv's entry from the
   accessibility list, so the Home button watch stays off.
4. Sets the saved original home (or `com.google.android.apps.tv.launcherx/.home.HomeActivity`) as
   the home, and presses Home.

**Expected:** ends with `home screen now: com.google.android.apps.tv.launcherx/.home.HomeActivity`.

**If it says the TV still picks couch-tv for Home:** Google TV sometimes keeps that choice while the
app is installed. Its own home and setup are back on for the next restart; to hand Home back fully,
remove the app (§2.4).

**If it fails:** it stops at the first failed step and says which one. It is safe to run again.
If the PC can't reach the TV, use §3.4.

### 2.2 Re-enable the original home app (only if it shows as disabled)

couch-tv 0.11+ never disables it, but check if Home goes to a blank or setup screen.

**Where:** Windows PowerShell (after §2.0)

```powershell
.venv\Scripts\python.exe tv-home\tools\tvadb.py shell "pm list packages -d"
```

- **Purpose:** lists every **disabled** package. It only reads.
- **Expected:** the nine Sony/MediaTek packages from [§1.3](#13-packages-that-were-already-switched-off-by-sony), and
  `com.google.android.apps.tv.dreamx` if couch-tv is set up. `com.google.android.apps.tv.launcherx`
  should **not** be in the list.

If `launcherx` *is* in the list:

```powershell
.venv\Scripts\python.exe tv-home\tools\tvadb.py shell "pm enable com.google.android.apps.tv.launcherx"
```

- **Expected:** `Package com.google.android.apps.tv.launcherx new state: enabled`
- **Safe to repeat**; it does nothing harmful if the package is already enabled.

Then make it the home again:

```powershell
.venv\Scripts\python.exe tv-home\tools\tvadb.py shell "cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity"
```

- **Expected:** `Success`

### 2.3 Restore the setup / recovery app

**Where:** Windows PowerShell (after §2.0)

```powershell
.venv\Scripts\python.exe tv-home\tools\tvadb.py shell "pm enable com.google.android.tungsten.setupwraith"
```

- **Purpose:** switches the Google TV setup app back on. Without it (and the Google TV home), the TV
  can loop at restart.
- **Expected:** `Package com.google.android.tungsten.setupwraith new state: enabled`
- **If it fails:** run §2.0's `ping` first. If the TV keeps restarting, see §3.5.

### 2.4 Remove couch-tv completely and safely

**Where:** Git Bash (after §2.0)

```bash
sh tv-home/tools/remove-all.sh
```

**What it does** (from `remove-all.sh`):

1. Checks the PC can reach the TV. If not, it changes **nothing** and prints the TV-remote steps.
2. Runs `undo.sh` (§2.1). **If that fails, it stops and does not remove the app**, so the TV
   always keeps a home screen.
3. Uninstalls `app.couchtv.launcher` and `dev.couchtv.ontv` (whichever are present), and presses
   Home.
4. Leaves Developer options **on** (add `DEV_OFF=1` to switch debugging off as well, see §2.6).

**Consequence:** uninstalling deletes couch-tv's own data on the TV: its Home settings, your photos
copied into it, and its saved bridge address. Nothing else on the TV is affected: other apps,
logins and Sony settings stay.

**Expected:** `Done. Press Home on the TV: you should see the TV's own home screen.`

**If it fails:** it lists the failed steps. It is safe to run again. Otherwise use §3.4.

### 2.5 Restore the original Home button behaviour

The Home button misbehaving is almost always the **Home button watch**, with Google TV's home
flashing up first and couch-tv then jumping back on top.

- **Permanent fix:** §2.1 (`undo.sh`) or §2.4 (`remove-all.sh`).
- **Temporary fix, no PC:** TV **Settings → System → Accessibility → Home button watch → Off**.
  couch-tv switches it back on the next time it starts (§1.5), so follow up with §2.1 or §2.4.
- **To escape right now:** press **Back** twice, or open apps from the remote's own app buttons.

After §2.1 or §2.4, optionally restore the exact original accessibility setting (originally `0`,
with no services). **Where:** Windows PowerShell:

```powershell
.venv\Scripts\python.exe tv-home\tools\tvadb.py shell "settings get secure enabled_accessibility_services"
```

- **Expected:** `null` or empty. ⚠️ If it lists **anything else** (another app's accessibility
  service, or a TV accessibility feature you switched on yourself), **stop here**. Don't run the
  next command, or it will switch that feature off too.

Only if the result was empty or `null`:

```powershell
.venv\Scripts\python.exe tv-home\tools\tvadb.py shell "settings put secure accessibility_enabled 0"
```

- **Expected:** no output. This matches the original recorded state.

### 2.6 Switch off ADB debugging (optional)

**Option 1: with the PC, as the last step of removal.** Git Bash (after §2.0):

```bash
DEV_OFF=1 sh tv-home/tools/remove-all.sh
```

- **Consequence:** after this, **the PC can no longer reach the TV** until you switch debugging on
  again on the TV itself. The script only does it if the home was restored successfully.
- It runs `settings put global adb_wifi_enabled 0`, `development_settings_enabled 0` and
  `adb_enabled 0`.
- **Expected:** `Done. …`. The connection dropping during step 3 is normal.

**Option 2: on the TV.** **Settings → System → Developer options**:

1. Switch off **Network debugging** (and **Wireless debugging**, if on).
2. Switch off **USB debugging**.
3. At the top, switch **Developer options** off.

### 2.7 Revoke this PC's debugging authorisation

Do this if you replace or give away the PC, or want no computer to have access.

**Where:** Sony TV Settings. **Settings → System → Developer options → Revoke USB debugging
authorisations** → OK.

- **Consequence:** every computer is forgotten. The next connection shows *"Allow USB debugging?"*
  again.
- There is no PC command for this; it can only be done on the TV.

**Then, on the PC (optional).** Windows PowerShell:

```powershell
Remove-Item "$env:USERPROFILE\.android\adbkey", "$env:USERPROFILE\.android\adbkey.pub"
```

- **Consequence:** deletes this PC's ADB identity. Any other Android tool on this PC that uses the
  same key (Android Studio, Google `adb`) has to be re-approved by each device. A new key is made
  automatically next time.

---

## 3. Recovery without a working custom launcher

Which methods need you **physically at the TV**:

| Method | At the TV? | Needs the PC? | Needs ADB? |
|---|---|---|---|
| Back button, remote app buttons, voice "open settings" | Yes | No | No |
| Wait for the crash guard (§3.1) | Yes | No | No |
| TV Settings route (§3.4) | Yes | No | No |
| Sony restart (§3.3) / Safe mode (§3.5) | Yes | No | No |
| Scripts and commands (§2) | No, once ADB is allowed | Yes | Yes |
| Approving *"Allow USB debugging?"* | Yes | Yes | — |

### 3.1 couch-tv can't start, or the screen is blank or frozen on couch-tv

1. **Wait about a minute and press Home.** If couch-tv failed to start twice within 3 minutes, the
   crash guard hands the screen to the Google TV home for 5 minutes (§1.5).
2. **Press Back** a few times, or use an app button on the remote (for example Netflix or YouTube)
   to prove the TV itself works.
3. **Restart the TV** (§3.3). couch-tv is started fresh, and the crash guard is still active.
4. With the Google TV home on screen, either use the PC (§2.1 or §2.4) or the TV Settings route (§3.4).

### 3.2 The Home button doesn't behave as expected

- Google TV's home **flashing briefly** before couch-tv appears is expected (documented upstream).
- Home **stays** on Google TV: the watch isn't bound. Restart the TV (§3.3), or run `apply.sh`
  again. That's a re-install step, so it needs the §4 checklist.
- Home **loops** between Google TV and couch-tv, or you can't get out: press **Back** twice, then
  use §2.5.

### 3.3 Restart the TV (Sony's official method)

From Sony support article 00114591:

1. Point the remote at the TV's LED.
2. **Press and hold POWER for about 5 seconds**, until **"Power off"** appears. The TV restarts.
3. If that doesn't work: unplug the TV's power cable, wait 2 minutes, and plug it back in.

### 3.4 ADB is unavailable: TV remote only

Use this when the PC can't reach the TV. It follows `docs/UNINSTALL.md`, "With only the TV remote".
The menu names are from the project docs and standard Google TV 12, not checked on this TV.

1. Open TV **Settings**: the gear on the Google TV home, the couch-tv **Settings** tile (gear at the
   end of the app row) → **TV settings**, or the remote's Settings button if it has one.
2. **Apps → See all apps → Show system apps.**
3. Find **Google TV Home**. If it says *Disabled*, open it and press **Enable**. Do the same for
   **Setup**.
   ⚠️ Never press *Disable*, *Force stop*, *Uninstall updates* or *Clear data* on these two.
4. **Do step 3 before step 5.** If couch-tv is removed while Google TV Home is still disabled, the TV
   has no home screen.
5. Back in **Apps**, open **Home**, which is couch-tv. It has a real **Uninstall** button, while
   Google TV Home doesn't. Press **Uninstall** → OK. Do the same for **Couch TV** if present.
6. Press **Home**. The Google TV home is back.
7. Optional: **Settings → System → Developer options** → switch off debugging (§2.6, option 2).

### 3.5 The TV is stuck or keeps restarting: Sony Safe mode (no PC needed)

Sony's **Safe mode** starts the TV with **all downloaded apps disabled**, and couch-tv is a
downloaded app. So in Safe mode the TV starts on the Google TV home without couch-tv.

The steps for **BRAVIA 3 (S30)** come from Sony support article 00127645 (checked 17 Nov 2026):

1. On the remote, **press and hold POWER for 3 seconds or longer**.
2. If a **Cancel / Restart** screen appears, select **Restart**.
3. When the **Google animation** starts, **press and hold Volume down (–)** until the animation
   disappears.
4. **"Safe mode"** appears in the bottom-left corner.
5. Now do §3.4 steps 2–6 to uninstall couch-tv (Settings → Apps).
6. **To leave Safe mode:** do step 1 again (restart).

**Consequence:** Bluetooth is disconnected in Safe mode and doesn't come back on by itself.
Reconnect Bluetooth headphones, speakers or a Bluetooth remote afterwards.

**If the TV loops and you have the PC** (from `docs/TROUBLESHOOTING.md`): switch the TV off at the wall
for 10–15 minutes, switch it on, and as soon as `ping` (§2.0) answers, run §2.3 and §2.2, or
`sh tv-home/tools/undo.sh`. Each restart gives a short window, so keep trying.

### 3.6 Last resort: factory reset

**Only if everything above has failed.** **Settings → System → About → Reset** (Sony article
00159487).

**Consequence:** this **erases every app, login, setting and download on the TV** and puts it back to
how it left the factory. You'll have to set the TV up again and sign in to every app. couch-tv
recovery never needs this.

---

## 4. Before every future installation (mandatory checklist)

**No installation, update or re-run of `apply.sh` proceeds until every box is ticked.** Record the
results in the private development log.

- [ ] **Fresh private survey saved.** Run the read-only survey (`bravia_survey.py`, kept in the private
      folder) and save it with today's date in the private backup folder (§7).
- [ ] **Original launcher state recorded.** The survey shows
      `com.google.android.apps.tv.launcherx/.home.HomeActivity` as the resolved home (or, on a
      re-install, the state is understood and written down).
- [ ] **Essential packages enabled.** `pm list packages -d` (§2.2) does **not** list
      `launcherx`, `setupwraith`, `com.android.tv.settings` or `com.sony.dtv.sonysystemservice`.
- [ ] **Sony launcher preserved.** The planned command has **no** `EXTRA_OFF` naming anything from
      §1.2.
- [ ] **Setup / recovery app preserved.** Same check for `setupwraith`.
- [ ] **ADB access verified.** `tvadb.py ping` → `ok` with **no** new prompt on the TV.
- [ ] **Recovery commands verified.** `undo.sh` and `remove-all.sh` match this guide (§6). `PY` is set
      in Git Bash. A dry run of the install was reviewed:
      `DRY_RUN=1 sh tv-home/tools/apply.sh` (changes nothing).
- [ ] **Enough free storage.** `/data` has at least **500 MB free**: `tvadb.py shell "df -h /data"`.
      Recorded: 1.5 GB free of 7.5 GB.
- [ ] **APK integrity verified.** The SHA-256 of the APK matches the GitHub release's published
      digest, or our own build's printed hash. PowerShell:
      `Get-FileHash dist\couch-tv-home.apk -Algorithm SHA256`.
      Release 0.12 `couch-tv-home.apk`: `b049cb1f393dc122f8e1151756435ce77eed92b1ea433ff06d92d29795c8e293`
      (21,759,133 bytes).
- [ ] **Rollback ready.** This guide is open on another device (phone or the GitHub page). After
      `apply.sh`, **copy `tv-home/tvs/` into the private backup folder**; `undo.sh` uses those
      files.
- [ ] **Physical access.** You're at the TV with the remote for the whole install.

---

## 5. Copy-and-paste command reference

Every command, where it runs, what to expect, and what to do if it fails. Always do §2.0 first.

| Where | How to open it |
|---|---|
| **Git Bash** | Start menu → *Git Bash*, then the three §2.0 lines |
| **Windows PowerShell** | Start menu → *Windows PowerShell*, then the two §2.0 lines |
| **ADB shell** | Not opened directly. Each command is passed through `tvadb.py shell "<command>"`, which runs it on the TV |
| **Sony TV settings** | The remote: Settings |

### Read-only checks (always safe)

Is the TV reachable? **Git Bash**:

```bash
"$PY" tv-home/tools/tvadb.py ping
```

Expected `ok`. Fails → §2.0.

Which home does the Home button open? **PowerShell**:

```powershell
.venv\Scripts\python.exe tv-home\tools\tvadb.py shell "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
```

Expected, originally: last line `com.google.android.apps.tv.launcherx/.home.HomeActivity`.
With couch-tv installed it may still show this; the Home button watch is what brings couch-tv
back.

Which packages are disabled? **PowerShell**:

```powershell
.venv\Scripts\python.exe tv-home\tools\tvadb.py shell "pm list packages -d"
```

Compare with §1.3.

Is couch-tv installed? **PowerShell**:

```powershell
.venv\Scripts\python.exe tv-home\tools\tvadb.py shell "pm list packages couchtv"
```

Expected `package:app.couchtv.launcher` if installed; nothing if not.

Free storage. **PowerShell**:

```powershell
.venv\Scripts\python.exe tv-home\tools\tvadb.py shell "df -h /data"
```

Look at *Avail*.

### Restore commands (change the TV; each is safe to repeat)

| Command | Section | Where |
|---|---|---|
| `sh tv-home/tools/undo.sh` | §2.1 | Git Bash |
| `pm enable com.google.android.apps.tv.launcherx` | §2.2 | PowerShell via `tvadb.py shell` |
| `cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity` | §2.2 | PowerShell via `tvadb.py shell` |
| `pm enable com.google.android.tungsten.setupwraith` | §2.3 | PowerShell via `tvadb.py shell` |
| `pm enable com.google.android.apps.tv.dreamx` (ambient mode; optional) | — | PowerShell via `tvadb.py shell` |
| Press Home: `tvadb.py keys HOME` | — | PowerShell |

### Removing commands (read the consequence first)

| Command | Consequence | Section |
|---|---|---|
| `sh tv-home/tools/remove-all.sh` | Uninstalls couch-tv and deletes its data on the TV. Refuses if the home can't be restored first | §2.4 |
| `DEV_OFF=1 sh tv-home/tools/remove-all.sh` | Same, **plus switches ADB off**: the PC loses access until you switch it on again on the TV | §2.6 |
| `settings put secure accessibility_enabled 0` | Switches accessibility off. Only when §2.5's check shows no other services | §2.5 |
| `Remove-Item …\.android\adbkey*` | Deletes this PC's ADB identity | §2.7 |

**Never run these on this TV:** `pm disable-user` or `pm uninstall` on anything in §1.2,
`pm clear` on the Google TV home or setup app, `wipe`, `reboot recovery` / `reboot bootloader`,
or flashing anything.

---

## 6. Keeping this guide up to date

This guide must always describe the **current** implementation. Update it, and re-check every
command against the code, in the same change whenever any of these change:

- `tv-home/tools/apply.sh`, `undo.sh`, `remove-all.sh`, `tvadb.py` (install, undo, ADB behaviour)
- `NEVER_OFF`, `needed()`, `EXTRA_OFF` or `PRIVACY` handling (package enable/disable)
- `FireWatch.kt`, `KeyWatch.kt`, `CrashGuard.kt`, `HomeRole.kt`, or the manifest's permissions and
  services (launcher and Home button behaviour)
- The TV's software (a Sony/Google update): take a fresh survey and compare it with §1
- Our own customisations on the `bravia-custom` branch
- Upstream merges (`git fetch upstream`): diff the files above and update this guide **before**
  installing the merged version

Each update: change the "Checked against" line at the top, and note it in the development log.

---

## 7. Where the backups live

| What | Where | In this public repo? |
|---|---|---|
| This guide | `docs/BRAVIA-RECOVERY.md` in `github.com/mrkd19/couch-tv` | ✅ Yes |
| Offline copy of this guide | Private TV Setup folder → `BRAVIA-RECOVERY-BACKUP.md` | No |
| Full original survey, packages, launcher state, survey script | Private TV Setup folder → `private\` (`bravia-survey-*.json`, `original-tv-state.json`, `original-packages.json`, `original-launcher-state.json`, `bravia_survey.py`) | **No, never** |
| `apply.sh`'s undo files | `tv-home/tvs/` on the PC (git-ignored); copy into `private\` after every install | **No, never** |
| ADB key | `%USERPROFILE%\.android\adbkey` (git-ignored by name) | **No, never** |
| TV IP address, personal app list | Only in the private files | **No, never** |

The private TV Setup folder is outside the repository, in the owner's OneDrive Desktop, so
OneDrive keeps a cloud copy of it. `.gitignore` also blocks `private/`, `TV Setup/`, survey JSON
and `original-*` state files, in case one is ever copied into the repo by mistake.
