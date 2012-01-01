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
import android.view.Window
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import android.widget.ListView

class ShowReceivedFilesHistoryActivity extends ListActivity {
  private val TAG = "ShowReceivedFilesHistoryActivity"
  private val D = Static.DBGLOG

  private val receiveFilesHistory = new ReceiveFilesHistory()
  private var receiveFilesHistoryLength=0
  private var context:Context = null

  @volatile private var userInteractionCount:Int = 0
  private var otherName:String = null
  private var sendKeyFilePath:String = null

  override def onCreate(savedInstanceState:Bundle) {
    super.onCreate(savedInstanceState)
    if(D) Log.i(TAG, "onCreate()")
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
    context = this

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

    getListView().setStackFromBottom(true)

    val fileHistoryAdapter = new FileHistoryAdapter(this, R.layout.file_history_entry)
		setListAdapter(fileHistoryAdapter)

    receiveFilesHistoryLength = receiveFilesHistory.load(context)
    for(historyEntry <- receiveFilesHistory.historyQueue) {
      val historyEntryString = receiveFilesHistory.historyEntryToCommaSeparatedString(historyEntry)
      if(D) Log.i(TAG, "onCreate() historyEntryString="+historyEntryString)
      fileHistoryAdapter.add(historyEntryString)
    }
    fileHistoryAdapter.notifyDataSetChanged

    sendKeyFilePath = bundle.getString("sendKeyFile")
  }

	override def onListItemClick(listView:ListView, view:View, position:Int, id:Long) :Unit = {
		super.onListItemClick(listView, view, position, id)

		// Get the item that was clicked
		val obj = getListAdapter.getItem(position)
		if(obj==null) {
      Log.e(TAG, "onListItemClick position="+position+" getListAdapter.getItem(position)=null")
		  return
		}

		val historyEntryString = obj.toString
    if(D) Log.i(TAG, "onListItemClick historyEntryString="+historyEntryString)

    // process historyEntryString = date,btname,kbs,listOfFiles...
    val resultArray = historyEntryString split ","
    val fileUriArray = resultArray drop 3

    val fileStringsArrayList = new ArrayList[String]()
    if(fileUriArray!=null) {
      for(filePathString <- fileUriArray)
        if(filePathString!=null)
          fileStringsArrayList add filePathString.trim

      val intent = new Intent(context, classOf[ShowReceivedFilesPopupActivity])
      val bundle = new Bundle()
      bundle.putString("date", resultArray(0))
      bundle.putString("name", resultArray(1))
      bundle.putString("kbs", resultArray(2))
      bundle.putStringArrayList("listOfUriStrings", fileStringsArrayList)
      bundle.putString("sendKeyFile", sendKeyFilePath)
      intent.putExtras(bundle)
      startActivity(intent)
    }
	}
}

