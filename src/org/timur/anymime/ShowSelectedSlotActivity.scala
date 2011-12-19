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

import scala.collection.mutable.StringBuilder

import java.util.ArrayList

import android.app.Activity
import android.app.AlertDialog
import android.app.AlertDialog.Builder
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Window
import android.view.View
import android.widget.Toast
import android.widget.ListView
import android.widget.TextView
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
	
object ShowSelectedSlotActivity {
  val MAX_SLOTS = 20
}

class ShowSelectedSlotActivity extends Activity {
  private val TAG = "ShowSelectedSlotActivity"
  private val D = true

  private val PREFS_SETTINGS = "org.timur.anymime.settings"
  private var prefSettings:SharedPreferences = null
  private var prefSettingsEditor:SharedPreferences.Editor = null

  private var context:Context = null
  private var selectedFilesStringArrayList:ArrayList[String] = null
  private var listView:ListView = null
  private var slotListAdapter:SlotListAdapter = null

  private val arraySlots = new Array[ArrayList[String]](ShowSelectedSlotActivity.MAX_SLOTS)
  private var selected = -1

  override def onCreate(savedInstanceState:Bundle) {
    super.onCreate(savedInstanceState)
    if(D) Log.i(TAG, "onCreate()")
    context = this
    setContentView(R.layout.slot_select)

    // prepare access to prefSettings
    if(prefSettings==null) {
      prefSettings = getSharedPreferences(PREFS_SETTINGS, Context.MODE_WORLD_WRITEABLE)
      if(prefSettings!=null)
        prefSettingsEditor = prefSettings.edit
    }

    listView = findViewById(R.id.selectedSlotList).asInstanceOf[ListView]
    if(listView==null) {
      Log.e(TAG, "onCreate R.id.selectedSlotList not available")
      Toast.makeText(this, "R.id.selectedSlotList not available", Toast.LENGTH_LONG).show
      finish
      return
    }

    slotListAdapter = new SlotListAdapter(this, R.layout.slot_list_entry, ShowSelectedSlotActivity.MAX_SLOTS)
		listView.setAdapter(slotListAdapter)

    val selectedSlotString = prefSettings.getString("selectedSlot", null)
    selected = if(selectedSlotString!=null) selectedSlotString.toInt else 0
    if(selected<0 || selected>ShowSelectedSlotActivity.MAX_SLOTS)
      selected = 0
    if(D) Log.i(TAG, "onCreate selected="+selected)
    slotListAdapter.selected = selected

    // read the lists of selected files
    for(slot <- 0 until ShowSelectedSlotActivity.MAX_SLOTS) {
      arraySlots(slot) = new ArrayList[String]
      val slotName = prefSettings.getString("fileSlotName"+slot, "")
      arraySlots(slot) add slotName
      val commaSeparatedString = prefSettings.getString("fileSlot"+slot, null)
      if(commaSeparatedString!=null && commaSeparatedString.size>0)
        putSlot(arraySlots(slot), commaSeparatedString)
      //if(D) Log.i(TAG, "onCreate slot="+slot+" commaSeparatedString="+commaSeparatedString+" size="+arraySlots(slot).size+" arraySlots(slot)="+arraySlots(slot))

      val commaSeparatedString2 = arrayToCommaSeparatedString(arraySlots(slot), true)  // includes slot name at zero position
      if(commaSeparatedString2!=null && commaSeparatedString2.size>0) {
        if(D) Log.i(TAG, "onCreate slot="+slot+" commaSeparatedString2="+commaSeparatedString2+" slotName=["+slotName+"]")
        slotListAdapter.add(commaSeparatedString2)
      }
    }

    slotListAdapter.add("")

    slotListAdapter.notifyDataSetChanged

    listView.setOnItemClickListener(new OnItemClickListener() {
      override def onItemClick(adapterView:AdapterView[_], view:View, position:Int, id:Long) {
        // user has clicked into the conversation view
        if(D) Log.i(TAG, "onClick position="+position+" selected="+selected)
        if(position!=selected) {
          selected = position
          slotListAdapter.selected = selected
          slotListAdapter.notifyDataSetChanged
          prefSettingsEditor.putString("selectedSlot",""+selected)
          prefSettingsEditor.commit
          if(D) Log.i(TAG, "onClick position="+position+" selected="+selected+" arraySlots(selected).size="+arraySlots(selected).size)
        }
    		setActivityResponse
    		finish
      }
    })
  }

  private def putSlot(arrayList:ArrayList[String], commaSeparatedString:String) {
    if(commaSeparatedString!=null) {
      val resultArray = commaSeparatedString split ","
      if(resultArray!=null) {
        if(D) Log.i(TAG,"onCreate putSlot resultArray.size="+resultArray.size)
        for(filePathString <- resultArray) {
          if(filePathString!=null) {
            if(D) Log.i(TAG,"onCreate putSlot add filePathString.trim=["+filePathString.trim+"]")
            arrayList add filePathString.trim
          }
        }
      }
    }
  }

  private def arrayToCommaSeparatedString(arrayList:ArrayList[String], onlyFilename:Boolean=false) :String = {
    val stringBuilder = new StringBuilder()
    var loop=0
    val iterator = arrayList.iterator 
    while(iterator.hasNext) {
      val fullPath = iterator.next
      if(fullPath!=null) {
        if(loop>0)
          stringBuilder append ", "
        if(onlyFilename) {
          val idx = fullPath.lastIndexOf("/")
          val fileName = if(idx>=0) fullPath.substring(idx+1) else fullPath
          stringBuilder append fileName.trim
        } else {
          stringBuilder append fullPath.trim
        }
        loop+=1
      }
    }
    return stringBuilder.toString
  }

	override def onBackPressed() {
    if(D) Log.i(TAG, "onBackPressed()")
		setActivityResponse
    super.onBackPressed
	}
	
	private def setActivityResponse() {
		setResult(Activity.RESULT_OK)
	}
}

