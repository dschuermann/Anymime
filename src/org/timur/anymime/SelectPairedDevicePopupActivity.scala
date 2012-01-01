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

import android.app.Activity
import android.app.ListActivity
import android.content.Context
import android.content.Intent
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

import org.timur.rfcomm.AndrTools

class SelectPairedDevicePopupActivity extends ListActivity {

  private val TAG = "SelectPairedDevicePopupActivity"
  private val D = Static.DBGLOG

  private val REQUEST_BT_SETTINGS = 1
  private val pairedDevicesArrayListOfStrings = new java.util.ArrayList[String]()
  private val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter

  override def onCreate(savedInstanceState:Bundle) {
    super.onCreate(savedInstanceState)
    if(D) Log.i(TAG, "onCreate()")

    if(mBluetoothAdapter == null) {
      Log.e(TAG, "onCreate mBluetoothAdapter not available")
      Toast.makeText(this, "Bluetooth not available", Toast.LENGTH_LONG).show
      return
    }

    setContentView(R.layout.bt_select)

    getPairedDevices
    val arrayAdapter = new ArrayAdapter[String](this, android.R.layout.simple_list_item_1, pairedDevicesArrayListOfStrings)
		setListAdapter(arrayAdapter)

    AndrTools.buttonCallback(this, R.id.buttonCancel) { () =>
      if(D) Log.i(TAG, "onClick buttonCancel")
      finish
    }

    AndrTools.buttonCallback(this, R.id.buttonBt) { () =>
      if(D) Log.i(TAG, "onClick buttonBt")
      val bluetoothSettingsIntent = new Intent
      bluetoothSettingsIntent.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
      startActivityForResult(bluetoothSettingsIntent, REQUEST_BT_SETTINGS) // -> onActivityResult()
    }
  }

  def getPairedDevices() {
    pairedDevicesArrayListOfStrings.clear
    val pairedDevicesSet = mBluetoothAdapter.getBondedDevices
    if(pairedDevicesSet.size<1) {
      Log.e(TAG, "onCreate pairedDevicesSet.size<1")
      Toast.makeText(this, "No paired Bluetooth devices available", Toast.LENGTH_LONG).show
    } else {
		  // Create an ArrayAdapter that will make the Strings above appear in the ListView
      val pairedDevicesArrayListOfBluetoothDevices = new ArrayList[BluetoothDevice](pairedDevicesSet)
      if(pairedDevicesArrayListOfBluetoothDevices==null) {
        Log.e(TAG, "onCreate pairedDevicesArrayListOfBluetoothDevices==null")
        Toast.makeText(this, "Could not create pairedDevicesArrayListOfBluetoothDevices", Toast.LENGTH_LONG).show
        return
      }

      val iterator = pairedDevicesArrayListOfBluetoothDevices.iterator 
      while(iterator.hasNext) {
        val device = iterator.next
        if(device!=null) {
          //if(D) Log.i(TAG, "updateDevicesView ADD paired="+device.getName)
          if(device.getName!=null && device.getName.size>0) {
            pairedDevicesArrayListOfStrings.add(device.getName+"\n"+device.getAddress)
          }
        }
      }
    }
  }

  override def onActivityResult(requestCode:Int, resultCode:Int, intent:Intent) {
    if(D) Log.i(TAG, "onActivityResult resultCode="+resultCode+" requestCode="+requestCode)
    requestCode match {
      case REQUEST_BT_SETTINGS =>
        if(D) Log.i(TAG, "onActivityResult - REQUEST_BT_SETTINGS")
        getPairedDevices
        val arrayAdapter = new ArrayAdapter[String](this, android.R.layout.simple_list_item_1, pairedDevicesArrayListOfStrings)
		    setListAdapter(arrayAdapter)
    }
  }

	override def onListItemClick(listView:ListView, view:View, position:Int, id:Long) {
		super.onListItemClick(listView, view, position, id);
		// Get the item that was clicked
		val obj = this.getListAdapter().getItem(position)
		val keyword = obj.toString()
		val returnIntent = new Intent()
    val bundle = new Bundle()
    bundle.putString("btdevice", keyword)
    returnIntent.putExtras(bundle)
		setResult(Activity.RESULT_OK,returnIntent)
		finish
	}

  override def dispatchKeyEvent(keyEvent:KeyEvent) :Boolean = {
    if(D) Log.i(TAG, "dispatchKeyEvent()")
    val keyCode = keyEvent.getKeyCode()
    if(keyCode==4) { // back key
      if(D) Log.i(TAG, "dispatchKeyEvent() back key")
  		setResult(-1)
    }

    return super.dispatchKeyEvent(keyEvent)
  }
}

