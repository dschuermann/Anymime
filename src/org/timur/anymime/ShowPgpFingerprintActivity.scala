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

import java.security.MessageDigest

import scala.collection.mutable.StringBuilder

import android.app.Activity
import android.content.DialogInterface
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.widget.ListView
import android.widget.TextView

class ShowPgpFingerprintActivity extends Activity {
  private val TAG = "ShowPgpFingerprintActivity"
  private val D = true

  override def onCreate(savedInstanceState:Bundle) {
    super.onCreate(savedInstanceState)
    if(D) Log.i(TAG, "onCreate()")
    setContentView(R.layout.show_text_file)

    val intent = getIntent
    if(intent==null) {
      Log.e(TAG, "onCreate() intent==null")
  		Toast.makeText(this, "This activity must be called with an intent set", Toast.LENGTH_LONG).show
      return
    }

    val fileUri = intent.getData
    if(fileUri==null) {
      Log.e(TAG, "onCreate() intent.fileUri==null")
  		Toast.makeText(this, "Path to file not given", Toast.LENGTH_LONG).show
      return
    }

    val filePathNameString = fileUri.getPath
    val fileNameString = fileUri.getLastPathSegment
    val textView = findViewById(R.id.text).asInstanceOf[TextView]
    if(textView!=null) {
      try {
        val source = scala.io.Source.fromFile(filePathNameString, "utf-8")
        val lines = source.mkString
        if(D) Log.i(TAG, "onCreate() lines="+lines)

        val messageDigest = MessageDigest.getInstance("SHA")
        try {
          messageDigest.update(lines.getBytes)
          val byteArray = messageDigest.digest
          if(D) Log.i(TAG, "onCreate() digest byteArray="+byteArray)
          
          val stringBuilder = new StringBuilder()
          for(byte <- byteArray) {
            if(stringBuilder.length>0)
              stringBuilder append " "
            val hexString = "%02X" format (byte & 0xff)
            stringBuilder append hexString
          }
          val bytesAsString = stringBuilder.toString
          if(D) Log.i(TAG, "onCreate() bytesAsString="+bytesAsString)
          textView.setText(bytesAsString)

        } catch {
          case ex:Exception => 
            Log.e(TAG, "onCreate() ex",ex)
            // todo: Toast
        }

        source.close
      } catch {
        case mfinpex:java.nio.charset.MalformedInputException =>
          Log.e(TAG, "onCreate() MalformedInputException",mfinpex)
      		Toast.makeText(this, "File '"+fileNameString+"' is malformed", Toast.LENGTH_LONG).show
        case fnfex:java.io.FileNotFoundException =>
          Log.e(TAG, "onCreate() FileNotFoundException",fnfex)
      		Toast.makeText(this, "File not found '"+fileNameString+"'", Toast.LENGTH_LONG).show
        case ex:java.lang.Exception =>
          Log.e(TAG, "onCreate() Exception",ex)
      		Toast.makeText(this, "Exception on file '"+fileNameString+"'", Toast.LENGTH_LONG).show
      }
    }
  }
}

