/*
 * This file is part of AnyMime, a program to help you swap files
 * wirelessly between mobile devices.
 *
 * Copyright (C) 2012 Timur Mehrvarz, timur.mehrvarz(a)gmail(.)com
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

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.ImageView
import android.widget.ArrayAdapter
import android.view.LayoutInflater
import android.view.ViewGroup

// DeviceListAdapter is being used by SelectDeviceActivity

class DeviceListAdapter(context:Context, messageResourceId:Int)
  extends ArrayAdapter[String](context, messageResourceId) {

  private val TAG = "DeviceListAdapter"
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
    val fullDeviceInfoString = msgList.get(position)

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

    val invisibleTextView = view.findViewById(R.id.invisibleText).asInstanceOf[TextView]
    if(invisibleTextView != null)
      invisibleTextView.setText(fullDeviceInfoString)

    val idxCR = fullDeviceInfoString.indexOf("\n")

    val visibleTextView = view.findViewById(R.id.visibleText).asInstanceOf[TextView]
    if(visibleTextView != null) {
      var visibleMsg = if(idxCR>=0) fullDeviceInfoString.substring(0,idxCR) else fullDeviceInfoString
      visibleTextView.setText(visibleMsg)
    }

    val visibleText2View = view.findViewById(R.id.visibleText2).asInstanceOf[TextView]
    if(visibleText2View != null) {
      var visibleMsg = if(idxCR>=0) fullDeviceInfoString.substring(idxCR+1) else ""
      visibleText2View.setText(visibleMsg)

      val iconView = view.findViewById(R.id.icon).asInstanceOf[ImageView]
      if(iconView != null) {
        val idxBlank = visibleMsg.indexOf(" ")
        if(idxBlank>=0) {
          val radio = visibleMsg.substring(idxBlank+1)
          if(radio.startsWith("bt")) {
            try {
              iconView.setImageResource(R.drawable.bticon)
            } catch {
              case oomex:java.lang.OutOfMemoryError =>
                Log.e(TAG, "getView OutOfMemoryError on iconView.setImageResource(R.drawable.bticon)")
            }
          } else if(radio.startsWith("wifi")) {
            try {
              iconView.setImageResource(R.drawable.wifiicon)
            } catch {
              case oomex:java.lang.OutOfMemoryError =>
                Log.e(TAG, "getView OutOfMemoryError on iconView.setImageResource(R.drawable.wifiicon)")
            }
          }
        }
      }
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

