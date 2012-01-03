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

import android.content.Context
import android.util.Log
import android.view.View
import android.content.SharedPreferences
import android.widget.TextView
import android.widget.ImageView
import android.widget.ArrayAdapter
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils

// FileListAdapter is being used by ShowReceivedFilesPopupActivity and ShowSelectedFilesActivity 

class FileListAdapter(context:Context, messageResourceId:Int)
  extends ArrayAdapter[String](context, messageResourceId) {

  private val TAG = "FileListAdapter"
  private val D = Static.DBGLOG

  private var msgList = new ArrayList[String]()

  override def clear() {
    msgList.clear
	}
	
  override def getCount() :Int = {
		return msgList.size
	}

  override def getItem(id:Int) :String = {
		return msgList.get(id)
	}

  override def getView(position:Int, setView:View, parentViewGroup:ViewGroup) :View = {
    val pathFileString = msgList.get(position)

    var view = setView
    if(view == null) {
      //if(D) Log.i(TAG, "getView position="+position+" inflate a new view")
      val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
      if(layoutInflater!=null) {
        view = layoutInflater.inflate(messageResourceId, null)
      }
      //if(D) Log.i(TAG, "getView("+position+") view="+view+" from layoutInflater")
    }

    if(view == null) {
      Log.e(TAG, "getView view==null abort")
      return null
    }

    //if(D) Log.i(TAG, "getView("+position+") view!=null")
    val visibleTextView = view.findViewById(R.id.visibleText).asInstanceOf[TextView]
    if(visibleTextView != null) {
      val idxLastSlash = pathFileString.lastIndexOf("/")
      var visibleMsg = if(idxLastSlash>=0) pathFileString.substring(idxLastSlash+1) else pathFileString
      // if the file does not exist (anymore), we add " (not found)"
      val file = new File(pathFileString)
      val fileLength = file.length
      if(D) Log.i(TAG, "getView position="+position+" pathFileString="+pathFileString+" fileLength="+fileLength)
      if(fileLength<1) 
        visibleMsg += " (not\u00A0found)"
      visibleTextView.setText(visibleMsg)

      val iconView = view.findViewById(R.id.icon).asInstanceOf[ImageView]
      if(iconView != null) {
        val idxLastDot = visibleMsg.lastIndexOf(".")
        val ext = if(idxLastDot>=0) visibleMsg.substring(idxLastDot+1) else null

        try {
          iconView.setImageResource(FileTypeMapper.map(ext))
        } catch {
          case oomex:java.lang.OutOfMemoryError =>
            Log.e(TAG, "getView OutOfMemoryError on iconView.setImageResource(FileTypeMapper.map(ext)) ext="+ext)
        }
      }
    }

    val invisibleTextView = view.findViewById(R.id.invisibleText).asInstanceOf[TextView]
    if(invisibleTextView != null)
      invisibleTextView.setText(pathFileString)

    val visibleText2View = view.findViewById(R.id.visibleText2).asInstanceOf[TextView]
    if(visibleText2View != null) {
      val idxLastSlash = pathFileString.lastIndexOf("/")
      var visibleMsg = if(idxLastSlash>=0) pathFileString.substring(0,idxLastSlash+1) else pathFileString
      if(visibleMsg.startsWith("/mnt/sdcard/"))
        visibleMsg = visibleMsg.substring(4)
      visibleText2View.setText(visibleMsg)
    }

    return view
  }

  override def add(msg:String) {
    msgList.add(msg)
  }

  override def remove(msg:String) {
    msgList.remove(msg)
  }
}

