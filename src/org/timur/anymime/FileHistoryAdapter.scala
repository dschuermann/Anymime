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
import java.util.Locale
import java.text.DateFormat

import scala.collection.mutable.HashMap

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.ImageView
import android.widget.ArrayAdapter
import android.telephony.TelephonyManager

class FileHistoryAdapter(context:Context, messageResourceId:Int)
  extends ArrayAdapter[String](context, messageResourceId) {

  private val TAG = "FileHistoryAdapter"
  private val D = true

  private var msgList = new ArrayList[String]()
  private val calendar = Calendar.getInstance

  private val curLang = Locale.getDefault.getLanguage
  private val iso3Country = context.getResources.getConfiguration.locale.getISO3Country
  private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE).asInstanceOf[TelephonyManager]
  private val countryCode = telephonyManager.getSimCountryIso
  private var locale = new Locale(curLang,iso3Country)

  // do the following, so one can have english be selected as UI-language and still see date+time in the format of the SIM home-country
  if(countryCode.length>0 && countryCode!=iso3Country)
    locale = new Locale(countryCode,countryCode)
  if(D) Log.i(TAG, "curLang="+curLang+" iso3Country="+iso3Country+" countryCode="+countryCode+" locale="+locale)

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

    //if(D) Log.i(TAG, "getView("+position+") fullString="+fullString)

    val numberView = view.findViewById(R.id.number).asInstanceOf[TextView]
    numberView.setText("")

    val dataView = view.findViewById(R.id.data).asInstanceOf[TextView]
    dataView.setText("")

    val invisibleTextView = view.findViewById(R.id.invisibleText).asInstanceOf[TextView]
    if(invisibleTextView != null) {
      invisibleTextView.setText(fullString)

      val resultArray = fullString split ","
      if(resultArray!=null) {
        val date = java.lang.Long.parseLong(resultArray(0), 10)
        val btName = resultArray(1)
        val kbs = java.lang.Long.parseLong(resultArray(2), 10)
        val arrayOfFiles = resultArray.drop(3)

        calendar.setTimeInMillis(date)
        val gettime = calendar.getTime
        val formattedDateString = DateFormat.getDateInstance(DateFormat.MEDIUM,locale).format(gettime).replaceAll(" ","\u00A0")
        val formattedTimeString = DateFormat.getTimeInstance(DateFormat.MEDIUM,locale).format(gettime).replaceAll(" ","\u00A0")

        numberView.setText(""+(position+1)+".")
        dataView.setText(formattedDateString+" "+formattedTimeString+"   "+arrayOfFiles.size+"\u00A0files   "+kbs+"\u00A0KB/s   "+btName.replaceAll(" ","\u00A0"))
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

