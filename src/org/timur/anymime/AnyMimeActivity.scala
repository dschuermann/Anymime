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

import scala.collection.mutable.HashMap
import scala.collection.mutable.StringBuilder

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.IOException
import java.io.DataOutputStream
import java.util.List
import java.util.Comparator
import java.util.ArrayList
import java.util.HashSet
import java.net.URI
import java.net.Socket
import java.net.ServerSocket
import java.net.InetSocketAddress

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.app.AlertDialog
import android.app.AlertDialog.Builder
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.ServiceConnection
import android.content.Intent
import android.content.res.Configuration
import android.content.SharedPreferences
import android.content.BroadcastReceiver
import android.content.DialogInterface
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.os.IBinder
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.os.Environment
import android.os.Parcelable
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.Window
import android.view.View
import android.view.View.OnClickListener
import android.view.inputmethod.EditorInfo
import android.view.ContextMenu
import android.view.ViewGroup.LayoutParams
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.MotionEvent
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.ImageView
import android.widget.Toast
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.LinearLayout
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.HorizontalScrollView
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputFilter
import android.text.format.Formatter
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.provider.MediaStore.Images.Media
import android.provider.MediaStore.MediaColumns

import android.media.MediaPlayer
//import android.os.SystemClock

import android.app.PendingIntent
import android.content.IntentFilter
import android.content.IntentFilter.MalformedMimeTypeException
import android.nfc.NfcAdapter
import android.nfc.tech.NfcF
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import java.nio.charset.Charset
import java.util.Locale
import android.webkit.MimeTypeMap

import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ActionListener
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.net.wifi.p2p.WifiP2pManager.ChannelListener
import android.net.wifi.p2p.WifiP2pManager.PeerListListener
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.NetworkInfo

import android.content.ContentValues

/*
object AnyMimeActivity {
  val RADIO_TYPE_ACTIVATION = 1
}
*/

import android.net.Uri
//import com.android.bluetooth.opp.BluetoothShare
object BluetoothShare {
  val URI = "uri"
  val CONTENT_URI = Uri.parse("content://com.android.bluetooth.opp/btopp")
  val VISIBILITY = "visibility"
  val DESTINATION = "destination"
  val DIRECTION = "direction"
  val TIMESTAMP = "timestamp"
  val VISIBILITY_VISIBLE = 0
  val DIRECTION_OUTBOUND = 0
}

class AnyMimeActivity extends Activity {
  private val TAG = "AnyMimeActivity"
  private val D = true

  private val DIALOG_ABOUT = 2

  private val REQUEST_ENABLE_BT = 1
  private val REQUEST_SELECT_PAIRED_DEVICE_AND_CONNECT_TO = 2
  private val REQUEST_READ_CURRENT_SLOT = 3
  private val REQUEST_READ_SELECTED_SLOT_ADD_FILE = 4

  private val mimeTypeMap = MimeTypeMap.getSingleton()

  private var mTitleView:TextView = null
  private var mBluetoothAdapter:BluetoothAdapter = null
  private var serviceConnection:ServiceConnection = null
  private var mConnectedDeviceAddr:String = null
  private var mConnectedDeviceName:String = null
  private var mNfcAdapter:NfcAdapter = null
  private var nfcPendingIntent:PendingIntent = null
  private var nfcFilters:Array[IntentFilter] = null
  private var nfcTechLists:Array[Array[String]] = null
  private var nfcForegroundPushMessage:NdefMessage = null
  private var context:Context = null
  private var activityResumed = false

  private val PREFS_SETTINGS = "org.timur.anymime.settings"
  private var prefSettings:SharedPreferences = null
  private var prefSettingsEditor:SharedPreferences.Editor = null

  private val receiveFilesHistory = new ReceiveFilesHistory()
  private var receiveFilesHistoryLength=0

	private var slowAnimation:Animation = null
  private var mainView:View = null
  private var radioLogoView:ImageView = null
  private var userHint1View:TextView = null
  private var userHint2View:TextView = null
  private var userHint3View:TextView = null
  private var simpleProgressBarView:ProgressBar = null
  private var progressBarView:ProgressBar = null
  private var quickBarView:HorizontalScrollView = null

//  @volatile private var blobDeliverId:Long = 0
  private var addFilePathNameString:String = null
  private var receivedFileUriStringArrayList = new ArrayList[String]()
  //private var numberOfSentFiles = 0

  private var audioConfirmSound:MediaPlayer = null
  private var selectedSlot = 0
  private var selectedSlotName = ""
  private var initiatedConnectionByThisDevice = false
  private var connectAttemptFromNfc = false
  @volatile private var startTime:Long = 0
  private var kbytesPerSecond:Long=0

  private var radioDialogNeeded = false
  private var wifiP2pManager:WifiP2pManager = null
  private var wifiDirectBroadcastReceiver:BroadcastReceiver = null
  @volatile private var activityExited = false // exited but not yet destroyed
  @volatile private var activityDestroyed = false
  private var isWifiP2pEnabled = false    // if false in onResume, we will offer ACTION_WIRELESS_SETTINGS 
  private val intentFilter = new IntentFilter()
  private var radioTypeSelected = false
  private var desiredBluetooth = true
  private var desiredWifiDirect = true
  private var desiredNfc = true

  // none-private objects (accessed by wifiDirectBroadcastReceiver)
  var selectedFileStringsArrayList = new ArrayList[String]()
  var p2pChannel:Channel = null
  var localP2pWifiAddr:String = null
  var p2pRemoteAddressToConnect:String = null   // needed to carry the target ip-p2p-addr from ACTION_NDEF_DISCOVERED/discoverPeers() to WIFI_P2P_PEERS_CHANGED_ACTION/wifiP2pManager.connect()
  var discoveringPeersInProgress = false  // so we do not call discoverPeers() again while it is active still
  var p2pConnected = false                // not needed at the moment
  var appService:RFCommHelperService = null


  // called by wifiDirectBroadcastReceiver #(1)
  def setIsWifiP2pEnabled(setIsWifiP2pEnabled:Boolean) {
    Log.i(TAG, "setIsWifiP2pEnabled="+setIsWifiP2pEnabled+" #####")
    if(isWifiP2pEnabled != setIsWifiP2pEnabled) {
      isWifiP2pEnabled = setIsWifiP2pEnabled
      if(!isWifiP2pEnabled)
        p2pConnected = false
    }    
  }

