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

import java.util.ArrayList

import android.app.Activity
import android.app.ListActivity
import android.app.AlertDialog
import android.app.AlertDialog.Builder
import android.content.DialogInterface
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.widget.TextView
import android.widget.ImageView
import android.widget.EditText
import android.widget.Button
import android.view.Window
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils

class UploadPgpFilesPopupActivity extends Activity {
  private val TAG = "UploadPgpFilesPopupActivity"
  private val D = true

  private val PREFS_SETTINGS = "org.timur.anymime.settings"
  private var prefSettings:SharedPreferences = null
  private var prefSettingsEditor:SharedPreferences.Editor = null

  private var sshTargetFilenameEditView:EditText = null
  private var sshPathphraseEditView:EditText = null
  private var sshHostnameEditView:EditText = null
  private var sshUsernameEditView:EditText = null
  private var sshTargetPathEditView:EditText = null
  private var sshLocalKeyPathEditView:EditText = null

  private var sshTargetFilename:String = null
  private var sshPathphrase:String = null
  private var sshHostname:String = null
  private var sshUsername:String = null
  private var sshTargetPath:String = null
  private var sshLocalKeyPath:String = null
  private var srcFilePathNameString:String = null
  private var srcFileNameString:String = null

  private var context:Context = null

  override def onCreate(savedInstanceState:Bundle) {
    super.onCreate(savedInstanceState)
    context = this
    if(D) Log.i(TAG, "onCreate()")
    //requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)

    // prepare access to prefSettings
    if(prefSettings==null) {
      prefSettings = getSharedPreferences(PREFS_SETTINGS, Context.MODE_WORLD_WRITEABLE)
      if(prefSettings!=null)
        prefSettingsEditor = prefSettings.edit
    }

    setPgpUploadDialog
    loadSettings

    val intent = getIntent
    if(intent==null) {
      Log.e(TAG, "onCreate() intent==null")
  		Toast.makeText(context, "This activity must be called with an intent set", Toast.LENGTH_LONG).show
      return
    }

    val fileUri = intent.getData
    if(fileUri==null) {
      Log.e(TAG, "onCreate() intent.fileUri==null")
  		Toast.makeText(context, "This activity must be called with intent.getData pointing to a fileUri", Toast.LENGTH_LONG).show
      return
    }

    srcFilePathNameString = fileUri.getPath
    srcFileNameString = fileUri.getLastPathSegment

    sshTargetFilename = ".asc"
    sshPathphrase = ""
    if(sshTargetFilenameEditView!=null)
      sshTargetFilenameEditView.setText(sshTargetFilename)
    if(sshPathphraseEditView!=null)
      sshPathphraseEditView.setText(sshPathphrase)

  }

	override def onBackPressed() {
    if(D) Log.i(TAG, "onBackPressed()")
    storeSettings
    super.onBackPressed
	}


  ////////////////////////////////////////////// private methods

  private def upload() {
    storeSettings

    if(sshTargetFilenameEditView!=null)
      sshTargetFilename = sshTargetFilenameEditView.getText.toString
    if(sshTargetFilename.length<=0 || sshTargetFilename==".asc") {
  		Toast.makeText(context, "Please enter the target filename", Toast.LENGTH_SHORT).show
      return
    }

    if(sshPathphraseEditView!=null)
      sshPathphrase = sshPathphraseEditView.getText.toString
    //if(sshPathphrase.length<=0) {
  	//	Toast.makeText(context, "Please enter the SSH keyphrase", Toast.LENGTH_SHORT).show
    //  return
    //}

    // start ssh upload...

    // before the pgp upload dialog will be shown, a few things _could_ be tested: 
    // do we have internet? can we ping sshHostname?

    setContentView(R.layout.upload_pgp_transmit)
    val radioImageView = findViewById(R.id.busyIndicator).asInstanceOf[ImageView]
    val userHintTextView = findViewById(R.id.userHint).asInstanceOf[TextView]
    val userHint2TextView = findViewById(R.id.userHint2).asInstanceOf[TextView]
    if(userHintTextView!=null)
      userHintTextView.setText("ssh upload "+sshTargetFilename)
    if(userHintTextView!=null)
      userHint2TextView.setText("to "+sshHostname+" "+sshTargetPath+" as "+sshUsername)

    if(D) Log.i(TAG, "onCreate() srcFilePathNameString="+srcFilePathNameString)
    new Thread() {
      override def run() {
        try {
          SshHelper.scp(sshHostname, sshUsername, sshLocalKeyPath, srcFilePathNameString, sshTargetPath, sshTargetFilename)

          context.asInstanceOf[Activity].runOnUiThread(new Runnable() {
            override def run() {
          		Toast.makeText(context, "PGP file was uploaded", Toast.LENGTH_LONG).show
              // stop upload-animation
              radioImageView.setAnimation(null)
              finish
            }
          })
        } catch {
          case ex:Exception =>
            Log.e(TAG, "Exception in sshScp", ex)

            context.asInstanceOf[Activity].runOnUiThread(new Runnable() {
              override def run() {
            		Toast.makeText(context, "PGP file upload error "+ex.getMessage, Toast.LENGTH_LONG).show
                // stop upload-animation
                radioImageView.setAnimation(null)

                setPgpUploadDialog
                loadSettings // todo: this does not work
                if(sshTargetFilenameEditView!=null)
                  sshTargetFilenameEditView.setText(sshTargetFilename)
                if(sshPathphraseEditView!=null)
                  sshPathphraseEditView.setText(sshPathphrase)
              }
            })
        }
      }
    }.start                        
	}

