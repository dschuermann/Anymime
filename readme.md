Anymime for Android
===================

Anymime is a NFC/Bluetooth Tap to share application for Android v2.2+

It allows you to easily and quickly establish ad-hoc two-way file exchange sessions.


Info
----

Download Android apk: [https://market.android.com/details?id=org.timur.anymime](https://market.android.com/details?id=org.timur.anymime)

App home page: [http://timur.mobi/anymime/](http://timur.mobi/anymime/)

Copyright (C) 2011 Timur Mehrvarz, timur.mehrvarz(a)gmail(.)com

Source code is licensed under the GNU General Public License, Version 3:

3rd party components being used

- Google protobuf

- Teambox file-icons
  https://github.com/teambox/Free-file-icons

- FileDialog
  https://code.google.com/p/android-file-dialog/

Prepare to build
----------------

Building requires Android SDK version-11, JDK 6, Scala 2.8.1 + Ant 1.8.2

$ cp local.properties.sample local.properties

- Adjust SDK path settings in "local.properties"
  (Do not commit "local.properties" back to git)

Building from source
--------------------

$ ./make

This will build a debug version of the application in bin/Anymime-debug.apk.
View the logs at runtime using this:

$ adb logcat |grep -E "Any|RFComm|Show|BtDevice|Popup|nfc|Nfc|Bluetooth|SshHelper|AndroidRuntime|E/|DEBUG"


