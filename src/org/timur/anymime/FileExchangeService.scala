package org.timur.anymime

import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.util.Calendar
import java.util.ArrayList
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter

import android.util.Log
import android.content.Context
import android.os.IBinder
import android.os.Environment
import android.os.Handler
import android.os.SystemClock
import android.os.Bundle
import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast

import com.google.protobuf.CodedOutputStream
import com.google.protobuf.CodedInputStream

import scala.collection.mutable.ListBuffer

import org.timur.rfcomm._


// app-specific code that needs to stay in memory when the activity goes into background
// so that filetransfer can continue while the app is in background (and the activity might have been removed from memory)

class FileExchangeService extends RFServiceTrait {

  private val TAG = "FileExchangeService"
  private val D = Static.DBGLOG

  private var bytesWritten = 0
  private var totalSend = 0
  @volatile private var sendMsgCounter:Long = 0
  private var receivedFileFolderString:String = null
  private var selectedFileStringsArrayList:ArrayList[String] = null

  private var firstActor = false
  @volatile private var disconnecting = false


  class LocalBinder extends android.os.Binder {
    def getService = FileExchangeService.this
  }
  private val localBinder = new LocalBinder

  override def onBind(intent:Intent) :IBinder = localBinder 

  override def onCreate() {
    //if(D) Log.i(TAG, "onCreate ####################")
    // note: our service was started via bindService() from activity onCreate()
    //       but ConnectedThread will only be instantiated when needed, from RFCommHelperService connectedBt() or connectedWifi()
    //       and will run while codedInputStream!=null (set null by ConnectedThread.cancel())
  }

  def createConnectedThread() {
    connectedThread = new ConnectedThread()
  }

  class ConnectedThread() extends ConnectedThreadTrait {
    private var mmInStream:InputStream = null
    private var mmOutStream:OutputStream = null
    private var pairedBtOnly:Boolean = false
    private var deviceAddr:String = null
    private var deviceName:String = null
    private var localDeviceAddr:String = null
    private var localDeviceName:String = null
    private var socketCloseFkt:() => Unit = null
    private var codedInputStream:CodedInputStream = null
    private var mConnectedSendThread:ConnectedSendThread = null
    private val sendQueue = new scala.collection.mutable.Queue[Any]
    @volatile var threadRunning = false     // set true by run(), set false by cancel()   

    bytesWritten=0

    def init(setMmInStream:InputStream, setMmOutStream:OutputStream, setPairedBtOnly:Boolean, 
             setLocalDeviceAddr:String, setLocalDeviceName:String, 
             setDeviceAddr:String, setDeviceName:String, 
             setSocketCloseFkt:() => Unit) {
      //if(D) Log.i(TAG, "ConnectedThread start pairedBtOnly="+pairedBtOnly)

      mmInStream = setMmInStream
      mmOutStream = setMmOutStream
      pairedBtOnly = setPairedBtOnly
      localDeviceAddr = setLocalDeviceAddr
      localDeviceName = setLocalDeviceName
      deviceAddr = setDeviceAddr
      deviceName = setDeviceName
      socketCloseFkt = setSocketCloseFkt

      disconnecting = false

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
      var dynName = "" + nowCalendar.get(Calendar.YEAR) + monthString + dayOfMonthString + "-" + hourOfDayString + minuteString + secondsStrings + "-" + deviceName
      val downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath
      if(downloadPath!=null)
        receivedFileFolderString = downloadPath+"/"+"anymime-"+dynName
      if(D) Log.i(TAG, "ConnectedThread start receivedFileFolderString="+receivedFileFolderString+" downloadPath="+downloadPath)

      try {
        codedInputStream = CodedInputStream.newInstance(mmInStream)
        if(D) Log.i(TAG, "ConnectedThread start mmInStream="+mmInStream+" codedInputStream="+codedInputStream+" mmOutStream="+mmOutStream)

        // start fifo queue delivery via codedOutputStream
        mConnectedSendThread = new ConnectedSendThread(sendQueue,CodedOutputStream.newInstance(mmOutStream))
        mConnectedSendThread.start
      } catch {
        case e: IOException =>
          Log.e(TAG, "ConnectedThread start temp sockets not created", e)
      }
    }

    def updateStreams(setMmInStream:InputStream, setMmOutStream:OutputStream) {
      mmInStream = setMmInStream
      mmOutStream = setMmOutStream
      if(D) Log.i(TAG, "ConnectedThread updateStreams done")
    } 

