/*
 * This file is part of AnyMime, a program to help you swap files
 * wirelessly between mobile devices.
 *
 * Copyright (C) 2011 Timur Mehrvarz, timur.mehrvarz(a)gmail(.)com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.timur.anymime

import java.util.Properties
import java.io.File
import java.io.FileInputStream

import android.util.Log
import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable

object FileTypeMapper {
  private val TAG = "FileTypeMapper"
  private val D = Static.DBGLOG

  def map(ext:String) :Int = {
    ext match {
      case "3gp"  => R.drawable.three_gp
      case "3gpp" => R.drawable.three_gp
      case "aac"  => R.drawable.aac
      case "apk"  => R.drawable.apk
      case "asc"  => R.drawable.asc
      case "avi"  => R.drawable.avi
      case "bmp"  => R.drawable.bmp
      case "dmg"  => R.drawable.dmg
      case "doc"  => R.drawable.doc
      case "eps"  => R.drawable.eps
      case "flv"  => R.drawable.flv
      case "gif"  => R.drawable.gif
      case "html" => R.drawable.html
      case "iso"  => R.drawable.iso
      case "jpg"  => R.drawable.jpg
      case "key"  => R.drawable.key
      case "mid"  => R.drawable.mid
      case "mp3"  => R.drawable.mp3
      case "mp4"  => R.drawable.mp4
      case "mpg"  => R.drawable.mpg
      case "pdf"  => R.drawable.pdf
      case "png"  => R.drawable.png
      case "ppt"  => R.drawable.ppt
      case "rar"  => R.drawable.rar
      case "tgz"  => R.drawable.tgz
      case "wav"  => R.drawable.wav
      case "xml"  => R.drawable.xml
      case "zip"  => R.drawable.zip

      case _      => R.drawable.icon
    }
  } 
}

