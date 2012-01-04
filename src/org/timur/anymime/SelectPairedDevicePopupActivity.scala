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

import scala.collection.mutable // for instance: mutable.HashMap

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
  private var arrayAdapter:ArrayAdapter[String] = null
  private var btBroadcastReceiver:BroadcastReceiver = null

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
    // if(D) Log.i(TAG, "onCreate desiredBluetooth="+rfCommHelper.desiredBluetooth+" desiredWifiDirect="+rfCommHelper.desiredWifiDirect+" desiredNfc="+rfCommHelper.desiredNfc+" ##############")

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


    // now fill our listView with all possible (paired/stored/discovered) devices of the requested device types
    arrayAdapter = new ArrayAdapter[String](this, android.R.layout.simple_list_item_1, new java.util.ArrayList[String]())
    setListAdapter(arrayAdapter)
    // we use pairedDevicesShadowHashMap[addr,name] as a shadow-HashMap containing all listed devices, so we can prevent double-entries in the visible arrayAdapter
    val pairedDevicesShadowHashMap = new mutable.HashMap[String,String]()

    if(rfCommHelper.rfCommService.desiredBluetooth) {
      // 1. get list of paired bt devices from rfCommHelper
      val pairedDevicesArrayListOfStrings = rfCommHelper.getBtPairedDevices  // java.util.ArrayList[String], "name/naddr"
      if(pairedDevicesArrayListOfStrings!=null) {
        if(D) Log.i(TAG, "add BtPairedDevices count="+pairedDevicesArrayListOfStrings.size)
        if(pairedDevicesArrayListOfStrings.size>0) {
          // copy all paired bt-devices to the listview
          arrayAdapter.addAll(Collections.synchronizedList(pairedDevicesArrayListOfStrings))

          // copy all paired bt-devices to pairedDevicesShadowHashMap 
          for(i <- 0 until pairedDevicesArrayListOfStrings.size) {
            val btDevice = pairedDevicesArrayListOfStrings.get(i)
            val idxCR = btDevice.indexOf("\n")
            val btName = btDevice.substring(0,idxCR)
            val idxBlank = btDevice.substring(idxCR+1).indexOf(" ")
            val btAddr = if(idxBlank>=0) btDevice.substring(idxCR+1,idxCR+1+idxBlank) else btDevice.substring(idxCR+1)
            if(D) Log.i(TAG, "add BtPairedDevices i="+i+" btAddr="+btAddr+" btName="+btName)
            pairedDevicesShadowHashMap += btAddr -> btName
          }
        }
      }

      // todo: 2. get list of stored (previously connected) bt devices

      // 3. start handler for all newly discovered bt devices
      if(rfCommHelper.mBluetoothAdapter!=null) {
        btBroadcastReceiver = new BroadcastReceiver() {
          override def onReceive(context:Context, intent:Intent) {
            val actionString = intent.getAction
            if(BluetoothDevice.ACTION_FOUND==actionString) {
              val bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE).asInstanceOf[BluetoothDevice]
              if(bluetoothDevice!=null) {
                if(D) Log.i(TAG, "btBroadcastReceiver BluetoothDevice.ACTION_FOUND name=["+bluetoothDevice.getName+"] addr="+bluetoothDevice.getAddress)
                if(bluetoothDevice.getName!=null && bluetoothDevice.getName.length>0) {
                  if(pairedDevicesShadowHashMap.getOrElse(bluetoothDevice.getAddress,null)==null) {
                    pairedDevicesShadowHashMap += bluetoothDevice.getAddress -> bluetoothDevice.getName
                    arrayAdapter.add(bluetoothDevice.getName+"\n"+bluetoothDevice.getAddress+" bt")
                  }
                }
              }
            }
            else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED==actionString) {
              //if(D) Log.i(TAG,"btBroadcastReceiver ACTION_DISCOVERY_FINISHED")
              rfCommHelper.mBluetoothAdapter.startDiscovery
            }
          }
        }

        //registerReceiver(btBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED))
        registerReceiver(btBroadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND))
        registerReceiver(btBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))

        rfCommHelper.mBluetoothAdapter.startDiscovery
      }
    }

    if(rfCommHelper.rfCommService.desiredWifiDirect) {
      // todo: 4. get list of previously connected p2pWifi devices

      // 5. start handler for freshly discovered p2pWifi devices
      if(rfCommHelper.wifiP2pManager!=null) {
        rfCommHelper.rfCommService.p2pWifiDiscoveredCallbackFkt = { wifiP2pDevice =>
          if(wifiP2pDevice != null) {
            if(pairedDevicesShadowHashMap.getOrElse(wifiP2pDevice.deviceAddress,null)==null) {
              if(D) Log.i(TAG, "add wifiP2p device deviceName="+wifiP2pDevice.deviceName+" deviceAddress="+wifiP2pDevice.deviceAddress+
                              " status="+wifiP2pDevice.status+" "+(wifiP2pDevice.deviceAddress==rfCommHelper.rfCommService.p2pRemoteAddressToConnect))
              pairedDevicesShadowHashMap += wifiP2pDevice.deviceAddress -> wifiP2pDevice.deviceName
              arrayAdapter.add(wifiP2pDevice.deviceName+"\n"+wifiP2pDevice.deviceAddress+" wifi")
            }
          }
        }

        rfCommHelper.wifiP2pManager.discoverPeers(rfCommHelper.rfCommService.p2pChannel, new WifiP2pManager.ActionListener() {
          override def onFailure(reasonCode:Int) {
            if(D) Log.i(TAG, "wifiP2pManager.discoverPeers failed reasonCode="+reasonCode)
            // reason ERROR=0, P2P_UNSUPPORTED=1, BUSY=2
          }
          override def onSuccess() {
            //if(D) Log.i(TAG, "wifiP2pManager.discoverPeers onSuccess")
          }
        })
      }
    }
  }
  
  // todo's somehere else
  //   todo: must store every connected bt-address in preferences (in a hash map)
  //   todo: must store every connected p2pWifi-address in preferences (in a hash map)

  override def onActivityResult(requestCode:Int, resultCode:Int, intent:Intent) {
    if(D) Log.i(TAG, "onActivityResult resultCode="+resultCode+" requestCode="+requestCode)
    requestCode match {
      case REQUEST_BT_SETTINGS =>
        if(D) Log.i(TAG, "onActivityResult - REQUEST_BT_SETTINGS")
        val pairedDevicesArrayListOfStrings = rfCommHelper.getBtPairedDevices
        if(pairedDevicesArrayListOfStrings!=null) {
          if(D) Log.i(TAG, "add BtPairedDevices count="+pairedDevicesArrayListOfStrings.size)
          if(pairedDevicesArrayListOfStrings.size>0)
            arrayAdapter.addAll(Collections.synchronizedList(pairedDevicesArrayListOfStrings))
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
		finish
	}

  override def onDestroy() {
    if(D) Log.i(TAG, "onDestroy")
    if(rfCommHelper!=null && rfCommHelper.rfCommService!=null) {
      if(D) Log.i(TAG, "onDestroy rfCommHelper.rfCommService.callbackFkt = null")
      rfCommHelper.rfCommService.p2pWifiDiscoveredCallbackFkt = null
    
      if(btBroadcastReceiver!=null) {
        if(rfCommHelper.mBluetoothAdapter!=null)
          rfCommHelper.mBluetoothAdapter.cancelDiscovery
        unregisterReceiver(btBroadcastReceiver)
      }
    }
    
    super.onDestroy
  }
  
/*
  // todo: rather use onBackPressed
  override def dispatchKeyEvent(keyEvent:KeyEvent) :Boolean = {
    //if(D) Log.i(TAG, "dispatchKeyEvent")
    val keyCode = keyEvent.getKeyCode()
    if(keyCode==4) { // back key
      if(D) Log.i(TAG, "dispatchKeyEvent back key")
  		setResult(-1)
    }

    return super.dispatchKeyEvent(keyEvent)
  }
*/
	override def onBackPressed() {
    if(D) Log.i(TAG, "onBackPressed()")
		setResult(-1)
    super.onBackPressed 
	}
}

