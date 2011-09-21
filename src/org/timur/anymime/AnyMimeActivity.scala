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

class AnyMimeActivity extends Activity {
  private val TAG = "AnyMimeActivity"
  private val D = true

  private val blobDeliverChunkSize = 10*1024
  private val DIALOG_ABOUT = 2

  private val REQUEST_ENABLE_BT = 3
  private val REQUEST_SELECT_PAIRED_DEVICE_AND_CONNECT_TO = 5
  private val REQUEST_EDIT_SELECTED_FILES = 6

  private val mimeTypeMap = MimeTypeMap.getSingleton()

  private var mTitleView:TextView = null
  private var mBluetoothAdapter:BluetoothAdapter = null
  private var serviceConnection:ServiceConnection = null
  private var btService:RFCommHelperService = null
  private var mBtAdapter:BluetoothAdapter = null
  private var mConnectedDeviceAddr:String = null
  private var firstBtActor = false
  private var mNfcAdapter:NfcAdapter = null
  private var nfcActionWanted = false
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
//private var fastAnimation:Animation = null
  private var rootView:View = null
  private var radioLogoView:ImageView = null
  private var userHint1View:TextView = null
  private var userHint2View:TextView = null
  private var userHint3View:TextView = null
  private var simpleProgressBarView:ProgressBar = null
  private var progressBarView:ProgressBar = null
  private var quickBarView:HorizontalScrollView = null

  @volatile private var blobDeliverId:Long = 0
  private var arrayListSelectedFileStrings = new ArrayList[String]()
  private var receivedFileUriStringArrayList = new ArrayList[String]()
  private var numberOfSentFiles = 0

  private var audioConfirmSound:MediaPlayer = null
  private var selectedSlot = 0
  private var selectedSlotName = ""
  private var initiatedConnection:Boolean = false

  @volatile private var startTime:Long = 0
  @volatile private var receivedAnyData:Boolean = false
  private var kbytesPerSecond:Long=0

  override def onCreate(savedInstanceState:Bundle) {
    super.onCreate(savedInstanceState)
    val packageInfo = getPackageManager.getPackageInfo(getPackageName, 0)
    if(D) Log.i(TAG, "onCreate versionName="+packageInfo.versionName)
    context = this
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.main)
    //setContentView(new BGView(this))    // renderscript

    audioConfirmSound = MediaPlayer.create(context, R.raw.textboxbloop8bit)

    // prepare access to prefSettings
    if(prefSettings==null) {
      prefSettings = getSharedPreferences(PREFS_SETTINGS, Context.MODE_WORLD_WRITEABLE)
      if(prefSettings!=null)
        prefSettingsEditor = prefSettings.edit
    }

    // get local Bluetooth adapter
    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter
    // If the adapter is null, then Bluetooth is not supported (mBluetoothAdapter must not be null, even if turned off)
    if(mBluetoothAdapter == null) {
      if(D) Log.i(TAG, "onCreate mBluetoothAdapter not available")
      Toast.makeText(this, "Bluetooth not available", Toast.LENGTH_LONG).show
      finish
      return
    }

    if(D) Log.i(TAG, "onCreate mBluetoothAdapter="+mBluetoothAdapter)
    mBtAdapter = BluetoothAdapter.getDefaultAdapter

    if(android.os.Build.VERSION.SDK_INT>=10) {
      if(D) Log.i(TAG, "onCreate nfc setup...")
      try {
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if(D) Log.i(TAG, "onCreate nfc support available")
        // continue to setup nfc in nfcBtServiceSetup()
      } catch {
        case ncdferr: java.lang.NoClassDefFoundError =>
          Log.e(TAG, "onCreate NfcAdapter.getDefaultAdapter(this) failed "+ncdferr)
      }
    }

    rootView = findViewById(R.id.main)
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
	  //fastAnimation = AnimationUtils.loadAnimation(this, R.anim.fast_anim)

    getArrayListSelectedFileStrings

/*
    {
      // reading the list of most recently received files
      val commaSeparatedString = prefSettings.getString("receivedFileUris", null)
      if(commaSeparatedString!=null) {
        val resultArray = commaSeparatedString split ","
        if(resultArray!=null) {
          if(D) Log.i(TAG,"onCreate prefSettings receivedFileUriStringArrayList resultArray.size="+resultArray.size)
          receivedFileUriStringArrayList.clear
          for(filePathString <- resultArray) {
            if(filePathString!=null) {
              receivedFileUriStringArrayList add filePathString
            }
          }
        }
      }
      if(receivedFileUriStringArrayList==null) {
        if(D) Log.i(TAG,"onCreate receivedFileUriStringArrayList == null")
      } else {
        if(D) Log.i(TAG,"onCreate receivedFileUriStringArrayList.size="+receivedFileUriStringArrayList.size)
      }
    }
*/

    receiveFilesHistoryLength = receiveFilesHistory.load(context)

    // all clickable areas

