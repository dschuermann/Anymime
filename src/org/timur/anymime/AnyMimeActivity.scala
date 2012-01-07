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

import scala.collection.mutable.StringBuilder

import java.util.ArrayList
import java.io.File

import android.util.Log
import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.app.AlertDialog
import android.app.AlertDialog.Builder
import android.content.Context
import android.content.ServiceConnection
import android.content.Intent
import android.content.res.Configuration
import android.content.SharedPreferences
import android.content.DialogInterface
import android.content.ComponentName
import android.content.ContentValues
import android.bluetooth.BluetoothAdapter
import android.os.IBinder
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.os.SystemClock
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.Window
import android.view.View
import android.view.View.OnClickListener
import android.view.animation.Animation
import android.view.animation.AnimationUtils
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
import android.widget.ProgressBar
import android.widget.HorizontalScrollView
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputFilter
import android.text.format.Formatter
import android.net.Uri
import android.media.MediaPlayer

import org.timur.rfcomm._

class AnyMimeApp extends android.app.Application {
  var rfCommHelper:RFCommHelper = null
}

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
  private val D = Static.DBGLOG

  private val DIALOG_ABOUT = 1

  private val REQUEST_SELECT_DEVICE_AND_CONNECT = 1
  private val REQUEST_READ_CURRENT_SLOT = 3
  private val REQUEST_READ_SELECTED_SLOT_ADD_FILE = 4

  private val PREFS_PRIVATE = "org.timur.anymime.settings"
  private var prefsPrivate:SharedPreferences = null
  private var prefsPrivateEditor:SharedPreferences.Editor = null

  private val PREFS_SHARED_P2P_BT = "org.timur.p2pDevices.bt"
  private var prefsSharedP2pBt:SharedPreferences = null
  private val PREFS_SHARED_P2P_WIFI = "org.timur.p2pDevices.wifi"
  private var prefsSharedP2pWifi:SharedPreferences = null

  private var activity:Activity = null
  private var activityResumed = false

  private var appServiceConnection:ServiceConnection = null
  private var mConnectedDeviceAddr:String = null
  private var mConnectedDeviceName:String = null

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

  @volatile private var startTime:Long = 0
  private var kbytesPerSecond:Long=0

  private var audioConfirmSound:MediaPlayer = null
  private var selectedSlot = 0
  private var selectedSlotName = ""

  private var initiatedConnectionByThisDevice = false

  private var addFilePathNameString:String = null
  private var receivedFileUriStringArrayList = new ArrayList[String]()
  private var selectedFileStringsArrayList = new ArrayList[String]()

  private var appService:FileExchangeService = null
  private var rfCommHelper:RFCommHelper = null
  
  def serviceInitializedFkt() { 
    // todo: move this (calling rfCommHelper.onResume) into RFCommHelper
    if(activityResumed) {
      new Thread() {
        override def run() {
          if(D) Log.i(TAG, "serviceInitializedFkt -> rfCommHelper.onResume ####")
          if(rfCommHelper!=null) {
            // make sure the UUID's for the mBluetoothAdapter.listenUsingxxxxx must have been set
            rfCommHelper.onResume  // this will run radioSelect and start the AcceptThread(s)
          }
        }
      }.start                        
    }

    if(D) Log.i(TAG, "serviceInitializedFkt -> mainViewUpdate")
    checkLayout
    mainViewUpdate

    /* TODO: re-include
        // have we been started with a file being handed over (say from OI File Manager)?
        val intent = getIntent
        if(intent!=null)
          processExternalIntent(intent)
    */
    if(D) Log.i(TAG, "serviceInitializedFkt DONE")
  }

  def serviceFailedFkt() {
    if(D) Log.i(TAG, "serviceFailedFkt ... ###########")
    AndrTools.runOnUiThread(activity) { () =>
      Toast.makeText(activity, "service initialization failed", Toast.LENGTH_LONG).show
    }
  }

  override def onCreate(savedInstanceState:Bundle) {
    super.onCreate(savedInstanceState)
    val packageInfo = getPackageManager.getPackageInfo(getPackageName, 0)
    if(D) Log.i(TAG, "onCreate versionName="+packageInfo.versionName+" android.os.Build.VERSION.SDK_INT="+android.os.Build.VERSION.SDK_INT)
    activity = this
    requestWindowFeature(Window.FEATURE_NO_TITLE)

/*
    if(android.os.Build.VERSION.SDK_INT>=11)
      setContentView(new BGView(this))    // renderscript
    else
*/
      setContentView(R.layout.main)

    // prepare access to preferences
    prefsPrivate = getSharedPreferences(PREFS_PRIVATE, Context.MODE_PRIVATE)
    if(prefsPrivate!=null)
      prefsPrivateEditor = prefsPrivate.edit
    prefsSharedP2pBt = getSharedPreferences(PREFS_SHARED_P2P_BT, Context.MODE_WORLD_WRITEABLE)
    prefsSharedP2pWifi = getSharedPreferences(PREFS_SHARED_P2P_WIFI, Context.MODE_WORLD_WRITEABLE)

    audioConfirmSound = MediaPlayer.create(activity, R.raw.textboxbloop8bit)

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
    receiveFilesHistoryLength = receiveFilesHistory.load(activity)

    AndrTools.buttonCallback(activity, R.id.buttonSendFiles) { () =>
      if(D) Log.i(TAG, "onClick buttonSendFiles")
      showSelectedFiles
    }

    // received files history
    AndrTools.buttonCallback(activity, R.id.buttonReceivedFiles) { () =>
      if(D) Log.i(TAG, "onClick buttonReceivedFiles")
      val intent = new Intent(activity, classOf[ShowReceivedFilesHistoryActivity])
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

    AndrTools.buttonCallback(activity, R.id.buttonManualConnect) { () =>
      if(D) Log.i(TAG, "onClick buttonManualConnect mConnectedDeviceAddr="+mConnectedDeviceAddr)
      // select one device from list of paired/bonded devices and connect to it
      // but only if there is no active connection yet
      if(mConnectedDeviceAddr!=null) {
        if(D) Log.i(TAG, "onClick buttonManualConnect toast 'You are Bluetooth connected already'")
        AndrTools.runOnUiThread(activity) { () =>
          Toast.makeText(activity, "You are connected already", Toast.LENGTH_LONG).show
          // todo: offer disconnect?
        }
        return
      }

      if(D) Log.i(TAG, "onClick buttonManualConnect new Intent(activity, classOf[SelectDeviceActivity])")
      val intent = new Intent(activity, classOf[SelectDeviceActivity])
      startActivityForResult(intent, REQUEST_SELECT_DEVICE_AND_CONNECT) // -> SelectDeviceActivity -> onActivityResult()

      if(D) Log.i(TAG, "onClick buttonManualConnect startActivityForResult done")
    }

    AndrTools.buttonCallback(activity, R.id.buttonRadioSelect) { () =>
      if(D) Log.i(TAG, "onClick buttonRadioSelect")
      //radioTypeSelected=false
      if(rfCommHelper!=null)
        rfCommHelper.radioDialog(false)
    }

    AndrTools.buttonCallback(activity, R.id.buttonBluetoothSettings) { () =>
      if(D) Log.i(TAG, "onClick buttonBluetoothSettings")
      val bluetoothSettingsIntent = new Intent
      bluetoothSettingsIntent.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
      startActivity(bluetoothSettingsIntent) // -> BLUETOOTH_SETTINGS
    }

    AndrTools.buttonCallback(activity, R.id.buttonAbout) { () =>
      if(D) Log.i(TAG, "onClick buttonAbout")
      showDialog(DIALOG_ABOUT)
    }

    AndrTools.buttonCallback(activity, R.id.applogo) { () =>
      if(D) Log.i(TAG, "onClick applogoView")
      showDialog(DIALOG_ABOUT)
    }

    AndrTools.buttonCallback(activity, R.id.main) { () =>
      if(D) Log.i(TAG, "onClick mainView")
      val intent = new Intent(activity, classOf[ShowSelectedSlotActivity])
      startActivityForResult(intent, REQUEST_READ_CURRENT_SLOT) // -> ShowSelectedSlotActivity -> onActivityResult()
    }

    AndrTools.buttonCallback(progressBarView) { () =>
      if(D) Log.i(TAG, "onClick progressBarView")
      offerUserToDisconnect
    }

    // instantiate FileExchangeService
    if(D) Log.i(TAG, "onCreate startService('FileExchangeService') ...")
    val serviceIntent = new Intent(activity, classOf[FileExchangeService])
    // see LocalBinder below

    //startService(serviceIntent)   // call this only, to keep service active after onDestroy()/unbindService()
    val intentReceiverActivityClass = getClass.asInstanceOf[java.lang.Class[Activity]]    // -> class of method onIntent(), needed to receive nfc-events
    appServiceConnection = new ServiceConnection { 
      def onServiceDisconnected(className:ComponentName) { 
        if(D) Log.i(TAG, "onCreate onServiceDisconnected set appService=null ####")
        appService = null
        serviceFailedFkt
      } 

      def onServiceConnected(className:ComponentName, rawBinder:IBinder) { 
        if(D) Log.i(TAG, "onCreate onServiceConnected localBinder.getService ...")
        appService = rawBinder.asInstanceOf[FileExchangeService#LocalBinder].getService
        if(appService==null) {
          Log.e(TAG, "onCreate onServiceConnected no interface to service, appService==null")
          AndrTools.runOnUiThread(activity) { () =>
            Toast.makeText(activity, "failed to get service interface from binder", Toast.LENGTH_LONG).show    // todo: create more 'human' text
          }
          return
        }
        if(D) Log.i(TAG, "onCreate onServiceConnected got appService object")
        appService.context = activity
        appService.activityMsgHandler = msgFromServiceHandler
        appService.setSendFiles(selectedFileStringsArrayList)

        // instantiate RFCommService
        // both services will use msgFromServiceHandler to communicate back to the activity (todo: maybe better use separate handlers?)
        // make sure the UUID's for the mBluetoothAdapter.listenUsingxxxxx have been set
        rfCommHelper = new RFCommHelper(activity, msgFromServiceHandler, 
                                        prefsPrivate, prefsSharedP2pBt, prefsSharedP2pWifi,
                                        serviceInitializedFkt, serviceFailedFkt, 
                                        appService,
                                        intentReceiverActivityClass,
                                        audioConfirmSound,
                                        RFCommHelper.RADIO_BT| RFCommHelper.RADIO_P2PWIFI| RFCommHelper.RADIO_NFC,
                                        "AnyMimeSecure",   "00001101-afac-11de-9991-0800200c9a66",
                                        "AnyMimeInsecure", "00001101-0000-1000-8000-00805F9B3466")

        val anyMimeApp = getApplication.asInstanceOf[AnyMimeApp]
        if(D) Log.i(TAG, "onCreate anyMimeApp="+anyMimeApp)
        if(anyMimeApp!=null) {
          anyMimeApp.rfCommHelper = rfCommHelper
          if(D) Log.i(TAG, "anyMimeApp="+anyMimeApp+" anyMimeApp.rfCommHelper="+anyMimeApp.rfCommHelper)
        }

        appService.rfCommHelper = rfCommHelper
      }
    } 
    if(appServiceConnection!=null) {
      if(D) Log.i(TAG, "onCreate bindService ...")
      bindService(serviceIntent, appServiceConnection, Context.BIND_AUTO_CREATE)
      if(D) Log.i(TAG, "onCreate bindService done")
    } else {
      Log.e(TAG, "onCreate bindService failed")
      serviceFailedFkt
    }

    if(D) Log.i(TAG, "onCreate DONE ####")
  }

  override def onResume() = synchronized {
    if(D) Log.i(TAG, "onResume")
    super.onResume

    if(rfCommHelper!=null) {
      // make sure the UUID's for the mBluetoothAdapter.listenUsingxxxxx must have been set
      rfCommHelper.onResume
    }
    else
      Log.e(TAG, "onResume rfCommHelper==null #####")

    activityResumed = true
  }

  override def onPause() = synchronized {
    if(D) Log.i(TAG, "onPause")
    super.onPause
    activityResumed = false

    if(rfCommHelper!=null) 
      rfCommHelper.onPause
    else
      Log.e(TAG, "onPause rfCommHelper==null #####")
  }

  override def onDestroy() {
    if(D) Log.i(TAG, "onDestroy")

    if(rfCommHelper!=null) 
      rfCommHelper.onDestroy
    else
      Log.e(TAG, "onDestroy rfCommHelper==null #####")

    if(appServiceConnection!=null) {
      unbindService(appServiceConnection)
      // note: our service will exit here, since we DID NOT use startService in front of bindService - this is our intent!
      if(D) Log.i(TAG, "onDestroy unbindService done")
      appServiceConnection=null
    }

    super.onDestroy
  }

  private def checkLayout() {
    // called by onCreate and onConfigurationChanged
    // we don't want to show the applogo if the height is very low (nexus one in landscape mode)
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
    if(D) Log.i(TAG, "onNewIntent intent="+intent+" intent.getAction="+intent.getAction+" rfCommHelper="+rfCommHelper)
    if(rfCommHelper!=null) 
      if(rfCommHelper.onNewIntent(intent))
        return
    // if not a nfc-intent
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
              val intent = new Intent(activity, classOf[ShowSelectedSlotActivity])
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
            val intent = new Intent(activity, classOf[ShowSelectedSlotActivity])
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

    if(rfCommHelper!=null) 
      if(rfCommHelper.onActivityResult(requestCode, resultCode, intent))
        return

    requestCode match {
      case REQUEST_SELECT_DEVICE_AND_CONNECT =>
        if(D) Log.i(TAG, "onActivityResult REQUEST_SELECT_DEVICE_AND_CONNECT")
        if(resultCode!=Activity.RESULT_OK) {
          Log.e(TAG, "REQUEST_SELECT_DEVICE_AND_CONNECT resultCode!=Activity.RESULT_OK ="+resultCode)
        } else if(intent==null) {
          Log.e(TAG, "onActivityResult REQUEST_SELECT_DEVICE_AND_CONNECT intent==null")
        } else {
          val bundle = intent.getExtras()
          if(bundle==null) {
            Log.e(TAG, "onActivityResult REQUEST_SELECT_DEVICE_AND_CONNECT intent.getExtras==null")
          } else {
            val device = bundle.getString("device")
            //if(D) Log.i(TAG, "REQUEST_SELECT_DEVICE_AND_CONNECT device="+device)
            if(device==null) {
              Log.e(TAG, "onActivityResult REQUEST_SELECT_DEVICE_AND_CONNECT device==null")
            } else {
              // user has selected one paired device to manually connect to
              val idxCR = device.indexOf("\n")
              if(idxCR<1) {
                Log.e(TAG, "onActivityResult REQUEST_SELECT_DEVICE_AND_CONNECT idxCR<1")
              } else {
                val deviceName = device.substring(0,idxCR)
                var deviceAddr = device.substring(idxCR+1)
                val idxComment = deviceAddr.indexOf(" ")
                if(idxComment>=0)
                  deviceAddr = deviceAddr.substring(0,idxComment)
                val deviceAddrComment = if(idxComment>=0) device.substring(idxCR+1+idxComment+1) else null

                // todo: if activity is currently in sleep mode, wait for it to be resumed
                new Thread() {
                  override def run() {
                    try { Thread.sleep(300) } catch { case ex:Exception => }

                    if(D) Log.i(TAG, "onActivityResult REQUEST_SELECT_DEVICE_AND_CONNECT deviceName="+deviceName+" deviceAddr="+deviceAddr+" deviceAddrComment="+deviceAddrComment+" ####################")              
                    initiatedConnectionByThisDevice = true
                    if(deviceAddrComment!=null && deviceAddrComment.startsWith("wifi")) {
                      // connect to wifi device
                      if(D) Log.i(TAG, "REQUEST_SELECT_DEVICE_AND_CONNECT connectWifi() rfCommHelper.wifiP2pManager="+rfCommHelper.wifiP2pManager)
                      if(rfCommHelper.wifiP2pManager!=null)
                        rfCommHelper.rfCommService.connectWifi(rfCommHelper.wifiP2pManager, deviceAddr, deviceName, false)

                    } else {
                      // connect to bt device
                      val remoteBluetoothDevice = BluetoothAdapter.getDefaultAdapter.getRemoteDevice(deviceAddr)
                      if(remoteBluetoothDevice==null) {
                        Log.e(TAG, "onActivityResult REQUEST_SELECT_DEVICE_AND_CONNECT remoteBluetoothDevice==null")
                      } else {
                        if(D) Log.i(TAG, "REQUEST_SELECT_DEVICE_AND_CONNECT rfCommService.connectBt() ...")
                        rfCommHelper.connectAttemptFromNfc = false  // on connect fail will ask user "fall back to OPP?" only if connect not initiated by nfc
                        rfCommHelper.rfCommService.connectBt(remoteBluetoothDevice)
                      }
                    }
                    
                    /*AndrTools.runOnUiThread(activity) { () =>
                      if(D) Log.i(TAG, "REQUEST_SELECT_DEVICE_AND_CONNECT -> mainViewUpdate")
                      mainViewUpdate
                    }*/
                  }
                }.start                        
              }
            }
          }
        }
        return true   // todo ???

      case REQUEST_READ_CURRENT_SLOT =>
        getArrayListSelectedFileStrings
        if(D) Log.i(TAG, "REQUEST_READ_CURRENT_SLOT -> mainViewUpdate")
        mainViewUpdate
        if(appService!=null)
          appService.setSendFiles(selectedFileStringsArrayList)

      case REQUEST_READ_SELECTED_SLOT_ADD_FILE =>
        getArrayListSelectedFileStrings
        if(D) Log.i(TAG, "REQUEST_READ_SELECTED_SLOT_ADD_FILE -> mainViewUpdate")
        mainViewUpdate

        if(D) Log.i(TAG, "REQUEST_READ_SELECTED_SLOT_ADD_FILE add addFilePathNameString="+addFilePathNameString)
        selectedFileStringsArrayList add addFilePathNameString

        persistArrayListSelectedFileStrings
        showSelectedFiles
        if(appService!=null)
          appService.setSendFiles(selectedFileStringsArrayList)
    }
  }

  override def onCreateDialog(id:Int) :Dialog = {
    val menuDialog = new Dialog(this)

    if(id==DIALOG_ABOUT) {
      if(D) Log.i(TAG, "onCreateDialog id==DIALOG_ABOUT")
      menuDialog.setTitle("About: Anymime")
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
          Toast.makeText(activity, errMsg, Toast.LENGTH_LONG).show
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
                Toast.makeText(activity, errMsg, Toast.LENGTH_LONG).show
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
            // disconnect active connection/transmission
            if(rfCommHelper!=null && rfCommHelper.rfCommService!=null) {
              rfCommHelper.rfCommService.stopActiveConnection
              if(D) Log.i(TAG, "offerUserToDisconnect -> mainViewUpdate")
              mainViewUpdate
            } else {
              Log.e(TAG, "offerUserToDisconnect BUTTON_POSITIVE failed to stopActiveConnection")
            }
          case DialogInterface.BUTTON_NEGATIVE =>
            // do nothing, just continue
        }
      }
    }

    val alertDialog = new AlertDialog.Builder(activity).setTitle("Disconnect?")
                           .setPositiveButton("Yes",dialogClickListener)
                           .setNegativeButton("No", dialogClickListener)
    if(rfCommHelper.rfCommService.state==RFCommHelperService.STATE_CONNECTING)
      alertDialog.setMessage("Are you sure you want to abort the ongoing connect request?")
    else
      alertDialog.setMessage("Are you sure you want to abort the ongoing transmission?")
    alertDialog.show     
	}

	override def onBackPressed() {
    if(D) Log.i(TAG, "onBackPressed()")
    if(rfCommHelper!=null && rfCommHelper.rfCommService!=null &&
       (rfCommHelper.rfCommService.state==RFCommHelperService.STATE_CONNECTED || 
        rfCommHelper.rfCommService.state==RFCommHelperService.STATE_CONNECTING)) {
      // ask the user to confirm before disconnecting active transmission
      offerUserToDisconnect
      return
    }
    // this activity will be closed here
    super.onBackPressed 
	}

  private def persistArrayList(arrayList:ArrayList[String], persistName:String) {
    if(prefsPrivateEditor!=null) {
      val iterator = arrayList.iterator 
      var stringBuilder = new StringBuilder()
      while(iterator.hasNext) {
        if(stringBuilder.size>0)
          stringBuilder append ","
        stringBuilder append iterator.next
      }
      if(D) Log.i(TAG, "persistArrayList stringBuilder="+stringBuilder.toString)
      prefsPrivateEditor.putString(persistName,stringBuilder.toString)
      prefsPrivateEditor.commit
    }
  }

  private def showSelectedFiles() {
    if(D) Log.i(TAG, "showSelectedFiles selectedFileStringsArrayList="+selectedFileStringsArrayList)
    val intent = new Intent(activity, classOf[ShowSelectedFilesActivity])
      val bundle = new Bundle()
      bundle.putStringArrayList("selectedFilesStringArrayList", selectedFileStringsArrayList)
      intent.putExtras(bundle)
    startActivityForResult(intent, REQUEST_READ_CURRENT_SLOT) // -> ShowSelectedFilesActivity -> onActivityResult()
  }

  private def getArrayListSelectedFileStrings() {
    val selectedSlotString = prefsPrivate.getString("selectedSlot", null)
    selectedSlot = if(selectedSlotString!=null) selectedSlotString.toInt else 0
    if(selectedSlot<0 || selectedSlot>ShowSelectedSlotActivity.MAX_SLOTS)
      selectedSlot = 0
    if(D) Log.i(TAG, "getArrayListSelectedFileStrings selectedSlot="+selectedSlot)
    if(selectedFileStringsArrayList!=null)
      selectedFileStringsArrayList.clear
    selectedSlotName = prefsPrivate.getString("fileSlotName"+selectedSlot, "")

    // read the lists of selected files
    var commaSeparatedString = prefsPrivate.getString("fileSlot"+selectedSlot, null)
    if(D) Log.i(TAG, "getArrayListSelectedFileStrings commaSeparatedString="+commaSeparatedString)
    if(commaSeparatedString!=null) {
      commaSeparatedString = commaSeparatedString.trim
      if(commaSeparatedString.size>0) {
        val resultArray = commaSeparatedString split ","
        if(resultArray!=null) {
          if(D) Log.i(TAG,"getArrayListSelectedFileStrings prefsPrivate selectedFilesStringArrayList size="+resultArray.size)
          for(filePathString <- resultArray) {
            if(filePathString!=null)
              selectedFileStringsArrayList add filePathString.trim
          }
        }
      }
    }
  }

  private def persistArrayListSelectedFileStrings() {
    if(prefsPrivate!=null && prefsPrivateEditor!=null) {
      val selectedSlotString = prefsPrivate.getString("selectedSlot", null)
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
      prefsPrivateEditor.putString("fileSlot"+selectedSlot,stringBuilder.toString)
      prefsPrivateEditor.commit
    }
  }

  // this is one service-to-activity handler for all services
  // TODO: we need certain standards for RFCommHelper/service as well as our specific appService to be able to post msgs to the user
  // msgFromServiceHandler initialized during startup by rfCommService.setActivityMsgHandler()
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
              if(D) Log.i(TAG, "STATE_CONNECTED -> mainViewUpdate")
              mainViewUpdate

              // switch off button bar, switch on progressBar
              if(quickBarView!=null)
                quickBarView.setVisibility(View.GONE)
              if(progressBarView!=null)
                progressBarView.setVisibility(View.VISIBLE)

              //if(D) Log.i(TAG, "RFCommHelperService.STATE_CONNECTED: reset startTime")
              startTime = SystemClock.uptimeMillis

            case RFCommHelperService.STATE_LISTEN | RFCommHelperService.STATE_NONE =>
              if(D) Log.i(TAG, "handleMessage MESSAGE_STATE_CHANGE: NOT CONNECTED -> mainViewUpdate")
              mainViewUpdate          
          }

        case RFCommHelperService.MESSAGE_DEVICE_NAME =>
          // note: MESSAGE_DEVICE_NAME is immediately followed by a MESSAGE_STATE_CHANGE/STATE_CONNECTED message
          mConnectedDeviceAddr = msg.getData.getString(RFCommHelperService.DEVICE_ADDR)
          mConnectedDeviceName = msg.getData.getString(RFCommHelperService.DEVICE_NAME)
          //val pairedBtOnly = msg.getData.getBoolean(RFCommHelperService.SOCKET_TYPE)
          if(D) Log.i(TAG, "handleMessage MESSAGE_DEVICE_NAME="+mConnectedDeviceName+" addr="+mConnectedDeviceAddr)

          // show "... has connected" toast only, if we did NOT initiate the connection
          if(!initiatedConnectionByThisDevice) {
            Toast.makeText(getApplicationContext, ""+mConnectedDeviceName+" has connected", Toast.LENGTH_LONG).show
                                                  // todo: why do I see a ip4 address here?
          }

        case RFCommHelperService.MESSAGE_YOURTURN =>
          if(D) Log.i(TAG, "handleMessage MESSAGE_YOURTURN reset startTime -> mainViewUpdate")
          if(progressBarView!=null)
            progressBarView.setProgress(0)
          mainViewUpdate          

        case RFCommHelperService.MESSAGE_USERHINT1 =>
          val writeMessage = msg.obj.asInstanceOf[String]
          if(userHint1View!=null && writeMessage!=null) {
            //if(D) Log.i(TAG, "MESSAGE_USERHINT1 userHint1View.setText writeMessage="+writeMessage)
            userHint1View.setText(writeMessage)
          }

        case RFCommHelperService.MESSAGE_USERHINT2 =>
          val readMessage = msg.obj.asInstanceOf[String]
          if(userHint2View!=null && readMessage!=null) {
            userHint2View.setText(readMessage)
          }

        case RFCommHelperService.CONNECTION_START =>
          // our outgoing connect attempt is starting now
          val mConnectingDeviceAddr = msg.getData.getString(RFCommHelperService.DEVICE_ADDR)
          val mConnectingDeviceName = msg.getData.getString(RFCommHelperService.DEVICE_NAME)
          if(D) Log.i(TAG, "handleMessage CONNECTION_START: "+mConnectingDeviceName+" addr="+mConnectingDeviceAddr+" -> mainViewUpdate")
          mainViewUpdate

          if(userHint1View!=null)
            userHint1View.setText("connecting to "+mConnectingDeviceName+" "+mConnectingDeviceAddr)
          // show a little round progress bar animation
          if(userHint2View!=null)
            userHint2View.setVisibility(View.GONE)
          if(userHint3View!=null)
            userHint3View.setVisibility(View.GONE)
          if(simpleProgressBarView!=null)
            simpleProgressBarView.setVisibility(View.VISIBLE)
          if(D) Log.i(TAG, "handleMessage CONNECTION_START done")

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
          if(D) Log.i(TAG, "CONNECTION_FAILED -> mainViewUpdate")
          if(audioConfirmSound!=null)
            audioConfirmSound.start
          Toast.makeText(getApplicationContext, "Failed to connect to "+mDisconnectedDeviceName+" "+mDisconnectedDeviceAddr, Toast.LENGTH_LONG).show
          mainViewUpdate

          if(!rfCommHelper.connectAttemptFromNfc) {
            // coming from REQUEST_SELECT_DEVICE_AND_CONNECT (and not NFC-initiated connect)
            // therefor we ask: "fall back to OPP?"
            val dialogClickListener = new DialogInterface.OnClickListener() {
              override def onClick(dialog:DialogInterface, whichButton:Int) {
                whichButton match {
                  case DialogInterface.BUTTON_POSITIVE =>
                    val remoteBluetoothDevice = BluetoothAdapter.getDefaultAdapter.getRemoteDevice(mDisconnectedDeviceAddr)
                    if(remoteBluetoothDevice==null) {
                      Log.e(TAG, "REQUEST_SELECT_PAIRED_DEVICE_AND_OPP_TO remoteBluetoothDevice==null")
                    } else {
                      // use build-in opex
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

            new AlertDialog.Builder(activity).setTitle("No Anymime device found")
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
          if(D) Log.i(TAG, "DEVICE_DISCONNECT -> mainViewUpdate")
          mainViewUpdate          

          if(receivedFileUriStringArrayList.size<1) {
            Log.e(TAG, "handleMessage MESSAGE_RECEIVED_FILE receivedFileUriStringArrayList.size<1")
            //TODO: numberOfSentFiles from FileExchangeService
            //Toast.makeText(getApplicationContext, "Received 0 files, sent "+rfCommService.numberOfSentFiles+" files", Toast.LENGTH_LONG).show
            return
          }

          //TODO: numberOfSentFiles from FileExchangeService
          //Toast.makeText(getApplicationContext, "Received "+receivedFileUriStringArrayList.size+" files, sent "+rfCommService.numberOfSentFiles+" files", Toast.LENGTH_LONG).show
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
          val intent = new Intent(activity, classOf[ShowReceivedFilesPopupActivity])
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
                
                // todo: we might want to auto-open optical key-verification
              }
            }
          }
          intent.putExtras(bundle)
          startActivity(intent)

        case RFCommHelperService.UI_UPDATE =>
          if(D) Log.i(TAG, "UI_UPDATE -> mainViewUpdate")
          mainViewUpdate

        case RFCommHelperService.ALERT_MESSAGE =>
          val alertMessage = msg.obj.asInstanceOf[String]
          if(D) Log.i(TAG, "handleMessage ALERT_MESSAGE ["+alertMessage+"] audioConfirmSound="+audioConfirmSound)
          if(alertMessage!=null) {
            if(audioConfirmSound!=null)
              audioConfirmSound.start
            Toast.makeText(getApplicationContext, "ALERT "+alertMessage, Toast.LENGTH_LONG).show
          }
        
        case RFCommHelperService.CONNECTING =>
          val otherDeviceInfo = msg.obj.asInstanceOf[String]
          if(D) Log.i(TAG, "handleMessage CONNECTING otherDeviceInfo="+otherDeviceInfo+" ################################")
          if(radioLogoView!=null)
            radioLogoView.setImageResource(R.drawable.bluetooth)
          if(userHint1View!=null)
            userHint1View.setText("waiting for "+otherDeviceInfo)
          // show a little round progress bar
          if(userHint2View!=null)
            userHint2View.setVisibility(View.GONE)
          if(userHint3View!=null)
            userHint3View.setVisibility(View.GONE)
          if(simpleProgressBarView!=null) {
            simpleProgressBarView.setVisibility(View.VISIBLE)
            if(D) Log.i(TAG, "handleMessage CONNECTING simpleProgressBarView now visible ################################")
          }
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
    if(rfCommHelper==null || rfCommHelper.rfCommService==null) {
      if(D) Log.i(TAG, "mainViewUpdate rfCommService==null || rfCommHelper.rfCommService==null")
    } else {
      if(D) Log.i(TAG, "mainViewUpdate rfCommService.activityResumed="+rfCommHelper.rfCommService.activityResumed+" rfCommService.state="+rfCommHelper.rfCommService.state)
    }
      
    if(rfCommHelper!=null && rfCommHelper.rfCommService!=null && 
        (rfCommHelper.rfCommService.state==RFCommHelperService.STATE_CONNECTED || rfCommHelper.rfCommService.state==RFCommHelperService.STATE_CONNECTING)) {
      if(rfCommHelper.rfCommService.connectedRadio==2)
        mainViewWifiDirect
      else
        mainViewBluetooth
    } else {
      mainViewDefaults
    }
  }

  private def mainViewDefaults() {
    if(D) Log.i(TAG, "mainViewDefaults")
    if(radioLogoView!=null) {
      if(rfCommHelper!=null && rfCommHelper.isNfcEnabled) {
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
      val statFs = new android.os.StatFs(android.os.Environment.getExternalStorageDirectory().getPath())
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
      if(rfCommHelper!=null && rfCommHelper.isNfcEnabled) {
        userHint3View.setTypeface(null, Typeface.BOLD)  // make bold
        userHint3View.setTextSize(18)  // bigger
        userHint3View.setText("NFC ready: Tap devices to share")   
      }
      else {
        userHint3View.setTypeface(null, 0)  // not bold
        userHint3View.setTextSize(15)  // normal size
        userHint3View.setText("NFC disabled - please connect manually")
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
    if(D) Log.i(TAG, "mainViewBluetooth")
    if(radioLogoView!=null) {
      radioLogoView.setImageResource(R.drawable.bluetooth)
    	radioLogoView.setAnimation(null)
    }
    
    //if(mainView!=null)
    //  mainView.setBackgroundDrawable(getResources().getDrawable(R.drawable.layer_list_blue))

    if(userHint1View!=null)
      userHint1View.setText("")

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
  
  private def mainViewWifiDirect() {
    if(D) Log.i(TAG, "mainViewWifiDirect")
    if(radioLogoView!=null) {
      radioLogoView.setImageResource(R.drawable.wifi_direct_logo)
    	radioLogoView.setAnimation(null)
    }
    
    //if(mainView!=null)
    //  mainView.setBackgroundDrawable(getResources().getDrawable(R.drawable.layer_list_blue))

    if(userHint1View!=null)
      userHint1View.setText("")

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

