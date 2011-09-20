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
import scala.collection.mutable.ListBuffer

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.FileWriter
import java.util.UUID
import java.util.LinkedList
import java.util.ArrayList
import java.util.Calendar

import android.app.Activity
import android.app.ActivityManager
import android.util.Log
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.os.SystemClock
import android.os.Environment
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.media.MediaPlayer
import android.provider.Settings
import android.widget.Toast

import com.google.protobuf.CodedOutputStream
import com.google.protobuf.CodedInputStream

object RFCommHelperService {
  val STATE_NONE = 0       // doing nothing
  val STATE_LISTEN = 1     // not yet connected but listening for incoming connections
  val STATE_CONNECTED = 3  // connected to at least one remote device

  // Message types sent from RFCommHelperService to the activity handler
  val MESSAGE_STATE_CHANGE = 1
  val MESSAGE_USERHINT1 = 2
  val MESSAGE_USERHINT2 = 3
  val MESSAGE_DEVICE_NAME = 4
  val DEVICE_DISCONNECT = 7
  val CONNECTION_FAILED = 8
  val CONNECTION_START = 9
  val MESSAGE_REDRAW_DEVICEVIEW = 10
  val MESSAGE_DELIVER_PROGRESS = 11
  val MESSAGE_YOURTURN = 12
  val MESSAGE_RECEIVED_FILE = 13

  // Key names received from RFCommHelperService to the activity handler
  val DEVICE_NAME = "device_name"
  val DEVICE_ADDR = "device_addr"
  val SOCKET_TYPE = "socket_type"
  val DELIVER_ID = "deliver_id"
  val DELIVER_PROGRESS = "deliver_progress"
  val DELIVER_BYTES = "deliver_bytes"
  val DELIVER_TYPE = "deliver_type"
  val DELIVER_FILENAME = "deliver_filename"
  val DELIVER_URI = "deliver_uri"
} 

class RFCommHelperService extends android.app.Service {
  // public objects
  var context:Context = null              // set by activity on new ServiceConnection()
  var activityMsgHandler:Handler = null   // set by activity on new ServiceConnection()
  @volatile var acceptAndConnect = true   // set by activity on onPause / onResume
  @volatile var state = RFCommHelperService.STATE_NONE  // retrieved by activity

  // private objects
  private val TAG = "RFCommHelperService"
  private val D = true

  private val NAME_SECURE = "AnyMime"
  private val MY_UUID_SECURE   = UUID.fromString("fa87c0d0-afac-11de-9991-0800200c9a66")
  @volatile private var mSecureAcceptThread:AcceptThread = null

  //private val NAME_INSECURE = "AnyMimeInsecure"
  //private val MY_UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-9992-0800200c9a66")
  //@volatile private var mInsecureAcceptThread:AcceptThread = null

  private val mAdapter = BluetoothAdapter.getDefaultAdapter
  private var myBtName = mAdapter.getName
  private var myBtAddr = mAdapter.getAddress
  @volatile private var sendMsgCounter:Long = 0
  @volatile private var mConnectThread:ConnectThread = null
  @volatile private var activeConnectedThread:ConnectedThread = null
  private var blobTaskId = 0
  private var receivedFileFolderString:String = null
  private var bytesWritten=0

  class LocalBinder extends android.os.Binder {
    def getService = RFCommHelperService.this
  }
  private val localBinder = new LocalBinder
  override def onBind(intent:Intent) :IBinder = localBinder 

  def meminfo() {
    if(D) Log.i(TAG, "meminfo: activeConnectedThread="+activeConnectedThread)
    if(D) Log.i(TAG, "meminfo: mConnectThread="+mConnectThread)
    if(D) Log.i(TAG, "meminfo: mSecureAcceptThread="+mSecureAcceptThread)
    if(D) Log.i(TAG, "meminfo: activityMsgHandler="+activityMsgHandler)
    if(activeConnectedThread!=null)
      activeConnectedThread.meminfo

    val activityManager = if(context!=null) context.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[ActivityManager] else null
    if(activityManager!=null) {
      val memoryInfo = new ActivityManager.MemoryInfo
      activityManager.getMemoryInfo(memoryInfo)
      if(D) Log.i(TAG, "meminfo: memoryInfo.availMem="+memoryInfo.availMem+" memoryInfo.lowMemory="+memoryInfo.lowMemory)
    }
  }

  // called by Activity onResume() 
  // but only while state == STATE_NONE
  // this is why we quickly switch state to STATE_LISTEN
  def start(acceptOnlySecureConnectRequests:Boolean) = synchronized {
    if(D) Log.i(TAG, "start: android.os.Build.VERSION.SDK_INT="+android.os.Build.VERSION.SDK_INT)

    setState(RFCommHelperService.STATE_LISTEN)   // this will send MESSAGE_STATE_CHANGE

    // in case bt was turned on after app start
    if(myBtName==null)
      myBtName = mAdapter.getName
    if(myBtAddr==null)
      myBtAddr = mAdapter.getAddress

    // Start the thread to listen on a BluetoothServerSocket
    if(mSecureAcceptThread == null) {
      if(D) Log.i(TAG, "start new AcceptThread for secure")
      mSecureAcceptThread = new AcceptThread()
      if(mSecureAcceptThread != null) 
        mSecureAcceptThread.start
    }

/*
    if(android.os.Build.VERSION.SDK_INT>=10 && !acceptOnlySecureConnectRequests) {
      // 2.3.3+: start insecure socket 
      if(mInsecureAcceptThread == null) {
        if(D) Log.i(TAG, "start new AcceptThread for insecure (running on 2.3.3+)")
        mInsecureAcceptThread = new AcceptThread(false)
        if(mInsecureAcceptThread != null)
          mInsecureAcceptThread.start
      }
    }
*/
    if(D) Log.i(TAG, "start: done")
  }