  override def onCreate(savedInstanceState:Bundle) {
    super.onCreate(savedInstanceState)
    activityDestroyed = false
    activityExited = false
    val packageInfo = getPackageManager.getPackageInfo(getPackageName, 0)
    if(D) Log.i(TAG, "onCreate versionName="+packageInfo.versionName+" android.os.Build.VERSION.SDK_INT="+android.os.Build.VERSION.SDK_INT)
    context = this
    requestWindowFeature(Window.FEATURE_NO_TITLE)
/*
    if(android.os.Build.VERSION.SDK_INT>=11)
      setContentView(new BGView(this))    // renderscript
    else
*/
      setContentView(R.layout.main)   // java.lang.OutOfMemoryError: bitmap size exceeds VM budget

    audioConfirmSound = MediaPlayer.create(context, R.raw.textboxbloop8bit)

    // prepare access to prefSettings
    if(prefSettings==null) {
      prefSettings = getSharedPreferences(PREFS_SETTINGS, Context.MODE_WORLD_WRITEABLE)
      if(prefSettings!=null)
        prefSettingsEditor = prefSettings.edit
    }

    localP2pWifiAddr = null
    if(android.os.Build.VERSION.SDK_INT>=14) {
      intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
      intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
      intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
      intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    mainView = findViewById(R.id.main)
    radioLogoView = findViewById(R.id.radioLogo).asInstanceOf[ImageView]
    userHint1View = findViewById(R.id.userHint).asInstanceOf[TextView]
    userHint2View = findViewById(R.id.userHint2).asInstanceOf[TextView]
    userHint3View = findViewById(R.id.userHint3).asInstanceOf[TextView]
    simpleProgressBarView = findViewById(R.id.simpleProgressBar).asInstanceOf[ProgressBar]
    progressBarView = findViewById(R.id.progressBar).asInstanceOf[ProgressBar]
    quickBarView = findViewById(R.id.quickBar).asInstanceOf[HorizontalScrollView]
    if(quickBarView!=null)
      quickBarView.setHorizontalScrollBarEnabled(false)
	  slowAnimation = AnimationUtils.loadAnimation(this, R.anim.slow_anim)

    getArrayListSelectedFileStrings

    receiveFilesHistoryLength = receiveFilesHistory.load(context)

    if(prefSettings!=null) {
      desiredBluetooth = prefSettings.getBoolean("radioBluetooth", desiredBluetooth)
      desiredWifiDirect = prefSettings.getBoolean("radioWifiDirect", desiredWifiDirect)
      desiredNfc = prefSettings.getBoolean("radioNfc", desiredNfc)
    }
    
    // initialize our service (but don't start bt-receive-thread yet)
    if(D) Log.i(TAG, "onCreate startService('RFCommHelperService') ...")
    val serviceIntent = new Intent(context.asInstanceOf[AnyMimeActivity], classOf[RFCommHelperService])
    //startService(serviceIntent)   // call this only, to keep service active after onDestroy()/unbindService()

    serviceConnection = new ServiceConnection { 
      def onServiceConnected(className:ComponentName, rawBinder:IBinder) { 
        if(D) Log.i(TAG, "onCreate onServiceConnected localBinder.getService ...")
        appService = rawBinder.asInstanceOf[RFCommHelperService#LocalBinder].getService
        if(appService==null) {
          Log.e(TAG, "onCreate onServiceConnected no interface to service, appService==null")
          // todo: run on ui-thread?
          Toast.makeText(context, "Error - failed to get service interface from binder", Toast.LENGTH_LONG).show    // todo: create more 'human' text

        } else {
          if(D) Log.i(TAG, "onCreate onServiceConnected got appService object")
          appService.context = context
          appService.activityMsgHandler = msgFromServiceHandler
        }
      } 

      def onServiceDisconnected(className:ComponentName) { 
        if(D) Log.i(TAG, "onCreate onServiceDisconnected set appService=null")
        appService = null
      } 
    } 
    if(serviceConnection!=null) {
      if(D) Log.i(TAG, "onCreate bindService ...")
      bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
      if(D) Log.i(TAG, "onCreate bindService done")
    } else {
      if(D) Log.i(TAG, "onCreate bindService failed")
    }


    // now follows the code tp handle all clickable areas of the main view

    AndrTools.buttonCallback(this, R.id.buttonSendFiles) { () =>
      if(D) Log.i(TAG, "onClick buttonSendFiles")
      showSelectedFiles
    }

    // received files history
    AndrTools.buttonCallback(this, R.id.buttonReceivedFiles) { () =>
      if(D) Log.i(TAG, "onClick buttonReceivedFiles")
      val intent = new Intent(context, classOf[ShowReceivedFilesHistoryActivity])
      // hand over .asc file from most recent delivery
      val bundle = new Bundle()
      if(selectedFileStringsArrayList!=null)
        if(selectedFileStringsArrayList.size>0) {
          val iterator = selectedFileStringsArrayList.iterator 
          while(iterator.hasNext) {
            val fileString = iterator.next
            if(fileString.endsWith(".asc")) {
              bundle.putString("sendKeyFile", fileString)
              // break
            }
          }
        }

      intent.putExtras(bundle)
      startActivity(intent) // -> ShowReceivedFilesHistoryActivity
    }


    AndrTools.buttonCallback(this, R.id.buttonManualConnect) { () =>
      if(D) Log.i(TAG, "onClick buttonManualConnect mConnectedDeviceAddr="+mConnectedDeviceAddr)
      // select one device from list of paired/bonded devices and connect to it
      // but only if there is no active bt-connection yet
      if(mConnectedDeviceAddr!=null) {
        if(D) Log.i(TAG, "onClick buttonManualConnect toast 'You are Bluetooth connected already'")
        AndrTools.runOnUiThread(context) { () =>
          Toast.makeText(context, "You are Bluetooth connected already", Toast.LENGTH_LONG).show
        }
        return
      }
      if(D) Log.i(TAG, "onClick buttonManualConnect new Intent(context, classOf[SelectPairedDevicePopupActivity])")
      val intent = new Intent(context, classOf[SelectPairedDevicePopupActivity])
      if(D) Log.i(TAG, "onClick buttonManualConnect startActivityForResult")
      startActivityForResult(intent, REQUEST_SELECT_PAIRED_DEVICE_AND_CONNECT_TO) // -> SelectPairedDevicePopupActivity -> onActivityResult()
      if(D) Log.i(TAG, "onClick buttonManualConnect startActivityForResult done")
    }

/*
    AndrTools.buttonCallback(this, R.id.buttonRadioSelect) { () =>
      if(D) Log.i(TAG, "onClick buttonRadioSelect")
      radioTypeSelected=false
      radioDialog(false)
    }
*/

    AndrTools.buttonCallback(this, R.id.buttonBluetoothSettings) { () =>
      if(D) Log.i(TAG, "onClick buttonBluetoothSettings")
      val bluetoothSettingsIntent = new Intent
      bluetoothSettingsIntent.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
      startActivity(bluetoothSettingsIntent) // -> BLUETOOTH_SETTINGS
    }

    AndrTools.buttonCallback(this, R.id.buttonAbout) { () =>
      if(D) Log.i(TAG, "onClick buttonAbout")
      showDialog(DIALOG_ABOUT)
    }

    AndrTools.buttonCallback(this, R.id.applogo) { () =>
      if(D) Log.i(TAG, "onClick applogoView")
      showDialog(DIALOG_ABOUT)
    }

    AndrTools.buttonCallback(this, R.id.main) { () =>
      if(D) Log.i(TAG, "onClick mainView")
      val intent = new Intent(context, classOf[ShowSelectedSlotActivity])
      startActivityForResult(intent, REQUEST_READ_CURRENT_SLOT) // -> ShowSelectedSlotActivity -> onActivityResult()
    }

    AndrTools.buttonCallback(this, progressBarView) { () =>
      if(D) Log.i(TAG, "onClick progressBarView")
      offerUserToDisconnect
    }

    radioDialogNeeded = true // will be evaluated in onResume
    checkLayout
	  mainViewUpdate

    // have we been started with a file being handed over (say from OI File Manager)?
    val intent = getIntent
    if(intent!=null)
      processExternalIntent(intent)
  }

  def radioDialog(backKeyIsExit:Boolean) {
    if(D) Log.i(TAG, "radioDialog radioTypeSelected="+radioTypeSelected+" ####")
    if(activityDestroyed || activityExited) {
      if(D) Log.i(TAG, "radioDialog aborted because: activityDestroyed="+activityDestroyed+" activityExited="+activityExited+" ####")
      return
    }

    if(!radioTypeSelected) {
      // offer the user a dialog to turn all wanted radio-hw on
      val radioSelectDialogBuilder = new AlertDialog.Builder(context)
      // todo: use a "radio wave" background ?

      val radioSelectDialogLayoutInflater = android.view.LayoutInflater.from(context)
      val radioSelectDialogLayout = radioSelectDialogLayoutInflater.inflate(R.layout.radio_select_dialog, null)
      radioSelectDialogBuilder.setView(radioSelectDialogLayout)
      val radioBluetoothCheckbox = radioSelectDialogLayout.findViewById(R.id.radioBluetooth).asInstanceOf[CheckBox]
      val radioWifiDirectCheckbox = radioSelectDialogLayout.findViewById(R.id.radioWifiDirect).asInstanceOf[CheckBox]
      val radioNfcCheckbox = radioSelectDialogLayout.findViewById(R.id.radioNfc).asInstanceOf[CheckBox]

      radioBluetoothCheckbox.setText("Bluetooth unavail")
      if(mBluetoothAdapter==null)
        radioBluetoothCheckbox.setEnabled(false)    // disable if bt-hardware is not available
      else {
        radioBluetoothCheckbox.setText("Bluetooth is off")
        if(mBluetoothAdapter.isEnabled)
          radioBluetoothCheckbox.setText("Bluetooth is on")
        radioBluetoothCheckbox.setChecked(desiredBluetooth)
      }

      radioWifiDirectCheckbox.setText("WiFi Direct unavail")
      if(wifiP2pManager==null || android.os.Build.VERSION.SDK_INT<14)
        radioWifiDirectCheckbox.setEnabled(false)   // disable if wifip2p-hardware is not available
      else {
        radioWifiDirectCheckbox.setText("WiFi Direct is off")
        if(isWifiP2pEnabled)
          radioWifiDirectCheckbox.setText("WiFi Direct is on")
        radioWifiDirectCheckbox.setChecked(desiredWifiDirect)
      }

      radioNfcCheckbox.setText("NFC unavail")
      if(mNfcAdapter==null || android.os.Build.VERSION.SDK_INT<10)
        radioNfcCheckbox.setEnabled(false)          // disable if nfc-hardware is not available
      else {
        radioNfcCheckbox.setText("NFC is off")
        if(mNfcAdapter.isEnabled)
          radioNfcCheckbox.setText("NFC is on")
        radioNfcCheckbox.setChecked(desiredNfc)
      }

      val backKeyLabel = if(backKeyIsExit) "Exit" else "Close"
      radioSelectDialogBuilder.setNegativeButton(backKeyLabel, new DialogInterface.OnClickListener() {
        def onClick(dialogInterface:DialogInterface, m:Int) {
          // persist desired-flags
          if(prefSettingsEditor!=null) {
            prefSettingsEditor.putBoolean("radioBluetooth",radioBluetoothCheckbox.isChecked)
            prefSettingsEditor.putBoolean("radioWifiDirect",radioWifiDirectCheckbox.isChecked)
            prefSettingsEditor.putBoolean("radioNfc",radioNfcCheckbox.isChecked)
            prefSettingsEditor.commit
          }
          if(backKeyIsExit)
            finish
        }
      })

      radioSelectDialogBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
        def onClick(dialogInterface:DialogInterface, m:Int) {
          // this is just to create the OK button
          // evaluation is found below under setOnShowListener()/onClick()
        }
      })

      if(backKeyIsExit)
        radioSelectDialogBuilder.setOnKeyListener(new DialogInterface.OnKeyListener() {
          override def onKey(dialogInterface:DialogInterface, keyCode:Int, keyEvent:KeyEvent) :Boolean = {
            if(keyCode == KeyEvent.KEYCODE_BACK && keyEvent.getAction()==KeyEvent.ACTION_UP && !keyEvent.isCanceled()) {
              if(D) Log.i(TAG, "radioDialog onKeyDown KEYCODE_BACK return false")
              return false
            }
            if(D) Log.i(TAG, "radioDialog onKeyDown not KEYCODE_BACK return true")
            return true
          }                   
        })

      AndrTools.runOnUiThread(context) { () =>
        val radioSelectDialog = radioSelectDialogBuilder.create
        var alertReady = false
        radioSelectDialog.setOnShowListener(new DialogInterface.OnShowListener() {
          override def onShow(dialogInterface:DialogInterface) {
            if(alertReady==false) {
              val button = radioSelectDialog.getButton(DialogInterface.BUTTON_POSITIVE)
              button.setOnClickListener(new View.OnClickListener() {
                override def onClick(view:View) {
                  // evaluate checkboxes and set desired booleans
                  desiredBluetooth = radioBluetoothCheckbox.isChecked
                  desiredWifiDirect = radioWifiDirectCheckbox.isChecked
                  desiredNfc = radioNfcCheckbox.isChecked
                  if(D) Log.i(TAG, "radioSelectDialog onClick desiredBluetooth="+desiredBluetooth+" desiredWifiDirect="+desiredWifiDirect+" desiredNfc="+desiredNfc+" #################")
                  if(desiredBluetooth==false && desiredWifiDirect==false) {
                    // we need at least 1 type of transport-radio
                    AndrTools.runOnUiThread(context) { () =>
                      Toast.makeText(context, "No radio enabled for transport", Toast.LENGTH_SHORT).show
                    }
                    // we let the dialog stay open

                  } else {
                    radioTypeSelected = true
                    dialogInterface.cancel

                    // persist desired-flags
                    if(prefSettingsEditor!=null) {
                      prefSettingsEditor.putBoolean("radioBluetooth",desiredBluetooth)
                      prefSettingsEditor.putBoolean("radioWifiDirect",desiredWifiDirect)
                      prefSettingsEditor.putBoolean("radioNfc",desiredNfc)
                      prefSettingsEditor.commit
                    }

                    if(mBluetoothAdapter!=null && mBluetoothAdapter.isEnabled && appService!=null) {
                      if(appService.state == RFCommHelperService.STATE_NONE) {
                        // start the Bluetooth accept thread(s) (implemented in RFCommHelperService.scala)
                        // this is for devices trying to connect to us
                        var acceptOnlySecureConnectRequests = true
                        //if(prefSettings!=null)
                        //  acceptOnlySecureConnectRequests = prefSettings.getBoolean("acceptOnlySecureConnectRequests",true)
                        if(D) Log.i(TAG, "radioSelectDialog onClick appService.start acceptOnlySecureConnectReq="+acceptOnlySecureConnectRequests+" ...")
                        appService.start(acceptOnlySecureConnectRequests)
                      }

                      mainViewUpdate    // todo: I think this is to update/show the activated bt-radio
                                        // todo: however, how do we display the use of dual-radio (p2pwifi+bt) ?
                    }

                    if(mNfcAdapter!=null && mNfcAdapter.isEnabled) {
                      if(D) Log.i(TAG, "radioSelectDialog onClick -> nfcServiceSetup")
                      nfcServiceSetup
                    }

                    switchOnDesiredRadios
                    radioDialogNeeded = false  // radioDialog will not again be shown on successive onResume's
                  }
                }
              })
              alertReady = true
            }
          }
        })
        radioSelectDialog.show
      }
    }
  }