    AndrTools.buttonCallback(this, R.id.buttonSendFiles) { () =>
      if(D) Log.i(TAG, "onClick buttonSendFiles")
      showSelectedFiles
    }

    AndrTools.buttonCallback(this, R.id.buttonReceivedFiles) { () =>
      if(D) Log.i(TAG, "onClick buttonReceivedFiles")
      val intent = new Intent(context, classOf[ShowReceivedFilesPopupActivity])
      val bundle = new Bundle()
      bundle.putStringArrayList("listOfUriStrings", receivedFileUriStringArrayList)
      // hand over .asc file from most recent delivery
      if(arrayListSelectedFileStrings!=null && arrayListSelectedFileStrings.size>0) {
        val iterator = arrayListSelectedFileStrings.iterator 
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

    AndrTools.buttonCallback(this, R.id.buttonManualConnect) { () =>
      if(D) Log.i(TAG, "onClick buttonManualConnect")
      // select one device from list of paired/bonded devices and connect to it
      // but only if there is no active bt-connection yet
      if(mConnectedDeviceAddr!=null) {
        Toast.makeText(context, "You are Bluetooth connected already "+mConnectedDeviceAddr, Toast.LENGTH_LONG).show
        return
      }
      val intent = new Intent(context, classOf[SelectPairedDevicePopupActivity])
      startActivityForResult(intent, REQUEST_SELECT_PAIRED_DEVICE_AND_CONNECT_TO) // -> onActivityResult()
    }

    AndrTools.buttonCallback(this, R.id.buttonBluetoothSettings) { () =>
      if(D) Log.i(TAG, "onClick buttonBluetoothSettings")
      val bluetoothSettingsIntent = new Intent
      bluetoothSettingsIntent.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
      startActivity(bluetoothSettingsIntent)
    }

    AndrTools.buttonCallback(this, R.id.buttonAbout) { () =>
      if(D) Log.i(TAG, "onClick buttonAbout")
      showDialog(DIALOG_ABOUT)
    }

    AndrTools.buttonCallback(this, R.id.applogo) { () =>
      if(D) Log.i(TAG, "onClick applogoView")
      showDialog(DIALOG_ABOUT)
    }

    AndrTools.buttonCallback(this, progressBarView) { () =>
      if(D) Log.i(TAG, "onClick progressBarView")
      offerUserToDisconnect
    }

    checkLayout
	  mainViewUpdate
  }

  override def onStart() {
    super.onStart
    if(D) Log.i(TAG, "onStart")
    if (mBluetoothAdapter.isEnabled) {
      if(btService == null)  
        nfcBtServiceSetup
    } else {
      // BT is off - user must confirm to have BT enabled - nfcBtServiceSetup() will then be called during onActivityResult
      val enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
      startActivityForResult(enableIntent, REQUEST_ENABLE_BT)
    }
  }

  override def onResume() = synchronized {
    if(D) Log.i(TAG, "onResume mNfcAdapter="+mNfcAdapter+" nfcActionWanted="+nfcActionWanted)
    super.onResume
    if(mNfcAdapter!=null && mNfcAdapter.isEnabled && nfcActionWanted) {
      mNfcAdapter.enableForegroundDispatch(this, nfcPendingIntent, nfcFilters, nfcTechLists)
      if(nfcForegroundPushMessage!=null) {
        mNfcAdapter.enableForegroundNdefPush(this, nfcForegroundPushMessage)
        if(D) Log.i(TAG, "onResume enableForegroundNdefPush done")
      }
    } else {
      // we got no NFC, do not offer it
    }

    mainViewUpdate
    activityResumed = true
    if(btService!=null) {
      btService.acceptAndConnect = true
      if(D) Log.i(TAG, "onResume set btService.acceptAndConnect="+btService.acceptAndConnect)
    } else {
      Log.e(TAG, "onResume btService==null, acceptAndConnect not set")
    }
    if(D) Log.i(TAG, "onResume done")
  }

  override def onPause() = synchronized {
    if(D) Log.i(TAG, "onPause")
    super.onPause
    if(mNfcAdapter!=null && mNfcAdapter.isEnabled && !nfcActionWanted) {
      mNfcAdapter.disableForegroundDispatch(this)
      if(nfcForegroundPushMessage!=null) {
        mNfcAdapter.disableForegroundNdefPush(this)
        if(D) Log.i(TAG, "ON PAUSE disableForegroundNdefPush done")
      }
    }
    activityResumed = false
    if(btService!=null) {
      btService.acceptAndConnect = false
      Log.e(TAG, "onPause btService.acceptAndConnect cleared")
    } else {
      Log.e(TAG, "onResume btService==null, acceptAndConnect not cleared")
    }
    System.gc
    if(D) Log.i(TAG, "onPause done")
  }

  override def onDestroy() {
    if(D) Log.i(TAG, "onDestroy")
    if(btService!=null)
      btService.context = null
    if(serviceConnection!=null) {
      unbindService(serviceConnection)
      // note: our service will exit here, since we DID NOT use startService in front of bindService
      if(D) Log.i(TAG, "onDestroy unbindService done")
      serviceConnection=null
    }
    super.onDestroy
  }

  private def checkLayout() {
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

  override def onNewIntent(intent: Intent) {
    if(D) Log.i(TAG, "onNewIntent Discovered tag with intent: " + intent)
    if(android.os.Build.VERSION.SDK_INT>=10) {
      if(mNfcAdapter!=null && mNfcAdapter.isEnabled) {
        // possible, as a result of NfcAdapter.ACTION_NDEF_DISCOVERED
        val remoteBtAddress = NfcHelper.checkForNdefAction(context, intent, btService, mBluetoothAdapter)
        if(remoteBtAddress!=null) {
          btService.acceptAndConnect=true
          if(D) Log.i(TAG, "onNewIntent NfcHelper found remoteBtAddress="+remoteBtAddress+" acceptAndConnect="+btService.acceptAndConnect)

          // play audio notification (as earliest possible feedback for nfc activity)
          val mediaPlayer = MediaPlayer.create(context, R.raw.textboxbloop8bit) // non-alert
          if(mediaPlayer!=null)
            mediaPlayer.start


          // visually indicate to both users, that a connect attempt is taking place
          // mainViewUpdate  // think this is not required here, because nobody is yet connected

          // todo: show some sort of "bt-connect-ProgressDialog" as indication that a connection is being build up

          def remoteBluetoothDevice = mBluetoothAdapter.getRemoteDevice(remoteBtAddress)
          if(remoteBluetoothDevice!=null) {
            if(mBluetoothAdapter.getAddress > remoteBluetoothDevice.getAddress) {
              // our local btAddr is > than the remote btAddr: we become the actor and we will bt-connect
              // our activity may still be in onPause mode due to NFC activity: sleep a bit before 
              new Thread() {
                override def run() {
                  try { Thread.sleep(100); } catch { case ex:Exception => }
                  if(D) Log.i(TAG, "onNewIntent runOnUiThread connecting...")
                  val secure=true
                  btService.connect(remoteBluetoothDevice, secure)
                }
              }.start                        

            } else {
              // our local btAddr is < than the remote btAddr: we just wait for a bt-connect request
              if(D) Log.i(TAG, "onNewIntent passively waiting for incoming connect request...")

              // our activity may still be in onPause mode due to NFC activity: sleep a bit before 
              new Thread() {
                override def run() {
                  try { Thread.sleep(100); } catch { case ex:Exception => }
                  if(D) Log.i(TAG, "onNewIntent runOnUiThread update user...")
                  context.asInstanceOf[Activity].runOnUiThread(new Runnable() {
                    override def run() {
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
                  })
                  
                  // todo: what if no connection can be ever established? we will have 'waiting for ...' displayed forever
                }
              }.start                        
            }
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
        if(D) Log.i(TAG, "onActivityResult - REQUEST_ENABLE_BT")
        // When the request to enable Bluetooth returns
        if (resultCode == Activity.RESULT_OK) {
          // Bluetooth is now enabled, so set up a chat session
          nfcBtServiceSetup
        } else {
          // User did not enable Bluetooth or an error occured
          if(D) Log.i(TAG, "onActivityResult - BT not enabled")
          Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show
          finish
        }
/*
      // we might want to offer app settings later
      case REQUEST_SETTINGS =>
        if(D) Log.i(TAG, "onActivityResult - REQUEST_SETTINGS")
        // we don't care for any activity result 
        // but "org.timur.btgrouplink.settings" may have been modified
        // we need to check all settings that we care for (in particular "auto_connect")
*/

      case REQUEST_SELECT_PAIRED_DEVICE_AND_CONNECT_TO =>
        if(D) Log.i(TAG, "onActivityResult - REQUEST_SELECT_PAIRED_DEVICE_AND_CONNECT_TO")
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
                  if(D) Log.i(TAG, "REQUEST_SELECT_PAIRED_DEVICE_AND_CONNECT_TO btService.connect() ...")
                  initiatedConnection = true
                  btService.connect(remoteBluetoothDevice)
                }
              }
            }
          }
        }

      case REQUEST_EDIT_SELECTED_FILES =>
        getArrayListSelectedFileStrings
        mainViewUpdate
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
        textView.setText(("v"+packageInfo.versionName).asInstanceOf[CharSequence],TextView.BufferType.NORMAL)
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

      // close button
      val btnClose = menuDialog.findViewById(R.id.buttonClose)
      if(btnClose!=null) {
        btnClose.setOnClickListener(new View.OnClickListener() {
          override def onClick(view:View) {
            dismissDialog(id)
          }
        })
      }
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
            if(btService!=null)
              btService.stopActiveConnection()
          case DialogInterface.BUTTON_NEGATIVE =>
            // do nothing, continue the transission
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
    if(btService!=null && btService.state==RFCommHelperService.STATE_CONNECTED) {
      // ask the user to confirm before disconnecting active transmission
      offerUserToDisconnect
      // activity will not be closed here
    } else {
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
    // call ShowSelectedFilesActivity and hand over arrayListSelectedFileStrings
    val intent = new Intent(context, classOf[ShowSelectedFilesActivity])
    val bundle = new Bundle()
    bundle.putStringArrayList("selectedFilesStringArrayList", arrayListSelectedFileStrings)
    intent.putExtras(bundle)
    if(D) Log.i(TAG, "showSelectedFiles arrayListSelectedFileStrings="+arrayListSelectedFileStrings)
    startActivityForResult(intent, REQUEST_EDIT_SELECTED_FILES) // -> onActivityResult()
  }

  private def getArrayListSelectedFileStrings() {
    val selectedSlotString = prefSettings.getString("selectedSlot", null)
    selectedSlot = if(selectedSlotString!=null) selectedSlotString.toInt else 0
    if(selectedSlot<0 || selectedSlot>ShowSelectedSlotActivity.MAX_SLOTS)
      selectedSlot = 0
    if(D) Log.i(TAG, "getArrayListSelectedFileStrings selectedSlot="+selectedSlot)
      arrayListSelectedFileStrings.clear
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
              arrayListSelectedFileStrings add filePathString.trim
          }
        }
      }
    }
  }

  private def nfcBtServiceSetup() {
    // we call this not before we know bluetooth is available and fully activated
    if(D) Log.i(TAG, "nfcBtServiceSetup...")

    mConnectedDeviceAddr = null
    firstBtActor = false

    // setup NFC (only for Android 2.3.3+ and only if NFC hardware is available)
    if(android.os.Build.VERSION.SDK_INT>=10 && mNfcAdapter!=null && mNfcAdapter.isEnabled) {
      // Create a generic PendingIntent that will be delivered to this activity (on a different device?)
      // The NFC stack will fill in the intent with the details of the discovered tag 
      // before delivering to this activity.
      nfcPendingIntent = PendingIntent.getActivity(this, 0,
              new Intent(this, getClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0)

      // setup an intent filter for all MIME based dispatches
      val ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
      try {
        if(D) Log.i(TAG, "nfcBtServiceSetup ndef.addDataType...")
        ndef.addDataType("*/*")
        //if(D) Log.i(TAG, "nfcBtServiceSetup ndef.addDataType done")
      } catch {
        case e: MalformedMimeTypeException =>
          Log.e(TAG, "nfcBtServiceSetup ndef.addDataType MalformedMimeTypeException")
          throw new RuntimeException("fail", e)
      }
      nfcFilters = Array(ndef)

      // Setup a tech list for all NfcF tags
      if(D) Log.i(TAG, "nfcBtServiceSetup setup a tech list for all NfcF tags...")
      nfcTechLists = Array(Array(classOf[NfcF].getName))

      // embed our btaddr in a new NdefMessage to be used via enableForegroundNdefPush in onResume
      val btAddress = mBluetoothAdapter.getAddress
      if(D) Log.i(TAG, "nfcBtServiceSetup btAddress="+btAddress)
      if(btAddress==null) {
        val errstr = "Bluetooth address is not available. Nfc setup failed."
        Log.e(TAG, "nfcBtServiceSetup "+errstr)
        Toast.makeText(this, errstr, Toast.LENGTH_LONG).show
      } else {
        nfcForegroundPushMessage = new NdefMessage(Array(NfcHelper.newTextRecord("bt="+btAddress, Locale.ENGLISH, true)))
        nfcActionWanted = true  // enableForegroundNdefPush will happen in onResume
      }
    } else {
      if(D) Log.i(TAG, "nfcBtServiceSetup NOT setting up NFC")
    }

    if(D) Log.i(TAG, "nfcBtServiceSetup startService('RFCommHelperService') ...")
    val serviceIntent = new Intent(this, classOf[RFCommHelperService])
    //startService(serviceIntent)   // call this only, to keep service active after onDestroy()/unbindService()

    serviceConnection = new ServiceConnection { 
      def onServiceConnected(className:ComponentName, rawBinder:IBinder) { 
        if(D) Log.i(TAG, "nfcBtServiceSetup onServiceConnected localBinder.getService ...")
        btService = rawBinder.asInstanceOf[RFCommHelperService#LocalBinder].getService
        if(btService==null) {
          Log.e(TAG, "nfcBtServiceSetup onServiceConnected no interface to service, btService==null")
          Toast.makeText(context, "Error - failed to get service interface from binder", Toast.LENGTH_LONG).show
        } else {
          if(D) Log.i(TAG, "nfcBtServiceSetup onServiceConnected got btService")
          
          btService.context = context
          btService.activityMsgHandler = msgFromServiceHandler

          if(btService.state == RFCommHelperService.STATE_NONE) {
            // start the Bluetooth accept thread(s) (implemented in RFCommHelperService.scala)
            // this is for devices trying to connect to us
            var acceptOnlySecureConnectRequests = true
            //if(prefSettings!=null)
            //  acceptOnlySecureConnectRequests = prefSettings.getBoolean("acceptOnlySecureConnectRequests",true)
            if(D) Log.i(TAG, "nfcBtServiceSetup btService.start acceptOnlySecureConnectReq="+acceptOnlySecureConnectRequests+" ...")
            btService.start(acceptOnlySecureConnectRequests)
          }

          mainViewUpdate          
        }
      } 

      def onServiceDisconnected(className:ComponentName) { 
        if(D) Log.i(TAG, "nfcBtServiceSetup onServiceDisconnected btService = null")
        btService = null
      } 
    } 

    if(serviceConnection!=null) {
      if(D) Log.i(TAG, "nfcBtServiceSetup bindService ...")
      bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
      if(D) Log.i(TAG, "nfcBtServiceSetup bindService done")
    } else {
      if(D) Log.i(TAG, "nfcBtServiceSetup onCreate bindService failed")
    }
  }

  // msgFromServiceHandler initialized during startup by btService.setActivityMsgHandler()
  private final def msgFromServiceHandler = new Handler() {
    override def handleMessage(msg: Message) {
      if(!activityResumed)
        return

      msg.what match {
        case RFCommHelperService.MESSAGE_STATE_CHANGE =>
          //if(D) Log.i(TAG, "handleMessage MESSAGE_STATE_CHANGE: " + msg.arg1)
          msg.arg1 match {
            case RFCommHelperService.STATE_CONNECTED =>
              if(D) Log.i(TAG, "handleMessage MESSAGE_STATE_CHANGE: STATE_CONNECTED")
              
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

              // todo: don't animate the radio log, but maybe some tx/tx-LED
              //if(radioLogoView!=null && fastAnimation!=null)
              //	radioLogoView.setAnimation(fastAnimation)

              if(D) Log.i(TAG, "RFCommHelperService.STATE_CONNECTED: reset startTime --------------------------------")
              startTime = System.currentTimeMillis
              receivedAnyData = false

              firstBtActor = false
              if(mConnectedDeviceAddr!=null && mConnectedDeviceAddr<mBluetoothAdapter.getAddress) {
                firstBtActor = true
                if(D) Log.i(TAG, "handleMessage firstBtActor=true")
                
                // auto-delivery: send selected files (we are the 1st actor)
                deliverFileArray(arrayListSelectedFileStrings)
              } else {
                if(D) Log.i(TAG, "handleMessage firstBtActor=false")
                // todo: new ReceiverIdleCheckThread().start
              }
              //if(D) Log.i(TAG, "handleMessage MESSAGE_STATE_CHANGE: STATE_CONNECTED DONE")

            case RFCommHelperService.STATE_LISTEN | RFCommHelperService.STATE_NONE =>
              if(D) Log.i(TAG, "handleMessage MESSAGE_STATE_CHANGE: NOT CONNECTED")
              mainViewUpdate          
          }

        case RFCommHelperService.MESSAGE_DEVICE_NAME =>
          // note: MESSAGE_DEVICE_NAME is immediately followed by a MESSAGE_STATE_CHANGE/STATE_CONNECTED message
          mConnectedDeviceAddr = msg.getData.getString(RFCommHelperService.DEVICE_ADDR)
          val mConnectedDeviceName = msg.getData.getString(RFCommHelperService.DEVICE_NAME)
          val mConnectedSocketType = msg.getData.getString(RFCommHelperService.SOCKET_TYPE)
          if(D) Log.i(TAG, "handleMessage MESSAGE_DEVICE_NAME="+mConnectedDeviceName+" addr="+mConnectedDeviceAddr)
          // show toast only, if we did not initiate the connection
          if(!initiatedConnection)
            Toast.makeText(getApplicationContext, ""+mConnectedDeviceName+" has connected", Toast.LENGTH_LONG).show

        case RFCommHelperService.MESSAGE_YOURTURN =>
          if(D) Log.i(TAG, "handleMessage MESSAGE_YOURTURN reset startTime ---------------------------------------------")
          startTime = System.currentTimeMillis
          if(progressBarView!=null)
            progressBarView.setProgress(0)
          if(firstBtActor) {
            if(D) Log.i(TAG, "handleMessage MESSAGE_YOURTURN firstBtActor CAN BT DISCONNECT")
            if(btService==null) {
              Log.e(TAG, "handleMessage MESSAGE_YOURTURN btService=null cannot call stopActiveConnection()")
            }
            else {
              btService.stopActiveConnection()
            }
            mainViewUpdate          

          } else {
            if(D) Log.i(TAG, "handleMessage MESSAGE_YOURTURN deliverFileArray("+arrayListSelectedFileStrings+")")
            deliverFileArray(arrayListSelectedFileStrings)
          }

        case RFCommHelperService.MESSAGE_USERHINT1 =>
          def writeMessage = msg.obj.asInstanceOf[String]
          if(userHint1View!=null && writeMessage!=null) {
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
          if(userHint1View!=null)
            userHint1View.setText("connecting to "+mConnectingDeviceName+" "+mConnectingDeviceAddr)
          // show a little round progress bar
          if(userHint2View!=null)
            userHint2View.setVisibility(View.GONE)
          if(userHint3View!=null)
            userHint3View.setVisibility(View.GONE)
          if(simpleProgressBarView!=null)
            simpleProgressBarView.setVisibility(View.VISIBLE)

        case RFCommHelperService.CONNECTION_FAILED =>
          // a connect attempt failed
          val mDisconnectedDeviceAddr = msg.getData.getString(RFCommHelperService.DEVICE_ADDR)
          val mDisconnectedDeviceName = msg.getData.getString(RFCommHelperService.DEVICE_NAME)
          if(D) Log.i(TAG, "handleMessage CONNECTION_FAILED: ["+mDisconnectedDeviceName+"] addr="+mDisconnectedDeviceAddr)
          if(radioLogoView!=null)
          	radioLogoView.setAnimation(null)
          mConnectedDeviceAddr=null
          initiatedConnection = false
          mainViewUpdate

        case RFCommHelperService.MESSAGE_DELIVER_PROGRESS =>
          val progressType = msg.getData.getString(RFCommHelperService.DELIVER_TYPE) // "receive" or "send"
          if(!receivedAnyData && progressType!=null && progressType=="receive") {
            receivedAnyData = true
            startTime = System.currentTimeMillis
          }
          //val deliverId = msg.getData.getLong(RFCommHelperService.DELIVER_ID)
          val progressPercent = msg.getData.getInt(RFCommHelperService.DELIVER_PROGRESS)
          //if(D) Log.i(TAG, "handleMessage MESSAGE_DELIVER_PROGRESS: progressPercent="+progressPercent)
          if(progressBarView!=null)
            progressBarView.setProgress(progressPercent)
          val progressBytes = msg.getData.getLong(RFCommHelperService.DELIVER_BYTES)
          if(userHint3View!=null) {
            val durationSeconds = (System.currentTimeMillis - startTime) / 1000
            if(durationSeconds>0) {
              kbytesPerSecond = (progressBytes/durationSeconds)/1024
              if(D) Log.i(TAG, "handleMessage MESSAGE_DELIVER_PROGRESS progressPercent="+progressPercent+" kbytesPerSecond="+kbytesPerSecond+" ##################################")
              if(kbytesPerSecond>0) {
                userHint3View.setTypeface(null, 0);  // un-bold
                userHint3View.setTextSize(15)  // normal size
                userHint3View.setText(""+(progressBytes/1024)+" KB "+durationSeconds+"s "+kbytesPerSecond+" KB/s")
              }
            }
          }
          // todo: set receiverActivityFlag, to prevent ReceiverIdleCheckThread() from forcing a disconnect (not yet impl.)

        case RFCommHelperService.MESSAGE_RECEIVED_FILE =>
          val receiveFileName = msg.getData.getString(RFCommHelperService.DELIVER_FILENAME)
          if(D) Log.i(TAG, "handleMessage MESSAGE_RECEIVED_FILE: receiveFileName=["+receiveFileName+"]")
          // store receiveFileName so we can show all received files later
          val receiveUriString = msg.getData.getString(RFCommHelperService.DELIVER_URI)
          if(D) Log.i(TAG, "handleMessage MESSAGE_RECEIVED_FILE: receiveUriString=["+receiveUriString+"]")
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

          // audio notification for disconnect
          if(audioConfirmSound!=null)
            audioConfirmSound.start

          // switch off progressBar, switch back on button bar
          if(progressBarView!=null)
            progressBarView.setVisibility(View.GONE)
          if(quickBarView!=null)
            quickBarView.setVisibility(View.VISIBLE)
            
          initiatedConnection = false
          mainViewUpdate          

          if(receivedFileUriStringArrayList.size<1) {
            Log.e(TAG, "handleMessage MESSAGE_RECEIVED_FILE receivedFileUriStringArrayList.size<1")
            Toast.makeText(getApplicationContext, "Received 0 files, sent "+numberOfSentFiles+" files", Toast.LENGTH_LONG).show
            return
          }

          Toast.makeText(getApplicationContext, "Received "+receivedFileUriStringArrayList.size+" files, sent "+numberOfSentFiles+" files", Toast.LENGTH_LONG).show
          if(D) Log.i(TAG, "handleMessage DEVICE_DISCONNECT: call ShowReceivedFilesPopupActivity receivedFileUriStringArrayList.size="+receivedFileUriStringArrayList.size)
          persistArrayList(receivedFileUriStringArrayList, "receivedFileUris")

          receiveFilesHistoryLength = receiveFilesHistory.add(System.currentTimeMillis, 
                                                              mDisconnectedDeviceName, 
                                                              kbytesPerSecond, 
                                                              receivedFileUriStringArrayList.toArray(new Array[String](0)) )
          receiveFilesHistoryLength = receiveFilesHistory.store()

          // run ShowReceivedFilesPopupActivity hand over receivedFileUriStringArrayList
          // this will show the list of receive files and allow the user to start intents on the individual files
          try { Thread.sleep(100); } catch { case ex:Exception => }
          val intent = new Intent(context, classOf[ShowReceivedFilesPopupActivity])
          val bundle = new Bundle()
          bundle.putStringArrayList("listOfUriStrings", receivedFileUriStringArrayList)
          bundle.putString("opentype", "auto") // activity will auto-close after about 15s if not used
          bundle.putString("otherName", mDisconnectedDeviceName)
          // hand over .asc file from most recent delivery
          if(arrayListSelectedFileStrings!=null && arrayListSelectedFileStrings.size>0) {
            val iterator = arrayListSelectedFileStrings.iterator 
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

  private def deliver(inputStream:InputStream, mime:String, contentLength:Long=0, fileUriString:String) :Int = {
    if(D) Log.i(TAG, "deliver fileUriString="+fileUriString+" contentLength="+contentLength+" mime="+mime)
    if(fileUriString==null)
      return -1

    var filename = fileUriString
    if(fileUriString!=null) {
      val idxLastSlash = fileUriString.lastIndexOf("/")
      if(idxLastSlash>=0)
        filename = fileUriString.substring(idxLastSlash+1)
    }

    // send blob in a separate thread (1st it will be queued - then be send via RFCommHelperService.ConnectedSendThread())
    // data will be received in RFCommHelperService processIncomingBlob()
    try {
      var localID:Long = 0
      synchronized {
        blobDeliverId+=1
        localID = blobDeliverId
      }
      if(D) Log.i(TAG, "deliver btService.sendBlob, localID="+localID+" mime="+mime)
      btService.sendBlob(mime, byteString=null, toAddr=null, filename, contentLength, localID)

      // send chunked data
      val byteChunkData = new Array[Byte](blobDeliverChunkSize)
      var totalSentBytes:Long = 0
      var readBytes = inputStream.read(byteChunkData,0,blobDeliverChunkSize)
      if(D) Log.i(TAG, "deliver read file done readBytes="+readBytes)
      while(readBytes>0) {
        btService.sendData(readBytes, byteChunkData) // may block
        totalSentBytes += readBytes
        readBytes = inputStream.read(byteChunkData,0,blobDeliverChunkSize)
      }
      if(D) Log.i(TAG, "deliver send fileUriString=["+fileUriString+"] done totalSentBytes="+totalSentBytes+" send EOM")
      btService.sendData(0, byteChunkData) // eom - may block
      inputStream.close
      return 0

    } catch { case ex:Exception =>
      Log.e(TAG, "deliver ",ex)
      val errMsg = "deliver "+ex.getMessage
      context.asInstanceOf[Activity].runOnUiThread(new Runnable() {
        override def run() { 
          Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show
        }
      })
      return -2
    }
  }

  private def deliverFile(file:File) :Int = {
    if(file!=null) {
      val fileName = file.getName
      if(fileName!=null) {
        try {
          val lastIdxOfDot = fileName.lastIndexOf(".")
          val extension = if(lastIdxOfDot>=0) fileName.substring(lastIdxOfDot+1) else null
          var mimeTypeFromExtension = if(extension!=null) mimeTypeMap.getMimeTypeFromExtension(extension) else "*/*"
          if(extension=="asc") mimeTypeFromExtension="application/pgp"
          if(D) Log.i(TAG, "deliverFile name=["+fileName+"] mime="+mimeTypeFromExtension)
          val fileInputStream = new FileInputStream(file) 
          if(fileInputStream!=null) {
            val fileSize = file.length()
            return deliver(fileInputStream, mimeTypeFromExtension, fileSize, fileName)
          }

        } catch {
          case fnfex: java.io.FileNotFoundException =>
            Log.e(TAG, "deliverFile file.getCanonicalPath()="+file.getCanonicalPath()+" FileNotFoundException "+fnfex)
            val errMsg = "File not found "+file.getCanonicalPath()

            context.asInstanceOf[Activity].runOnUiThread(new Runnable() {
              override def run() { 
	              if(radioLogoView!=null)
                	radioLogoView.setAnimation(null)
                if(userHint2View!=null) {
                  userHint2View.setText(errMsg)
                }
              }
            })
        }
      }
    }
    return -1
  }

/*
  // todo: implement idle connection timeout
  private class ReceiverIdleCheckThread() extends Thread {
    override def run() {
      while connected {
        receiverActivityFlag = false
        try { Thread.sleep(10000); } catch { case ex:Exception => }
        if(!receiverActivityFlag)
          force hangup
      }
    }
  }
*/

  private def deliverFileArray(arrayListSelectedFileStrings:ArrayList[String]) {
    numberOfSentFiles = 0
    if(arrayListSelectedFileStrings==null || arrayListSelectedFileStrings.size<1) {
      Log.e(TAG, "deliverFileArray no files to send")
      mainViewUpdate

      // send special token to indicate the other side may now become the actor
      if(D) Log.i(TAG, "handleMessage MESSAGE_STATE_CHANGE: sending 'yourturn'")
      btService.send("yourturn")
      // todo: if the other side does not respond to this, we hang - we need to time out
      //       we must start a thread to come back every 10 seconds to check if we had received any MESSAGE_DELIVER_PROGRESS msgs in msgFromServiceHandler
      //       new ReceiverIdleCheckThread().start

    } else {
      new Thread() {
        override def run() {
          context.asInstanceOf[Activity].runOnUiThread(new Runnable() {
            override def run() {
              // todo: don't animate the radio log, but maybe some tx/tx-LED
              //if(radioLogoView!=null && fastAnimation!=null)
              //	radioLogoView.setAnimation(fastAnimation)
              if(userHint1View!=null) {
                userHint1View.setText("Upload")
                userHint1View.postInvalidate()
              }
            }
          })
          try { Thread.sleep(100); } catch { case ex:Exception => }

          try {
            val iterator = arrayListSelectedFileStrings.iterator 
            while(iterator.hasNext) {
              val fileString = iterator.next
              if(fileString!=null) {
                if(D) Log.i(TAG, "deliverFileArray fileString=["+fileString+"] numberOfSentFiles="+numberOfSentFiles+" ###")

                val idxLastDot = fileString.lastIndexOf(".")
                if(idxLastDot<0) {
                  Log.e(TAG, "deliverFileArray idxLastDot<0 (no file extension)")
                } else {
                  val ext = fileString.substring(idxLastDot+1)
                  //val dstFileName = mBluetoothAdapter.getName+"."+ext
                  //if(D) Log.i(TAG, "deliverFileArray dstFileName=["+dstFileName+"]")
                  if(deliverFile(new File(fileString))==0)
                    numberOfSentFiles += 1
                }
              }
            }
          } catch {
            case npex: java.lang.NullPointerException =>
              Log.e(TAG, "handleMessage MESSAGE_STATE_CHANGE: NullPointerException "+npex)
              val errMsg = npex.getMessage
              Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show
          }

          // send special token to indicate the other side is becoming the actor
          if(D) Log.i(TAG, "handleMessage MESSAGE_STATE_CHANGE: sending 'yourturn'")
          btService.send("yourturn")
          // note: we expect the other party to start sending files immediately now (and after that to call stopActiveConnection())
          // todo: for the case that nothing happens, we need to disconnect the bt-connection ourselfs
          // solution:
          // 1. capture the current number of received bytes from the service
          // 2. start a dedicated thread to come back in 5 to 10 seconds
          // 3. if no additional new bytes were received ... hang up
        }
      }.start                        
    } 
  }

  private def mainViewUpdate() {
    if(btService==null) {
      if(D) Log.i(TAG, "mainViewUpdate btService==null")
    } else {
      if(D) Log.i(TAG, "mainViewUpdate btService.acceptAndConnect="+btService.acceptAndConnect)
    }
      
    if(btService!=null && btService.state==RFCommHelperService.STATE_CONNECTED) {
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

    if(userHint1View!=null) {
      // get free space on SD-card
      val statFs = new StatFs(Environment.getExternalStorageDirectory().getPath())
      val sdAvailSize = statFs.getAvailableBlocks().asInstanceOf[Long] * statFs.getBlockSize().asInstanceOf[Long]
      val str = Formatter.formatFileSize(this, sdAvailSize)
      userHint1View.setText(str+" free media to receive files")
    }

    if(userHint2View!=null) {
      val numberOfFilesToSend = if(arrayListSelectedFileStrings==null) 0 else arrayListSelectedFileStrings.size
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
        userHint3View.setTypeface(null, 0);  // not bold
        userHint3View.setTextSize(15)  // normal size
        userHint3View.setText("NFC disabled - must connect manually")
      }
      userHint3View.setVisibility(View.VISIBLE)
    }
    if(simpleProgressBarView!=null)
      simpleProgressBarView.setVisibility(View.GONE)
    if(progressBarView!=null) {
      progressBarView.setMax(100)
      progressBarView.setProgress(0)
    }
  }

  private def mainViewBluetooth() {
    //if(D) Log.i(TAG, "mainViewBluetooth")
    if(radioLogoView!=null)
      radioLogoView.setImageResource(R.drawable.bluetooth)

    if(radioLogoView!=null)
    	radioLogoView.setAnimation(null)

    if(userHint1View!=null)
      userHint1View.setText("")
    if(userHint2View!=null) {
      userHint2View.setText("")
      userHint2View.setVisibility(View.VISIBLE)
    }
    if(userHint3View!=null) {
      userHint3View.setTypeface(null, 0);  // not bold
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

