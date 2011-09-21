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
import java.util.Calendar

import scala.collection.mutable.HashMap

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

class FileHistoryAdapter(context:Context, messageResourceId:Int)
  extends ArrayAdapter[String](context, messageResourceId) {

  private val TAG = "FileHistoryAdapter"
  private val D = true

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
    val fullString = msgList.get(position)

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

    if(D) Log.i(TAG, "getView("+position+") fullString="+fullString)

    val dateView = view.findViewById(R.id.date).asInstanceOf[TextView]
    if(dateView != null)
      dateView.setText("")

    val nameView = view.findViewById(R.id.name).asInstanceOf[TextView]
    if(nameView != null)
      nameView.setText("")

    val kbsView = view.findViewById(R.id.kbs).asInstanceOf[TextView]
    if(kbsView != null)
      kbsView.setText("")

    val invisibleTextView = view.findViewById(R.id.invisibleText).asInstanceOf[TextView]
    if(invisibleTextView != null) {
      invisibleTextView.setText(fullString)

      val resultArray = fullString split ","
      if(resultArray!=null) {
        val date = java.lang.Long.parseLong(resultArray(0), 10)
        val btName = resultArray(1)
        val kbs = java.lang.Long.parseLong(resultArray(2), 10)
        //val arrayOfFiles = resultArray.drop(3)

        if(dateView != null) {
          val calendar = Calendar.getInstance()
          calendar.setTimeInMillis(date)
          //val date = cal.getTime()
          //mHour = date.getHours();
          //mMinute = date.getMinutes();

          dateView.setText(""+calendar.toString)
        }

        if(nameView != null)
          nameView.setText(btName)

        if(kbsView != null)
          kbsView.setText(""+kbs)
      }
    }

    return view
  }
}