  override def onResume() = synchronized {
    //if(D) Log.i(TAG, "onResume mNfcAdapter="+mNfcAdapter+" wifiP2pManager="+wifiP2pManager+" isWifiP2pEnabled="+isWifiP2pEnabled)
    super.onResume

    // find out if nfc hardware is supported (not necessarily on)
    if(android.os.Build.VERSION.SDK_INT>=10) {
      if(mNfcAdapter==null) {
        try {
          mNfcAdapter = NfcAdapter.getDefaultAdapter(this)
          // continue to setup nfc in nfcServiceSetup()
        } catch {
          case ncdferr:java.lang.NoClassDefFoundError =>
            Log.e(TAG, "onResume NfcAdapter.getDefaultAdapter(this) failed "+ncdferr)
        }
      }
    }
    if(mNfcAdapter!=null) {
      //if(D) Log.i(TAG, "onResume nfc supported ####")
    } else {
      if(D) Log.i(TAG, "onResume nfc not supported ####")
    }

    // find out if bt-hardware is supported (not necessarily on)
    if(mBluetoothAdapter==null) {
      // get local Bluetooth adapter
      mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter
      // If the adapter is null, then Bluetooth is not supported (mBluetoothAdapter must not be null, even if turned off)
    }
    if(mBluetoothAdapter!=null) {
      //if(D) Log.i(TAG, "onResume bt supported ####")
    } else {
      if(D) Log.i(TAG, "onResume bt not supported")
    }

    // find out if wifi-direct is supported, if so initialze wifiP2pManager
    if(android.os.Build.VERSION.SDK_INT>=14 && wifiP2pManager==null) {
      wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE).asInstanceOf[WifiP2pManager]
      if(wifiP2pManager!=null) {
        // register p2pChannel and wifiDirectBroadcastReceiver
        if(D) Log.i(TAG, "onResume wifiP2p is supported, initialze p2pChannel and register wifiDirectBroadcastReceiver ####")
        p2pChannel = wifiP2pManager.initialize(this, getMainLooper, null)
        wifiDirectBroadcastReceiver = new WiFiDirectBroadcastReceiver(wifiP2pManager, this)
        registerReceiver(wifiDirectBroadcastReceiver, intentFilter)
      }
    }
    if(wifiP2pManager==null) {
      if(D) Log.i(TAG, "onResume wifiP2p not supported")
    }

    if(radioDialogNeeded) {
      new Thread() {
        override def run() {
          if(D) Log.i(TAG, "onResume new thread -> radioDialog")
          radioDialog(true)
        }
      }.start
    } else {
      new Thread() {
        override def run() {
          // delay this, so that user can still exit app if wanted
          try { Thread.sleep(600) } catch { case ex:Exception => }
          if(!activityDestroyed && !activityExited)
            switchOnDesiredRadios
        }
      }.start
    }

    if(mNfcAdapter!=null && mNfcAdapter.isEnabled) {
      if(nfcPendingIntent!=null) {
        mNfcAdapter.enableForegroundDispatch(context.asInstanceOf[AnyMimeActivity], nfcPendingIntent, nfcFilters, nfcTechLists)
        if(D) Log.i(TAG, "onResume enable nfc ForegroundNdefPush done")
      }
      if(nfcForegroundPushMessage!=null) {
        mNfcAdapter.enableForegroundNdefPush(this, nfcForegroundPushMessage)
        if(D) Log.i(TAG, "onResume enableForegroundNdefPush done")
      }
    }

    // set acceptAndConnect if possible / update mainViewUpdate if necessary
    if(appService!=null) {
      appService.acceptAndConnect = true
      if(D) Log.i(TAG, "onResume set appService.acceptAndConnect="+appService.acceptAndConnect)
      if(appService.state!=RFCommHelperService.STATE_CONNECTED)
        mainViewUpdate
    } else {
      Log.i(TAG, "onResume appService==null, acceptAndConnect not set")
    }