  // called by the activity: options menu "connect" -> onActivityResult() -> connectDevice()
  // called by the activity: as a result of NfcAdapter.ACTION_NDEF_DISCOVERED
  def connect(newRemoteDevice:BluetoothDevice, secure:Boolean=true, reportConnectState:Boolean=true) :Unit = synchronized {
    if(newRemoteDevice==null) {
      Log.e(TAG, "connect() newRemoteDevice==null, give up")
      return
    }

    if(D) Log.i(TAG, "connect() remoteAddr="+newRemoteDevice.getAddress()+" name="+newRemoteDevice.getName+" secure="+secure)

    if(reportConnectState && activityMsgHandler!=null) {
      val msg = activityMsgHandler.obtainMessage(RFCommHelperService.CONNECTION_START)
      val bundle = new Bundle
      bundle.putString(RFCommHelperService.DEVICE_ADDR, newRemoteDevice.getAddress)
      bundle.putString(RFCommHelperService.DEVICE_NAME, newRemoteDevice.getName)
      msg.setData(bundle)
      activityMsgHandler.sendMessage(msg)
    }
    
    // Start the thread to connect with the given device
    mConnectThread = new ConnectThread(newRemoteDevice, secure, reportConnectState)
    mConnectThread.start
  }

  // called by onDestroy()
  // called by activity (on MESSAGE_YOURTURN)
  def stopActiveConnection() = synchronized {
    if(D) Log.i(TAG, "stopActiveConnection mConnectThread="+mConnectThread+" activeConnectedThread="+activeConnectedThread+" mSecureAcceptThread="+mSecureAcceptThread)

    if(mConnectThread != null) {
      mConnectThread.cancel
      mConnectThread = null
    }

    if(activeConnectedThread != null) {
      activeConnectedThread.cancel
      activeConnectedThread = null
    }

    System.gc
    setState(RFCommHelperService.STATE_LISTEN)   // will send MESSAGE_STATE_CHANGE to activity
  }

  def send(cmd:String, message:String=null, toAddr:String=null) = synchronized {
    // the idea with synchronized is that no other send() shall take over (will interrupt) an ongoing send()
    var thisSendMsgCounter:Long = 0
    synchronized { 
      val nowMs = SystemClock.uptimeMillis
      if(sendMsgCounter>=nowMs) 
        sendMsgCounter+=1
      else
        sendMsgCounter=nowMs

      thisSendMsgCounter = sendMsgCounter
    }
    val myCmd = if(cmd!=null) cmd else "strmsg"
    if(D) Log.i(TAG, "send myCmd="+myCmd+" message="+message+" toAddr="+toAddr+" sendMsgCounter="+thisSendMsgCounter)
    if(activeConnectedThread!=null)
      activeConnectedThread.writeCmdMsg(myCmd,message,toAddr,thisSendMsgCounter)

    if(D) Log.i(TAG, "send myCmd="+myCmd+" DONE")
  }

  def sendData(size:Int, data:Array[Byte], toAddr:String=null) {
    if(activeConnectedThread!=null)
      try {
        activeConnectedThread.writeData(data,size)
      } catch {
        case e: IOException =>
          Log.e(TAG, "sendData exception during write", e)
      }
  }

