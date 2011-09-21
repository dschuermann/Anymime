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
import java.io.File

import android.app.Activity
import android.app.ListActivity
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Window
import android.view.View
import android.widget.CheckBox
import android.widget.ArrayAdapter
import android.widget.Toast
import android.widget.ListView
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.webkit.MimeTypeMap

class ShowReceivedFilesPopupActivity extends ListActivity {
  private val TAG = "ShowReceivedFilesPopupActivity"
  private val D = true
  @volatile private var userInteractionCount:Int = 0
  private var otherName:String = null
  private var sendKeyFilePath:String = null

  override def onCreate(savedInstanceState:Bundle) {
    super.onCreate(savedInstanceState)
    if(D) Log.i(TAG, "onCreate()")
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)

    val intent = getIntent
    if(intent==null) {
      Log.e(TAG, "onCreate() This activity must be called with an intent set")
  		Toast.makeText(this, "This activity must be called with an intent set", Toast.LENGTH_LONG).show
  		finish
      return
    }
    val bundle = intent.getExtras
    if(bundle==null) {
      Log.e(TAG, "onCreate() This activity must be called with an intent containing a bundle")
  		Toast.makeText(this, "This activity must be called with an intent set containing a bundle", Toast.LENGTH_LONG).show
  		finish
      return
    }

    val receivedFilesUriStringArrayList = bundle.getStringArrayList("listOfUriStrings")
    if(receivedFilesUriStringArrayList==null) {
      Log.e(TAG, "onCreate() from 'listOfUriStrings' receivedFilesUriStringArrayList==null")
  		Toast.makeText(this, "No received files available", Toast.LENGTH_LONG).show
  		finish
      return
    }
    if(receivedFilesUriStringArrayList.size<1) {
      Log.e(TAG, "onCreate() receivedFilesUriStringArrayList.size<1")
  		Toast.makeText(this, "No received files available", Toast.LENGTH_LONG).show
  		finish
      return
    }

    val fileListAdapter = new FileListAdapter(this, R.layout.file_list_entry)
		setListAdapter(fileListAdapter)

    val iterator = receivedFilesUriStringArrayList.iterator 
    while(iterator.hasNext) {
      var filePathString = iterator.next 
      if(filePathString.startsWith("file:"))
        filePathString = filePathString.substring(5)
      fileListAdapter.add(filePathString)
    }
    fileListAdapter.notifyDataSetChanged

    sendKeyFilePath = bundle.getString("sendKeyFile")

    val opentype = bundle.getString("opentype")
    if(opentype!=null && opentype=="auto") {
      // auto close activity after 10 seconds timeout
      new Thread() {
        override def run() {
          try { Thread.sleep(15000); } catch { case ex:Exception => }
          if(userInteractionCount==0)
            finish
        }
      }.start                        

      otherName = bundle.getString("otherName")
    }
  }

	override def onListItemClick(listView:ListView, view:View, position:Int, id:Long) :Unit = {
		super.onListItemClick(listView, view, position, id);

    userInteractionCount+=1

		// Get the item that was clicked
		val obj = getListAdapter.getItem(position)
		if(obj==null) {
      Log.e(TAG, "onListItemClick position="+position+" getListAdapter.getItem(position)=null")
		  return
		}

    // process fileUriString
		var fileUriString = obj.toString
		if(fileUriString.startsWith("file:"))
		  fileUriString = fileUriString.substring(5)
    if(D) Log.i(TAG, "onListItemClick fileUriString="+fileUriString)
    try {
      val selectedUri = Uri.fromFile(new File(fileUriString))
      if(D) Log.i(TAG, "onListItemClick fileUriString="+fileUriString+" selectedUri="+selectedUri)
      val processFileIntent = new Intent(Intent.ACTION_VIEW)
      val fileUriStringLower = fileUriString.toLowerCase
      val lastIdxOfDot = fileUriStringLower.lastIndexOf(".")
      val extension = if(lastIdxOfDot>=0) fileUriStringLower.substring(lastIdxOfDot+1) else null
      if(extension!=null) {
        val mimeTypeMap = MimeTypeMap.getSingleton()
        var mimeTypeFromExtension = mimeTypeMap.getMimeTypeFromExtension(extension)
        if(extension=="asc") mimeTypeFromExtension="application/pgp"
        if(D) Log.i(TAG, "onListItemClick extension="+extension+" mimeType="+mimeTypeFromExtension)
        processFileIntent.setDataAndType(selectedUri,mimeTypeFromExtension)

      } else {
        if(D) Log.i(TAG, "onListItemClick extension=null mimeType=*/*")
        processFileIntent.setDataAndType(selectedUri,"*/*")
      }

      val bundle = new Bundle()

      // hint the name of the bt device, may for example be used as the default filename when uploading a pgp file
      bundle.putString("anymimeOtherName", otherName)

      // hand over .asc file from most recent delivery
      if(sendKeyFilePath!=null)
        bundle.putString("sendKeyFile", sendKeyFilePath)

      processFileIntent.putExtras(bundle)
      if(D) Log.i(TAG, "onListItemClick startActivity processFileIntent="+processFileIntent)
      startActivity(Intent.createChooser(processFileIntent,"Apply action..."))

    } catch {
      case ex:Exception =>
        Log.e(TAG, "onListItemClick startActivity fileUriString="+fileUriString,ex)
        val errMsg = ex.getMessage
        Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show
    }
	}
}