    activityResumed = true
    //if(D) Log.i(TAG, "onResume done")
  }


  override def onPause() = synchronized {
    if(D) Log.i(TAG, "onPause")
    super.onPause
    activityResumed = false
    if(mNfcAdapter!=null && mNfcAdapter.isEnabled) {
      mNfcAdapter.disableForegroundDispatch(this)
      if(nfcForegroundPushMessage!=null) {
        mNfcAdapter.disableForegroundNdefPush(this)
        if(D) Log.i(TAG, "onPause disableForegroundNdefPush done")
      }
    }
    if(appService!=null) {
      appService.acceptAndConnect = false
      Log.i(TAG, "onPause appService.acceptAndConnect cleared")
    } else {
      Log.i(TAG, "onPause appService==null, acceptAndConnect not cleared")
    }
    System.gc
    //if(D) Log.i(TAG, "onPause done")
  }


  override def onDestroy() {
    if(D) Log.i(TAG, "onDestroy")

    if(appService!=null) {
      appService.stopActiveConnection
      appService.stopAcceptThread
      appService.context = null
    } else {
      Log.e(TAG, "onDestroy appService=null cannot call stopActiveConnection")
    }

    if(serviceConnection!=null) {
      unbindService(serviceConnection)
      // note: our service will exit here, since we DID NOT use startService in front of bindService - this is our intent!
      if(D) Log.i(TAG, "onDestroy unbindService done")
      serviceConnection=null
    }

    if(wifiDirectBroadcastReceiver!=null) {
      if(D) Log.i(TAG, "onDestroy unregisterReceiver(wifiDirectBroadcastReceiver)")
      unregisterReceiver(wifiDirectBroadcastReceiver)
    }

    if(wifiP2pManager!=null && p2pChannel!=null) {
      if(D) Log.i(TAG, "onDestroy wifiP2pManager.removeGroup")
      wifiP2pManager.removeGroup(p2pChannel, new ActionListener() {
        override def onSuccess() {
          if(D) Log.i(TAG, "onDestroy wifiP2pManager.removeGroup() success ####")
          // wifiDirectBroadcastReceiver will notify us
        }

        override def onFailure(reason:Int) {
          if(D) Log.i(TAG, "onDestroy wifiP2pManager.removeGroup() failed reason="+reason+" ##################")
          // reason ERROR=0, P2P_UNSUPPORTED=1, BUSY=2
        }
      })
      p2pConnected = false  // maybe not necessary
      //p2pChannel = null
      //wifiP2pManager = null
    }

    activityDestroyed=true

    super.onDestroy
  }

  private def checkLayout() {
    // we don't want to show the applogo if the height is very low (nexus one in landscape mode)
    // this shall be called by onCreate and by onConfigurationChanged
    val display = getWindowManager.getDefaultDisplay
    if(D) Log.i(TAG, "checkLayout height="+display.getHeight)
    val headerView = findViewById(R.id.header)
    if(headerView!=null)
      if(display.getHeight<560)
        headerView.setVisibility(View.GONE)
      else
        headerView.setVisibility(View.VISIBLE)
  }

  override def onConfigurationChanged(newConfig:Configuration) {
    checkLayout
    super.onConfigurationChanged(newConfig)
  }

  override def onNewIntent(intent:Intent) {
    // all sort of intents may arrive here... 
    //if(D) Log.i(TAG, "onNewIntent intent="+intent+" intent.getAction="+intent.getAction)

    // we are interested in nfc-intents (ACTION_NDEF_DISCOVERED)
    if(android.os.Build.VERSION.SDK_INT>=10 && mNfcAdapter!=null && mNfcAdapter.isEnabled) {
      val ncfActionString = NfcHelper.checkForNdefAction(context, intent)
      if(D) Log.i(TAG, "onNewIntent ncfActionString="+ncfActionString+" desiredWifiDirect="+desiredWifiDirect+" desiredBluetooth="+desiredBluetooth)
      if(ncfActionString!=null) {
        // this is a nfc-intent, ncfActionString may look something like this: "bt=xxyyzzxxyyzz|p2pWifi=xx:yy:zz:xx:yy:zz"
        val idxP2p = ncfActionString.indexOf("p2pWifi=")
        val idxBt = ncfActionString.indexOf("bt=")
        //if(D) Log.i(TAG, "onNewIntent idxP2p="+idxP2p+" idxBt="+idxBt+" mBluetoothAdapter="+mBluetoothAdapter)

        if(wifiP2pManager!=null && desiredWifiDirect && idxP2p>=0) {
          var p2pWifiAddr = ncfActionString.substring(idxP2p+8)
          val idxPipe = p2pWifiAddr.indexOf("|")
          if(idxPipe>=0) 
            p2pWifiAddr = p2pWifiAddr.substring(0,idxPipe)
          if(D) Log.i(TAG, "onNewIntent p2pWifiAddr="+p2pWifiAddr)

          // play audio notification (as earliest possible feedback for nfc activity)
          if(audioConfirmSound!=null)
            audioConfirmSound.start

          p2pRemoteAddressToConnect = p2pWifiAddr

          if(discoveringPeersInProgress) {
            if(D) Log.i(TAG, "onNewIntent discoveringPeersInProgress: do not call discoverPeers() again ####")

          } else {
            if(D) Log.i(TAG, "onNewIntent wifiP2pManager.discoverPeers() ####")
            wifiP2pManager.discoverPeers(p2pChannel, new WifiP2pManager.ActionListener() {
              // note: discovered peers arrive via wifiDirectBroadcastReceiver WIFI_P2P_PEERS_CHANGED_ACTION
              //       a call to manager.requestPeers() will hand over a PeerListListener with onPeersAvailable() which contains a WifiP2pDeviceList
              //       WifiP2pDeviceList.getDeviceList(), a list of WifiP2pDevice objects, each containg deviceAddress, deviceName, primaryDeviceType, etc.
              
              // note: initiated discovery requests stay active until the device starts connecting to a peer or forms a p2p group
              
              // todo: problem: sometimes we get neither onSuccess nor onFailure
              //       and the cause does not seem to be the other device (problem stays after other devices was rebooted)
              //       just restarting the app (on GN) solves the problem - this is an app issue!

              override def onSuccess() {
                discoveringPeersInProgress = true
                if(D) Log.i(TAG, "onNewIntent discoverPeers() success ####")
              }

              override def onFailure(reasonCode:Int) {
                if(D) Log.i(TAG, "onNewIntent discoverPeers() fail reasonCode="+reasonCode+" #####################")
                // reason ERROR=0, P2P_UNSUPPORTED=1, BUSY=2
                // note: we do get 2
                if(reasonCode!=2)
                  discoveringPeersInProgress = false
              }
            })
            if(D) Log.i(TAG, "onNewIntent wifiP2pManager.discoverPeers() done")
          }

        } else if(mBluetoothAdapter!=null && desiredBluetooth && idxBt>=0) {
          var btAddr = ncfActionString.substring(idxBt+3)
          val idxPipe = btAddr.indexOf("|")
          if(idxPipe>=0) 
            btAddr = btAddr.substring(0,idxPipe)
          if(D) Log.i(TAG, "onNewIntent btAddr="+btAddr+" appService="+appService)

          // play audio notification (as earliest possible feedback for nfc activity)
          if(audioConfirmSound!=null)
            audioConfirmSound.start

          if(appService!=null) {
            // we must set this flag to true, because the nfcservice has put our activity temporarily to onSleep (what? todo: better explain)
            //if(activityResumed) ?
              appService.acceptAndConnect=true

            def remoteBluetoothDevice = mBluetoothAdapter.getRemoteDevice(btAddr)
            if(remoteBluetoothDevice!=null) {
              val sendFilesCount = if(selectedFileStringsArrayList!=null) selectedFileStringsArrayList.size else 0
              if(D) Log.i(TAG, "onNewIntent NdefAction sendFilesCount="+sendFilesCount+" ...")
              appService.setSendFiles(selectedFileStringsArrayList)

              if(mBluetoothAdapter.getAddress > remoteBluetoothDevice.getAddress) {
                // our local btAddr is > than the remote btAddr: we become the actor and we will bt-connect
                // our activity may still be in onPause mode due to NFC activity: sleep a bit before 
                if(D) Log.i(TAG, "onNewIntent NdefAction connecting ...")
                connectAttemptFromNfc=true
                appService.connectBt(remoteBluetoothDevice)

              } else {
                // our local btAddr is < than the remote btAddr: we just wait for a bt-connect request
                if(D) Log.i(TAG, "onNewIntent passively waiting for incoming connect request... mSecureAcceptThread="+appService.mSecureAcceptThread)

                //if(D) Log.i(TAG, "onNewIntent runOnUiThread update user... context="+context)
                AndrTools.runOnUiThread(context) { () =>
                  if(radioLogoView!=null)
                    radioLogoView.setImageResource(R.drawable.bluetooth)
                  if(userHint1View!=null)
                    userHint1View.setText("waiting for "+remoteBluetoothDevice.getName+" "+remoteBluetoothDevice.getAddress)
                  // show a little round progress bar
                  if(userHint2View!=null)
                    userHint2View.setVisibility(View.GONE)
                  if(userHint3View!=null)
                    userHint3View.setVisibility(View.GONE)
                  if(simpleProgressBarView!=null)
                    simpleProgressBarView.setVisibility(View.VISIBLE)
                }
              }
            }
          }
        }
        return
      }
    }

    // this was not a nfc-intent
    processExternalIntent(intent)
  }

  def processExternalIntent(intent:Intent) {
    // will be called by onCreate or onNewIntent
    if(intent!=null) {
      val actionString = intent.getAction
      if(D) Log.i(TAG, "processExternalIntent actionString="+actionString)

      // called from (Gallery) share menu
      if(Intent.ACTION_SEND.equals(actionString)) {
        val extrasBundle = intent.getExtras
        if(extrasBundle.containsKey(Intent.EXTRA_STREAM)) {
          // we have been started with a file being handed over: get resource path from intent callee
          // see: http://stackoverflow.com/questions/2632966/receiving-an-action-send-intent-from-the-gallery
          val fileUri = extrasBundle.getParcelable(Intent.EXTRA_STREAM).asInstanceOf[Uri]
          val schemeString = fileUri.getScheme
          if(schemeString!=null && schemeString.equals("content")) {
            val mimeTypeString = intent.getType
            val contentResolver = getContentResolver
            val cursor = contentResolver.query(fileUri, null, null, null, null)
            cursor.moveToFirst
            addFilePathNameString = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA)).trim
            if(D) Log.i(TAG, "processExternalIntent adding file from intent.getExtras.getPath="+addFilePathNameString)
            if(addFilePathNameString!=null && addFilePathNameString.length>0) {
              // user must select the slot where this new file should be added
              val intent = new Intent(context, classOf[ShowSelectedSlotActivity])
              startActivityForResult(intent, REQUEST_READ_SELECTED_SLOT_ADD_FILE) // -> ShowSelectedSlotActivity -> onActivityResult()
              Toast.makeText(this, "Select where to add "+fileUri.getLastPathSegment, Toast.LENGTH_LONG).show
            }
          }
        }
      } else
      // called from (File Browser) open/view action
      if(Intent.ACTION_VIEW.equals(actionString)) {
        val fileUri = intent.getData
        if(fileUri!=null && fileUri.getPath!=null) {
          addFilePathNameString = fileUri.getPath.trim
          if(addFilePathNameString!=null && addFilePathNameString.length>0) {
            if(D) Log.i(TAG, "processExternalIntent adding file from intent.getData.getPath="+addFilePathNameString)

            // user must select the slot where this new file should be added
            val intent = new Intent(context, classOf[ShowSelectedSlotActivity])
            startActivityForResult(intent, REQUEST_READ_SELECTED_SLOT_ADD_FILE) // -> ShowSelectedSlotActivity -> onActivityResult()
            Toast.makeText(this, "Select where to add "+fileUri.getLastPathSegment, Toast.LENGTH_LONG).show
          }
        }
      }    
    }    
  }


  override def onActivityResult(requestCode:Int, resultCode:Int, intent:Intent) {
    // Called when an activity you launched exits, giving you the requestCode you 
    // started it with, the resultCode it returned, and any additional data from it. 
    // The resultCode will be RESULT_CANCELED if the activity explicitly returned that, 
    // didn't return any result, or crashed during its operation. 
    if(D) Log.i(TAG, "onActivityResult resultCode="+resultCode+" requestCode="+requestCode)
    requestCode match {
      case REQUEST_ENABLE_BT =>
        if(D) Log.i(TAG, "onActivityResult REQUEST_ENABLE_BT")
        // When the request to enable Bluetooth returns
        if (resultCode == Activity.RESULT_OK) {
          // Bluetooth is now enabled, so set up a chat session
          if(mBluetoothAdapter==null)
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter
          if(mBluetoothAdapter!=null) {
            if(D) Log.i(TAG, "onActivityResult REQUEST_ENABLE_BT -> nfcServiceSetup")
            nfcServiceSetup // update our ndef push message to include our btAddr
          }

        } else {
          // User did not enable Bluetooth or an error occured
          if(D) Log.i(TAG, "onActivityResult BT not enabled")
          Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show
          finish
        }
/*
      // we might want to offer app settings later
      case REQUEST_SETTINGS =>
        if(D) Log.i(TAG, "onActivityResult REQUEST_SETTINGS")
        // we don't care for any activity result 
        // but "org.timur.btgrouplink.settings" may have been modified
        // we need to check all settings that we care for (in particular "auto_connect")
*/

      case REQUEST_SELECT_PAIRED_DEVICE_AND_CONNECT_TO =>
        if(D) Log.i(TAG, "onActivityResult REQUEST_SELECT_PAIRED_DEVICE_AND_CONNECT_TO")
        if(resultCode!=Activity.RESULT_OK) {
          Log.e(TAG, "REQUEST_SELECT_PAIRED_DEVICE_AND_CONNECT_TO resultCode!=Activity.RESULT_OK ="+resultCode)
        } else
        if(intent==null) {
          Log.e(TAG, "REQUEST_SELECT_PAIRED_DEVICE_AND_CONNECT_TO intent==null")
        } else {
          val bundle = intent.getExtras()
          if(bundle==null) {
            Log.e(TAG, "REQUEST_SELECT_PAIRED_DEVICE_AND_CONNECT_TO intent.getExtras==null")
          } else {
            val btDevice = bundle.getString("btdevice")
            if(D) Log.i(TAG, "REQUEST_SELECT_PAIRED_DEVICE_AND_CONNECT_TO btDevice="+btDevice)
            if(btDevice==null) {
              Log.e(TAG, "REQUEST_SELECT_PAIRED_DEVICE_AND_CONNECT_TO btDevice==null")
            } else {
              // user has selected one paired device to manually connect to
              val idxCR = btDevice.indexOf("\n")
              if(idxCR<1) {
                Log.e(TAG, "REQUEST_SELECT_PAIRED_DEVICE_AND_CONNECT_TO idxCR<1")
              } else {
                val btAddr = btDevice.substring(idxCR+1)
                val btName = btDevice.substring(0,idxCR)
                if(D) Log.i(TAG, "REQUEST_SELECT_PAIRED_DEVICE_AND_CONNECT_TO btName="+btDevice+" btAddr="+btAddr)
            		Toast.makeText(this, "Connecting to "+btName, Toast.LENGTH_SHORT).show()
               
                // connect to btAddr
                val remoteBluetoothDevice = BluetoothAdapter.getDefaultAdapter.getRemoteDevice(btAddr)
                if(remoteBluetoothDevice==null) {
                  Log.e(TAG, "REQUEST_SELECT_PAIRED_DEVICE_AND_CONNECT_TO remoteBluetoothDevice==null")
                } else {
                  val sendFilesCount = if(selectedFileStringsArrayList!=null) selectedFileStringsArrayList.size else 0
                  if(D) Log.i(TAG, "REQUEST_SELECT_PAIRED_DEVICE_AND_CONNECT_TO appService.connectBt() sendFilesCount="+sendFilesCount+" ...")
                  initiatedConnectionByThisDevice = true
                  connectAttemptFromNfc=false
                  appService.setSendFiles(selectedFileStringsArrayList)
                  appService.connectBt(remoteBluetoothDevice)
                }
              }
            }
          }
        }

      case REQUEST_READ_CURRENT_SLOT =>
        getArrayListSelectedFileStrings
        mainViewUpdate

      case REQUEST_READ_SELECTED_SLOT_ADD_FILE =>
        getArrayListSelectedFileStrings
        mainViewUpdate

        if(D) Log.i(TAG, "REQUEST_READ_SELECTED_SLOT_ADD_FILE add addFilePathNameString="+addFilePathNameString)
        selectedFileStringsArrayList add addFilePathNameString

        persistArrayListSelectedFileStrings
        showSelectedFiles
    }
  }

  override def onCreateDialog(id:Int) :Dialog = {
    val menuDialog = new Dialog(this,R.style.NoTitleDialog)

    if(id==DIALOG_ABOUT) {
      if(D) Log.i(TAG, "onCreateDialog id==DIALOG_ABOUT")
      menuDialog.setContentView(R.layout.about_dialog)

      try {
        val packageInfo = getPackageManager.getPackageInfo(getPackageName, 0)
        if(D) Log.i(TAG, "onCreateDialog id==DIALOG_ABOUT manifest versionName="+packageInfo.versionName)
        val textView = menuDialog.findViewById(R.id.aboutVersion).asInstanceOf[TextView]
        val dispVersion = "v"+packageInfo.versionName + " ("+android.os.Build.VERSION.SDK_INT+")"
        textView.setText(dispVersion.asInstanceOf[CharSequence],TextView.BufferType.NORMAL)
      } catch {
        case nnfex:android.content.pm.PackageManager.NameNotFoundException =>
          Log.e(TAG, "onClick btnAbout FAILED on getPackageManager.getPackageInfo(getPackageName, 0) "+nnfex)
          val errMsg = nnfex.getMessage
          Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show
      }

/*
      // DL-link per SMS
      val btnPromoteSMS = menuDialog.findViewById(R.id.buttonPromoteSMS)
      if(btnPromoteSMS!=null) {
        btnPromoteSMS.setOnClickListener(new View.OnClickListener() {
          override def onClick(view:View) { 
            if(D) Log.i(TAG, "onClick btnPromoteSMS")

            val smsUri = Uri.parse("smsto:")
            val sendSmsIntent = new Intent(Intent.ACTION_SENDTO, smsUri)
            sendSmsIntent.putExtra("sms_body", 
              "Download AnyMime from http://timur.mobi/android/AnyMime.apk")
            try {
              startActivity(sendSmsIntent)
            } catch {
              case ex:Exception =>
                val errMsg = ex.getMessage
                Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show
            }

            dismissDialog(id)
          } 
        })
      }
*/

/*
      // close button
      val btnClose = menuDialog.findViewById(R.id.buttonClose)
      if(btnClose!=null) {
        btnClose.setOnClickListener(new View.OnClickListener() {
          override def onClick(view:View) {
            dismissDialog(id)
          }
        })
      }
*/
    } 

    return menuDialog
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    if(D) Log.i(TAG, "onCreateOptionsMenu android.os.Build.VERSION.SDK_INT="+android.os.Build.VERSION.SDK_INT)
    showDialog(DIALOG_ABOUT)
    return true
  }

	def offerUserToDisconnect() {
    val dialogClickListener = new DialogInterface.OnClickListener() {
      override def onClick(dialog:DialogInterface, whichButton:Int) {
        whichButton match {
          case DialogInterface.BUTTON_POSITIVE =>
            // disconnect the active transmission
            if(appService!=null)
              appService.stopActiveConnection
          case DialogInterface.BUTTON_NEGATIVE =>
            // do nothing, just continue
        }
      }
    }

    new AlertDialog.Builder(context).setTitle("Disconnect?")
                                    .setMessage("Are you sure you want to disconnect the onging transmission?")
                                    .setPositiveButton("Yes",dialogClickListener)
                                    .setNegativeButton("No", dialogClickListener)
                                    .show     
	}

	override def onBackPressed() {
    if(D) Log.i(TAG, "onBackPressed()")
    if(appService!=null && appService.state==RFCommHelperService.STATE_CONNECTED) {
      // ask the user to confirm before disconnecting active transmission
      offerUserToDisconnect
      // activity will not be closed here
    } else {

/*
todo: onExit we want to offer the user to turn off radio hardware that we have turned on
todo: onExit we shouls at least DISCONNECT from all other radio (if we not turn off radio hardware)

      if(wifiP2pManager!=null && p2pChannel!=null) {
        if(D) Log.i(TAG, "onBackPressed wifiP2pManager.removeGroup")
        wifiP2pManager.removeGroup(p2pChannel, new ActionListener() {
          override def onSuccess() {
            if(D) Log.i(TAG, "onBackPressed wifiP2pManager.removeGroup() success ####")
            // wifiDirectBroadcastReceiver will notify us
          }

          override def onFailure(reason:Int) {
            if(D) Log.i(TAG, "onBackPressed wifiP2pManager.removeGroup() failed reason="+reason+" ##################")
            // reason ERROR=0, P2P_UNSUPPORTED=1, BUSY=2
          }
        })
        p2pConnected = false  // maybe not necessary
        //p2pChannel = null
        //wifiP2pManager = null

      } else {
        if(D) Log.i(TAG, "onBackPressed p2pConnected="+p2pConnected+" p2pChannel="+p2pChannel)
      }
*/
      // this activity will be closed here
      super.onBackPressed 
    }
	}

  private def persistArrayList(arrayList:ArrayList[String], persistName:String) {
    if(prefSettingsEditor!=null) {
      val iterator = arrayList.iterator 
      var stringBuilder = new StringBuilder()
      while(iterator.hasNext) {
        if(stringBuilder.size>0)
          stringBuilder append ","
        stringBuilder append iterator.next
      }
      if(D) Log.i(TAG, "persistArrayList stringBuilder="+stringBuilder.toString)
      prefSettingsEditor.putString(persistName,stringBuilder.toString)
      prefSettingsEditor.commit
    }
  }

  private def showSelectedFiles() {
    if(D) Log.i(TAG, "showSelectedFiles selectedFileStringsArrayList="+selectedFileStringsArrayList)
    val intent = new Intent(context, classOf[ShowSelectedFilesActivity])
      val bundle = new Bundle()
      bundle.putStringArrayList("selectedFilesStringArrayList", selectedFileStringsArrayList)
      intent.putExtras(bundle)
    startActivityForResult(intent, REQUEST_READ_CURRENT_SLOT) // -> ShowSelectedFilesActivity -> onActivityResult()
  }

  private def getArrayListSelectedFileStrings() {
    val selectedSlotString = prefSettings.getString("selectedSlot", null)
    selectedSlot = if(selectedSlotString!=null) selectedSlotString.toInt else 0
    if(selectedSlot<0 || selectedSlot>ShowSelectedSlotActivity.MAX_SLOTS)
      selectedSlot = 0
    if(D) Log.i(TAG, "getArrayListSelectedFileStrings selectedSlot="+selectedSlot)
    if(selectedFileStringsArrayList!=null)
      selectedFileStringsArrayList.clear
    selectedSlotName = prefSettings.getString("fileSlotName"+selectedSlot, "")

    // read the lists of selected files
    var commaSeparatedString = prefSettings.getString("fileSlot"+selectedSlot, null)
    if(D) Log.i(TAG, "getArrayListSelectedFileStrings commaSeparatedString="+commaSeparatedString)
    if(commaSeparatedString!=null) {
      commaSeparatedString = commaSeparatedString.trim
      if(commaSeparatedString.size>0) {
        val resultArray = commaSeparatedString split ","
        if(resultArray!=null) {
          if(D) Log.i(TAG,"getArrayListSelectedFileStrings prefSettings selectedFilesStringArrayList size="+resultArray.size)
          for(filePathString <- resultArray) {
            if(filePathString!=null)
              selectedFileStringsArrayList add filePathString.trim
          }
        }
      }
    }
  }

  private def persistArrayListSelectedFileStrings() {
    if(prefSettings!=null && prefSettingsEditor!=null) {
      val selectedSlotString = prefSettings.getString("selectedSlot", null)
      selectedSlot = if(selectedSlotString!=null) selectedSlotString.toInt else 0
      if(selectedSlot<0 || selectedSlot>ShowSelectedSlotActivity.MAX_SLOTS)
        selectedSlot = 0

      val iterator = selectedFileStringsArrayList.iterator 
      var stringBuilder = new StringBuilder()
      while(iterator.hasNext) {
        if(stringBuilder.size>0)
          stringBuilder append ","
        stringBuilder append iterator.next
      }
      if(D) Log.i(TAG, "persistArrayListSelectedFileStrings stringBuilder="+stringBuilder.toString)
      prefSettingsEditor.putString("fileSlot"+selectedSlot,stringBuilder.toString)
      prefSettingsEditor.commit
    }
  }


  def nfcServiceSetup() {
    // this is called by radioDialog/onOK, by wifiDirectBroadcastReceiver:WIFI_P2P_THIS_DEVICE_CHANGED_ACTION and by onActivityResult:REQUEST_ENABLE_BT
    // on first call: call enableForegroundDispatch
    // on every call: update enableForegroundNdefPush

    mConnectedDeviceAddr = null
    mConnectedDeviceName = null

    // setup NFC (only for Android 2.3.3+ and only if NFC hardware is available)
    if(android.os.Build.VERSION.SDK_INT>=10 && mNfcAdapter!=null && mNfcAdapter.isEnabled) {
      if(nfcPendingIntent==null) {
        // Create a generic PendingIntent that will be delivered to this activity (on a different device?)
        // The NFC stack will fill in the intent with the details of the discovered tag 
        // before delivering to this activity.
        nfcPendingIntent = PendingIntent.getActivity(context.asInstanceOf[AnyMimeActivity], 0,
                new Intent(context.asInstanceOf[AnyMimeActivity], getClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0)

        // setup an intent filter for all MIME based dispatches
        val ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        try {
          if(D) Log.i(TAG, "nfcServiceSetup ndef.addDataType...")
          ndef.addDataType("*/*")
          //if(D) Log.i(TAG, "nfcServiceSetup ndef.addDataType done")
        } catch {
          case e: MalformedMimeTypeException =>
            Log.e(TAG, "nfcServiceSetup ndef.addDataType MalformedMimeTypeException")
            throw new RuntimeException("fail", e)
        }
        nfcFilters = Array(ndef)

        // Setup a tech list for all NfcF tags
        if(D) Log.i(TAG, "nfcServiceSetup setup a tech list for all NfcF tags...")
        nfcTechLists = Array(Array(classOf[NfcF].getName))
      }
      if(D) Log.i(TAG, "nfcServiceSetup enable nfc dispatch mNfcAdapter="+mNfcAdapter+" nfcPendingIntent="+nfcPendingIntent+" nfcFilters="+nfcFilters+" nfcTechLists="+nfcTechLists+" ...")
      if(activityResumed) {
        mNfcAdapter.enableForegroundDispatch(context.asInstanceOf[AnyMimeActivity], nfcPendingIntent, nfcFilters, nfcTechLists)
        //if(D) Log.i(TAG, "nfcServiceSetup enableForegroundDispatch done")
      } else {
        if(D) Log.i(TAG, "nfcServiceSetup enableForegroundDispatch delayed until activity is resumed #####")
      }

      // embed our btAddress + localP2pWifiAddr in a new NdefMessage to be used via enableForegroundNdefPush
      var nfcString = ""
      val btAddress = mBluetoothAdapter.getAddress
      if(desiredBluetooth && btAddress!=null)
        nfcString += "bt="+btAddress
      if(desiredWifiDirect && localP2pWifiAddr!=null) {
        if(nfcString.length>0)
          nfcString += "|"
        nfcString += "p2pWifi="+localP2pWifiAddr
      }

      if(nfcString.length==0) {
        // this should never happen, right?
        if(D) Log.i(TAG, "nfcServiceSetup nfcString empty ###############")
        nfcForegroundPushMessage=null
        if(activityResumed)
          mNfcAdapter.disableForegroundNdefPush(context.asInstanceOf[AnyMimeActivity])

      } else {        
        nfcForegroundPushMessage = new NdefMessage(Array(NfcHelper.newTextRecord(nfcString, Locale.ENGLISH, true)))
        if(nfcForegroundPushMessage!=null) {
          if(activityResumed) {
            mNfcAdapter.enableForegroundNdefPush(context.asInstanceOf[AnyMimeActivity], nfcForegroundPushMessage)
            if(D) Log.i(TAG, "nfcServiceSetup enable nfc ForegroundNdefPush nfcString=["+nfcString+"] done ############")

          } else {
            if(D) Log.i(TAG, "nfcServiceSetup enable nfc ForegroundNdefPush nfcString=["+nfcString+"] delayed until activity is resumed")
          }
        }
      }

    } else {
      if(D) Log.i(TAG, "nfcServiceSetup NFC NOT set up mNfcAdapter="+mNfcAdapter+" #############")
    }
  }


  def switchOnDesiredRadios() {
    if(desiredNfc && android.os.Build.VERSION.SDK_INT>=10 && mNfcAdapter!=null && !mNfcAdapter.isEnabled) {
      // let user enable nfc
      if(D) Log.i(TAG, "activityMsgHandler switchOnDesiredRadios !mNfcAdapter.isEnabled: ask user to enable nfc #################")
      AndrTools.runOnUiThread(context) { () =>
        Toast.makeText(context, "Please enable 'NFC', then go back...", Toast.LENGTH_SHORT).show
      }
      startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))
      // todo: onexit: offer to disable NFC

    } else if(desiredWifiDirect && android.os.Build.VERSION.SDK_INT>=14 && wifiP2pManager!=null && !isWifiP2pEnabled) {
      // let user enable wifip2p
      if(D) Log.i(TAG, "activityMsgHandler switchOnDesiredRadios isWifiP2pEnabled="+isWifiP2pEnabled+": ask user to enable p2p #################")
      AndrTools.runOnUiThread(context) { () =>
        Toast.makeText(context, "Please enable 'WiFi direct', then go back...", Toast.LENGTH_SHORT).show
      }

      startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))
      // note: once wifi-direct will be switched on (manually by the user), we will receive setIsWifiP2pEnabled(true)
      //       -> which will trigger discoverPeers()
      //       -> which will trigger a p2p connect request to the ipAddr given by nfc-dispatch
      // todo: onexit: offer to disable wifi-direct

    } else if(desiredBluetooth && mBluetoothAdapter!=null && !mBluetoothAdapter.isEnabled) {
      // let user enable bluetooth
      val enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
      startActivityForResult(enableIntent, REQUEST_ENABLE_BT)
      // -> onActivityResult/REQUEST_ENABLE_BT -> if(resultCode == Activity.RESULT_OK) nfcServiceSetup()
      // todo: onexit: offer to disable BT
    }
  }

  // msgFromServiceHandler initialized during startup by appService.setActivityMsgHandler()
  private final def msgFromServiceHandler = new Handler() {
    override def handleMessage(msg:Message) {

      msg.what match {
        case RFCommHelperService.MESSAGE_STATE_CHANGE =>
          //if(D) Log.i(TAG, "handleMessage MESSAGE_STATE_CHANGE: " + msg.arg1)
          msg.arg1 match {
            case RFCommHelperService.STATE_CONNECTED =>
              if(D) Log.i(TAG, "handleMessage MESSAGE_STATE_CHANGE: STATE_CONNECTED")
              mConnectedDeviceAddr = null

              // audio notification for connect
              if(audioConfirmSound!=null)
                audioConfirmSound.start
                
              receivedFileUriStringArrayList.clear
              mainViewUpdate

              // switch off button bar, switch on progressBar
              if(quickBarView!=null)
                quickBarView.setVisibility(View.GONE)
              if(progressBarView!=null)
                progressBarView.setVisibility(View.VISIBLE)

              //if(D) Log.i(TAG, "RFCommHelperService.STATE_CONNECTED: reset startTime")
              startTime = SystemClock.uptimeMillis

            case RFCommHelperService.STATE_LISTEN | RFCommHelperService.STATE_NONE =>
              if(D) Log.i(TAG, "handleMessage MESSAGE_STATE_CHANGE: NOT CONNECTED")
              mainViewUpdate          
          }

        case RFCommHelperService.MESSAGE_DEVICE_NAME =>
          // note: MESSAGE_DEVICE_NAME is immediately followed by a MESSAGE_STATE_CHANGE/STATE_CONNECTED message
          mConnectedDeviceAddr = msg.getData.getString(RFCommHelperService.DEVICE_ADDR)
          mConnectedDeviceName = msg.getData.getString(RFCommHelperService.DEVICE_NAME)
          val mConnectedSocketType = msg.getData.getString(RFCommHelperService.SOCKET_TYPE)
          if(D) Log.i(TAG, "handleMessage MESSAGE_DEVICE_NAME="+mConnectedDeviceName+" addr="+mConnectedDeviceAddr)
          // show toast only, if we did not initiate the connection
          if(!initiatedConnectionByThisDevice)
            Toast.makeText(getApplicationContext, ""+mConnectedDeviceName+" has connected", Toast.LENGTH_LONG).show

        case RFCommHelperService.MESSAGE_YOURTURN =>
          //if(D) Log.i(TAG, "handleMessage MESSAGE_YOURTURN reset startTime ---------------------------------------------")
          //startTime = SystemClock.uptimeMillis
          if(progressBarView!=null)
            progressBarView.setProgress(0)
          mainViewUpdate          

        case RFCommHelperService.MESSAGE_USERHINT1 =>
          def writeMessage = msg.obj.asInstanceOf[String]
          if(userHint1View!=null && writeMessage!=null) {
            //if(D) Log.i(TAG, "MESSAGE_USERHINT1 userHint1View.setText writeMessage="+writeMessage)
            userHint1View.setText(writeMessage)
          }

        case RFCommHelperService.MESSAGE_USERHINT2 =>
          def readMessage = msg.obj.asInstanceOf[String]
          if(userHint2View!=null && readMessage!=null) {
            userHint2View.setText(readMessage)
          }

        case RFCommHelperService.CONNECTION_START =>
          // our outgoing connect attempt is starting now
          val mConnectingDeviceAddr = msg.getData.getString(RFCommHelperService.DEVICE_ADDR)
          val mConnectingDeviceName = msg.getData.getString(RFCommHelperService.DEVICE_NAME)
          if(D) Log.i(TAG, "handleMessage CONNECTION_START: "+mConnectingDeviceName+" addr="+mConnectingDeviceAddr)
          if(radioLogoView!=null)
            radioLogoView.setImageResource(R.drawable.bluetooth)
          if(userHint1View!=null) {
            if(D) Log.i(TAG, "CONNECTION_START userHint1View.setText")
            userHint1View.setText("connecting to "+mConnectingDeviceName+" "+mConnectingDeviceAddr)
          }
          // show a little round progress bar
          if(userHint2View!=null)
            userHint2View.setVisibility(View.GONE)
          if(userHint3View!=null)
            userHint3View.setVisibility(View.GONE)
          if(simpleProgressBarView!=null)
            simpleProgressBarView.setVisibility(View.VISIBLE)

        case RFCommHelperService.CONNECTION_FAILED =>
          // Anymime connect attempt has failed
          val mDisconnectedDeviceAddr = msg.getData.getString(RFCommHelperService.DEVICE_ADDR)
          val mDisconnectedDeviceName = msg.getData.getString(RFCommHelperService.DEVICE_NAME)
          if(D) Log.i(TAG, "handleMessage CONNECTION_FAILED: ["+mDisconnectedDeviceName+"] addr="+mDisconnectedDeviceAddr)
          mConnectedDeviceAddr = null
          mConnectedDeviceName = null
          initiatedConnectionByThisDevice = false
          if(radioLogoView!=null)
          	radioLogoView.setAnimation(null)
          mainViewUpdate

          if(!connectAttemptFromNfc) {
            // coming from REQUEST_SELECT_PAIRED_DEVICE_AND_CONNECT_TO (and not via NFC connect)

            // ask: "fall back to OPP?" alertDialog
            val dialogClickListener = new DialogInterface.OnClickListener() {
              override def onClick(dialog:DialogInterface, whichButton:Int) {
                whichButton match {
                  case DialogInterface.BUTTON_POSITIVE =>
                    val remoteBluetoothDevice = BluetoothAdapter.getDefaultAdapter.getRemoteDevice(mDisconnectedDeviceAddr)
                    if(remoteBluetoothDevice==null) {
                      Log.e(TAG, "REQUEST_SELECT_PAIRED_DEVICE_AND_OPP_TO remoteBluetoothDevice==null")
                    } else {
                      new Thread() {
                        override def run() {
                          val iterator = selectedFileStringsArrayList.iterator 
                          while(iterator.hasNext) {
                            val fileString = iterator.next
                            if(fileString!=null) {
                              if(D) Log.i(TAG, "send obex/opp fileString=["+fileString+"]")
                              val idxLastDot = fileString.lastIndexOf(".")
                              if(idxLastDot<0) {
                                Log.e(TAG, "send obex/opp idxLastDot<0 (no file extension)")
                              } else {
                                val contentValues = new ContentValues()
                                contentValues.put(BluetoothShare.URI, Uri.fromFile(new File(fileString)).toString)
                                contentValues.put(BluetoothShare.VISIBILITY, new java.lang.Integer(BluetoothShare.VISIBILITY_VISIBLE))
                                contentValues.put(BluetoothShare.DESTINATION, remoteBluetoothDevice.getAddress)
                                contentValues.put(BluetoothShare.DIRECTION, new java.lang.Integer(BluetoothShare.DIRECTION_OUTBOUND))
                                //contentValues.put(BluetoothShare.USER_CONFIRMATION, new java.lang.Integer(BluetoothShare.___))
                                //contentValues.put(BluetoothShare.MIMETYPE, "application/pgp")
                                
                                val ts = SystemClock.uptimeMillis
                                contentValues.put(BluetoothShare.TIMESTAMP, new java.lang.Long(ts))
                                val contentUri = getContentResolver().insert(BluetoothShare.CONTENT_URI, contentValues)
                              }
                            }
                          }
                        
                        }
                      }.start                        
                    }

                  case DialogInterface.BUTTON_NEGATIVE =>
                    // do nothing
                }
              }
            }

            new AlertDialog.Builder(context).setTitle("No Anymime device found")
                                            .setMessage("Send files using OBEX/OPP?")
                                            .setPositiveButton("Yes",dialogClickListener)
                                            .setNegativeButton("No", dialogClickListener)
                                            .show     
          }

        case RFCommHelperService.MESSAGE_DELIVER_PROGRESS =>
          val progressType = msg.getData.getString(RFCommHelperService.DELIVER_TYPE) // "receive" or "send"
          //val deliverId = msg.getData.getLong(RFCommHelperService.DELIVER_ID)
          val progressPercent = msg.getData.getInt(RFCommHelperService.DELIVER_PROGRESS)
          //if(D) Log.i(TAG, "handleMessage MESSAGE_DELIVER_PROGRESS: progressPercent="+progressPercent)
          if(progressBarView!=null)
            progressBarView.setProgress(progressPercent)
          val progressBytes = msg.getData.getLong(RFCommHelperService.DELIVER_BYTES)
          val durationSeconds = (SystemClock.uptimeMillis - startTime) / 1000
          if(durationSeconds>0) {
            val newKbytesPerSecond = (progressBytes/1024)/durationSeconds
            //if(D) Log.i(TAG, "handleMessage MESSAGE_DELIVER_PROGRESS progressPercent="+progressPercent+" kbytesPerSecond="+kbytesPerSecond)
            if(newKbytesPerSecond>0 || kbytesPerSecond==0) {
              kbytesPerSecond = newKbytesPerSecond
              if(userHint3View!=null) {
                userHint3View.setTypeface(null, 0)  // un-bold
                userHint3View.setTextSize(15)  // normal size
                userHint3View.setText(""+(progressBytes/1024)+"\u00A0KB   "+durationSeconds+"s   "+kbytesPerSecond+"\u00A0KB/s")
              }
            }
          }
          // todo: set receiverActivityFlag, to prevent ReceiverIdleCheckThread() from forcing a disconnect (not yet impl.)

        case RFCommHelperService.MESSAGE_RECEIVED_FILE =>
          val receiveFileName = msg.getData.getString(RFCommHelperService.DELIVER_FILENAME)
          //if(D) Log.i(TAG, "handleMessage MESSAGE_RECEIVED_FILE: receiveFileName=["+receiveFileName+"]")
          // store receiveFileName so we can show all received files later
          val receiveUriString = msg.getData.getString(RFCommHelperService.DELIVER_URI)
          //if(D) Log.i(TAG, "handleMessage MESSAGE_RECEIVED_FILE: receiveUriString=["+receiveUriString+"]")
          receivedFileUriStringArrayList.add(receiveUriString)
          // todo: must set receiverActivityFlag, to prevent ReceiverIdleCheckThread() from forcing a disconnect

        case RFCommHelperService.DEVICE_DISCONNECT =>
          // a remote device got disconnected after being connected
          val mDisconnectedDeviceAddr = msg.getData.getString(RFCommHelperService.DEVICE_ADDR)
          val mDisconnectedDeviceName = msg.getData.getString(RFCommHelperService.DEVICE_NAME)
          if(D) Log.i(TAG, "handleMessage DEVICE_DISCONNECT: "+mDisconnectedDeviceName+" addr="+mDisconnectedDeviceAddr)
          if(radioLogoView!=null)
          	radioLogoView.setAnimation(null)
          mConnectedDeviceAddr=null
          mConnectedDeviceName=null

          // audio notification for disconnect
          if(audioConfirmSound!=null)
            audioConfirmSound.start

          initiatedConnectionByThisDevice = false
          mainViewUpdate          

          if(receivedFileUriStringArrayList.size<1) {
            Log.e(TAG, "handleMessage MESSAGE_RECEIVED_FILE receivedFileUriStringArrayList.size<1")
            Toast.makeText(getApplicationContext, "Received 0 files, sent "+appService.numberOfSentFiles+" files", Toast.LENGTH_LONG).show
            return
          }

          Toast.makeText(getApplicationContext, "Received "+receivedFileUriStringArrayList.size+" files, sent "+appService.numberOfSentFiles+" files", Toast.LENGTH_LONG).show
          if(D) Log.i(TAG, "handleMessage DEVICE_DISCONNECT: call ShowReceivedFilesPopupActivity receivedFileUriStringArrayList.size="+receivedFileUriStringArrayList.size)
          //persistArrayList(receivedFileUriStringArrayList, "receivedFileUris")

          receiveFilesHistoryLength = receiveFilesHistory.add(SystemClock.uptimeMillis, 
                                                              mDisconnectedDeviceName, 
                                                              kbytesPerSecond, 
                                                              receivedFileUriStringArrayList.toArray(new Array[String](0)) )
          receiveFilesHistoryLength = receiveFilesHistory.store()

          // run ShowReceivedFilesPopupActivity hand over receivedFileUriStringArrayList
          // this will show the list of receive files and allow the user to start intents on the individual files
          try { Thread.sleep(100) } catch { case ex:Exception => }
          val intent = new Intent(context, classOf[ShowReceivedFilesPopupActivity])
          val bundle = new Bundle()
          bundle.putStringArrayList("listOfUriStrings", receivedFileUriStringArrayList)
          bundle.putString("opentype", "auto") // activity will auto-close after about 15s if not used
          bundle.putString("otherName", mDisconnectedDeviceName)
          // hand over .asc file from most recent delivery
          if(selectedFileStringsArrayList!=null && selectedFileStringsArrayList.size>0) {
            val iterator = selectedFileStringsArrayList.iterator 
            while(iterator.hasNext) {
              val fileString = iterator.next
              if(fileString.endsWith(".asc")) {
                bundle.putString("sendKeyFile", fileString)
                // break
              }
            }
          }
          intent.putExtras(bundle)
          startActivity(intent)
      }
    }
  }


