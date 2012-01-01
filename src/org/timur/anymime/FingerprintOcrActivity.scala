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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.view.animation.AlphaAnimation
import android.view.MotionEvent
import android.view.Window
import android.view.LayoutInflater
import android.view.Gravity
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Matrix
import android.graphics.Color
import android.widget.TextView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.PopupWindow
import android.os.Handler
import android.os.Message
import android.os.SystemClock
import android.content.res.Configuration

import org.timur.rfcomm._

object FingerprintOcrActivity {
  val SHOW_DETECTED_CHARACTER1 = 1
  val SHOW_DETECTED_CHARACTER2 = 2
  val TEXT_MESSAGE = 3
}

class FingerprintOcrActivity extends Activity {
  private val TAG = "FingerprintOcrActivity"
  private val D = Static.DBGLOG

  private var context:FingerprintOcrActivity = null
  private var scanMessageTextView:TextView = null
  private var scanResult1Adapter:ScanResultAdapter = null
  private var scanResult2Adapter:ScanResultAdapter = null
  private var startTime:Long = 0
  private var destroyed = false
  

  override def onCreate(savedInstanceState:Bundle) {
    super.onCreate(savedInstanceState)
    if(D) Log.i(TAG, "onCreate")
    context = this

    val intent = getIntent
    if(intent==null) {
      return
    }

    val extrasBundle = intent.getExtras
    if(extrasBundle==null) {
      return
    }

    startTime = SystemClock.uptimeMillis

    val origBitmap = intent.getParcelableExtra("bitmap").asInstanceOf[Bitmap]
    val sentKeyFingerprint = extrasBundle.getString("sentKeyFingerprint")
    val receivedKeyFingerprint = extrasBundle.getString("receivedKeyFingerprint")
    val originalOrientation = extrasBundle.getInt("orientation");


// todo: onetime file save bitmap to sdcard +  log-display of the two fingerprints
// todo: then create a very simple activity to call this activity with the propper 3 settings
//       this activity should only be made available by manifest, in non-release mode

    if(D) Log.i(TAG, "onCreate setRequestedOrientation="+originalOrientation)
    setRequestedOrientation(originalOrientation)

    requestWindowFeature(Window.FEATURE_NO_TITLE)
    setContentView(R.layout.compare_fingerprint)
    val mainView = findViewById(R.id.main)
    mainView.setBackgroundColor(Color.argb(0xff, 0x20, 0x20, 0x20))

    val screenshotFrame = findViewById(R.id.screenshotFrame)
    val screenshot = findViewById(R.id.screenshot)
    val progressBarView = findViewById(R.id.progressBar).asInstanceOf[ProgressBar]

    scanMessageTextView = findViewById(R.id.scanMessage).asInstanceOf[TextView]
    scanResult1Adapter = new ScanResultAdapter(this, R.layout.scanresult_entry)
    val scanResult1View = findViewById(R.id.scanResult1View).asInstanceOf[ListView]
    if(scanResult1View!=null)
      scanResult1View.setAdapter(scanResult1Adapter)

    scanResult2Adapter = new ScanResultAdapter(this, R.layout.scanresult_entry)
    val scanResult2View = findViewById(R.id.scanResult2View).asInstanceOf[ListView]
    if(scanResult2View!=null)
      scanResult2View.setAdapter(scanResult2Adapter)

    screenshot.setBackgroundDrawable(new android.graphics.drawable.BitmapDrawable(getResources, origBitmap))
    progressBarView.setProgress(5)
    var width = origBitmap.getWidth
    var height = origBitmap.getHeight
    if(D) Log.i(TAG, "onCreate origBitmap.size="+origBitmap.getRowBytes*height+" w="+width+"*"+height+" rb="+origBitmap.getRowBytes+" time="+(SystemClock.uptimeMillis-startTime))  // 200 ms

    // scanner animation
    new Thread() { override def run() {
      // we need to wait for screenshot BackgroundDrawable to be loaded
      try { Thread.sleep(300) } catch { case ex:Exception => }
      AndrTools.runOnUiThread(context) { () =>
        val scanLineView = findViewById(R.id.scanLine).asInstanceOf[View]
        scanLineView.setVisibility(View.VISIBLE)
        val translateAnimation = new TranslateAnimation(0,0, 0,screenshotFrame.getHeight)
        translateAnimation.setDuration(3000)
        translateAnimation.setFillAfter(true)
        translateAnimation.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
          override def onAnimationEnd(animation:Animation) {
            val translateBackAnimation = new TranslateAnimation(0,0, screenshotFrame.getHeight,-10)
            translateBackAnimation.setDuration(1300) 
            translateBackAnimation.setFillAfter(true)
            translateBackAnimation.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
              override def onAnimationEnd(animation:Animation) {
                scanLineView.setVisibility(View.GONE)
                val scanLineVertiView = findViewById(R.id.scanLineVerti).asInstanceOf[View]
                scanLineVertiView.setVisibility(View.VISIBLE)
                val translateVertiAnimation = new TranslateAnimation(0,screenshotFrame.getWidth, 0,0)
                translateVertiAnimation.setDuration(1300) 
                translateVertiAnimation.setFillAfter(true)
                translateVertiAnimation.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
                  override def onAnimationEnd(animation:Animation) {
                    val translateVertiBackAnimation = new TranslateAnimation(screenshotFrame.getWidth,-10, 0,0)
                    translateVertiBackAnimation.setDuration(1300) 
                    translateVertiBackAnimation.setFillAfter(true)
                    translateVertiBackAnimation.setAnimationListener(new android.view.animation.Animation.AnimationListener(){
                      override def onAnimationEnd(animation:Animation) {
                        scanLineVertiView.setVisibility(View.GONE)
                      }
                      override def onAnimationRepeat(animation:Animation) { }
                      override def onAnimationStart(animation:Animation) { }
                    })
                    scanLineVertiView.startAnimation(translateVertiBackAnimation)
                  }
                  override def onAnimationRepeat(animation:Animation) { }
                  override def onAnimationStart(animation:Animation) { }
                })
                scanLineVertiView.startAnimation(translateVertiAnimation)
              }
              override def onAnimationRepeat(animation:Animation) { }
              override def onAnimationStart(animation:Animation) { }
            })
            scanLineView.startAnimation(translateBackAnimation)
          }
          override def onAnimationRepeat(animation:Animation) { }
          override def onAnimationStart(animation:Animation) { }
        })
        scanLineView.startAnimation(translateAnimation)
      }
    }}.start

    // scanning / detecting
    new Thread() { override def run() {
      activityMsgHandler.obtainMessage(FingerprintOcrActivity.TEXT_MESSAGE, 0, -1, "detection in progress ...").sendToTarget
      progressBarView.setProgress(10)
      var countVerticalBits = 0

      // align screen by rotating towards the longest CountLongestVerticalLine
      var alignDegreeBase = 0f
      var alignDegree = 0.10f
      var i=0
      var breakout=false
      while(i<16 && !breakout) {
        val countVerticalBits1 = Ocr.fktCountLongestVerticalLine(origBitmap,alignDegree,false)
        if(countVerticalBits1>=countVerticalBits) {
          alignDegreeBase = alignDegree
          countVerticalBits = countVerticalBits1
        }
        val countVerticalBits2 = Ocr.fktCountLongestVerticalLine(origBitmap,-alignDegree,false)
        if(countVerticalBits2>=countVerticalBits) {
          alignDegreeBase = -alignDegree
          countVerticalBits = countVerticalBits2
        }
        progressBarView.setProgress(10+i*3)
        if(D) Log.i(TAG, "onCreate grob alignDegree="+alignDegree+" alignDegreeBase="+alignDegreeBase+
                         " countVerticalBits="+countVerticalBits+" countVerticalBits1="+countVerticalBits1+" countVerticalBits2="+countVerticalBits2+
                         " time="+(SystemClock.uptimeMillis-startTime))
        if(countVerticalBits>305 && countVerticalBits1<countVerticalBits-80 && countVerticalBits2<countVerticalBits-80)
          breakout=true
        else
          alignDegree += 0.5f
        i+=1
      }
      if(D) Log.i(TAG, "onCreate scan grob alignDegreeBase="+alignDegreeBase+" ("+height+") time="+(SystemClock.uptimeMillis-startTime))  // 3900/2590 ms

      alignDegree = 0.22f
      i=0
      while(countVerticalBits<555 && i<4) {
        val countVerticalBits1 = Ocr.fktCountLongestVerticalLine(origBitmap,alignDegreeBase-alignDegree,false)
        val countVerticalBits2 = Ocr.fktCountLongestVerticalLine(origBitmap,alignDegreeBase+alignDegree,false)
        if(countVerticalBits1>countVerticalBits && countVerticalBits1>countVerticalBits2) {
          alignDegreeBase = alignDegreeBase-alignDegree
          countVerticalBits = countVerticalBits1
        } else if(countVerticalBits2>countVerticalBits && countVerticalBits2>countVerticalBits1) {
          alignDegreeBase = alignDegreeBase+alignDegree
          countVerticalBits = countVerticalBits2
        }
        if(D) Log.i(TAG, "onCreate scan fine  countVBits="+countVerticalBits+" countVBits1="+countVerticalBits1+" countVBits2="+countVerticalBits2+
                         " alignDegreeBase="+alignDegreeBase+" alignDegree="+alignDegree+" time="+(SystemClock.uptimeMillis-startTime))
        alignDegree = alignDegree/1.4f
        progressBarView.setProgress(70+i*2)
        i+=1
      }
      if(D) Log.i(TAG, "onCreate scan fine alignDegreeBase="+alignDegreeBase+" time="+(SystemClock.uptimeMillis-startTime))  // 6100/4857 ms

      progressBarView.setProgress(82)

      // create alignedBitmap as after alignDegreeBase rotation
      // rotate by the specified number of degrees, with a pivot point at (px, py). The pivot point is the coordinate that should remain unchanged by the specified transformation. 
      var matrix = new Matrix()
      matrix.postRotate(alignDegreeBase,width.asInstanceOf[Float]/2f,height.asInstanceOf[Float]/2f)
      var alignedBitmap = Bitmap.createBitmap(origBitmap, 0, 0, width, height, matrix, true)

      width = alignedBitmap.getWidth
      height = alignedBitmap.getHeight
      if(width<248 || width>=252) {
        //if(D) Log.i(TAG, "onCreate scaled to optimal from width="+width+" height="+height)
        height=(height.asInstanceOf[Float]/(width.asInstanceOf[Float]/250)).asInstanceOf[Int]
        width=250
        alignedBitmap = Bitmap.createScaledBitmap(alignedBitmap, width, height, true)
      }

      //if(D) Log.i(TAG, "onCreate post align width="+width+" height="+height+" time="+(SystemClock.uptimeMillis-startTime))  // 6200/4925 ms
      progressBarView.setProgress(85)

      //AndrTools.runOnUiThread(context) { () =>
      //  screenshot.setBackgroundDrawable(new android.graphics.drawable.BitmapDrawable(getResources, alignedBitmap))
      //}

      // this is to initialze pxls[] and pixelInColumn[] for our zoom-scale operation
      Ocr.fktCountLongestVerticalLine(alignedBitmap,0f,false)     // true for debug display

      // zoom-scale image along outer frame to result in 'optimal size'
      // search leftMax and rightMax in pixelInColumn
      // then derive center from leftMax and rightMax
      var leftMax = 0
      var countLongestVerticalLine = -1
      for(ix <- 0 until alignedBitmap.getWidth/2) {
        try {
          if(Ocr.pixelInColumn(ix)>countLongestVerticalLine) {
            countLongestVerticalLine = Ocr.pixelInColumn(ix)
            leftMax = ix
          }
        } catch {
          case aioobex:java.lang.ArrayIndexOutOfBoundsException =>
            Log.e(TAG, "onCreate set leftMax ix="+ix+" ex="+aioobex)
        }
      }
      leftMax = leftMax+3

      var rightMax = alignedBitmap.getWidth-1
      countLongestVerticalLine = -1 //countLongestVerticalLine-1
      for(ix <- alignedBitmap.getWidth-1 until alignedBitmap.getWidth/2 by -1) {
        try {
          if(Ocr.pixelInColumn(ix)>countLongestVerticalLine) {
            countLongestVerticalLine = Ocr.pixelInColumn(ix)
            rightMax = ix-1
          }
        } catch {
          case aioobex:java.lang.ArrayIndexOutOfBoundsException =>
            Log.e(TAG, "onCreate set rightMax ix="+ix+" ex="+aioobex)
        }
      }
      rightMax = rightMax-1

      val newWidth = rightMax - leftMax
      val horiCenter = leftMax + newWidth/2

      //derive scaleFactor from leftMax and rightMax
      val scaleFactor = alignedBitmap.getWidth.asInstanceOf[Float] / newWidth.asInstanceOf[Float]
      if(D) Log.i(TAG, "onCreate scan camera bitmap scaling leftMax="+leftMax+" rightMax="+rightMax+" horiCenter="+horiCenter+" oldWidth="+alignedBitmap.getWidth+" newWidth="+newWidth+" scaleFactor="+scaleFactor)

      if(leftMax>0 && leftMax<rightMax) {
        // zoom into the important info
        matrix = new Matrix()
        // scale by sx and sy, with a pivot point at (px, py). The pivot point is the coordinate that should remain unchanged by the specified transformation. 
        matrix.postScale(scaleFactor, scaleFactor, horiCenter.asInstanceOf[Float], alignedBitmap.getHeight.asInstanceOf[Float]/2f)
        var newHeight = ((newWidth*7f)/4f).asInstanceOf[Int]+30   // 7/4 is the aspect ratio of our double fingerprint matrix
        if(newHeight>alignedBitmap.getHeight)
          newHeight = alignedBitmap.getHeight
        if(D) Log.i(TAG, "onCreate before zoom newHeight="+newHeight)
        var top = Ocr.fktPixlinesAboveNextHoriLine(alignedBitmap.getWidth, 0, math.min(160,alignedBitmap.getHeight), 0.40f, 0.65f)
        top += 4  // cut off top line
        
        if(top + newHeight > alignedBitmap.getHeight)
          newHeight = alignedBitmap.getHeight - top

        if(D) Log.i(TAG, "onCreate before zoom top="+top+" newHeight="+newHeight)
        val zoomedBitmap = Bitmap.createBitmap( alignedBitmap, 
                                                leftMax,    // x
                                                top,        // y
                                                newWidth,   // width
                                                newHeight,  // height (y+height must be <= alignedBitmap.getHeight)
                                                matrix, 
                                                true)
        width = zoomedBitmap.getWidth
        height = zoomedBitmap.getHeight
        if(D) Log.i(TAG, "onCreate scan camera bitmap done precalculation width="+width+" height="+height+" time="+(SystemClock.uptimeMillis-startTime))  // 6460/5210 ms
        progressBarView.setProgress(88)

        // display the outcome of the zooming operation
        //if(D) Log.i(TAG, "activityMsgHandler after zoom")
        //Ocr.fktCountLongestVerticalLine(zoomedBitmap,0f,true)   // true for debug
        //AndrTools.runOnUiThread(context) { () =>
        //  screenshot.setBackgroundDrawable(new android.graphics.drawable.BitmapDrawable(getResources, zoomedBitmap))
        //}

        // set pxls to zoomedBitmap (and also make them transparent)
        Ocr.setPxlsFromBitmap(zoomedBitmap)

        // find the bottom line, which is expected to be:
        // for 1 fingerprint:  207 pixels down 
        // for 2 fingerprints: 415 pixels down 
        var bottom = Ocr.fktPixlinesAboveNextHoriLine(zoomedBitmap.getWidth, 170, zoomedBitmap.getHeight, 0.75f, 0.85f)
        if(D) Log.i(TAG, "before detectCharacterCells zoomedBitmap.getHeight="+zoomedBitmap.getHeight+" bottom="+bottom+" time="+(SystemClock.uptimeMillis-startTime))  // 6600/5340 ms

        // now we can scan, detect and show the result (number of errors) to the user
        Ocr.detectCharacterCells(zoomedBitmap.getWidth, bottom, 
                                 sentKeyFingerprint, receivedKeyFingerprint, 
                                 activityMsgHandler, progressBarView)
        // switch to page2: show detection results
        if(D) Log.i(TAG, "switch to page2... time="+(SystemClock.uptimeMillis-startTime))   // 8870/7980 ms
        val resultTitleView = findViewById(R.id.resultTitle).asInstanceOf[TextView]
        val page1 = findViewById(R.id.page1).asInstanceOf[View]
        val page2 = findViewById(R.id.page2).asInstanceOf[View]
        val scanResultsMain = findViewById(R.id.scanResultsMain).asInstanceOf[android.widget.LinearLayout]
        var errorHighlightVisible = false

        // let the user to drag the calculated fingerprint(s) over the scanned fingerprint(s)
        val fullViewTouchListener = new android.view.View.OnTouchListener() {
          var draggingStartTime = 0l
          var startTouchX = 0f
          var startTouchY = 0f
          var touchX = 0f
          var touchY = 0f
          var posX = 0f
          var posY = 0f
          var actionDownView:View = null
          val result1Popup = findViewById(R.id.result1Popup).asInstanceOf[View]
          val result1Text = findViewById(R.id.result1Text).asInstanceOf[TextView]
          var result1IsVisible = false
          val result2Popup = findViewById(R.id.result2Popup).asInstanceOf[View]
          val result2Text = findViewById(R.id.result2Text).asInstanceOf[TextView]
          var result2IsVisible = false

          val popup1Text = 
            // click upper half
            if(Ocr.errorCount1==0)
              "The fingerprint captured by camera is identical to the one generated locally"
            else
            if(Ocr.errorCount1<=3)
              "The fingerprint captured by camera shows some deviations. "+
              "This looks like a image capturing problem."
            else
              "The fingerprint captured by camera shows severe deviations. "+
              "It is not possible to confirm a correct key delivery."

          val popup2Text = 
            if(Ocr.errorCount2==0)
              "The fingerprint captured by camera is identical to the one generated locally"
            else
            if(Ocr.errorCount2<=3)
              "The fingerprint captured by camera shows some deviations. "+
              "This looks like a image capturing problem."
            else
              "The fingerprint captured by camera shows severe deviations. "+
              "It is not possible to confirm the delivery."

          val color1 = 
            if(Ocr.errorCount1==0)
              Color.argb(0xff,0x30,0xb0,0x40)
            else
            if(Ocr.errorCount1<=3)
              Color.argb(0xff,0xff,0xe0,0x20)
            else
              Color.argb(0xff,0xff,0x20,0x20) // same as colorError in ScanResultAdapter

          val color2 = 
            if(Ocr.errorCount2==0)
              Color.argb(0xff,0x30,0xb0,0x40)
            else
            if(Ocr.errorCount2<=3)
              Color.argb(0xff,0xff,0xe0,0x20)
            else
              Color.argb(0xff,0xff,0x20,0x20) // same as colorError in ScanResultAdapter

          result1Text.setTextColor(color1)
          result1Text.setText(popup1Text)
          result2Text.setTextColor(color2)
          result2Text.setText(popup2Text)

          override def onTouch(view:View, motionEvent:MotionEvent) :Boolean = {
            if(motionEvent.getAction == MotionEvent.ACTION_DOWN) {
              draggingStartTime = SystemClock.uptimeMillis
              //if(D) Log.i(TAG, "onTouch DRAGGING_START view="+view)
              touchX = motionEvent.getRawX
              touchY = motionEvent.getRawY // 0 = screen top !
              startTouchX = touchX
              startTouchY = touchY
              posX = 0f
              posY = 0f
              actionDownView = view

            } else if(motionEvent.getAction == MotionEvent.ACTION_MOVE) {
              if(draggingStartTime>0l) {
                val totalMoveX = (motionEvent.getRawX-startTouchX)/4
                val totalMoveY = (motionEvent.getRawY-startTouchY)/6
                if(math.abs(totalMoveX)>2f || math.abs(totalMoveY)>2f) {
                  if(result1IsVisible) {
                    result1Popup.setVisibility(View.GONE)
                    result1IsVisible=false
                  }
                  if(result2IsVisible) {
                    result2Popup.setVisibility(View.GONE)
                    result2IsVisible=false
                  }
                }

                val moveX = (motionEvent.getRawX-touchX)/4
                val moveY = (motionEvent.getRawY-touchY)/6
                //if(D) Log.i(TAG, "onTouch DRAGGING X="+motionEvent.getRawX+" Y="+motionEvent.getRawY+" moveX="+moveX+" moveY="+moveY+" ("+totalMoveX+" "+totalMoveY+")")
                val translateAnimation = new TranslateAnimation(posX,posX+moveX, posY,posY+moveY)
                translateAnimation.setDuration(300)
                translateAnimation.setFillAfter(true)
                for(i <- 0 until 5) {
                  scanResult1Adapter.wantedResultViewList.get(i).startAnimation(translateAnimation)
                  if(!Ocr.skipped2ndFingerprint)
                    scanResult2Adapter.wantedResultViewList.get(i).startAnimation(translateAnimation)
                }

                posX += moveX
                posY += moveY
                touchX = motionEvent.getRawX
                touchY = motionEvent.getRawY
              }

            } else if(motionEvent.getAction == MotionEvent.ACTION_UP) {
              //if(D) Log.i(TAG, "onTouch DRAGGING_STOP view="+view)
              if(view==actionDownView) {
                if(SystemClock.uptimeMillis-draggingStartTime<160) {
                  if(view==scanResult1View) {
                    if(!result1IsVisible) {
                      result1Popup.setVisibility(View.VISIBLE)
                      result1IsVisible=true
                    } else {
                      result1Popup.setVisibility(View.GONE)
                      result1IsVisible=false
                    }
                    return false

                  } else if(view==scanResult2View) {
                    if(!result2IsVisible) {
                      result2Popup.setVisibility(View.VISIBLE)
                      result2IsVisible=true
                    } else {
                      result2Popup.setVisibility(View.GONE)
                      result2IsVisible=false
                    }
                    return false
                  }
                }
              }

              draggingStartTime = 0l
              val translateAnimation = new TranslateAnimation(posX,0, posY,0)
              translateAnimation.setDuration((posX*posX + posY*posY).asInstanceOf[Long]/8+300)   // duration depends on distance 
              translateAnimation.setFillAfter(true)
              for(i <- 0 until 5) {
                scanResult1Adapter.wantedResultViewList.get(i).startAnimation(translateAnimation)
                if(!Ocr.skipped2ndFingerprint)
                  scanResult2Adapter.wantedResultViewList.get(i).startAnimation(translateAnimation)
              }
            }

            return false
          }
        }

        def page2Init() {
          if(D) Log.i(TAG, "page2Init")
          resultTitleView.setText("Fingerprint verification")

          // alpha hide errorMarker
          val alphaAnimation = new AlphaAnimation(0.0f, 0.0f);
          alphaAnimation.setDuration(0);
          alphaAnimation.setFillAfter(true)
          //if(D) Log.i(TAG, "page2Init scanResult1Adapter.getCount="+scanResult1Adapter.getCount)
          for(i <- 0 until 5) {
            scanResult1Adapter.errorMarkerViewList.get(i).startAnimation(alphaAnimation)
            if(!Ocr.skipped2ndFingerprint)
              scanResult2Adapter.errorMarkerViewList.get(i).startAnimation(alphaAnimation)
          }

          // translate-animate both fingerprint-layers
          val translateAnimationFromLeft = new TranslateAnimation(-60,0, 0,0)
          translateAnimationFromLeft.setDuration(1100)
          translateAnimationFromLeft.setFillAfter(true)
          val translateAnimationFromRight = new TranslateAnimation(60,0, 0,0)
          translateAnimationFromRight.setDuration(1100)
          translateAnimationFromRight.setFillAfter(true)
          for(i <- 0 until 5) {
            if(i==4) {
              //if(D) Log.i(TAG, "page2Init translateAnimationFromLeft setAnimationListener")
              translateAnimationFromLeft.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
                override def onAnimationEnd(animation:Animation) {
                  //if(D) Log.i(TAG, "page2Init translateAnimationFromLeft onAnimationEnd")
                  // errorHighlight
                  val alphaAnimation = new AlphaAnimation(0.0f, 1.0f);
                  alphaAnimation.setDuration(600);
                  alphaAnimation.setFillAfter(true)
                  for(i <- 0 until 5) {
                    scanResult1Adapter.errorMarkerViewList.get(i).startAnimation(alphaAnimation)
                    if(!Ocr.skipped2ndFingerprint)
                      scanResult2Adapter.errorMarkerViewList.get(i).startAnimation(alphaAnimation)
                  }
                }
                override def onAnimationRepeat(animation:Animation) { }
                override def onAnimationStart(animation:Animation) { }
              })
            }

            scanResult1Adapter.wantedResultViewList.get(i).startAnimation(translateAnimationFromLeft)
            scanResult1Adapter.scannedResultViewList.get(i).startAnimation(translateAnimationFromRight)
            if(!Ocr.skipped2ndFingerprint) {
              scanResult2Adapter.wantedResultViewList.get(i).startAnimation(translateAnimationFromLeft)
              scanResult2Adapter.scannedResultViewList.get(i).startAnimation(translateAnimationFromRight)
            }
          }

          scanResult1View.setVisibility(View.VISIBLE)
          if(!Ocr.skipped2ndFingerprint)
            scanResult2View.setVisibility(View.VISIBLE)

          // enable dragging & clicking
          mainView.setOnTouchListener(fullViewTouchListener)
          scanResult1View.setOnTouchListener(fullViewTouchListener)
          scanResult2View.setOnTouchListener(fullViewTouchListener)
        }

        if(!destroyed) {
          AndrTools.runOnUiThread(context) { () =>
            page1.setVisibility(View.GONE)
            if(D) Log.i(TAG, "page2.setVisibility page2="+page2)
            page2.setVisibility(View.VISIBLE)
            //page2Init // delay this so we get access to the rendered views
          }
          try { Thread.sleep(500) } catch { case ex:Exception => }
          AndrTools.runOnUiThread(context) { () =>
            if(D) Log.i(TAG, "delayed page2Init...")
            if(!destroyed)
              page2Init
          }
        }

      } else {
        activityMsgHandler.obtainMessage(FingerprintOcrActivity.TEXT_MESSAGE, 0, -1, "failed to detect").sendToTarget
      }
    }}.start
  }

  val activityMsgHandler = new Handler() {
    override def handleMessage(msg:Message) {
      msg.what match {
        case FingerprintOcrActivity.TEXT_MESSAGE =>
          val textMessage = msg.obj.asInstanceOf[String]
          AndrTools.runOnUiThread(context) { () =>
            scanMessageTextView.setText(textMessage)
          }
          
        case FingerprintOcrActivity.SHOW_DETECTED_CHARACTER1 =>
          val displayCharDetected = msg.arg1.asInstanceOf[Char]
          val errorFlag = if(msg.arg2>0) true else false
          val currentBitmap = msg.obj.asInstanceOf[Bitmap]
          //if(D) Log.i(TAG, "activityMsgHandler SHOW_DETECTED_CHARACTER1 char="+displayCharDetected+" error="+errorFlag)
          scanResult1Adapter.add(displayCharDetected,currentBitmap,errorFlag)
          scanResult1Adapter.notifyDataSetChanged

        case FingerprintOcrActivity.SHOW_DETECTED_CHARACTER2 =>
          val displayCharDetected = msg.arg1.asInstanceOf[Char]
          val errorFlag = if(msg.arg2>0) true else false
          val currentBitmap = msg.obj.asInstanceOf[Bitmap]
          //if(D) Log.i(TAG, "activityMsgHandler SHOW_DETECTED_CHARACTER2 char="+displayCharDetected+" error="+errorFlag)
          scanResult2Adapter.add(displayCharDetected,currentBitmap,errorFlag)
          scanResult2Adapter.notifyDataSetChanged
      }
    }
  }

  override def onConfigurationChanged(newConfig:Configuration) {
    if(D) Log.i(TAG, "onConfigurationChanged")
    super.onConfigurationChanged(newConfig)
  }

  override def onDestroy() {
    if(D) Log.i(TAG, "onDestroy")
    destroyed = true
    super.onDestroy()
  }
}

