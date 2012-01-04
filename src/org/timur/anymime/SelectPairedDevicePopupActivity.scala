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
import java.util.Collections

import android.app.Activity
import android.app.ListActivity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Window
import android.view.View
import android.widget.CheckBox
import android.widget.ArrayAdapter
import android.widget.Toast
import android.widget.ListView

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice

import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.PeerListListener
import android.net.wifi.p2p.WifiP2pDeviceList

import org.timur.rfcomm._

class SelectPairedDevicePopupActivity extends ListActivity {

  private val TAG = "SelectPairedDevicePopupActivity"
  private val D = Static.DBGLOG

  private val REQUEST_BT_SETTINGS = 1

  private var rfCommHelper:RFCommHelper = null

  override def onCreate(savedInstanceState:Bundle) {
    super.onCreate(savedInstanceState)
    if(D) Log.i(TAG, "onCreate()")

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

    setContentView(R.layout.bt_select)

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

    val arrayAdapter = new ArrayAdapter[String](this, android.R.layout.simple_list_item_1, new java.util.ArrayList[String]())
    setListAdapter(arrayAdapter)
    rfCommHelper.addAllDevices(arrayAdapter)
  }
  
  // todo's somehere else
  //   todo: must store every connected bt-address in preferences (in a hash map)
  //   todo: must store every connected p2pWifi-address in preferences (in a hash map)

  override def onActivityResult(requestCode:Int, resultCode:Int, intent:Intent) {
    if(D) Log.i(TAG, "onActivityResult resultCode="+resultCode+" requestCode="+requestCode)
    requestCode match {
      case REQUEST_BT_SETTINGS =>
        if(D) Log.i(TAG, "onActivityResult - REQUEST_BT_SETTINGS")
        // in case the user has paired new devices
        val pairedDevicesArrayListOfStrings = rfCommHelper.getBtPairedDevices
        if(pairedDevicesArrayListOfStrings!=null) {
          if(D) Log.i(TAG, "add BtPairedDevices count="+pairedDevicesArrayListOfStrings.size)
          if(pairedDevicesArrayListOfStrings.size>0)
            for(i <- 0 until pairedDevicesArrayListOfStrings.size)
              rfCommHelper.addDevice(pairedDevicesArrayListOfStrings.get(i))
        }
    }
  }

	override def onListItemClick(listView:ListView, view:View, position:Int, id:Long) {
		super.onListItemClick(listView, view, position, id);
		// Get the item that was clicked
		val obj = this.getListAdapter().getItem(position)
		val keyword = obj.toString()
    if(D) Log.i(TAG, "onListItemClick keyword="+keyword)
		val returnIntent = new Intent()
    val bundle = new Bundle()
    bundle.putString("device", keyword)
    returnIntent.putExtras(bundle)
		setResult(Activity.RESULT_OK,returnIntent)
    rfCommHelper.addAllDevicesUnregister
		finish
	}

  override def onDestroy() {
    if(D) Log.i(TAG, "onDestroy")
    rfCommHelper.addAllDevicesUnregister
    super.onDestroy
  }
  
	override def onBackPressed() {
    if(D) Log.i(TAG, "onBackPressed()")
		setResult(Activity.RESULT_CANCELED)
    super.onBackPressed 
		finish
	}
}