    private def splitString(line:String, delim:List[String]) :List[String] = delim match {
      case head :: tail => 
        val listBuffer = new ListBuffer[String]
        //if(D) Log.i(TAG, "splitString line="+line)
        for(addr <- line.split(head).toList) {
          listBuffer += addr
          //if(D) Log.i(TAG, "splitString addr="+addr+" listBuffer.size="+listBuffer.size)
        }
        //if(D) Log.i(TAG, "splitString listBuffer.size="+listBuffer.size)
        return listBuffer.toList
      case Nil => 
        return List(line.trim)
    }

    // todo: the use of "bt"Message and "Bt"Share is now misleading

    private def processBtMessage(cmd:String, arg1:String, fromAddr:String, fromName:String, btMessage:BtShare.Message)(readCodedInputStream:() => Array[Byte]) :Boolean = {
      if(D) Log.i(TAG, "processBtMessage cmd="+cmd+" arg1="+arg1+" fromAddr="+fromAddr)

      if(cmd.equals("disconnect")) {
        if(connectedThread!=null) {
          disconnecting = true
          if(D) Log.i(TAG, "processBtMessage 'disconnect' ################################")
          if(rfCommHelper!=null && rfCommHelper.rfCommService!=null)
            rfCommHelper.rfCommService.stopActiveConnection
          else {
            Log.e(TAG, "processBtMessage 'disconnect' unable to rfCommHelper.rfCommService.stopActiveConnection")
          }
          connectedThread.cancel
        } else {
          Log.e(TAG, "processBtMessage 'disconnect' connectedThread==null, unable to connectedThread.cancel")
        }
        return true
      }

      if(cmd.equals("yourturn")) {
        activityMsgHandler.sendMessage(activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_YOURTURN))
        // note that this will not arive in the activity, if another activity is running in front of it
        if(firstActor) {
          if(D) Log.i(TAG, "processBtMessage 'yourturn' firstActor stopActiveConnection ...")
          send("disconnect",null,fromAddr,fromName)
          threadRunning = false
          // remind disconnect-status - so we wont try to switch to backup-connection in case the other side will disconnect now
          disconnecting=true
          if(connectedThread!=null) {
            // sleep to allow the disconnect command to transmit
            try { Thread.sleep(200); } catch { case ex:Exception => }
            if(rfCommHelper!=null && rfCommHelper.rfCommService!=null)
              rfCommHelper.rfCommService.stopActiveConnection
            else {
              Log.e(TAG, "processBtMessage 'yourturn' firstActor unable to rfCommHelper.rfCommService.stopActiveConnection")
            }
            connectedThread.cancel
          } else {
            Log.e(TAG, "processBtMessage 'yourturn' firstActor connectedThread==null, unable to connectedThread.cancel")
          }

        } else {
          if(D) Log.i(TAG, "processBtMessage 'yourturn' not-firstActor deliverFileArray() ...")
          deliverFileArray(deviceName, deviceAddr)
        }

        return true
      }
      
      if(cmd.equals("blob")) {
        if(D) Log.i(TAG, "processBtMessage receive blob mime="+arg1+" receivedFileFolderString="+receivedFileFolderString)
        if(receivedFileFolderString!=null)
          processIncomingBlob(btMessage, fromAddr, receivedFileFolderString, deviceName)(readCodedInputStream)
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
      if(!processBtMessage(cmd, arg1, fromAddr, fromName, btMessage) { () =>
        // this closure is used as readCodedInputStream() from within subclassed clients
        //if(D) Log.i(TAG, "ConnectedThread processReceivedRawData closure from processBtMessage ...")
        var magic=0
        var magicRecount=0
        do {
          magic = codedInputStream.readRawVarint32 // may block
          // todo: possible java.io.IOException on unexpected disconnect while receiving, switch to backupConnection!
          magicRecount+=1
        } while(magic!=11111)

        var size = codedInputStream.readRawVarint32 // may block
        //if(D) Log.i(TAG, "ConnectedThread processReceivedRawData closure codedInputStream size="+size+" magic="+magic+" magicRecount="+magicRecount)
        var rawdata:Array[Byte] = null
        if(size>0 /*&& threadRunning*/) {      // todo: must implement running-check
          //if(D) Log.i(TAG, "ConnectedThread processReceivedRawData closure wait for "+size+" bytes data ...")
          rawdata = codedInputStream.readRawBytes(size)     // may block, may be aborted by call to cancel
        }          

        if(size>0 /*&& threadRunning*/) {      // todo: must implement running-check
          //if(D) Log.i(TAG, "ConnectedThread processReceivedRawData closure return rawdata="+rawdata)
          rawdata

        } else {
          //if(D) Log.i(TAG, "ConnectedThread processReceivedRawData closure return null")
          null
        }
      }) {
        if(D) Log.i(TAG, "ConnectedThread run basic behaviour for cmd="+cmd+" arg1="+arg1+" toName="+toName)
        // todo: must make it possible for the activity to make user aware of this "unknown type" message - Toast?
      }
    }

