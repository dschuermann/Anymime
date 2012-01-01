/*
 * This file is part of AnymimeKSP, a program to help you swap key files
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

import java.io.File

import scala.collection.mutable.StringBuilder

import android.app.Activity
import android.content.DialogInterface
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import android.widget.ListView
import android.widget.TextView
import android.view.Window
import android.view.View
import android.provider.MediaStore
import android.net.Uri
import android.content.res.Configuration

import org.timur.rfcomm._

class ShowPgpFingerprintActivity extends Activity {
  private val TAG = "ShowPgpFingerprintActivity"
  private val D = Static.DBGLOG
  private var context:Context = null
  private var currentOrientation:Int = -1   // 0=landscape, 1=portrait

  override def onCreate(savedInstanceState:Bundle) {
    super.onCreate(savedInstanceState)
    context = this

    val intent = getIntent
    if(intent==null) {
      Log.e(TAG, "onCreate() intent==null")
  		Toast.makeText(this, "This activity must be called with an intent set", Toast.LENGTH_LONG).show
      finish
      return
    }

    val fileUri = intent.getData
    if(fileUri==null) {
      Log.e(TAG, "onCreate() intent.fileUri==null")
  		Toast.makeText(this, "Path to file not given", Toast.LENGTH_LONG).show
      finish
      return
    }

    if(D) Log.i(TAG, "onCreate()")
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.show_fingerprint)

    val filePathNameString = fileUri.getPath
    val fileNameString = fileUri.getLastPathSegment

    val textReceivedFingerprintView = findViewById(R.id.textReceivedFingerprint).asInstanceOf[TextView]
    if(textReceivedFingerprintView==null) {
      Log.e(TAG, "onCreate() textReceivedFingerprintView==null (for R.id.textReceivedFingerprint)")
      finish
      return
    }
    val textSentFingerprintView = findViewById(R.id.textSentFingerprint).asInstanceOf[TextView]
    if(textSentFingerprintView==null) {
      Log.e(TAG, "onCreate() textSentFingerprintView==null (for R.id.textSentFingerprint)")
      finish
      return
    }

    val receivedKeyFingerprint = showFileFingerprint(textReceivedFingerprintView, filePathNameString, fileNameString)
    
    var sentKeyFingerprint:String = null
    var displayingSendKey=false
    val bundle = intent.getExtras
    if(bundle!=null) {
      val sendKeyFilePath = bundle.getString("sendKeyFile")
      if(sendKeyFilePath!=null) {
        sentKeyFingerprint = showFileFingerprint(textSentFingerprintView, sendKeyFilePath, null)
        displayingSendKey=true
      }
    }
    if(D) Log.i(TAG, "displayingSendKey="+displayingSendKey)
    if(!displayingSendKey) {
      findViewById(R.id.titleReceivedFingerprint).asInstanceOf[TextView].setText("Selected key fingerprint")
      findViewById(R.id.titleSentFingerprint).asInstanceOf[TextView].setVisibility(View.GONE)
      findViewById(R.id.textSentFingerprint).asInstanceOf[TextView].setVisibility(View.GONE)
    }

    AndrTools.buttonCallback(this, R.id.verification) { () =>
      //if(D) Log.i(TAG, "onClick verification sentKeyFingerprint="+sentKeyFingerprint+" receivedKeyFingerprint="+receivedKeyFingerprint)
      val intent = new Intent(context, classOf[CameraPreview])
      val bundle = new Bundle()
      bundle.putString("sentKeyFingerprint", sentKeyFingerprint)
      bundle.putString("receivedKeyFingerprint", receivedKeyFingerprint)
      val getOrient = getWindowManager.getDefaultDisplay
      currentOrientation = getOrient.getOrientation
      if(currentOrientation==Configuration.ORIENTATION_UNDEFINED) {
        val configuration = getResources.getConfiguration
        currentOrientation = configuration.orientation
        if(currentOrientation==Configuration.ORIENTATION_UNDEFINED) {
          //if height and widht of screen are equal then
          // it is square orientation
          if(getOrient.getWidth==getOrient.getHeight) {
            currentOrientation = Configuration.ORIENTATION_SQUARE
          } else if(getOrient.getWidth < getOrient.getHeight) {
            currentOrientation = Configuration.ORIENTATION_PORTRAIT
          } else {
            currentOrientation = Configuration.ORIENTATION_LANDSCAPE
          }
        }
      }
      //if(D) Log.i(TAG, "CALL CAMERAPREVIEW currentOrientation="+currentOrientation)
      bundle.putInt("orientation",currentOrientation)
      intent.putExtras(bundle)
      startActivity(intent)
    }
  }

  override def onResume() {
    super.onResume()
    if(currentOrientation>=0) {
      if(D) Log.i(TAG, "onResume setRequestedOrientation="+currentOrientation)
      setRequestedOrientation(currentOrientation)
    }
    if(D) Log.i(TAG, "onResume done")
  }

  private def showFileFingerprint(textView:TextView, filePathName:String, fileNameString:String) :String = {
    if(D) Log.i(TAG, "showFileFingerprint filePathName="+filePathName+" textView="+textView)
    if(textView!=null) {
      try {
        // generate PGP fingerprint
        val source = scala.io.Source.fromFile(filePathName, "utf-8")
        if(source!=null) {
          // ripping off pgp header + footer
          val fileLines = source.getLines
          val fileFiltered =
            for {
              line <- fileLines
              if line.length>0
              if !line.startsWith("-----BEGIN")
              if !line.startsWith("Version:")
              if !line.startsWith("=")
              if !line.startsWith("-----END")
            } yield line
          val fileAsString = fileFiltered.toList.mkString
          if(D) Log.i(TAG, "showFileFingerprint filePathName="+filePathName+" fileAsString="+fileAsString)

          val fingerprintString =
            if(fileAsString.length>0) {
              val fileAsStringBase64 = android.util.Base64.decode(fileAsString,android.util.Base64.DEFAULT)
              val pgpObjectFactory = new org.bouncycastle.openpgp.PGPObjectFactory(fileAsStringBase64)
              val pgpPublicKeyRing = pgpObjectFactory.nextObject.asInstanceOf[org.bouncycastle.openpgp.PGPPublicKeyRing]
              val pgpPublicKey = pgpPublicKeyRing.getPublicKey
              val byteArray = pgpPublicKey.getFingerprint
              textView.setText(getBytesAsFormattedString(byteArray))
              getBytesAsString(byteArray)

            } else {
              textView.setText("---")
              "---"
            }

          source.close
          if(D) Log.i(TAG, "showFileFingerprint fingerprintString="+fingerprintString)
          return fingerprintString
        }


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
    return null
  }

  private def getBytesAsString(byteArray:Array[Byte]) :String = {
    val stringBuilder = new StringBuilder()
    if(byteArray!=null) {
      for(byte <- byteArray) {
        val hexString = "%02X" format (byte & 0xff)
        stringBuilder append hexString
      }
    }
    return stringBuilder.toString
  }

  private def getBytesAsFormattedString(byteArray:Array[Byte]) :String = {
    val stringBuilder = new StringBuilder()
    if(byteArray!=null) {
      var count=0
      for(byte <- byteArray) {
        if(count>0 && count%4==0)
          stringBuilder append "\n"
        else
        if(stringBuilder.length>0)
          stringBuilder append " "
        val hexString = "%02X" format (byte & 0xff)
        stringBuilder append hexString
        count += 1
      }
    }
    return stringBuilder.toString
  }
}

