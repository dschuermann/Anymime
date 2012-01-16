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

import android.app.Activity
import android.app.ListActivity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import android.widget.ListView
import android.media.MediaPlayer

import android.widget.TextView
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener

import org.timur.rfcomm._

class SelectDeviceActivity extends Activity {

  private val TAG = "SelectDeviceActivity"
  private val D = Static.DBGLOG

  private val REQUEST_BT_SETTINGS = 1

  private var activity:Activity = null
  //private var activityDestroyed = false
  private var rfCommHelper:RFCommHelper = null
  private var mediaMiniAlert:MediaPlayer = null
  private var deviceListAdapter:DeviceListAdapter = null

  override def onCreate(savedInstanceState:Bundle) {
    super.onCreate(savedInstanceState)
    if(D) Log.i(TAG, "onCreate")
    activity = this
    //activityDestroyed = false

    val anyMimeApp = getApplication.asInstanceOf[AnyMimeApp]
    if(anyMimeApp==null) {
      val errMsg = "getApplication returns null"
      Log.e(TAG, "onCreate "+errMsg)
      Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show
      return
    }

    rfCommHelper = anyMimeApp.rfCommHelper
    if(rfCommHelper==null) {
      val errMsg = "anyMimeApp.rfCommHelper == null"
      Log.e(TAG, "onCreate "+errMsg)
      Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show
      return
    }
    // good, we got access to rfCommHelper
    // todo tmtmtm: yeah but rfCommHelper now got state=onPause from parent activity

    setContentView(R.layout.select_device)

    val listView = findViewById(R.id.listView).asInstanceOf[ListView]
    if(listView==null) {
      val errMsg = "no access to listView"
      Log.e(TAG, "onCreate "+errMsg)
      Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show
      return
    }

    deviceListAdapter = new DeviceListAdapter(this, R.layout.device_list_entry)
		listView.setAdapter(deviceListAdapter)
    mediaMiniAlert = MediaPlayer.create(this, R.raw.confirm8bit)
    rfCommHelper.addAllDevices(deviceListAdapter,mediaMiniAlert)

    listView.setOnItemClickListener(new OnItemClickListener() {
      override def onItemClick(adapterView:AdapterView[_], view:View, position:Int, id:Long) {
        // user has clicked into the listview
        var deviceAddrString = view.findViewById(R.id.visibleText2).asInstanceOf[TextView].getText.toString
        var deviceNameString = view.findViewById(R.id.visibleText).asInstanceOf[TextView].getText.toString
        var deviceCommentString = view.findViewById(R.id.invisibleText).asInstanceOf[TextView].getText.toString        
        if(D) Log.i(TAG, "onItemClick deviceAddrString="+deviceAddrString+" deviceNameString="+deviceNameString+" deviceCommentString="+deviceCommentString)
		    val returnIntent = new Intent()
        val bundle = new Bundle()
        bundle.putString("device", deviceNameString+"\n"+deviceAddrString+" "+deviceCommentString)
        returnIntent.putExtras(bundle)
		    setResult(Activity.RESULT_OK,returnIntent)
        rfCommHelper.addAllDevicesUnregister
        if(D) Log.i(TAG, "onItemClick finish")
		    finish
      }
    })

    AndrTools.buttonCallback(this, R.id.buttonCancel) { () =>
      if(D) Log.i(TAG, "onClick buttonCancel")
  		setResult(-1)
      finish
    }

    AndrTools.buttonCallback(this, R.id.buttonBt) { () =>
      if(D) Log.i(TAG, "onClick buttonBt")
      val bluetoothSettingsIntent = new Intent
      bluetoothSettingsIntent.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
      startActivityForResult(bluetoothSettingsIntent, REQUEST_BT_SETTINGS) // -> onActivityResult()
    }

    if(D) Log.i(TAG, "onCreate done")
  }

  override def onConfigurationChanged(newConfig:Configuration) {
    super.onConfigurationChanged(newConfig)
  }
  
  override def onActivityResult(requestCode:Int, resultCode:Int, intent:Intent) {
    if(D) Log.i(TAG, "onActivityResult resultCode="+resultCode+" requestCode="+requestCode)
    requestCode match {
      case REQUEST_BT_SETTINGS =>
        if(D) Log.i(TAG, "onActivityResult - REQUEST_BT_SETTINGS")
        // in case the user has paired new devices
        val pairedDevicesArrayListOfStrings = RFCommHelper.getBtPairedDevices(activity)
        if(pairedDevicesArrayListOfStrings!=null) {
          if(D) Log.i(TAG, "add BtPairedDevices count="+pairedDevicesArrayListOfStrings.size)
          if(pairedDevicesArrayListOfStrings.size>0)
            for(i <- 0 until pairedDevicesArrayListOfStrings.size)
              rfCommHelper.addDevice(pairedDevicesArrayListOfStrings.get(i))
        }
    }
  }

  override def onDestroy() {
    if(D) Log.i(TAG, "onDestroy")
    rfCommHelper.addAllDevicesUnregister
    //activityDestroyed = true
    deviceListAdapter.exit
    super.onDestroy
  }
  
	override def onBackPressed() {
    if(D) Log.i(TAG, "onBackPressed()")
		setResult(Activity.RESULT_CANCELED)
    super.onBackPressed 
		finish
	}
}

