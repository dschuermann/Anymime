Anymime for Android
===================

Anymime is a NFC/Bluetooth Tap to share application for Android v2.2+

It allows you to easily and quickly establish ad-hoc two-way file exchange sessions.


Info
----

Download Android apk: [https://market.android.com/details?id=org.timur.anymime](https://market.android.com/details?id=org.timur.anymime)

App home page: [http://timur.mobi/anymime/](http://timur.mobi/anymime/)

Copyright (C) 2011 Timur Mehrvarz

Source code is licensed under the GNU General Public License, Version 3:

3rd party components being used

- Google prototype

- JQuest jsch + jzlib

- Teambox file-icons
  https://github.com/teambox/Free-file-icons

- FileDialog
  https://code.google.com/p/android-file-dialog/

Initial adjustments
-------------------

Building requires Android SDK version-8 "Froyo", JDK 6 and Ant 1.8.2.

$ cp local.properties.sample local.properties

- Adjust SDK path settings in "local.properties"

Building from source
--------------------

$ ./make

This will build a debug version of the application in bin/Anymime-debug.apk

Installation and remote execution
---------------------------------

$ ./deploy

This will install (replace) the app on your Android device and remote execute it.

Viewing the logs at runtime
---------------------------

$ adb logcat |grep -E "Anymime|RFComm|AndroidRuntime|Show|BtDevice|Popup|nfc|Nfc|Bluetooth|SshHelper|E/|DEBUG"