/*
  // todo: implement idle connection timeout
  private class ReceiverIdleCheckThread() extends Thread {
    override def run() {
      while connected {
        receiverActivityFlag = false
        try { Thread.sleep(10000) } catch { case ex:Exception => }
        if(!receiverActivityFlag)
          force hangup
      }
    }
  }
*/


  private def mainViewUpdate() {
    if(appService==null) {
      if(D) Log.i(TAG, "mainViewUpdate appService==null")
    } else {
      //if(D) Log.i(TAG, "mainViewUpdate appService.acceptAndConnect="+appService.acceptAndConnect)
    }
      
    if(appService!=null && appService.state==RFCommHelperService.STATE_CONNECTED) {
      mainViewBluetooth

    } else {
      mainViewDefaults
    }
  }

  private def mainViewDefaults() {
    //if(D) Log.i(TAG, "mainViewDefaults")
    if(radioLogoView!=null) {
      if(mNfcAdapter!=null && mNfcAdapter.isEnabled) {
        radioLogoView.setImageResource(R.drawable.nfc)
        if(radioLogoView!=null && slowAnimation!=null)
        	radioLogoView.setAnimation(slowAnimation)
      } else {
        radioLogoView.setImageResource(R.drawable.bluetooth)
      }
    }

    if(mainView!=null)
      mainView.setBackgroundDrawable(getResources().getDrawable(R.drawable.layer_list_dark))

    if(userHint1View!=null) {
      // get free space on SD-card
      val statFs = new StatFs(Environment.getExternalStorageDirectory().getPath())
      val sdAvailSize = statFs.getAvailableBlocks().asInstanceOf[Long] * statFs.getBlockSize().asInstanceOf[Long]
      val str = Formatter.formatFileSize(this, sdAvailSize)
      userHint1View.setText(str+" free media to receive files")
    }

    if(userHint2View!=null) {
      val numberOfFilesToSend = if(selectedFileStringsArrayList==null) 0 else selectedFileStringsArrayList.size
      if(numberOfFilesToSend<1)
        userHint2View.setText("No files selected for delivery")
      else if(selectedSlotName!=null && selectedSlotName.length>0)
        userHint2View.setText("Ready to send "+numberOfFilesToSend+" files from '"+selectedSlotName+"'")
      else
        userHint2View.setText("Ready to send "+numberOfFilesToSend+" files from slot "+(selectedSlot+1))
      userHint2View.setVisibility(View.VISIBLE)
    }

    if(userHint3View!=null) {
      if(mNfcAdapter!=null && mNfcAdapter.isEnabled) {
        userHint3View.setTypeface(null, Typeface.BOLD)  // make bold
        userHint3View.setTextSize(18)  // bigger
        userHint3View.setText("NFC ready: Tap devices to share")   
      }
      else {
        userHint3View.setTypeface(null, 0)  // not bold
        userHint3View.setTextSize(15)  // normal size
        userHint3View.setText("NFC disabled - manual connect required")
      }
      userHint3View.setVisibility(View.VISIBLE)
    }
    if(simpleProgressBarView!=null)
      simpleProgressBarView.setVisibility(View.GONE)
    if(progressBarView!=null) {
      progressBarView.setMax(100)
      progressBarView.setProgress(0)
    }

    // switch off progressBar, switch back on button bar
    if(progressBarView!=null)
      progressBarView.setVisibility(View.GONE)
    if(quickBarView!=null)
      quickBarView.setVisibility(View.VISIBLE)
  }

  private def mainViewBluetooth() {
    //if(D) Log.i(TAG, "mainViewBluetooth")
    if(radioLogoView!=null) {
      radioLogoView.setImageResource(R.drawable.bluetooth)
    	radioLogoView.setAnimation(null)
    }
    
    if(mainView!=null)
      mainView.setBackgroundDrawable(getResources().getDrawable(R.drawable.layer_list_blue))

    if(userHint1View!=null) {
      //if(D) Log.i(TAG, "mainViewBluetooth userHint1View.setText clr")
      userHint1View.setText("")
    }

    if(userHint2View!=null) {
      userHint2View.setText("")
      userHint2View.setVisibility(View.VISIBLE)
    }

    if(userHint3View!=null) {
      userHint3View.setTypeface(null, 0)  // not bold
      userHint3View.setTextSize(15)  // normal size
      userHint3View.setText("")
      userHint3View.setVisibility(View.VISIBLE)
    }

    if(simpleProgressBarView!=null)
      simpleProgressBarView.setVisibility(View.GONE)

    if(progressBarView!=null) {
      progressBarView.setMax(100)
      progressBarView.setProgress(0)
    }
  }
}

