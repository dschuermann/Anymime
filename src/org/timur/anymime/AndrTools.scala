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

import android.util.Log
import android.app.Activity
import android.content.Context
import android.view.View

object AndrTools {
  private val TAG = "AndrTools"
  private val D = true

  def buttonCallback(activity:Activity, resId:Int)(buttonAction:() => Unit) { 
    val button = activity.findViewById(resId)
    buttonCallback(activity:Activity, button)(buttonAction)
  }

  def buttonCallback(activity:Activity, button:View)(buttonAction:() => Unit) { 
    if(D) Log.i(TAG, "buttonCallback button="+button)
    if(button!=null) {
      button.setOnClickListener(new View.OnClickListener() {
        override def onClick(view:View) { 
          if(D) Log.i(TAG, "buttonCallback call buttonAction")
          buttonAction()
        }
      })
    }
  }
}

