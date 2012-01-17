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

import android.content.Context
import android.util.Log
import android.view.View
import android.content.SharedPreferences
import android.widget.TextView
import android.widget.ImageView
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils

import android.content.SharedPreferences

class HistoryEntry(var date:Long, var btName:String, var kbs:Long, var arrayOfFiles:Array[String])

class ReceiveFilesHistory() {
  private val TAG = "ReceiveFilesHistory"
  private val D = Static.DBGLOG

  val history_length = 40
  val historyQueue = new scala.collection.mutable.Queue[HistoryEntry]

  private val PREFS_SETTINGS = "org.timur.anymime.historyQueue"
  private var prefSettings:SharedPreferences = null
  private var prefSettingsEditor:SharedPreferences.Editor = null

  def load(context:Context) :Int = {
    // prepare access to prefSettings
    if(prefSettings==null) {
      prefSettings = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_WORLD_WRITEABLE)
      if(prefSettings!=null)
        prefSettingsEditor = prefSettings.edit
    }
    for(i <- 0 until history_length) {
      val commaSeparatedString = prefSettings.getString(""+i, null)
      if(commaSeparatedString!=null) {
        val resultArray = commaSeparatedString split ","
        if(resultArray!=null) {
          val date = java.lang.Long.parseLong(resultArray(0), 10)
          val btName = resultArray(1)
          val kbs = java.lang.Long.parseLong(resultArray(2), 10)
          val arrayOfFiles = resultArray.drop(3)
          val historyEntry = new HistoryEntry(date, btName, kbs, arrayOfFiles)
          //if(D) Log.i(TAG,"load "+i+" historyEntry="+historyEntry)
          historyQueue += historyEntry
          if(historyQueue.size>history_length)
            historyQueue.dequeue
        }
      }
    }
    if(D) Log.i(TAG,"load historyQueue.size="+historyQueue.size)
    return historyQueue.size
  }

  def add(date:Long, btName:String, kbs:Long, arrayOfFiles:Array[String]) :Int = {
    val historyEntry = new HistoryEntry(date, btName, kbs, arrayOfFiles)
    if(D) Log.i(TAG,"add historyEntry="+historyEntry)
    historyQueue += historyEntry
    if(historyQueue.size>history_length)
      historyQueue.dequeue
    if(D) Log.i(TAG,"add historyQueue.size="+historyQueue.size)
    return historyQueue.size
  }

  def historyEntryToCommaSeparatedString(historyEntry:HistoryEntry) :String = {
    var stringBuilder = new StringBuilder()
    stringBuilder append ""+historyEntry.date
    stringBuilder append ","
    stringBuilder append historyEntry.btName
    stringBuilder append ","
    stringBuilder append ""+historyEntry.kbs
    for(filename <- historyEntry.arrayOfFiles) {
      stringBuilder append ","
      stringBuilder append ""+filename
    }
    return stringBuilder.toString
  }

  def store() :Int = {
    // prepare access to prefSettings
    if(prefSettingsEditor==null)
      return -1
    var loop=0
    for(historyEntry <- historyQueue) {
      val storeString = historyEntryToCommaSeparatedString(historyEntry)
      //if(D) Log.i(TAG, "persistArrayList loop="+loop+" storeString="+storeString)
      prefSettingsEditor.putString(""+loop,storeString)
      prefSettingsEditor.commit
      loop+=1
    }
    return loop
  }
}