class WiFiDirectBroadcastReceiver(val wifiP2pManager:WifiP2pManager, val anyMimeActivity:AnyMimeActivity) extends BroadcastReceiver {
  private val TAG = "WiFiDirectBroadcastReceiver"
  private val D = true

  override def onReceive(context:Context, intent:Intent) {
    val action = intent.getAction

    if(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
      // update UI to indicate wifi p2p status
      val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
      Log.i(TAG, "WIFI_P2P_STATE_CHANGED_ACTION state="+state+" ####")
      if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
        // Wifi Direct mode is enabled
        //Log.i(TAG, "WifiP2pEnabled true ####")
        anyMimeActivity.setIsWifiP2pEnabled(true)

      } else {
        //Log.i(TAG, "WifiP2pEnabled false ####")
        anyMimeActivity.p2pRemoteAddressToConnect = null
        anyMimeActivity.p2pConnected = false
        anyMimeActivity.setIsWifiP2pEnabled(false)
      }

    } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
      // this tells us that there is a change with the number of p2p peers (like a discovery result)
      Log.i(TAG, "WIFI_P2P_PEERS_CHANGED_ACTION number of p2p peers changed ####")
      anyMimeActivity.discoveringPeersInProgress = false

      if(wifiP2pManager==null) {
        Log.i(TAG, "WIFI_P2P_PEERS_CHANGED_ACTION wifiP2pManager==null ####")

      } else {
        //Log.i(TAG, "WIFI_P2P_PEERS_CHANGED_ACTION requestPeers() ####")
        wifiP2pManager.requestPeers(anyMimeActivity.p2pChannel, new PeerListListener() {
          override def onPeersAvailable(wifiP2pDeviceList:WifiP2pDeviceList) {
            // wifiP2pDeviceList.getDeviceList() is a list of WifiP2pDevice objects, each containg deviceAddress, deviceName, primaryDeviceType, etc.
            val wifiP2pDeviceArrayList = new ArrayList[WifiP2pDevice]()
            wifiP2pDeviceArrayList.addAll(wifiP2pDeviceList.getDeviceList.asInstanceOf[java.util.Collection[WifiP2pDevice]])
            val wifiP2pDeviceListCount = wifiP2pDeviceArrayList.size
            Log.i(TAG, "onPeersAvailable wifiP2pDeviceListCount="+wifiP2pDeviceListCount+" trying to connect to="+anyMimeActivity.p2pRemoteAddressToConnect+" ####")
            if(wifiP2pDeviceListCount>0) {
              // list all peers
              for(i <- 0 until wifiP2pDeviceListCount) {
                val wifiP2pDevice = wifiP2pDeviceArrayList.get(i)
                if(wifiP2pDevice != null) {
                  Log.i(TAG, "device "+i+" deviceName="+wifiP2pDevice.deviceName+" deviceAddress="+wifiP2pDevice.deviceAddress+" status="+wifiP2pDevice.status+" "+(wifiP2pDevice.deviceAddress==anyMimeActivity.p2pRemoteAddressToConnect)+" ####")
                  // status: connected=0, invited=1, failed=2, available=3

                  if(anyMimeActivity.p2pRemoteAddressToConnect!=null && wifiP2pDevice.deviceAddress==anyMimeActivity.p2pRemoteAddressToConnect) {
                    if(anyMimeActivity.localP2pWifiAddr<anyMimeActivity.p2pRemoteAddressToConnect) {
                      Log.i(TAG, "onPeersAvailable - local="+anyMimeActivity.localP2pWifiAddr+" < remote="+anyMimeActivity.p2pRemoteAddressToConnect+" - stay passive - let other device connect() ########################")

                    } else {
                      Log.i(TAG, "onPeersAvailable - local="+anyMimeActivity.localP2pWifiAddr+" > remote="+anyMimeActivity.p2pRemoteAddressToConnect+" - be active ########################")
                      Log.i(TAG, "onPeersAvailable connect() to="+wifiP2pDevice.deviceAddress+" p2pConnected="+anyMimeActivity.p2pConnected+" ########################")
                      val wifiP2pConfig = new WifiP2pConfig()
                      wifiP2pConfig.deviceAddress = anyMimeActivity.p2pRemoteAddressToConnect
                      wifiP2pConfig.groupOwnerIntent = -1
                      wifiP2pConfig.wps.setup = WpsInfo.PBC
                      wifiP2pManager.connect(anyMimeActivity.p2pChannel, wifiP2pConfig, new ActionListener() {
                        // note: may result in "E/wpa_supplicant(): Failed to create interface p2p-wlan0-5: -12 (Out of memory)"
                        //       in which case onSuccess() is often still be called
                      
                        override def onSuccess() {
                          if(D) Log.i(TAG, "wifiP2pManager.connect() success ####")
                          // we expect WIFI_P2P_CONNECTION_CHANGED_ACTION in WiFiDirectBroadcastReceiver to notify us
                          // todo: however this often does NOT happen
                        }

                        override def onFailure(reason:Int) {
                          if(D) Log.i(TAG, "wifiP2pManager.connect() failed reason="+reason+" ##################")
                          // reason ERROR=0, P2P_UNSUPPORTED=1, BUSY=2
                          //Toast.makeText(context, "Connect failed. Retry.", Toast.LENGTH_SHORT).show()
                        }
                      })
                      if(D) Log.i(TAG, "wifiP2pManager.connect() done")
                    }
                    anyMimeActivity.p2pRemoteAddressToConnect = null
                  }
                }
              }
            }
          }
        })
      }

    } else if(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION==action) {
      // this signals a new p2p-connect or a new p2p-disconnect (unfortunately, this sometimes does NOT happen, when it is really expected)
      val networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO).asInstanceOf[NetworkInfo]
      Log.i(TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION new p2p-connect-state="+networkInfo.isConnected+" getSubtypeName="+networkInfo.getSubtypeName+" ###################")

      if(wifiP2pManager==null) {
        Log.i(TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION wifiP2pManager==null ###################")
        return
      }
      
      if(networkInfo.isConnected && anyMimeActivity.p2pConnected) {
        Log.i(TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION we are already connected - strange... ignore ###################")
        // todo: new p2p-connect, but we were connected already (maybe this is how we set up a group of 3 or more clients?)
        return
      }

      if(!networkInfo.isConnected) {
        anyMimeActivity.p2pRemoteAddressToConnect = null

        if(!anyMimeActivity.p2pConnected) {
          Log.i(TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION we are now disconnected, we were disconnect already ###################")
          return
        }
        // we think we are connected, but now we are being disconnected
        Log.i(TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION we are now disconnected, set p2pConnected=false ###################")
        anyMimeActivity.p2pConnected = false
        return

      } else {
        // we got connected with another device, request connection info to find group owner IP
        anyMimeActivity.p2pConnected = true
        Log.i(TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION we are now p2pWifi connected with the other device ###################")
        wifiP2pManager.requestConnectionInfo(anyMimeActivity.p2pChannel, new WifiP2pManager.ConnectionInfoListener() {
          override def onConnectionInfoAvailable(wifiP2pInfo:WifiP2pInfo) {
            Log.i(TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION onConnectionInfoAvailable groupOwnerAddress="+wifiP2pInfo.groupOwnerAddress+" isGroupOwner="+wifiP2pInfo.isGroupOwner+" ###############")

            // start socket communication
            anyMimeActivity.appService.setSendFiles(anyMimeActivity.selectedFileStringsArrayList)
            // todo: probably move this code into the service thread already
            new Thread() {
              override def run() {
                var serverSocket:ServerSocket = null
                var socket:Socket = null

                def closeDownP2p() {
                  // this will be called (by both sides) when the thread is finished
                  Log.d(TAG, "closeDownP2p p2pConnected="+anyMimeActivity.p2pConnected+" p2pChannel="+anyMimeActivity.p2pChannel)
                  try { Thread.sleep(1200) } catch { case ex:Exception => }
                  if(socket!=null) {
                    socket.close
                    socket=null
                  }
                  if(serverSocket!=null) {
                    serverSocket.close
                    serverSocket=null
                  }
                  if(anyMimeActivity.p2pConnected) {
                    Log.d(TAG, "closeDownP2p wifiP2pManager.removeGroup() (this is how we disconnect from p2pWifi) ###############")
                    wifiP2pManager.removeGroup(anyMimeActivity.p2pChannel, new ActionListener() {
                      override def onSuccess() {
                        if(D) Log.i(TAG, "closeDownP2p wifiP2pManager.removeGroup() success ####")
                        // wifiDirectBroadcastReceiver will notify us
                      }

                      override def onFailure(reason:Int) {
                        if(D) Log.i(TAG, "closeDownP2p wifiP2pManager.removeGroup() failed reason="+reason+" ##################")
                        // reason ERROR=0, P2P_UNSUPPORTED=1, BUSY=2
                        // note: it seems to be 'normal' for one of the two devices to receive reason=2 on disconenct
                      }
                    })

                    anyMimeActivity.p2pConnected = false  // probably not required, because WIFI_P2P_CONNECTION_CHANGED_ACTION will be called again with networkInfo.isConnected=false
                    anyMimeActivity.p2pRemoteAddressToConnect = null
                  }
                }

                val port = 8954
                if(wifiP2pInfo.isGroupOwner) {
                  // which device becomes the isGroupOwner is random, but it will be the device we run our serversocket on...
                  // by convention, we make the GroupOwner (using the serverSocket) also the filetransfer-non-actor
                  // start server socket
                  //Log.d(TAG, "Server: new ServerSocket("+port+")")
                  try {
                    serverSocket = new ServerSocket(port)
                    Log.d(TAG, "serverSocket opened")
                    socket = serverSocket.accept
                    if(socket!=null) {
                      anyMimeActivity.appService.connectedWifi(socket, false, closeDownP2p)
                    }

                  } catch {
                    case ioException:IOException =>
                      Log.e(TAG, "serverSocket failed to connect ex="+ioException.getMessage+" #######")
                      closeDownP2p
                  }

                } else {
                  // which device becomes the Group client is random, but this is the device we run our client socket on...
                  // by convention, we make the Group client (using the client socket) also the filetransfer-actor (will start the delivery)
                  // because we are NOT the groupOwner, the groupOwnerAddress is the address of the OTHER device
                  val SOCKET_TIMEOUT = 5000
                  val host = wifiP2pInfo.groupOwnerAddress
                  socket = new Socket()
                  try {
                    //Log.d(TAG, "client socket opened")
                    socket.bind(null)
                    socket.connect(new InetSocketAddress(host, port), SOCKET_TIMEOUT)
                    // we wait up to 5000 ms for the connection... if we don't get connected, an ioexception is thrown                  
                    anyMimeActivity.appService.connectedWifi(socket, true, closeDownP2p)

                  } catch {
                    case ioException:IOException =>
                      Log.e(TAG, "client socket failed to connect ex="+ioException.getMessage+" ########")
                      closeDownP2p
                  }
                }
              }
            }.start                     
          }
        })
      }

    } else if(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
      // this is being called on wifi-connect (not p2p-connect) as well as on wifi-disconnect (not p2p-disconnect)
      // we get our own dynamic p2p-mac-addr
      // note: not sure how this is triggered, seems to fly just in

      val wifiP2pDevice = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE).asInstanceOf[WifiP2pDevice]
      if(anyMimeActivity.localP2pWifiAddr==null || anyMimeActivity.localP2pWifiAddr!=wifiP2pDevice.deviceAddress) {
        // we know our p2p mac address, we can now do nfcServiceSetup
        Log.i(TAG, "WIFI_P2P_THIS_DEVICE_CHANGED_ACTION OUR deviceName="+wifiP2pDevice.deviceName+" deviceAddress="+wifiP2pDevice.deviceAddress) //+" info="+wifiP2pDevice.toString)
        anyMimeActivity.localP2pWifiAddr = wifiP2pDevice.deviceAddress
        anyMimeActivity.nfcServiceSetup
      }
    }
  }
}

