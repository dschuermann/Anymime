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

class SlotListAdapter(context:Context, messageResourceId:Int, count:Int)
  extends ArrayAdapter[String](context, messageResourceId) {

  private val TAG = "SlotListAdapter"
  private val D = Static.DBGLOG

  var selected = -1

  override def getView(position:Int, setView:View, parentViewGroup:ViewGroup) :View = {
    var view = setView
    if(view == null) {
      if(D) Log.i(TAG, "getView position="+position+" inflate a new view")
      val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
      if(layoutInflater!=null) {
        view = layoutInflater.inflate(messageResourceId, null)
      }
    }

    if(view == null) {
      Log.e(TAG, "getView view==null abort")
      return null
    }

    val commaSeparatedString = getItem(position)
    //if(D) Log.i(TAG, "getView("+position+") view!=null commaSeparatedString="+commaSeparatedString)
    var slotName = ""
    var fileList = "(empty)"
    if(commaSeparatedString!=null && commaSeparatedString.length>0) {
      val idxFirstComma = commaSeparatedString.indexOf(",")
      if(idxFirstComma>=0) {
        slotName = commaSeparatedString.substring(0,idxFirstComma).trim
        fileList = commaSeparatedString.substring(idxFirstComma+1).trim
      }
    }

    val visibleTextView = view.findViewById(R.id.visibleText).asInstanceOf[TextView]
    if(visibleTextView != null) {
      if(slotName!=null && slotName.length>0)
        visibleTextView.setText("Slot "+(position+1)+": "+slotName)
      else
        visibleTextView.setText("Slot "+(position+1))
    }

    val visibleText2View = view.findViewById(R.id.visibleText2).asInstanceOf[TextView]
    if(visibleText2View != null) {
      visibleText2View.setText(fileList)
    }

    val iconView = view.findViewById(R.id.icon).asInstanceOf[ImageView]
    if(iconView != null) {
      if(position==selected)
        iconView.setImageResource(R.drawable.checkmark)
      else
        iconView.setImageResource(R.drawable.empty)
    }

    return view
  }
}

