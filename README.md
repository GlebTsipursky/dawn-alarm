# Dawn Alarm

A sunrise alarm clock for Android. The screen slowly brightens before the alarm rings, then the alarm sounds and vibrates until you dismiss it or snooze it.

This is a working first version. It has not yet been compiled or run on a phone. The first build will happen on your computer, and you should expect to fix a few small errors on the first try. Tell me the exact error text and I will fix it.

## What you need

1. A computer running Windows, Mac, or Linux.
2. Android Studio, Google's free program for building Android apps. Download it from developer.android.com/studio and install it with the default options.
3. An Android phone and a USB cable that carries data (some cables only charge).

## Step 1: Open the project

1. Open Android Studio.
2. Choose File, then Open. Select the folder named dawn-alarm (the folder containing this README).
3. Wait for the status bar at the bottom to say the project is synced. The first sync downloads several hundred megabytes and can take 10 to 20 minutes.

## Step 2: Turn on developer mode on your phone

Android hides the tools needed to install apps from a computer until you turn on developer mode.

1. On your phone, open Settings, then About phone.
2. Tap Build number seven times. A message says you are now a developer.
3. Go back to Settings, open System (or Settings directly on some phones), then Developer options.
4. Turn on USB debugging.
5. Connect the phone to the computer with the cable. On the phone, tap Allow when asked to trust the computer.

## Step 3: Run the app on your phone

1. At the top of Android Studio, select your phone from the device menu.
2. Click the green triangle (Run). Android Studio builds the app and installs it. The first build can take several minutes.
3. The app opens on your phone. Allow notifications when asked. If the app asks to allow exact alarms, tap Allow, because without it alarms can ring a few minutes late.

## Step 4: Create an installation file for other phones

Use this to share the app without the Google Play Store.

1. In Android Studio, choose Build, then Build App Bundles or APKs, then Build APKs.
2. When the build finishes, click locate in the notification. The file is named app-debug.apk and sits in app/build/outputs/apk/debug/.
3. Send that file to a friend (email, Google Drive, or a file-sharing link). On their phone, they open the file and allow installs from that source when Android asks.

A debug file works for personal and small-group sharing. A public Google Play listing requires a different, signed file, which I can prepare later.

## How the main pieces fit together

The MainActivity file is the list of alarms and the editor. The AlarmStore file saves alarms on the phone. The AlarmScheduler file tells Android when to wake the app. The AlarmReceiver file runs at those times and shows a full-screen notification. The AlarmActivity file is the screen that brightens, plays the sound, and vibrates. The BootReceiver file restores alarms after the phone restarts.

## Known limits of this first version

Some phone makers, including Samsung and Xiaomi, stop background apps aggressively. If an alarm does not ring, open the phone's battery settings for this app and set it to unrestricted or no optimization. The app does not yet have the settings screen from the original app, and it uses the phone's default alarm sound. The sunrise brightness depends on the screen being allowed to brighten, and some phones limit that when battery saver is on.