    def isRunning() :Boolean = {
      return threadRunning
    }

    def disconnectBackupConnection() {
      if(D) Log.i(TAG, "ConnectedThread disconnectBackupConnection")
/*
// TODO
      if(backupConnectionSocket!=null)
        backupConnectionSocket.close
*/
    }

    def doFirstActor() {
      firstActor = false
      if(deviceAddr<localDeviceAddr) {
        firstActor = true
        if(D) Log.i(TAG, "ConnectedThread doFirstActor=true deliverFileArray ...")
        deliverFileArray(deviceName, deviceAddr)
      } else {
        if(D) Log.i(TAG, "ConnectedThread doFirstActor=false, stay passive ...")
      }
    }

    override def run() {
      if(D) Log.i(TAG, "ConnectedThread run pairedBtOnly="+pairedBtOnly)
      try {
        // while connected, keep listening to the InputStream
        threadRunning = true
        while(threadRunning) {
          //if(D) Log.i(TAG, "ConnectedThread run pairedBtOnly="+pairedBtOnly+" read size...")
          var magic=0
          var magicRecount=0
          do {
            if(codedInputStream==null) {
              threadRunning=false
            } else {
              magic = codedInputStream.readRawVarint32 // may block
              magicRecount+=1
            }
          } while(threadRunning && codedInputStream!=null && magic!=11111)

          if(threadRunning && codedInputStream!=null) {
            val size = codedInputStream.readRawVarint32 // may block
            //if(D) Log.i(TAG, "ConnectedThread run pairedBtOnly="+pairedBtOnly+" read size="+size+" magic="+magic+" magicRecount="+magicRecount+" socket="+socket+" threadRunning="+threadRunning)
            if(threadRunning && size>0) {
              val rawdata = codedInputStream.readRawBytes(size) // bc we know the size of data to expect, this will not block
              if(threadRunning)
                processReceivedRawData(rawdata)
            }
          }
        }

      } catch {
        case ioex:IOException =>
          if(D) Log.i(TAG, "ConnectedThread run IOException disconnected "+ioex+" ##############################")
          // "Software caused connection abort" 
          // todo: catch unexpected disconnect while sending, then switch to backupConnection!
          if(rfCommHelper!=null && rfCommHelper.rfCommService!=null)
            rfCommHelper.rfCommService.stopActiveConnection
          else {
            Log.e(TAG, "ConnectedThread run IOException unable to rfCommHelper.rfCommService.stopActiveConnection")
          }
          if(connectedThread!=null)
            connectedThread.cancel
          else {
            Log.e(TAG, "ConnectedThread run IOException unable to connectedThread.cancel")
          }
        case istex:java.lang.IllegalStateException =>
          Log.e(TAG, "ConnectedThread run IllegalStateException disconnected "+istex)
          if(rfCommHelper!=null && rfCommHelper.rfCommService!=null)
            rfCommHelper.rfCommService.stopActiveConnection
          else {
            Log.e(TAG, "ConnectedThread run IllegalStateException unable to rfCommHelper.rfCommService.stopActiveConnection")
          }
          if(connectedThread!=null)
            connectedThread.cancel
          else {
            Log.e(TAG, "ConnectedThread run IllegalStateException unable to connectedThread.cancel")
          }
      }

      if(D) Log.i(TAG, "ConnectedThread run pairedBtOnly="+pairedBtOnly+" DONE threadRunning="+threadRunning+" ##############################")
    }

    def writeBtShareMessage(btMessage:BtShare.Message) :Unit = {
      //if(D) Log.i(TAG, "ConnectedThread writeBtShareMessage btMessage="+btMessage)
      if(btMessage==null) return

      // fifo queue btMessage - and actually send it from somewhere else
      sendQueue += btMessage
    }

