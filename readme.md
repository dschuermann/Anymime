Anymime 2.0
===========

Anymime is a two-way wireless tap-to-share file transfer application for Android 2.2 - 4.0

Info
----

Download Android apk: [https://market.android.com/details?id=org.timur.anymime](https://market.android.com/details?id=org.timur.anymime)

App home page: [http://timur.mobi/anymime/](http://timur.mobi/anymime/)

Feature set:

- Android 2.2 / API 08:

  Bluetooth Filetransfer
  Multiple transfer lists can be predefined
  Camera verification of received PGP keys

- Android 2.3.3 / API 10:

  NFC tap-to-share

- Android 4.0 / API 14: 

  Pairless Bluetooth

Source code is licensed under the GNU General Public License, Version 3

Copyright (C) 2012 timur.mehrvarz at gmail dot com

3rd party components being used:

- JCraft JSch

- Bouncycastele

- Google protobuf

- Teambox file-icons
  https://github.com/teambox/Free-file-icons

- FileDialog
  https://code.google.com/p/android-file-dialog/

- Scala, Ant, Proguard

Building the source
-------------------

You need: Android SDK version-14, JDK 6, Scala 2.8.1, Ant 1.8.2

$ cp local.properties.sample local.properties

Adjust path settings in "local.properties".

$ ./make

This will build a debug version of the application in bin/Anymime.apk.

More info: [http://timur.mobi/anymime/](http://timur.mobi/anymime/)


