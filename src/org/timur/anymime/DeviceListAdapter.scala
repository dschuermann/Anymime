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
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import android.widget.ImageView
import android.widget.ArrayAdapter
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AlphaAnimation

import org.timur.rfcomm._

// DeviceListAdapter is being used by SelectDeviceActivity

class DeviceListAdapter(context:Context, messageResourceId:Int)
  extends ArrayAdapter[String](context, messageResourceId) {

  private val TAG = "DeviceListAdapter"
  private val D = true //Static.DBGLOG
  
  //private val messageResourceId = R.layout.device_list_entry

  private val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
  private val drawableWireless96 = context.getResources().getDrawable(R.drawable.wireless96)

  private var nameList = new ArrayList[String]()
  private var addrList = new ArrayList[String]()
  private var otherList = new ArrayList[String]()
  private var discoveredOnList = new ArrayList[Long]()
  @volatile private var activityDestroyed = false


  override def clear() {
    nameList.clear
    addrList.clear
    otherList.clear
    discoveredOnList.clear
	}

  override def getCount() :Int = {
		return addrList.size
	}

  override def getView(position:Int, setView:View, parentViewGroup:ViewGroup) :View = {
    var view = setView
    if(view == null) {
      //if(D) Log.i(TAG, "getView position="+position+" inflate a new view")
      if(layoutInflater!=null)
        view = layoutInflater.inflate(messageResourceId, null)
      //if(D) Log.i(TAG, "getView("+position+") view="+view+" from layoutInflater")
    }

    if(view == null) {
      Log.e(TAG, "getView view==null abort")
      return null
    }

    val visibleTextView = view.findViewById(R.id.visibleText).asInstanceOf[TextView]
    if(visibleTextView != null)
      visibleTextView.setText(nameList.get(position))

    val visibleText2View = view.findViewById(R.id.visibleText2).asInstanceOf[TextView]
    if(visibleText2View != null) {
      visibleText2View.setText(addrList.get(position))

      val iconView = view.findViewById(R.id.icon).asInstanceOf[ImageView]
      if(iconView != null) {
        val radio = otherList.get(position)
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

        if(android.os.Build.VERSION.SDK_INT>10) {
          val idxBlank = radio.indexOf(" ")
          if(idxBlank>=0) {
            val discovered = radio.substring(idxBlank+1)
            if(discovered.startsWith("discovered")) {
              otherList.set(position,radio.substring(0,idxBlank)) // we remove "discovered" so a redraw does not hit again

              var discoveredOn = discoveredOnList.get(position)
              if(D) Log.i(TAG, "getView("+position+") prolong current icon visibility time, new discoveredOn="+(discoveredOn+1))
              discoveredOnList.set(position,SystemClock.uptimeMillis+25000)  // prolong current icon visibility time
              if(discoveredOn>0) {
                // discovered icon was already on, do nothing, leave it on
                return view
              }

              val iconDiscovered = view.findViewById(R.id.iconDiscovered).asInstanceOf[ImageView]
              if(iconDiscovered!=null) {
                try {
                  iconDiscovered.setImageDrawable(drawableWireless96)
                } catch {
                  case oomex:java.lang.OutOfMemoryError =>
                    Log.e(TAG, "getView OutOfMemoryError on iconView.setImageResource(R.drawable.iconDiscovered)")
                }
              }

            	iconDiscovered.setVisibility(View.VISIBLE)

              new Thread() {
                override def run() {
                  do {
                    try { Thread.sleep(2000) } catch { case ex:Exception => }
                    // todo: maybe parent activity was ended?
                  } while(discoveredOnList.get(position) > SystemClock.uptimeMillis && !activityDestroyed)

                  if(!activityDestroyed) {
                    discoveredOnList.set(position,0)

                    AndrTools.runOnUiThread(context) { () =>
                    	iconDiscovered.setVisibility(View.INVISIBLE)
                      //notifyDataSetChanged
                    }
                    if(D) Log.i(TAG, "getView("+position+") turnOff after sleep")
                  }
                }
              }.start                        
            } //todo else if(discovered.startsWith("stored")) {
              //todo else if(discovered.startsWith("paired")) {
          }
        }
      }
    }

    val invisibleTextView = view.findViewById(R.id.invisibleText).asInstanceOf[TextView]
    if(invisibleTextView != null)
      invisibleTextView.setText(otherList.get(position))

    return view
  }

  override def add(msg:String) {
    val idxCR = msg.indexOf("\n")
    val name = if(idxCR>=0) msg.substring(0,idxCR) else msg
    val rest = if(idxCR>=0) msg.substring(idxCR+1) else null
    if(rest!=null) {
      val idxBlank = rest.indexOf(" ")
      val addr = if(idxBlank>=0) rest.substring(0,idxBlank) else rest
      if(addr!=null) {
        val other = rest.substring(idxBlank+1)
        val idxInArray = addrList.indexOf(addr)
        if(idxInArray>=0) {
          // replace
          //if(D) Log.i(TAG, "replace idxInArray="+idxInArray+" addr="+addr+" name="+name+" other="+other)
          nameList.set(idxInArray,name)
          otherList.set(idxInArray,other)
        } else {
          // add
          //if(D) Log.i(TAG, "add addr="+addr+" name="+name+" other="+other)
          nameList.add(name)
          addrList.add(addr)
          otherList.add(other)
          discoveredOnList.add(0)
        }
      }
    }
  }

  def exit() {
    activityDestroyed = true
  }
}