    // called by send() only
    def writeCmdMsg(cmd:String, message:String, toAddr:String, toName:String, sendMsgCounter:Long) = synchronized {
      if(D) Log.i(TAG, "ConnectedThread writeCmdMsg cmd="+cmd+" message="+message+" localDeviceAddr="+localDeviceAddr+" toAddr="+toAddr)
      val btBuilder = BtShare.Message.newBuilder
                                     .setArgCount(sendMsgCounter)
                                     .setFromName(localDeviceName)    // may not be null
                                     .setFromAddr(localDeviceAddr)    // may not be null

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
          if(!threadRunning || disconnecting)
            return
          if(D) Log.i(TAG, "ConnectedThread writeData sendQueue.size="+sendQueue.size+" >500 sleep ===================")
          try { Thread.sleep(1000); } catch { case ex:Exception => }
        }
        if(D) Log.i(TAG, "ConnectedThread writeData sendQueue.size="+sendQueue.size+" after sleep")
      }

      if(!threadRunning || disconnecting)
        return
      var sendData:Array[Byte] = null
      if(size>0) {
        while(sendData==null) {
          if(!threadRunning || disconnecting)
            return
          try {
            sendData = new Array[Byte](size)
          } catch {
            case e: java.lang.OutOfMemoryError =>
              if(D) Log.i(TAG, "ConnectedThread writeData OutOfMemoryError - force System.gc() sendQueue.size="+sendQueue.size+" ===================")
              System.gc
              try { Thread.sleep(2000); } catch { case ex:Exception => }
              System.gc
              if(D) Log.i(TAG, "ConnectedThread writeData OutOfMemoryError - continue after System.gc sendQueue.size="+sendQueue.size)
          }
        }
        Array.copy(data,0,sendData,0,size)
      }