  private def setPgpUploadDialog() {
    setContentView(R.layout.upload_pgp_dialog)
    sshTargetFilenameEditView = findViewById(R.id.sshTargetFilename).asInstanceOf[EditText]
    sshPathphraseEditView = findViewById(R.id.sshPathphrase).asInstanceOf[EditText]
    sshHostnameEditView = findViewById(R.id.sshHostname).asInstanceOf[EditText]
    sshUsernameEditView = findViewById(R.id.sshUsername).asInstanceOf[EditText]
    sshTargetPathEditView = findViewById(R.id.sshTargetPath).asInstanceOf[EditText]
    sshLocalKeyPathEditView = findViewById(R.id.sshLocalKeyPath).asInstanceOf[EditText]

    val cancelButton = findViewById(R.id.cancel).asInstanceOf[Button]
    if(cancelButton!=null) {
      cancelButton.setOnClickListener(new View.OnClickListener() {
        override def onClick(view:View) { 
          if(D) Log.i(TAG, "onClick cancelButton")
          storeSettings
          finish
        } 
      })
    }

    val uploadButton = findViewById(R.id.upload).asInstanceOf[Button]
    if(uploadButton!=null) {
      uploadButton.setOnClickListener(new View.OnClickListener() {
        override def onClick(view:View) { 
          if(D) Log.i(TAG, "onClick uploadButton")
          upload
        } 
      })
    }
	}

  private def loadSettings() {
    if(prefSettings!=null) {
      sshHostname = prefSettings.getString("sshHostname", null)
      if(sshHostnameEditView!=null)
        sshHostnameEditView.setText(sshHostname)

      sshUsername = prefSettings.getString("sshUsername", null)
      if(sshUsernameEditView!=null)
        sshUsernameEditView.setText(sshUsername)

      sshTargetPath = prefSettings.getString("sshTargetPath", null)
      if(sshTargetPath==null || sshTargetPath.length<=0)
        sshTargetPath = "."
      if(sshTargetPathEditView!=null)
        sshTargetPathEditView.setText(sshTargetPath)

      sshLocalKeyPath = prefSettings.getString("sshLocalKeyPath", null) // "/sdcard/id_rsa_openssh"
      if(sshLocalKeyPath==null || sshLocalKeyPath.length<=0)
        sshLocalKeyPath = "/sdcard/id_rsa_openssh"
      if(sshLocalKeyPathEditView!=null)
        sshLocalKeyPathEditView.setText(sshLocalKeyPath)
    }
  }

  private def storeSettings() {
    if(prefSettingsEditor!=null) {
      if(sshHostnameEditView!=null) {
        sshHostname = sshHostnameEditView.getText.toString
        prefSettingsEditor.putString("sshHostname",sshHostname)
      }
      if(sshUsernameEditView!=null) {
        sshUsername = sshUsernameEditView.getText.toString
        prefSettingsEditor.putString("sshUsername",sshUsername)
      }
      if(sshTargetPathEditView!=null) {
        sshTargetPath = sshTargetPathEditView.getText.toString
        prefSettingsEditor.putString("sshTargetPath",sshTargetPath)
      }
      if(sshLocalKeyPathEditView!=null) { 
        sshLocalKeyPath = sshLocalKeyPathEditView.getText.toString
        prefSettingsEditor.putString("sshLocalKeyPath",sshLocalKeyPath)
      }
      prefSettingsEditor.commit
    }
  }
}