  def sendBlob(mime:String, byteString:com.google.protobuf.ByteString, toAddr:String=null, filename:String=null, contentLength:Long=0, id:Long=0) {
    if(D) Log.i(TAG, "sendBlob mime="+mime+" filename="+filename)
    sendMsgCounter+=1
    val btBuilder = BtShare.Message.newBuilder
                                   .setCommand("blob")
                                   .setArgCount(sendMsgCounter)
                                   .setId(id)
                                   .setDataLength(contentLength)
                                   .setFromName(myBtName)
                                   .setFromAddr(myBtAddr)
    if(mime!=null)
      btBuilder.setArg1(mime)
    if(byteString!=null)
      btBuilder.setArgBytes(byteString)
    if(filename!=null)
      btBuilder.setArg2(filename)
    if(toAddr!=null)
      btBuilder.setToAddr(toAddr)

    val btShareMessage = btBuilder.build
    if(D) Log.i(TAG, "sendBlob toAddr="+toAddr+" getSerializedSize="+btShareMessage.getSerializedSize)   
    if(activeConnectedThread!=null)
      try {
        activeConnectedThread.writeBtShareMessage(btShareMessage)
      } catch {
        case e: IOException =>
          Log.e(TAG, "Exception during write", e)
          if(activityMsgHandler!=null)
            activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_USERHINT1, -1, -1, e.getMessage).sendToTarget
      }
  }

  def processIncomingBlob(btMessage:BtShare.Message, fromAddr:String, downloadPath:String, bluetoothDevice:BluetoothDevice)(readCodedInputStream:() => Array[Byte]) {
    val mime = btMessage.getArg1
    var originalFilename = btMessage.getArg2
    originalFilename = originalFilename.replaceAll(" ","_")
    val contentLength = btMessage.getDataLength
    if(D) Log.i(TAG, "processIncomingBlob originalFilename=["+originalFilename+"] mime="+mime+" contentLength="+contentLength+" downloadPath="+downloadPath)
    new File(downloadPath).mkdirs    // for instance "/mnt/sdcard/Download/" via Environment.DIRECTORY_DOWNLOADS

    val noMediaFile = new File(downloadPath+"/.nomedia")
    try {
      val noMediaWriter = new FileWriter(noMediaFile)
      noMediaWriter.write("")
      noMediaWriter.close
    } catch {
      case ex:Exception =>
        Log.e(TAG, "Exception during write .nomedia file", ex)
        if(context!=null)
          context.asInstanceOf[Activity].runOnUiThread(new Runnable() {
            override def run() { 
              Toast.makeText(context, "Error writing to "+downloadPath, Toast.LENGTH_LONG).show
            }
          })
    }

    val startMS = SystemClock.uptimeMillis
    var progressLastStep:Long = 0
    var progressLastMS:Long = SystemClock.uptimeMillis
    try {
      val file = if(originalFilename!=null) new File(downloadPath+"/"+originalFilename) else null
      val outputStream = if(file!=null) new FileOutputStream(file) else null

      // receive loop
      var bytesRead=0
      //var bytesWritten=0
      var fileWritten=0
      if(activityMsgHandler!=null) {
        activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_USERHINT1, -1, -1, "Download").sendToTarget
        activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_USERHINT2, -1, -1, originalFilename).sendToTarget

        val msg = activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_DELIVER_PROGRESS)
        val bundle = new Bundle
        //bundle.putLong(RFCommHelperService.DELIVER_ID, blobId)
        bundle.putInt(RFCommHelperService.DELIVER_PROGRESS, 0)
        bundle.putLong(RFCommHelperService.DELIVER_BYTES, 0)
        bundle.putString(RFCommHelperService.DELIVER_TYPE, "receive")
        msg.setData(bundle)
        activityMsgHandler.sendMessage(msg)
      }
      var rawdata = readCodedInputStream()
      while(rawdata != null) {
        //if(D) Log.i(TAG, "processIncomingBlob rawdata.size="+rawdata.size)
        if(rawdata.size>0) {
          bytesRead += rawdata.size
          if(outputStream!=null) {
            outputStream.write(rawdata)  // write blob data to filesystem
            fileWritten += rawdata.size
            bytesWritten += rawdata.size
          }
        }

        // if size == 0, message back "blobId finished" to activity
        // else if contentLength > 0, message back "percentage progress" to activity
        if(activityMsgHandler!=null) {
          if(rawdata==null || rawdata.size==0) {
            val msg = activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_DELIVER_PROGRESS)
            val bundle = new Bundle
            //bundle.putLong(RFCommHelperService.DELIVER_ID, blobId)
            bundle.putInt(RFCommHelperService.DELIVER_PROGRESS, 100)
            bundle.putLong(RFCommHelperService.DELIVER_BYTES, bytesWritten)
            bundle.putString(RFCommHelperService.DELIVER_TYPE, "receive")
            msg.setData(bundle)
            activityMsgHandler.sendMessage(msg)
          } 
          else
          if(contentLength>0 && SystemClock.uptimeMillis-progressLastMS>500) {
            progressLastMS = SystemClock.uptimeMillis
            val msg = activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_DELIVER_PROGRESS)
            val bundle = new Bundle
            //bundle.putLong(RFCommHelperService.DELIVER_ID, blobId)
            val divider = if(contentLength>=100) contentLength/100 else 1
            bundle.putInt(RFCommHelperService.DELIVER_PROGRESS, (fileWritten/divider).asInstanceOf[Int])
            bundle.putLong(RFCommHelperService.DELIVER_BYTES, bytesWritten)
            bundle.putString(RFCommHelperService.DELIVER_TYPE, "receive")
            msg.setData(bundle)
            activityMsgHandler.sendMessage(msg)
          }
        }
        rawdata = readCodedInputStream()
      }

      if(outputStream!=null) {
        outputStream.close
        val durationMS = SystemClock.uptimeMillis - startMS
        var durationSecs = durationMS/1000
        if(durationSecs<1) durationSecs=1
        val bytesPerSeconds = bytesWritten / durationSecs
        if(D) Log.i(TAG, "processIncomingBlob written file ["+file+"] received="+bytesWritten+" secs="+((durationMS+500)/1000)+" B/s="+bytesPerSeconds+" -------------------")

        // send MESSAGE_RECEIVED_FILE/DELIVER_FILENAME with 'fileName' to activity
        if(activityMsgHandler!=null) {
          val msg = activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_RECEIVED_FILE)
          val bundle = new Bundle
          bundle.putString(RFCommHelperService.DELIVER_FILENAME, originalFilename)
          bundle.putString(RFCommHelperService.DELIVER_URI, file.toURI.toString)
          msg.setData(bundle)
          activityMsgHandler.sendMessage(msg)
        }
      }
    } catch { case ex:Exception =>
      Log.e(TAG, "processIncomingBlob ex="+ex.toString,ex)
      // example: java.io.FileNotFoundException: /mnt/sdcard/Pictures (Is a directory)
      // example: java.io.FileNotFoundException: /mnt/sdcard/Download/Nexus S secr-20110725-1423.asc (Permission denied)
      // example: java.lang.ArithmeticException: divide by zero

      // todo: it seems this exception is not recoverable
      // may result in "ConnectedThread run disconnected (38:16:D1:78:96:D0 Nexus S tm) com.google.protobuf.InvalidProtocolBufferException: Protocol message tag had invalid wire type"
    }
  }

  // called by ConnectedThread.cancel()
  def disconnect(socket:BluetoothSocket) = synchronized {
    if(socket!=null) {
      try {
        socket.close
      } catch {
        case ex: IOException =>
          Log.e(TAG, "disconnect() socket="+socket+" ex=",ex)
      }
    }
  }

  // called by: AcceptThread() -> socket = mmServerSocket.accept()
  // called by: ConnectThread() / activity options menu (or NFC touch) -> connect() -> ConnectThread()
  // called by: ConnectPopupActivity
  def connected(socket:BluetoothSocket, remoteDevice:BluetoothDevice, socketType:String) :Unit = synchronized {
    // in case of nfc triggered connect: for the device with the bigger btAddr, this is the 1st indication of the connect
    if(D) Log.i(TAG, "connected, sockettype="+socketType+" remoteDevice="+remoteDevice)
    if(remoteDevice==null) 
      return

    val btAddrString = remoteDevice.getAddress
    var btNameString = remoteDevice.getName
    // convert spaces to underlines in btNameString (some android activities, for instance the browser, dont like encoded spaces =%20 in file pathes)
    btNameString = btNameString.replaceAll(" ","_")

    // Start the thread to manage the connection and perform transmissions
    if(D) Log.i(TAG, "connected, Start ConnectedThread to manage the connection")
    activeConnectedThread = new ConnectedThread(socket, socketType)
    activeConnectedThread.start

    // Send the name of the connected device back to the UI Activity
    // note: the main activity may not be active at this moment (but for instance the ConnectPopupActivity)
    if(activityMsgHandler!=null) {
      val msg = activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_DEVICE_NAME)
      val bundle = new Bundle
      bundle.putString(RFCommHelperService.DEVICE_NAME, btNameString)
      bundle.putString(RFCommHelperService.DEVICE_ADDR, btAddrString)
      bundle.putString(RFCommHelperService.SOCKET_TYPE, socketType)
      msg.setData(bundle)
      activityMsgHandler.sendMessage(msg)
    }

    setState(RFCommHelperService.STATE_CONNECTED)

    // create a dynamic folder-name for all files to be received in this connect-session
    val nowCalendar = Calendar.getInstance
    val month = nowCalendar.get(Calendar.MONTH) +1
    val monthString = if(month<10) "0"+month else ""+month
    val dayOfMonth = nowCalendar.get(Calendar.DAY_OF_MONTH)
    val dayOfMonthString = if(dayOfMonth<10) "0"+dayOfMonth else ""+dayOfMonth
    val hourOfDay = nowCalendar.get(Calendar.HOUR_OF_DAY)
    val hourOfDayString = if(hourOfDay<10) "0"+hourOfDay else ""+hourOfDay
    val minute = nowCalendar.get(Calendar.MINUTE)
    val minuteString = if(minute<10) "0"+minute else ""+minute
    val seconds = nowCalendar.get(Calendar.SECOND)
    val secondsStrings = if(seconds<10) "0"+seconds else ""+seconds
    var dynName = "" + nowCalendar.get(Calendar.YEAR) + monthString + dayOfMonthString + "-" + hourOfDayString + minuteString + secondsStrings + "-" + btNameString
    val downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()
    receivedFileFolderString = downloadPath+"/"+"anymime-"+dynName

    if(D) Log.i(TAG, "connected done, receivedFileFolderString="+receivedFileFolderString)
  }

  // private methods

  private def setState(setState:Int) = synchronized {
    if(setState != state) {
      if(D) Log.i(TAG, "setState() "+state+" -> "+setState)
      state = setState
      // send modified state to the activity Handler
      if(activityMsgHandler!=null) {
        activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_STATE_CHANGE, state, -1).sendToTarget
      } else {
        Log.e(TAG, "setState() activityMsgHandler not set")
      }
    }
  }
  
  // called by ConnectedThread() IOException on send()
  private def connectionLost(socket: BluetoothSocket) {
    if(D) Log.i(TAG, "connectionLost socket="+socket)
    if(socket!=null) {
      val remoteDevice = socket.getRemoteDevice
      //if(D) Log.i(TAG, "connectionLost remoteDevice="+remoteDevice)
      if(remoteDevice!=null) {
        val btAddrString = remoteDevice.getAddress
        val btNameString = remoteDevice.getName
        activeConnectedThread = null
        if(D) Log.i(TAG, "connectionLost btAddrString="+btAddrString+" btNameString="+btNameString)

        // tell the activity that the connection was lost
        val msg = activityMsgHandler.obtainMessage(RFCommHelperService.DEVICE_DISCONNECT)
        val bundle = new Bundle
        bundle.putString(RFCommHelperService.DEVICE_ADDR, btAddrString)
        bundle.putString(RFCommHelperService.DEVICE_NAME, btNameString)
        msg.setData(bundle)
        activityMsgHandler.sendMessage(msg)
      }
    }
    System.gc    
  }

  private class AcceptThread(secure:Boolean=true) extends Thread {
    if(D) Log.i(TAG, "AcceptThread")
    private var mSocketType: String = /*if(secure)*/ "Secure" /*else "Insecure"*/
    private var mmServerSocket: BluetoothServerSocket = null
    mmServerSocket = null
    try {
/*
      if(secure) {
*/
        mmServerSocket = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, MY_UUID_SECURE)
/*
      } else {
        try {
          mmServerSocket = mAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME_INSECURE, MY_UUID_INSECURE)
        } catch {
          case nsmerr: java.lang.NoSuchMethodError =>
            // this should reall not happen, because we run the insecure method only if os >= 2.3.3
            Log.e(TAG, "listenUsingInsecureRfcommWithServiceRecord failed ", nsmerr)
        }
      }
*/
    } catch {
      case e: IOException =>
        Log.e(TAG, "Socket Type: " + mSocketType + " listen() failed", e)
    }

    override def run() {
      if(mmServerSocket==null)
        return

      setName("AcceptThread" + mSocketType)
      var socket:BluetoothSocket = null

      // Listen to the server socket if we're not connected
      while(mmServerSocket!=null) {
        if(D) Log.i(TAG, "AcceptThread run Socket Type: " + mSocketType)
        try {
          synchronized {
            socket = null
            if(mmServerSocket!=null)
              // This is a blocking call and will only return on a successful connection or an exception
              socket = mmServerSocket.accept
          }
        } catch {
          case ioex: IOException =>
            // log exception only if not stopped
            if(state != RFCommHelperService.STATE_NONE)
              Log.e(TAG, "AcceptThread run SocketType="+mSocketType+" state="+state+" ioex="+ioex)
        }

        if(socket != null) {
          // a bt connection is technically possible and can be accepted
/* */
          // note: this is where we can decide to acceptAndConnect - or not
          // todo: acceptAndConnect can be false here, while it is set to true in the activity (CRAZY!)
          if(!acceptAndConnect) {
            if(D) Log.i(TAG, "AcceptThread - denying incoming connect request, acceptAndConnect="+acceptAndConnect)
            // hangup
            socket.close

            if(context!=null)
              context.asInstanceOf[Activity].runOnUiThread(new Runnable() {
                override def run() { 
                  // we want to show our appname, this toast will appear if Anymime is running in background
                  Toast.makeText(context, "Bluetooth connect request is being denied. "+
                                          "Run Anymime in the foreground to accept connections.", Toast.LENGTH_LONG).show
                }
              })
          } else 
/* */
          {
            // activity is not paused
            RFCommHelperService.this synchronized {
              connected(socket, socket.getRemoteDevice, mSocketType)
            }
          }
        }
        
        // prevent tight loop
        try { Thread.sleep(100); } catch { case ex:Exception => }
      }
      if(D) Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType)
    }

    def cancel() { // called by stopActiveConnection()
      if(D) Log.i(TAG, "AcceptThread cancel() SocketType="+mSocketType+" mmServerSocket="+mmServerSocket)
      if(mmServerSocket!=null) {
        try {
          setState(RFCommHelperService.STATE_NONE)   // so that run() will NOT log an error; will send MESSAGE_STATE_CHANGE
          mmServerSocket.close
          mmServerSocket=null
        } catch {
          case ex: IOException =>
            Log.e(TAG, "cancel() mmServerSocket="+mmServerSocket+" ex=",ex)
        }
      }
    }
  }

  private class ConnectThread(remoteDevice:BluetoothDevice, secure:Boolean, reportConnectState:Boolean=true) extends Thread {
    private val mSocketType = /*if(secure)*/ "Secure" /*else "Insecure"*/
    private var mmSocket:BluetoothSocket = null

    // Get a BluetoothSocket for a connection with the given BluetoothDevice
    try {
/*
      if(secure) {
*/
        mmSocket = remoteDevice.createRfcommSocketToServiceRecord(MY_UUID_SECURE)
/*
      } else {
        mmSocket = remoteDevice.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE)
      }
*/
    } catch {
      case e: IOException =>
        Log.e(TAG, "ConnectThread Socket Type: "+mSocketType+" create() failed", e)
    }

    override def run() {
      if(D) Log.i(TAG, "ConnectThread run SocketType="+mSocketType+" #############################")
      setName("ConnectThread" + mSocketType)

      // Always cancel discovery because it will slow down a connection
/*
      if(mAdapter.isDiscovering) {
        Log.e(TAG, "ConnectThread run isDiscovering -> cancelDiscovery() ###########################")
*/
        mAdapter.cancelDiscovery
/*
      } else {
        Log.e(TAG, "ConnectThread run NOT isDiscovering ###########################")
      }
*/

      try {
        // This is a blocking call and will only return on a successful connection or an exception
        if(D) Log.i(TAG, "ConnectThread run connect() #######################################################")
        mmSocket.connect()
      } catch {
        case ex: IOException =>
          Log.e(TAG, "ConnectThread run unable to connect() "+mSocketType+" ex=",ex)
          var errMsg = ex.getMessage  // tmtmtm
          if(errMsg=="Connection reset by peer")
            errMsg = "Connection failed"
          if(context!=null)
            context.asInstanceOf[Activity].runOnUiThread(new Runnable() {
              override def run() { 
                Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show
              }
            })

          // Close the socket
          try {
            mmSocket.close
          } catch {
            case e2: IOException =>
              Log.e(TAG, "ConnectThread run unable to close() "+mSocketType+" socket during connection failure",e2)
          }

          if(reportConnectState) {
            val msg = activityMsgHandler.obtainMessage(RFCommHelperService.CONNECTION_FAILED)
            val bundle = new Bundle
            bundle.putString(RFCommHelperService.DEVICE_ADDR, remoteDevice.getAddress)
            bundle.putString(RFCommHelperService.DEVICE_NAME, remoteDevice.getName)
            msg.setData(bundle)
            activityMsgHandler.sendMessage(msg)
          }
          return
      }

      // Reset the ConnectThread because we're done
      RFCommHelperService.this synchronized {
        mConnectThread = null
      }

      // Start the connected thread
      connected(mmSocket, remoteDevice, mSocketType)
    }

    def cancel() {
      if(D) Log.i(TAG, "ConnectThread cancel() SocketType="+mSocketType+" mmSocket="+mmSocket)
      if(mmSocket!=null) {
        try {
          mmSocket.close
        } catch {
          case e: IOException =>
            Log.e(TAG, "ConnectThread cancel() socket.close() failed for " + mSocketType + " socket", e)
        }
      }
    }
  }

  class ConnectedThread(var socket:BluetoothSocket, socketType:String) extends Thread {
    if(D) Log.i(TAG, "ConnectedThread start " + socketType)
    private var mmInStream:InputStream = null
    private var codedInputStream:CodedInputStream = null
    private var mmOutStream:OutputStream = null
    private var mConnectedSendThread:ConnectedSendThread = null
    private val sendQueue = new scala.collection.mutable.Queue[Any]

    var connectedBluetoothDevice:BluetoothDevice = null
    var connectedBtAddr:String = null
    var connectedBtName:String = null
    @volatile var running = false     // set true by run(), set false by cancel()

    bytesWritten=0

    if(socket!=null) {
      connectedBluetoothDevice = socket.getRemoteDevice
      if(connectedBluetoothDevice!=null) {
        connectedBtAddr = connectedBluetoothDevice.getAddress
        connectedBtName = connectedBluetoothDevice.getName
      }

      try {
        // Get the BluetoothSocket input and output streams
        mmInStream = socket.getInputStream
        codedInputStream = CodedInputStream.newInstance(mmInStream)

        // start fifo queue delivery via codedOutputStream
        mmOutStream = socket.getOutputStream
        mConnectedSendThread = new ConnectedSendThread(sendQueue,CodedOutputStream.newInstance(mmOutStream),socket)
        mConnectedSendThread.start
      } catch {
        case e: IOException =>
          Log.e(TAG, "ConnectedThread start temp sockets not created", e)
      }
    }

    def meminfo() {
      if(D) Log.i(TAG, "ConnectedThread meminfo: sendQueue="+sendQueue)
      if(D) Log.i(TAG, "ConnectedThread meminfo: codedInputStream="+codedInputStream)
      if(D) Log.i(TAG, "ConnectedThread meminfo: mConnectedSendThread="+mConnectedSendThread)
      if(mConnectedSendThread!=null)
        mConnectedSendThread.meminfo
    }

    private def splitString(line:String, delim:List[String]) :List[String] = delim match {
      case head :: tail => 
        val listBuffer = new ListBuffer[String]
        //if(D) Log.i(TAG, "ConnectedThread run splitString line="+line)
        for(addr <- line.split(head).toList) {
          listBuffer += addr
          //if(D) Log.i(TAG, "ConnectedThread run splitString addr="+addr+" listBuffer.size="+listBuffer.size)
        }
        //if(D) Log.i(TAG, "ConnectedThread run splitString listBuffer.size="+listBuffer.size)
        return listBuffer.toList
      case Nil => 
        return List(line.trim)
    }

    // called by RFCommMultiplexerService.ConnectedThread()
    private def processBtMessage(cmd:String, arg1:String, fromAddr:String, btMessage:BtShare.Message)(readCodedInputStream:() => Array[Byte]) :Boolean = {
      if(D) Log.i(TAG, "processBtMessage cmd="+cmd+" arg1="+arg1+" fromAddr="+fromAddr)

      if(cmd.equals("yourturn")) {
        val msg = activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_YOURTURN)
        activityMsgHandler.sendMessage(msg)
        // note that this will not arive in the activity, if another activity is running in front of it
        return true

      } else if(cmd.equals("blob")) {
        //if(D) Log.i(TAG, "processBtMessage receive blob mime="+arg1)
        processIncomingBlob(btMessage, fromAddr, receivedFileFolderString, connectedBluetoothDevice)(readCodedInputStream)
        return true
      }

      return false  // not processed
    }

    private def processReceivedRawData(rawdata:Array[Byte]) :Unit = synchronized {
      val btMessage = BtShare.Message.parseFrom(rawdata)
      val cmd = btMessage.getCommand
      val toAddr = btMessage.getToAddr
      val fromAddr = btMessage.getFromAddr
      val fromName = btMessage.getFromName
      val arg1 = btMessage.getArg1
      val toName = btMessage.getToName
      //if(D) Log.i(TAG, "ConnectedThread processReceivedRawData: read1 cmd="+cmd+" fromName="+fromName+" fromAddr="+fromAddr+" toAddr="+toAddr)

      // plug-in app-specific behaviour
      if(!processBtMessage(cmd, arg1, fromAddr, btMessage) { () =>
        // this closure is used as readCodedInputStream() from within subclassed clients
        //if(D) Log.i(TAG, "ConnectedThread processReceivedRawData closure from processBtMessage ...")
        var magic=0
        var magicRecount=0
        do {
          magic = codedInputStream.readRawVarint32 // may block
          magicRecount+=1
        } while(magic!=11111)

        var size = codedInputStream.readRawVarint32 // may block
        //if(D) Log.i(TAG, "ConnectedThread processReceivedRawData closure codedInputStream size="+size+" magic="+magic+" magicRecount="+magicRecount)
        var rawdata:Array[Byte] = null
        if(size>0 /*&& running*/) {      // todo: must implement running-check
          //if(D) Log.i(TAG, "ConnectedThread processReceivedRawData closure wait for "+size+" bytes data ...")
          rawdata = codedInputStream.readRawBytes(size)     // may block, may be aborted by call to cancel
        }          

        if(size>0 /*&& running*/) {      // todo: must implement running-check
          //if(D) Log.i(TAG, "ConnectedThread processReceivedRawData closure return rawdata="+rawdata)
          rawdata

        } else {
          //if(D) Log.i(TAG, "ConnectedThread processReceivedRawData closure return null")
          null
        }
      }) {
        if(D) Log.i(TAG, "ConnectedThread run basic behaviour for cmd="+cmd+" arg1="+arg1+" toName="+toName)
        // todo: must make it possible for the activity to make user aware of this "unknown type" message
      }
    }

    override def run() {
      if(D) Log.i(TAG, "ConnectedThread run " + socketType)
      try {
        // while connected, keep listening to the InputStream
        running = true
        while(running) {
          //if(D) Log.i(TAG, "ConnectedThread run " + socketType+" read size...")
          var magic=0
          var magicRecount=0
          do {
            if(codedInputStream==null) {
              running=false
            } else {
              magic = codedInputStream.readRawVarint32 // may block
              magicRecount+=1
            }
          } while(running && codedInputStream!=null && magic!=11111)

          if(running && codedInputStream!=null) {
            val size = codedInputStream.readRawVarint32 // may block
            //if(D) Log.i(TAG, "ConnectedThread run " + socketType+" read size="+size+" magic="+magic+" magicRecount="+magicRecount+" socket="+socket+" running="+running)
            if(running && size>0) {
              val rawdata = codedInputStream.readRawBytes(size) // bc we know the size of data to expect, this will not block
              if(running)
                processReceivedRawData(rawdata)
            }
          }
        }

      } catch {
        case ioex:IOException =>
          if(D) Log.i(TAG, "ConnectedThread run IOException disconnected ("+connectedBtAddr+" "+connectedBtName+") "+ioex)
          // "Software caused connection abort" 
          // this is really just a connection failed - why does it seem to get connected at all?
          stopActiveConnection
        case istex:java.lang.IllegalStateException =>
          Log.e(TAG, "ConnectedThread run IllegalStateException disconnected ("+connectedBtAddr+" "+connectedBtName+") "+istex)
          stopActiveConnection
      }

      if(D) Log.i(TAG, "ConnectedThread run " + socketType+ " DONE")
    }

    def writeBtShareMessage(btMessage:BtShare.Message) :Unit = {
      //if(D) Log.i(TAG, "ConnectedThread writeBtShareMessage btMessage="+btMessage)
      if(btMessage==null) return

      // fifo queue btMessage - and actually send it from somewhere else
      sendQueue += btMessage
    }

    def writeCmdMsg(cmd:String, message:String, toAddr:String, sendMsgCounter:Long) = synchronized {
      if(D) Log.i(TAG, "ConnectedThread writeCmdMsg cmd="+cmd+" message="+message+" socket="+socket) //+" toAddr="+toAddr+" myBtName="+myBtName+" myBtAddr="+myBtAddr)
      val btBuilder = BtShare.Message.newBuilder
                                     .setArgCount(sendMsgCounter)
                                     .setFromName(myBtName)
                                     .setFromAddr(myBtAddr)
      if(message!=null)
        btBuilder.setArg1(message)     

      if(cmd!=null)
        btBuilder.setCommand(cmd)
      else
        btBuilder.setCommand("strmsg")

      if(toAddr!=null)
        btBuilder.setToAddr(toAddr)

      writeBtShareMessage(btBuilder.build)
    }

    def writeData(data:Array[Byte], size:Int) {
      //if(D) Log.i(TAG, "ConnectedThread writeData size="+size)
      // queue the Array, take care of "out of memory" issues

      if(sendQueue.size>500) {
        // 500 sendQueue allocations of 10.000 bytes = 500 KB buffer 
        while(sendQueue.size>500) {
          if(D) Log.i(TAG, "ConnectedThread writeData sendQueue.size="+sendQueue.size+" >500 sleep ... ###")
          try { Thread.sleep(1000); } catch { case ex:Exception => }
        }
        if(D) Log.i(TAG, "ConnectedThread writeData sendQueue.size="+sendQueue.size+" after sleep ###")
      }

      var sendData:Array[Byte] = null
      if(size>0) {
        while(sendData==null) {
          try {
            sendData = new Array[Byte](size)
          } catch {
            case e: java.lang.OutOfMemoryError =>
              if(D) Log.i(TAG, "ConnectedThread writeData OutOfMemoryError - force System.gc() sendQueue.size="+sendQueue.size+" ######################")
              System.gc
              try { Thread.sleep(2000); } catch { case ex:Exception => }
              System.gc
              if(D) Log.i(TAG, "ConnectedThread writeData OutOfMemoryError - continue after System.gc sendQueue.size="+sendQueue.size+" ######################")
          }
        }
        Array.copy(data,0,sendData,0,size)
      }

      sendQueue += sendData
    }

    def cancel() {
      // called by stopActiveConnection()
      if(D) Log.i(TAG, "ConnectedThread cancel() socket="+socket)

      if(mmInStream != null) {
        try { mmInStream.close } catch { case e: Exception => }
        mmInStream = null
      }

      if(mmOutStream != null) {
        try { mmOutStream.close } catch { case e: Exception => }
        mmOutStream = null
      }

      codedInputStream = null
      if(mConnectedSendThread!=null)
        mConnectedSendThread.halt

      if(socket!=null) {
        disconnect(socket)
        connectionLost(socket)
        socket=null
      }

      running = false
      sendQueue.clear
      System.gc
      if(D) Log.i(TAG, "ConnectedThread cancel() done")
    }
  }

  class ConnectedSendThread(sendQueue:scala.collection.mutable.Queue[Any], var codedOutputStream:CodedOutputStream, socket:BluetoothSocket) extends Thread {
    //if(D) Log.i(TAG, "ConnectedSendThread start")
    var running = false
    var totalSend = 0
    var blobId:Long = 0
    var contentLength:Long = 0
    var progressLastStep:Long = 0
    val activityManager = if(context!=null) context.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[ActivityManager] else null
    val memoryInfo = new ActivityManager.MemoryInfo

    def meminfo() {
      if(D) Log.i(TAG, "ConnectedSendThread meminfo: codedOutputStream="+codedOutputStream)
      if(activityManager!=null) {
        activityManager.getMemoryInfo(memoryInfo)
        if(D) Log.i(TAG, "ConnectedSendThread meminfo: memoryInfo.availMem="+memoryInfo.availMem+" memoryInfo.lowMemory="+memoryInfo.lowMemory+" ######################")
      }
    }

    override def run() {
      //if(D) Log.i(TAG, "ConnectedSendThread run ")
      var progressLastMS:Long = SystemClock.uptimeMillis
      var fileSend:Long = 0
      try {
        while(codedOutputStream!=null && sendQueue!=null) {
          if(sendQueue.size>0) {
            val obj = sendQueue.dequeue
            if(obj.isInstanceOf[BtShare.Message]) {
              //if(D) Log.i(TAG, "ConnectedSendThread run BtShare.Message")
              val btShareMessage = obj.asInstanceOf[BtShare.Message]
              val fileName = btShareMessage.getArg2
              contentLength = btShareMessage.getDataLength
              //val msg = fileName+" "+contentLength+" bytes"
              activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_USERHINT2, -1, -1, fileName).sendToTarget

              // a new blob delivery is starting...
              blobId = btShareMessage.getId
              fileSend = 0
              progressLastStep = 0
              progressLastMS = SystemClock.uptimeMillis
              writeBtShareMessage(btShareMessage)

            } else {
              val data = obj.asInstanceOf[Array[Byte]]
              if(data!=null) {
                writeData(data, data.size)
                // a new blob delivery is in progress... (if data.size==0 then this is the end of this blob delivery)
                fileSend += data.size
                totalSend += data.size
              } else {
                writeData(null, 0)
              }
              
              // if data.size == 0, message back "blobId finished" to activity
              // else if contentLength > 0, message back "percentage progress" to activity
              if(data==null || data.size == 0) {
                val msg = activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_DELIVER_PROGRESS)
                val bundle = new Bundle
                //bundle.putLong(RFCommHelperService.DELIVER_ID, blobId)
                bundle.putInt(RFCommHelperService.DELIVER_PROGRESS, 100)
                bundle.putLong(RFCommHelperService.DELIVER_BYTES, totalSend)
                bundle.putString(RFCommHelperService.DELIVER_TYPE, "send")
                if(D) Log.i(TAG, "ConnectedSendThread run totalSend="+totalSend+" done ######################")
                msg.setData(bundle)
                activityMsgHandler.sendMessage(msg)
              }
              else if(contentLength>0 && SystemClock.uptimeMillis-progressLastMS>500) {
                progressLastMS = SystemClock.uptimeMillis
                val msg = activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_DELIVER_PROGRESS)
                val bundle = new Bundle
                //bundle.putLong(RFCommHelperService.DELIVER_ID, blobId)
                if(contentLength/100>=1)
                  bundle.putInt(RFCommHelperService.DELIVER_PROGRESS, (fileSend/(contentLength/100)).asInstanceOf[Int] )
                bundle.putLong(RFCommHelperService.DELIVER_BYTES, totalSend)
                bundle.putString(RFCommHelperService.DELIVER_TYPE, "send")
                if(D) Log.i(TAG, "ConnectedSendThread run totalSend="+totalSend+" ######################")
                msg.setData(bundle)
                activityMsgHandler.sendMessage(msg)
              }
            }
          } else {
            try { Thread.sleep(50); } catch { case ex:Exception => }
          }
        }
      } catch {
        case e: IOException =>
          if(D) Log.i(TAG, "ConnectedSendThread socket="+socket+" run ex="+e)
          halt
          //connectionLost(socket)
      }

      if(activityManager!=null) {
        activityManager.getMemoryInfo(memoryInfo)
        if(D) Log.i(TAG, "ConnectedSendThread run pre DONE memoryInfo.availMem="+memoryInfo.availMem+" memoryInfo.lowMemory="+memoryInfo.lowMemory+" ######################")
      }
      if(D) Log.i(TAG, "ConnectedSendThread run DONE")
    }

    def halt() {
      codedOutputStream=null
    }

    private def writeBtShareMessage(btMessage:BtShare.Message) :Unit = synchronized {
      if(btMessage==null) return
      //if(D) Log.i(TAG, "ConnectedSendThread writeBtShareMessage btMessage.getCommand="+btMessage.getCommand+" btMessage.getArg2="+btMessage.getArg2)
      try {
        val size = btMessage.getSerializedSize
        if(D) Log.i(TAG, "ConnectedSendThread writeBtShareMessage size="+size+" contentLength="+contentLength+" totalSend="+totalSend+" btMessage.getArg2="+btMessage.getArg2)
        if(size>0) {
          val byteData = new Array[Byte](size)
          com.google.protobuf.ByteString.copyFrom(byteData)
          if(codedOutputStream!=null) {
            codedOutputStream.writeRawVarint32(11111)
            if(codedOutputStream!=null) {
              codedOutputStream.writeRawVarint32(size)
              if(codedOutputStream!=null) {
                btMessage.writeTo(codedOutputStream)
                if(codedOutputStream!=null)
                  codedOutputStream.flush
              }
            }
          }
          //if(D) Log.i(TAG, "ConnectedSendThread writeBtShareMSG flushed size="+size+" codedOutputStr="+codedOutputStream)
        }
      } catch {
        case ex: IOException =>
          // we receive: "java.io.IOException: Connection reset by peer"
          // or:         "java.io.IOException: Transport endpoint is not connected"
          var errMsg = ex.getMessage
          Log.e(TAG, "ConnectedSendThread writeBtShareMessage socket="+socket+" ioexception errMsg="+errMsg, ex)
          activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_USERHINT1, -1, -1, errMsg).sendToTarget
          halt
      }
    }

    private def writeData(data:Array[Byte], size:Int) = synchronized {
      //if(D) Log.i(TAG, "ConnectedSendThread writeData size="+size+" totalSend="+totalSend+" contentLength="+contentLength)
      try {
        if(codedOutputStream!=null) {
          codedOutputStream.writeRawVarint32(11111)     
          if(codedOutputStream!=null) {
            codedOutputStream.writeRawVarint32(size)
            if(size>0)
              if(codedOutputStream!=null)
                codedOutputStream.writeRawBytes(data,0,size)     
            if(codedOutputStream!=null)
              codedOutputStream.flush
          }
        }
      } catch {
        case ioex:IOException =>
          Log.e(TAG, "ConnectedSendThread writeData socket="+socket+" "+ioex)
          activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_USERHINT1, -1, -1, ioex.getMessage).sendToTarget
          halt
      }
    }
  }
}

