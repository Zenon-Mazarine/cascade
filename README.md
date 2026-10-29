# Cascade

A personal, Niagara-style Android home screen: a single vertical list of favorite apps, an
alphabet rail on the right edge for everything else, and notifications shown right under
the app they belong to. Kotlin + Jetpack Compose, Android 8.0+ (minSdk 26).

## Build and install

1. Open this folder in Android Studio (Ladybug or newer). Let it sync. It downloads Gradle and the
   Android SDK pieces it needs.
2. On your phone: Settings › About phone › tap **Build number** 7 times, then turn on
   **USB debugging** in Developer options. Plug the phone into the Mac.
3. Press **Run ▶**. Cascade installs and opens in **preview mode**: it's just an app, your normal
   home screen is still in charge.

Installing through Android Studio avoids Android 13+'s "restricted setting" block on notification
access. If you ever sideload the APK instead and the toggle is greyed out: App info › ⋮ ›
**Allow restricted settings**.

## Using it

- **Favorites**: the list above your thumb. Tap to open, long-press to unpin, reorder,
  see app info or uninstall. Phone, Messages, Camera and your browser are pinned on first run.
- **Alphabet rail** (right edge): touch or slide along it, and a panel shows apps for that letter.
  Long-press any app there to pin it.
- **Search**: magnifier at the top of the rail. Type, then press Go to open the first match.
- **Notifications**: grant access in settings. The newest notification shows under the app;
  tap to open it, ✕ to dismiss.
- **Clock**: tap for alarms, long-press for settings. Settings is also the gear at the bottom of the rail.

## Going back to your old home screen

Settings › **Go back to your old home screen** › **Switch back**. This opens Android's
Home app setting with your previous launcher (remembered automatically, and labelled
"Your previous home screen") ready to pick.

Your old layout is never modified. Every launcher stores its own icons, folders and widgets,
and Cascade doesn't touch them, so switching back puts everything exactly where it was.
Uninstalling Cascade also reverts automatically.

Android doesn't let any app change the default home screen silently, which is why
the last tap happens in the system screen.

## Project layout

```
app/src/main/java/com/bidule/cascade/
  MainActivity.kt         Entry point, Home-button handling, screen switching
  LauncherViewModel.kt    App list, favorites, reacts to installs/uninstalls
  AppRepository.kt        Loading/launching apps, accent-insensitive search and A–Z letters
  HomeUtils.kt            Default-home detection, remembering and returning to the old launcher
  NotificationService.kt  Notification listener feeding the inline previews
  Prefs.kt                Saved favorites and previous home
  ui/HomeScreen.kt        Clock, favorites, letter panel, search
  ui/AlphabetRail.kt      The draggable A–Z strip
  ui/AppRow.kt            App row with long-press menu and inline notification
  ui/SettingsScreen.kt    Revert, set as home, notification access, favorites order
```
