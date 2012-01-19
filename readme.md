Anymime 2.0
===========

Anymime is a two-way wireless tap-to-share filetransfer application for Android 2.2 - 4.0

Info
----

Download Android apk: [https://market.android.com/details?id=org.timur.anymime](https://market.android.com/details?id=org.timur.anymime)

App home page: [http://timur.mobi/anymime/](http://timur.mobi/anymime/)

Features:

- API 08 Android 2.2 - Bluetooth Filetransfer
- API 10 Android 2.3.3 - NFC tap-to-share
- API 14 Android 4.0 - Pairless Bluetooth

Source code is licensed under the GNU General Public License, Version 3:

Copyright (C) 2012 timur.mehrvarz(a)gmail(.)com

3rd party components being used

- JCraft JSch

- Bouncycastele

- Google protobuf

- Teambox file-icons
  https://github.com/teambox/Free-file-icons

- FileDialog
  https://code.google.com/p/android-file-dialog/

- Scala, Ant, Proguard

Prepare to build
----------------

You need: Android SDK version-11, JDK 6, Scala 2.8.1 + Ant 1.8.2

$ cp local.properties.sample local.properties

Adjust path settings in "local.properties". Done.

Building from source
--------------------

$ ./make

This will build a debug version of the application in bin/Anymime.apk.

All info: [http://timur.mobi/anymime/](http://timur.mobi/anymime/)