      sendQueue += sendData
    }

    def cancel() {
      if(D) Log.i(TAG, "ConnectedThread cancel()")

      threadRunning = false

      if(mmInStream != null) {
        try { mmInStream.close } catch { case e: Exception => }
        //mmInStream = null
      }

      if(mmOutStream != null) {
        try { mmOutStream.close } catch { case e: Exception => }
        //mmOutStream = null
      }

      codedInputStream = null
      if(mConnectedSendThread!=null)
        mConnectedSendThread.halt

      if(D) Log.i(TAG, "ConnectedThread -> socketCloseFkt")
      socketCloseFkt() // call device-type specific socket.close
      sendQueue.clear
      System.gc
      if(D) Log.i(TAG, "ConnectedThread cancel() done")
    }
  }



  class ConnectedSendThread(sendQueue:scala.collection.mutable.Queue[Any], var codedOutputStream:CodedOutputStream) extends Thread {
    //if(D) Log.i(TAG, "ConnectedSendThread start")
    var running = false
    var blobId:Long = 0
    var contentLength:Long = 0
    var progressLastStep:Long = 0
    val activityManager = if(context!=null) context.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[ActivityManager] else null
    val memoryInfo = new ActivityManager.MemoryInfo

    totalSend = 0

    override def run() {
      //if(D) Log.i(TAG, "ConnectedSendThread run ")
      var progressLastMS:Long = SystemClock.uptimeMillis
      var fileSend:Long = 0
      try {
        while(connectedThread!=null && codedOutputStream!=null && sendQueue!=null) {
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
                bundle.putLong(RFCommHelperService.DELIVER_BYTES, bytesWritten+totalSend)
                bundle.putString(RFCommHelperService.DELIVER_TYPE, "send")
                if(D) Log.i(TAG, "ConnectedSendThread run totalSend="+totalSend+" done")
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
                bundle.putLong(RFCommHelperService.DELIVER_BYTES, bytesWritten+totalSend)
                bundle.putString(RFCommHelperService.DELIVER_TYPE, "send")
                if(D) Log.i(TAG, "ConnectedSendThread run totalSend="+totalSend)
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
          if(D) Log.i(TAG, "ConnectedSendThread run ex="+e)
          halt
      }

      if(activityManager!=null) {
        activityManager.getMemoryInfo(memoryInfo)
        if(D) Log.i(TAG, "ConnectedSendThread run pre DONE memoryInfo.availMem="+memoryInfo.availMem+" memoryInfo.lowMemory="+memoryInfo.lowMemory)
      }

      if(D) Log.i(TAG, "ConnectedSendThread run DONE connectedThread="+connectedThread)
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
          Log.e(TAG, "ConnectedSendThread writeBtShareMessage ioexception errMsg="+errMsg, ex)
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
          Log.e(TAG, "ConnectedSendThread writeData "+ioex)
          activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_USERHINT1, -1, -1, ioex.getMessage).sendToTarget
          halt
      }
    }
  }



  //////////////////////////// file delivery

  private val blobDeliverChunkSize = 10*1024
  private val mimeTypeMap = MimeTypeMap.getSingleton()
  var numberOfSentFiles = 0
  @volatile private var blobDeliverId:Long = 0

  private def deliver(inputStream:InputStream, mime:String, contentLength:Long=0, fileUriString:String, remoteDeviceName:String, remoteDeviceAddr:String) :Int = {
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
      if(D) Log.i(TAG, "deliver sendBlob, localID="+localID+" mime="+mime)
      sendBlob(mime, byteString=null, remoteDeviceAddr, remoteDeviceName, filename, contentLength, localID)

      // send chunked data
      val byteChunkData = new Array[Byte](blobDeliverChunkSize)
      var totalSentBytes:Long = 0
      var readBytes = inputStream.read(byteChunkData,0,blobDeliverChunkSize)
      if(D) Log.i(TAG, "deliver read file done readBytes="+readBytes)
      while(readBytes>0) {
        sendData(readBytes, byteChunkData) // may block
        totalSentBytes += readBytes
        readBytes = inputStream.read(byteChunkData,0,blobDeliverChunkSize)
      }
      if(D) Log.i(TAG, "deliver send fileUriString=["+fileUriString+"] done totalSentBytes="+totalSentBytes+" send EOM")
      sendData(0, byteChunkData) // eom - may block
      inputStream.close
      return 0

    } catch { case ex:Exception =>
      Log.e(TAG, "deliver ",ex)
      val errMsg = "deliver "+ex.getMessage

      AndrTools.runOnUiThread(context) { () =>
        Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show
      }
      return -2
    }
  }

  private def deliverFile(file:File, remoteDeviceName:String, remoteDeviceAddr:String) :Int = {
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
            return deliver(fileInputStream, mimeTypeFromExtension, fileSize, fileName, remoteDeviceName, remoteDeviceAddr)
          }

        } catch {
          case fnfex: java.io.FileNotFoundException =>
            Log.e(TAG, "deliverFile file.getCanonicalPath()="+file.getCanonicalPath()+" FileNotFoundException "+fnfex)
            val errMsg = "File not found "+file.getCanonicalPath()

            AndrTools.runOnUiThread(context) { () =>
              Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show
            }
        }
      }
    }
    return -1
  }

  // called by connectedBt(), connectedWifi(), processBtMessage()
  def deliverFileArray(remoteDeviceName:String, remoteDeviceAddr:String) {
    numberOfSentFiles = 0
    if(selectedFileStringsArrayList==null || selectedFileStringsArrayList.size<1) {
      Log.e(TAG, "deliverFileArray no files to send selectedFileStringsArrayList="+selectedFileStringsArrayList)

      // send special token to indicate the other side may now become the actor
      if(D) Log.i(TAG, "deliverFileArray sending 'yourturn' on empty selectedFileStringArrayList")     
      send("yourturn",null,remoteDeviceAddr,remoteDeviceName)
      // todo: if the other side does not respond to this, we hang - we need to time out
      //       we must start a thread to come back every 10 seconds to check if we had received any MESSAGE_DELIVER_PROGRESS msgs in msgFromServiceHandler
      //       new ReceiverIdleCheckThread().start

    } else {
      new Thread() {
        override def run() {
          AndrTools.runOnUiThread(context) { () =>
            if(activityMsgHandler!=null)
              activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_USERHINT1, -1, -1, "Upload to "+remoteDeviceName).sendToTarget
          }
          try { Thread.sleep(100) } catch { case ex:Exception => }

          try {
            val iterator = selectedFileStringsArrayList.iterator 
            while(iterator.hasNext) {
              val fileString = iterator.next
              if(fileString!=null) {
                if(D) Log.i(TAG, "deliverFileArray fileString=["+fileString+"] numberOfSentFiles="+numberOfSentFiles)

                val idxLastDot = fileString.lastIndexOf(".")
                if(idxLastDot<0) {
                  Log.e(TAG, "deliverFileArray idxLastDot<0 (no file extension)")
                } else {
                  val ext = fileString.substring(idxLastDot+1)
                  //val dstFileName = mBluetoothAdapter.getName+"."+ext
                  //if(D) Log.i(TAG, "deliverFileArray dstFileName=["+dstFileName+"]")
                  if(deliverFile(new File(fileString),remoteDeviceName, remoteDeviceAddr)==0)
                    numberOfSentFiles += 1
                }
              }
            }
          } catch {
            case npex: java.lang.NullPointerException =>
              Log.e(TAG, "deliverFileArray NullPointerException "+npex)
              val errMsg = npex.getMessage
              Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show
          }

          // send special token to indicate the other side is becoming the actor
          if(D) Log.i(TAG, "deliverFileArray sending 'yourturn' after sending")
          send("yourturn",null,remoteDeviceAddr,remoteDeviceName)
          // note: we expect the other party to start sending files immediately now (and after that to call stopActiveConnection)
          // TODO: for the case that nothing happens, we need to disconnect the bt-connection ourselfs
          // TODO: possible solution:
          // 1. capture the current number of received bytes from the service
          // 2. start a dedicated thread to come back in 5 to 10 seconds
          // 3. if no additional new bytes were received ... hang up
        }
      }.start                        
    } 
  }

  def setSendFiles(setSelectedFileStringsArrayList:ArrayList[String]) {
    selectedFileStringsArrayList = setSelectedFileStringsArrayList
    if(D) Log.i(TAG, "setSendFiles() "+selectedFileStringsArrayList)
  }

  def send(cmd:String, message:String=null, toAddr:String=null, toName:String=null) = synchronized {
    // the idea with synchronized is that no other send() shall take over (interrupt) an ongoing send()
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

    if(connectedThread!=null) {
      if(D) Log.i(TAG, "send myCmd="+myCmd+" message="+message+" toAddr="+toAddr+" sendMsgCounter="+thisSendMsgCounter)
      connectedThread.asInstanceOf[ConnectedThread].writeCmdMsg(myCmd,message,toAddr,toName,thisSendMsgCounter)
      //if(D) Log.i(TAG, "send myCmd="+myCmd+" DONE")
    } else {
      if(D) Log.e(TAG, "send myCmd="+myCmd+" message="+message+" toAddr="+toAddr+" sendMsgCounter="+thisSendMsgCounter+" NO connectedThread ########")
    }
  }

  def sendData(size:Int, data:Array[Byte]) {
    if(connectedThread!=null)
      try {
        connectedThread.asInstanceOf[ConnectedThread].writeData(data,size)
      } catch {
        case e: IOException =>
          Log.e(TAG, "sendData exception during write", e)
      }
  }

  def sendBlob(mime:String, byteString:com.google.protobuf.ByteString, toAddr:String=null, toName:String, filename:String=null, contentLength:Long=0, id:Long=0) {
    if(D) Log.i(TAG, "sendBlob mime="+mime+" filename="+filename)
    sendMsgCounter+=1
    val btBuilder = BtShare.Message.newBuilder
                                   .setCommand("blob")
                                   .setArgCount(sendMsgCounter)
                                   .setId(id)
                                   .setDataLength(contentLength)
                                   .setFromName("")    // may not be null // todo: got no fromName here, but it must be set
                                   .setFromAddr("")    // may not be null // todo: got no fromAddr here, but it must be set

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

    if(connectedThread!=null)
      try {
        connectedThread.asInstanceOf[ConnectedThread].writeBtShareMessage(btShareMessage)
      } catch {
        case e: IOException =>
          Log.e(TAG, "Exception during write", e)
          if(activityMsgHandler!=null)
            activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_USERHINT1, -1, -1, e.getMessage).sendToTarget
      }
  }

  def processIncomingBlob(btMessage:BtShare.Message, fromAddr:String, downloadPath:String, remoteDeviceName:String)(readCodedInputStream:() => Array[Byte]) {
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
      var fileWritten=0
      if(activityMsgHandler!=null) {
        activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_USERHINT1, -1, -1, "Download from "+remoteDeviceName).sendToTarget
        activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_USERHINT2, -1, -1, originalFilename).sendToTarget

        val msg = activityMsgHandler.obtainMessage(RFCommHelperService.MESSAGE_DELIVER_PROGRESS)
        val bundle = new Bundle
        //bundle.putLong(RFCommHelperService.DELIVER_ID, blobId)
        bundle.putInt(RFCommHelperService.DELIVER_PROGRESS, 0)
        //bundle.putLong(RFCommHelperService.DELIVER_BYTES, 0)
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
            bundle.putLong(RFCommHelperService.DELIVER_BYTES, bytesWritten+totalSend)
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
            bundle.putLong(RFCommHelperService.DELIVER_BYTES, bytesWritten+totalSend)
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

}

