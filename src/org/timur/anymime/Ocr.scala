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
import android.view.View
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Matrix
import android.graphics.Color
import android.widget.ProgressBar
import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.Message

object Ocr {
  private val TAG = "Ocr"
  private val D = false //Static.DBGLOG

  var pixelInColumn:Array[Int] = null
  var skipped2ndFingerprint = false
  var errorCount1 = 0
  var errorCount2 = 0

  private var pxls = new Array[Int](1)
  private val pixelThreshold  = 0xc0
  private val pixelThresholdLow = 0x90
  private val cellHoriDist = 21
  private val cellVertiDist = 31
  private val characters = 16     // 0123456789ABCDEF
  private val fontLines = 20      // pixel height of one character
  private val charColumns = 18    // pixel width of one character
  private val fontSetArray = new Array[Int](characters)
  private val fontClrArray = new Array[Int](characters)

  private var activityMsgHandler:Handler = null
  private var cellNumber = 0
  private var keyFingerprint:String = null
  private var keyFingerprintPos = 0
  private var detectedCharacters = 0
  private var fixedCount = 0
  private var currentThreshold = pixelThreshold
  private var sumSetPixelInCell = 0
  private var fixedCelly = 0

  def setPxlsFromBitmap(bitmap:Bitmap) {
    pxls = new Array[Int](bitmap.getWidth*bitmap.getHeight)
    bitmap.getPixels(pxls, 0, bitmap.getWidth, 0, 0, bitmap.getWidth, bitmap.getHeight)
    val pixelThresholdLow2=0x70
    for(xa <- 0 until pxls.length) {
      if((pxls(xa)&0x00FF0000) < 0x900000 && 
         (pxls(xa)&0x0000FF00) < 0x9000 && 
         (pxls(xa)&0x000000FF) < 0x90) {
        pxls(xa) = 0x00000000 // fully transparent pixel
      } else
      if((pxls(xa)&0x00FF0000) < 0xb00000 && 
         (pxls(xa)&0x0000FF00) < 0xb000 && 
         (pxls(xa)&0x000000FF) < 0xb0) {
        pxls(xa) = 0xc0000000 | pxls(xa)&0xFFFFFF  // partly transparent pixel
      }
    }
  }

  def fktCountLongestVerticalLine(srcBitmap:Bitmap, rotate:Float, show:Boolean) :Int = {
    var matrix = new Matrix()
    matrix.postRotate(rotate,srcBitmap.getWidth.asInstanceOf[Float]/2f,srcBitmap.getHeight.asInstanceOf[Float]/2f);
    val alignedBitmap = Bitmap.createBitmap(srcBitmap, 0, 0, srcBitmap.getWidth, srcBitmap.getHeight, matrix, true)
    pxls = new Array[Int](alignedBitmap.getWidth*alignedBitmap.getHeight) 
    for(i <- 0 until alignedBitmap.getWidth*alignedBitmap.getHeight) pxls(i)=0
    alignedBitmap.getPixels(pxls, 0, alignedBitmap.getWidth, 0, 0, alignedBitmap.getWidth, alignedBitmap.getHeight)

    if(show) {
      for(iy <- 0 until alignedBitmap.getHeight) {
        val stringBuilder = new StringBuilder()
        for(ix <- 0 until alignedBitmap.getWidth) {
          val bits = pxls(iy*alignedBitmap.getWidth+ix) & 0xff
          if(bits>=pixelThreshold) 
            stringBuilder.append("#")
          else
            stringBuilder.append(".")
        }
        if(D) Log.i(TAG, stringBuilder.toString)
      }
    }

    pixelInColumn = new Array[Int](alignedBitmap.getWidth) 
    for(iy <- 0 until alignedBitmap.getHeight) {
      for(ix <- 0 until alignedBitmap.getWidth) {
        var pixel=0
        var pixel2=0
        var pixel3=0
        pixel = pxls(iy*alignedBitmap.getWidth+ix) & 0xff
        if(ix+1<alignedBitmap.getWidth) {
          pixel2 = pxls(iy*alignedBitmap.getWidth+ix+1) & 0xff
          if(ix+2<alignedBitmap.getWidth)
            pixel3 = pxls(iy*alignedBitmap.getWidth+ix+2) & 0xff
        }
        if(pixel>=pixelThreshold || pixel2>=pixelThreshold || pixel3>=pixelThreshold)
          pixelInColumn(ix) = pixelInColumn(ix) +1
      }
    }
    // get the longest vertical line on the left half
    var countLongestVerticalLine1 = -1
    var longestIndex1 = -1
    for(ix <- 0 until alignedBitmap.getWidth/2) {
      if(pixelInColumn(ix)>countLongestVerticalLine1) {
        countLongestVerticalLine1 = pixelInColumn(ix)
        longestIndex1 = ix
      }
    }
    // get the longest vertical line on the right half
    var countLongestVerticalLine2 = -1
    var longestIndex2 = -1;
    for(ix <- alignedBitmap.getWidth/2 until alignedBitmap.getWidth) {
      if(pixelInColumn(ix)>countLongestVerticalLine2) {
        countLongestVerticalLine2 = pixelInColumn(ix)
        longestIndex2 = ix
      }
    }
    if(show) {
      if(D) Log.i(TAG, "fktCountLongestVerticalLine rotate="+rotate+" countLongestVerticalLine1="+countLongestVerticalLine1+" longestIndex1="+longestIndex1+" countLongestVerticalLine2="+countLongestVerticalLine2+" longestIndex2="+longestIndex2)
    }
    return countLongestVerticalLine1 + countLongestVerticalLine2
  }

  def fktPixlinesAboveNextHoriLine(width:Int, offset:Int, height:Int, minPercent:Float, breakPercent:Float) :Int = {
    var basey = 0
    var baseyPixelInLine = 0
    var breakout = false
    var iy = offset
    if(D) Log.i(TAG, "fktPixlinesAboveNextHoriLine width="+width+" height="+height)
    while(!breakout && iy<height) {
      var countPixelInLine=0
      //if(D) Log.i(TAG, "fktPixlinesAboveNextHoriLine iy="+iy+" basey="+basey)
      for(ix <- 0 until width) {
        val pixel = pxls(iy*width+ix) & 0xff
        val pixelBelow  = if(iy+1<height) pxls((iy+1)*width+ix) & 0xff else 0
        val pixelBelow2 = if(iy+2<height) pxls((iy+2)*width+ix) & 0xff else 0
        val pixelBelow3 = if(iy+3<height) pxls((iy+3)*width+ix) & 0xff else 0
        if(pixel>=pixelThresholdLow || pixelBelow>=pixelThresholdLow || pixelBelow2>=pixelThresholdLow || pixelBelow3>=pixelThresholdLow) {
          countPixelInLine+=1
        }
      }

      if(countPixelInLine>baseyPixelInLine) {
        basey = iy
        baseyPixelInLine = countPixelInLine
        if(D) Log.i(TAG, "fktPixlinesAboveTopline iy="+iy+" basey="+basey+" baseyPixelInLine="+baseyPixelInLine)
        if(baseyPixelInLine > (width.asInstanceOf[Float]*breakPercent).asInstanceOf[Int])
          breakout=true
      }
      iy+=1
    } 

    if(baseyPixelInLine > (width.asInstanceOf[Float]*minPercent).asInstanceOf[Int])
      return basey
    return 0
  }

  def detectCharacterCells(width:Int, height:Int, sentKeyFingerprint:String, receivedKeyFingerprint:String, setActivityMsgHandler:Handler, progressBarView:ProgressBar) :Int = {
    if(D) Log.i(TAG, "detectCharacterCells width="+width+" height="+height)
    activityMsgHandler = setActivityMsgHandler
    detectedCharacters = 0
    errorCount1 = 0
    errorCount2 = 0
    cellNumber = 0
    fixedCount = 0
    skipped2ndFingerprint = false
    currentThreshold = pixelThreshold
    sumSetPixelInCell = 0

    // 1. scan other devices "Received key fingerprint" (of the key we sent)
    var cellx = 8
    var celly = 42
    var fixCelly = 0
    if(D) Log.i(TAG, "detectCharacterCells cellx="+cellx+" celly="+celly)

    keyFingerprint = sentKeyFingerprint
    if(keyFingerprint==null)
      keyFingerprint = receivedKeyFingerprint
    keyFingerprintPos = 0

    // 1st line in upper section
    fixCelly = scanCellLine(pxls, width, height, cellx, celly)
    if(fixCelly>celly) celly+=(fixCelly-celly+1)/2
    else if(fixCelly<celly) celly-=(celly-fixCelly+1)/2
    progressBarView.setProgress(90)

    // 2nd line in upper section
    celly+=cellVertiDist
    fixCelly = scanCellLine(pxls, width, height, cellx, celly)
    if(fixCelly>celly) celly+=(fixCelly-celly+1)/2
    else if(fixCelly<celly) celly-=(celly-fixCelly+1)/2
    progressBarView.setProgress(91)

    // 3rd line in upper section
    celly+=cellVertiDist
    fixCelly = scanCellLine(pxls, width, height, cellx, celly)
    if(fixCelly>celly) celly+=(fixCelly-celly+1)/2
    else if(fixCelly<celly) celly-=(celly-fixCelly+1)/2
    progressBarView.setProgress(92)

    // 4th line in upper section
    celly+=cellVertiDist
    fixCelly = scanCellLine(pxls, width, height, cellx, celly)
    if(fixCelly>celly) celly+=(fixCelly-celly+1)/2
    else if(fixCelly<celly) celly-=(celly-fixCelly+1)/2
    progressBarView.setProgress(93)

    // 5th line in upper section
    celly+=cellVertiDist
    fixCelly = scanCellLine(pxls, width, height, cellx, celly)
    if(fixCelly>celly) celly+=(fixCelly-celly+1)/2
    else if(fixCelly<celly) celly-=(celly-fixCelly+1)/2
    progressBarView.setProgress(94)

    // at this point celly is roughly 168-172 (and will be set to 218 further down)
    if(D) Log.i(TAG, "detectCharacterCells before 2nd fingerprint celly="+celly+" height="+height)
    if(height-celly>200) {
      // 2. scan other devices "Sent key fingerprint" (this is the one we received)
      keyFingerprint = receivedKeyFingerprint
      keyFingerprintPos = 0

      // 1st line in lower section
      celly+=cellVertiDist+50
      fixCelly = scanCellLine(pxls, width, height, cellx, celly)
      if(fixCelly>celly) celly+=(fixCelly-celly+1)/2
      else if(fixCelly<celly) celly-=(celly-fixCelly+1)/2
      progressBarView.setProgress(95)

      // 2nd line in lower section
      celly+=cellVertiDist
      fixCelly = scanCellLine(pxls, width, height, cellx, celly)
      if(fixCelly>celly) celly+=(fixCelly-celly+1)/2
      else if(fixCelly<celly) celly-=(celly-fixCelly+1)/2
      progressBarView.setProgress(96)

      // 3rd line in lower section
      celly+=cellVertiDist
      fixCelly = scanCellLine(pxls, width, height, cellx, celly)
      if(fixCelly>celly) celly+=(fixCelly-celly+1)/2
      else if(fixCelly<celly) celly-=(celly-fixCelly+1)/2
      progressBarView.setProgress(97)

      // 4th line in lower section
      celly+=cellVertiDist
      fixCelly = scanCellLine(pxls, width, height, cellx, celly)
      if(fixCelly>celly) celly+=(fixCelly-celly+1)/2
      else if(fixCelly<celly) celly-=(celly-fixCelly+1)/2
      progressBarView.setProgress(98)

      // 5th line in lower section
      celly+=cellVertiDist
      scanCellLine(pxls, width, height, cellx, celly)
    } else {
      if(D) Log.i(TAG, "detectCharacterCells 2nd fingerprint detection was skipped !(height-celly="+(height-celly)+">200) ###################")
      skipped2ndFingerprint = true
    }

    progressBarView.setProgress(100)

    if(D) Log.i(TAG, "detectCharacterCells done, detectedCharacters="+detectedCharacters+" fixedCount="+fixedCount+" errorCount1="+errorCount1+" errorCount2="+errorCount2)

    return errorCount1 + errorCount2
  }

  def scanCellLine(pxls:Array[Int], width:Int, height:Int, startCellx:Int, startCelly:Int) :Int = {
    //Log.i(TAG, "scanCellLine startCellx="+startCellx+" startCelly="+startCelly)
    var cellx = startCellx
    var celly = startCelly

    // 1st char in line
    cellx = scanCell(pxls, width, height, cellx, celly) +1

    // 2nd char in line
    cellx+=cellHoriDist
    var fixCellx = scanCell(pxls, width, height, cellx, celly)
    if(fixCellx>cellx) cellx+=(fixCellx-cellx+1)/2
    else if(fixCellx<cellx) cellx-=(cellx-fixCellx+1)/2
    if(fixedCelly>celly) celly+=(fixedCelly-celly+1)/2
    else if(fixedCelly<celly) celly-=(celly-fixedCelly+1)/2

    // 3rd char in line
    cellx+=(cellHoriDist+cellHoriDist)
    fixCellx = scanCell(pxls, width, height, cellx, celly)
    if(fixCellx>cellx) cellx+=(fixCellx-cellx+1)/2
    else if(fixCellx<cellx) cellx-=(cellx-fixCellx+1)/2
    if(fixedCelly>celly) celly+=(fixedCelly-celly+1)/2
    else if(fixedCelly<celly) celly-=(celly-fixedCelly+1)/2

    // 4th char in line
    cellx+=cellHoriDist
    fixCellx = scanCell(pxls, width, height, cellx, celly)
    if(fixCellx>cellx) cellx+=(fixCellx-cellx+1)/2
    else if(fixCellx<cellx) cellx-=(cellx-fixCellx+1)/2
    if(fixedCelly>celly) celly+=(fixedCelly-celly+1)/2
    else if(fixedCelly<celly) celly-=(celly-fixedCelly+1)/2

    // 5st char in line
    cellx+=(cellHoriDist+cellHoriDist)
    fixCellx = scanCell(pxls, width, height, cellx, celly)
    if(fixCellx>cellx) cellx+=(fixCellx-cellx+1)/2
    else if(fixCellx<cellx) cellx-=(cellx-fixCellx+1)/2
    if(fixedCelly>celly) celly+=(fixedCelly-celly+1)/2
    else if(fixedCelly<celly) celly-=(celly-fixedCelly+1)/2

    // 6st char in line
    cellx+=cellHoriDist
    fixCellx = scanCell(pxls, width, height, cellx, celly)
    if(fixCellx>cellx) cellx+=(fixCellx-cellx+1)/2
    else if(fixCellx<cellx) cellx-=(cellx-fixCellx+1)/2
    if(fixedCelly>celly) celly+=(fixedCelly-celly+1)/2
    else if(fixedCelly<celly) celly-=(celly-fixedCelly+1)/2

    // 7st char in line
    cellx+=(cellHoriDist+cellHoriDist)
    fixCellx = scanCell(pxls, width, height, cellx, celly)
    if(fixCellx>cellx) cellx+=(fixCellx-cellx+1)/2
    else if(fixCellx<cellx) cellx-=(cellx-fixCellx+1)/2
    if(fixedCelly>celly) celly+=(fixedCelly-celly+1)/2
    else if(fixedCelly<celly) celly-=(celly-fixedCelly+1)/2

    // 8st char in line
    cellx+=cellHoriDist
    scanCell(pxls, width, height, cellx, celly)

    return fixedCelly
  }

  private def scanCell(pxls:Array[Int], width:Int, height:Int, startCellx:Int, startCelly:Int) :Int = {
    //Log.i(TAG, "scanCell startCellx="+startCellx+" startCelly="+startCelly)
    val pixelsSetCount = Array.ofDim[Int](characters,fontLines)
    val pixelsClrCount = Array.ofDim[Int](characters,fontLines)

    var setPixelInCell = 0
    var cellx = 0
    var celly = 0
    var showScannedMatrix=false

    var redo=false
    var redoLoops=0
    do {
      setPixelInCell = 0
      cellx = startCellx
      celly = startCelly

      // precisely position cellx/celly above cell

      // 1a. while the line two below this line is blank, then celly++
      var breakout=false
      while(!breakout && celly<startCelly+18) { 
        var countPixelInLine=0
        var ix = cellx
        var breakout2=false
        while(!breakout2 && ix<cellx+fontLines) {
          if(celly<0 || celly+2>=height || ix>=width) {
            if(D) Log.i(TAG, "scanCell break out of 1a to prevent exoutofbounds ix="+ix+" cellx="+cellx+" width="+width+" celly="+celly+" height="+height)
            breakout2=true
          } else {
            val pixelBelow = pxls((celly+2)*width+ix) & 0xff
            if(pixelBelow>=currentThreshold)
              countPixelInLine+=1
            ix+=1
          }
        }
        if(countPixelInLine<=1) // one pixel will be ignored
          celly+=1
        else
          breakout=true
        //if(D) Log.i(TAG, "scanCell autocorrect celly to "+celly+", countPixelInLine="+countPixelInLine+" breakout="+breakout+" startCelly="+startCelly)
      }

      //Log.i(TAG, "scanCell 1b")
      // 1b. while the line below this line is blank, then celly++
      breakout=false
      while(!breakout && celly<startCelly+18) { 
        var countPixelInLine=0
        var ix = cellx
        var breakout2=false
        while(!breakout2 && ix<cellx+fontLines) {
          if(celly<0 || celly+1>=height || ix>=width) {
            if(D) Log.i(TAG, "scanCell break out of 1b to prevent exoutofbounds ix="+ix+" cellx="+cellx+" width="+width+" celly="+celly+" height="+height)
            breakout2=true
          } else { 
            val pixelBelow = pxls((celly+1)*width+ix) & 0xff
            if(pixelBelow>=currentThreshold)
              countPixelInLine+=1
            ix+=1
          }
        }
        if(countPixelInLine<=1) // one pixel will be ignored
          celly+=1
        else
          breakout=true
        //if(D) Log.i(TAG, "scanCell autocorrect celly to "+celly+", countPixelInLine="+countPixelInLine)
      }

      //Log.i(TAG, "scanCell 2")
      // 2. while the current line is NOT blank, then celly--
      breakout=false
      while(!breakout && celly>startCelly-14) { 
        var countPixelInLine=0
        var ix = cellx
        var breakout2=false
        while(!breakout2 && ix<cellx+fontLines) {
          if(celly<0 || celly>=height || ix>=width) {
            if(D) Log.i(TAG, "scanCell break out of 2 to prevent exoutofbounds ix="+ix+" cellx="+cellx+" width="+width+" celly="+celly+" height="+height)
            breakout2=true
          } else {
            val pixel = pxls(celly*width+ix) & 0xff
            if(pixel>=currentThreshold)
              countPixelInLine+=1
            ix+=1
          }
        }
        if(countPixelInLine>1) // one pixel will be ignored
          celly-=1
        else
          breakout=true
        //if(D) Log.i(TAG, "scanCell autocorrect celly to "+celly+", countPixelInLine="+countPixelInLine)
      }

      // 3. while /*this column is blank and*/ the columns to the right of this column are blank, then incr cellx
      var ix = 1    // not wider than the smallest character
      breakout=false
      while(!breakout) { 
        var countPixelInRowRight=0
        var iy = celly
        var breakout2=false
        while(!breakout2 && iy<celly+fontLines) {
          if(iy<0 || iy>=height || cellx+ix>=width) {
            if(D) Log.i(TAG, "scanCell break out of 3 to prevent exoutofbounds ix="+ix+" cellx="+cellx+" width="+width+" iy="+iy+" celly="+celly+" height="+height)
            breakout2=true
          } else {
            val pixelRight = pxls(iy*width+cellx+ix) & 0xff
            if(pixelRight>=currentThreshold)
              countPixelInRowRight+=1
            iy+=1
          }
        }

        if(ix>=12 || countPixelInRowRight>1) {  // treating 1 pixel as none
          breakout=true
          if(ix>1) {
            ix-=1
            cellx += ix
            //if(D) Log.i(TAG, "scanCell autocorrect 3 cellx by=+"+ix+" to="+cellx)
          }
          // else no correction of cellx (cannot find an empty column to the left of the cell)
        } else {
          ix+=1
        }
      }
      
      // 4. while this column is NOT blank and the columns to the left ARE blank, then subtract from cellx
      ix = 1
      breakout=false
      while(!breakout) { 
        var countPixelInRow=0
        var countPixelInRowLeft=0
        var iy = celly
        var breakout2=false
        while(!breakout2 && iy<celly+fontLines) {
          if(iy<0 || iy>=height || cellx-ix<0 || cellx>=width) {
            if(D) Log.i(TAG, "scanCell break out of 4 to prevent exoutofbounds ix="+ix+" cellx="+cellx+" width="+width+" iy="+iy+" celly="+celly+" height="+height)
            breakout2=true
          } else {
            val pixel = pxls(iy*width+cellx) & 0xff
            if(pixel>=currentThreshold)
              countPixelInRow+=1
            val pixelLeft = pxls(iy*width+cellx-ix) & 0xff
            if(pixelLeft>=currentThreshold)
              countPixelInRowLeft+=1
            iy+=1
          }
        }
        if(countPixelInRow<=1) {  // treating 1 pixel as none
          // no correction of cellx
          breakout=true
        } else
        if(countPixelInRowLeft<=1) {  // treating 1 pixel as none
          breakout=true
          cellx -= ix
          //if(D) Log.i(TAG, "scanCell autocorrect 4 cellx by=-"+ix+" to="+cellx)
        } else
        if(ix>=12) {
          // no correction of cellx (cannot find an empty column to the left of the cell)
          breakout=true
        } else {  // countPixelInRowLeft>0
          ix+=1
        }
      }

      // 5. while character is left of center in cell, subtract from cellx
      // cw = the width of the character
      // era = the number of empty rows after the character
      var cw = 0
      breakout=false
      while(!breakout) { 
        var countPixelInRow=0
        var iy = celly
        var breakout2=false
        while(!breakout2 && iy<celly+fontLines) {
          if(iy<0 || iy>=height || cellx+1+cw>=width) {
            if(D) Log.i(TAG, "scanCell break out of 5a to prevent exoutofbounds ix="+ix+" cellx="+cellx+" width="+width+" iy="+iy+" celly="+celly+" height="+height)
            breakout2=true
          } else {
            val pixel = pxls(iy*width+cellx+1+cw) & 0xff
            if(pixel>=currentThreshold)
              countPixelInRow+=1
            iy+=1
          }
        }
        if(breakout2 || countPixelInRow<=1) {  // treating 1 pixel as none
          // found the width of the character
          breakout=true
        } else {
          cw+=1
        }
      }

      var era = 0
      breakout=false
      while(!breakout) { 
        var countPixelInRow=0
        var iy = celly
        var breakout2=false
        while(!breakout2 && iy<celly+fontLines) {
          if(iy<0 || iy>=height || cellx+1+cw+era>=width) {
            if(D) Log.i(TAG, "scanCell break out of 5b to prevent exoutofbounds ix="+ix+" cellx="+cellx+" width="+width+" iy="+iy+" celly="+celly+" height="+height)
            breakout2=true
          } else {
            val pixel = pxls(iy*width+cellx+1+cw+era) & 0xff
            if(pixel>=currentThreshold)
              countPixelInRow+=1
            iy+=1
          }
        }
        if(breakout2 || countPixelInRow>1) {  // treating 1 pixel as none
          // found the first non-empty row after the character
          breakout=true
        } else {
          era+=1
        }
      }

      if(D) Log.i(TAG, "scanCell 5 centering cw="+cw+" era="+era+" cellx="+cellx)
      while(cw+era>charColumns)
        era-=1
      var shiftRight=0
      while((charColumns-cw)/2-shiftRight>1 && era>0) {
        shiftRight += 1
        cellx -=1 
        era -=1
        if(D) Log.i(TAG, "scanCell 5 centering cw="+cw+" era="+era+" cellx="+cellx+" shiftRight="+shiftRight+" ####")
      }
      if(shiftRight>0)
        showScannedMatrix=true

      //if(D) Log.i(TAG, "scanCell cellx="+cellx+" celly="+celly+" (fixed)")
      fixedCelly = celly

      // the current character-cell has been detected (in pxls at position cellx/celly)
      ////////////////////////////////////////////////////////////////////////////////////////////////////7
      // now we try to recognized the character in this cell => charDetected

      for(ifont <- 0 until characters)
        for(iy <- 0 until fontLines) {
          pixelsSetCount(ifont)(iy)=0
          pixelsClrCount(ifont)(iy)=0
        }

      var iy = 0
      while(iy<fontLines) {
        val stringBuilder = new StringBuilder()
        var ix = cellx
        var breakout2 = false
        while(!breakout2 && ix<cellx+charColumns) {
          if(celly<0 || celly>=height || ix>=width) {
            Log.e(TAG, "scanCell break out to prevent exoutofbounds celly="+celly+" height="+height+" celly="+celly+" ix="+ix+" width="+width)
            breakout2=true
          } else {
            //if(D) Log.i(TAG, "scan-Cell iy="+iy+" celly="+celly+" width="+width+" ix="+ix)
            try {
              val compPos = (iy+celly)*width+ix
              if(compPos>pxls.length) {
                Log.e(TAG, "scanCell pixels compare iy="+iy+" ix="+ix+" celly="+celly+" width="+width+", compPos="+compPos+" > pxls.length="+pxls.length)
                breakout2=true
              } else {
                val pixel = pxls(compPos) & 0xff
                if(pixel>=currentThreshold) {
                  stringBuilder.append("#")
                  setPixelInCell+=1
                } 
                else
                  stringBuilder.append(".")
                ix+=1
              }
            } catch {
              case aioobex:java.lang.ArrayIndexOutOfBoundsException =>
                Log.e(TAG, "scanCell pixels compare iy="+iy+" ix="+ix+" celly="+celly+" width="+width+" pxls.length="+pxls.length+" ex="+aioobex)
                breakout2=true
            }
          }
        }
        val pixelLine = stringBuilder.toString


        if(pixelLine.length<charColumns-5) {    // todo: explain rational ???
          if(D) Log.i(TAG, "something is wrong with the length ("+pixelLine.length+") of pixelLine="+pixelLine)

        } else {
          // compare this pixelLine with those from each character (in fontArray)
          for(ifont <- 0 until characters) {
            for(pixColumn <- 0 until pixelLine.length) {
              try {
                val cmpFontString = fontArray(ifont*fontLines+iy)  // .asInstanceOf[String]

                if(cellNumber==0) {
                  if(cmpFontString.charAt(pixColumn)=='#')
                    fontSetArray(ifont) += 1;
                  else
                    fontClrArray(ifont) += 1;
                }

                if(pixelLine.length>=pixColumn) {
                  if(pixelLine.charAt(pixColumn)=='#') {
                    if(cmpFontString.charAt(pixColumn)=='#')
                      pixelsSetCount(ifont)(iy)+=1
                  } else
                  if(pixelLine.charAt(pixColumn)=='.') {
                    if(cmpFontString.charAt(pixColumn)=='.')
                      pixelsClrCount(ifont)(iy)+=1
                  }
                }
              } catch {
                case aioobex:java.lang.ArrayIndexOutOfBoundsException =>
                  Log.e(TAG, "scanCell compare pixelLine="+pixelLine+" pixelLine.length="+pixelLine.length+" pixColumn="+pixColumn+" ifont="+ifont+" iy="+iy+" ",aioobex)
                  // todo: break out inner loop
                case stioobex:java.lang.StringIndexOutOfBoundsException =>
                  Log.e(TAG, "scanCell compare pixelLine="+pixelLine+" pixelLine.length="+pixelLine.length+" pixColumn="+pixColumn+" ifont="+ifont+" iy="+iy+" ",stioobex)
                  // todo: break out inner loop
              }
            }
          }
        }
        iy+=1
      }

      sumSetPixelInCell += setPixelInCell
      val averageSetPixelInCell = sumSetPixelInCell/(cellNumber+1)
      if(averageSetPixelInCell<170 && currentThreshold>0x90 && redoLoops==0) {
        if(D) Log.i(TAG, "scanCell REDO currentThreshold="+currentThreshold+" averageSetPixelInCell="+averageSetPixelInCell+" ***** *****")
        currentThreshold -= 0x08
        redo=true
        redoLoops+=1
        sumSetPixelInCell -= setPixelInCell
      } else
      if(averageSetPixelInCell>190 && currentThreshold<=0xe0 && redoLoops==0) {
        if(D) Log.i(TAG, "scanCell REDO currentThreshold="+currentThreshold+" averageSetPixelInCell="+averageSetPixelInCell+" ***** *****")
        currentThreshold += 0x08
        redo=true
        redoLoops+=1
        sumSetPixelInCell -= setPixelInCell
      } else {
        if(redo) {
          showScannedMatrix=true
          if(D) Log.i(TAG, "scanCell POSTREDO currentThreshold="+currentThreshold+" averageSetPixelInCell="+averageSetPixelInCell+" *****")
        }
        redo=false
      }
    } while(redo)


    // actualFingerprintCharacter is the character we are supposed to find (based on the key fingerprint we received earlier)
    val actualFingerprintCharacter = keyFingerprint.charAt(keyFingerprintPos)
    var charDetected = 0
    var scannedCharacter = 0

    var bestCharIdx=0
    var bestCharSetSum=0
    var bestCharClrSum=0

    var actualCharIdx=0
    var actualCharSetSum=0
    var actualCharClrSum=0

    var comment = ""
    var actualFingerprintCharIdx=actualFingerprintCharacter-'0';
    if(actualFingerprintCharacter>='A')
      actualFingerprintCharIdx=actualFingerprintCharacter-'A'+10;
    
    for(ifont <- 0 until characters) {
      var charSetSum=0
      var charClrSum=0

      for(iy <- 0 until fontLines) {
        charSetSum += pixelsSetCount(ifont)(iy)
        charClrSum += pixelsClrCount(ifont)(iy)
      }

      if(charSetSum+charClrSum > bestCharSetSum+bestCharClrSum) {
        // this is the likelyness-value of the most likely character, based on detection
        bestCharSetSum = charSetSum
        bestCharClrSum = charClrSum
        bestCharIdx = ifont
      }
      if(fontCharacter(ifont).charAt(0)==actualFingerprintCharacter) {
        // this is the likelyness-value of the expected character, based on detection
        actualCharSetSum = charSetSum
        actualCharClrSum = charClrSum
        actualCharIdx = ifont
      }

    }

    if(bestCharIdx>=0) {
      // scannedCharacter is the character we have recognized from the camera snapshot
      scannedCharacter = fontCharacter(bestCharIdx).charAt(0)

      // charDetected can be different from scannedCharacter, if the camera snapshot could very well also be actualFingerprintCharacter  
      charDetected = scannedCharacter
      if(scannedCharacter != actualFingerprintCharacter) {  // this may be fixed
        if(actualCharSetSum+actualCharClrSum>=240 && actualCharSetSum>70 && actualCharClrSum>70
           && bestCharSetSum+bestCharClrSum-(actualCharSetSum+actualCharClrSum)<=30
          ) {
          charDetected = actualFingerprintCharacter
          comment += "FIXED"
          fixedCount+=1
          showScannedMatrix=true
        } else {
          // error
          charDetected = 0
        }
      }
    }

    //if(D) Log.i(TAG, "bitmap.setPixels width="+width+" celly="+celly+" cellx="+cellx)
    val bitmap = Bitmap.createBitmap(20, 21, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pxls, celly*width+cellx, width, 0, 0, 20, 21)

    if(charDetected>0) {
      // draw charDetected to display
      detectedCharacters+=1
      if(cellNumber<40)
        activityMsgHandler.obtainMessage(FingerprintOcrActivity.SHOW_DETECTED_CHARACTER1, actualFingerprintCharacter, 0, bitmap).sendToTarget
      else
        activityMsgHandler.obtainMessage(FingerprintOcrActivity.SHOW_DETECTED_CHARACTER2, actualFingerprintCharacter, 0, bitmap).sendToTarget

    } else {
      if(cellNumber<40) {
        errorCount1+=1
        activityMsgHandler.obtainMessage(FingerprintOcrActivity.SHOW_DETECTED_CHARACTER1, actualFingerprintCharacter, 1, bitmap).sendToTarget
      } else {
        errorCount2+=1
        activityMsgHandler.obtainMessage(FingerprintOcrActivity.SHOW_DETECTED_CHARACTER2, actualFingerprintCharacter, 1, bitmap).sendToTarget
      }
      showScannedMatrix=true
      comment += "ERR"
    } 

    if(D) {
      val bestSetPercent = (bestCharSetSum.asInstanceOf[Float]*100f)/fontSetArray(bestCharIdx)
      val bestClrPercent = (bestCharClrSum.asInstanceOf[Float]*100f)/fontClrArray(bestCharIdx)
      val actualSetPercent = (actualCharSetSum.asInstanceOf[Float]*100f)/fontSetArray(actualCharIdx)
      val actualClrPercent = (actualCharClrSum.asInstanceOf[Float]*100f)/fontClrArray(actualCharIdx)
      val averageSetPixelInCell = sumSetPixelInCell/(cellNumber+1)
    
      Log.i(TAG, 
        ""+cellNumber+
        " scan="+scannedCharacter.asInstanceOf[Char]+
        " sum="+(bestCharSetSum+bestCharClrSum)+
        " "+bestSetPercent+" "+bestClrPercent+
        " cmpWithUse="+charDetected.asInstanceOf[Char]+
        " sum="+(actualCharSetSum+actualCharClrSum)+
        " "+actualSetPercent+" "+actualClrPercent+" "+

        " cx="+startCellx+"/"+cellx+" cy="+startCelly+"/"+celly+" av="+averageSetPixelInCell+" "+comment)

      if(cellNumber<8)
        showScannedMatrix=true   // for debugging only
showScannedMatrix=true
      if(showScannedMatrix) {
        var iy = 0
        var breakout=false
        while(!breakout && iy<fontLines) {
          val stringBuilder = new StringBuilder()
          var ix = startCellx
          while(!breakout && ix<startCellx+charColumns) {
            val compPos = (iy+startCelly)*width+ix
            if(compPos>pxls.length) {
              Log.e(TAG, "scanCell display pixel matrix iy="+iy+" ix="+ix+" celly="+celly+" width="+width+", compPos="+compPos+" > pxls.length="+pxls.length)
              breakout=false
            } else {
              val pixel = pxls(compPos) & 0xff
              if(pixel>=currentThreshold)
                stringBuilder.append("#") 
              else
                stringBuilder.append(".")
            }
            ix+=1
          }

          stringBuilder.append("  ")
          ix = cellx
          while(!breakout && ix<cellx+charColumns) {
            val compPos = (iy+celly)*width+ix
            if(compPos>pxls.length) {
              Log.e(TAG, "scanCell display pixel matrix iy="+iy+" ix="+ix+" celly="+celly+" width="+width+", compPos="+compPos+" > pxls.length="+pxls.length)
              breakout=true
            } else {
              val pixel = pxls(compPos) & 0xff
              if(pixel>=currentThreshold)
                stringBuilder.append("#") 
              else
                stringBuilder.append(".")
              ix+=1
            }
          }

          val pixelLine = stringBuilder.toString
          Log.i(TAG, pixelLine)
          iy+=1
        }
      }
    }

    keyFingerprintPos+=1
    cellNumber+=1
    return cellx
  }


  private val fontCharacter = Array("0","1","2","3","4","5","6","7","8","9","A","B","C","D","E","F")

  private val fontArray = Array(
    "..................",
    "......########....",
    "....###########...",
    "...#####...#####..",
    "..####.......####.",
    ".####.........####",
    ".####.........####",
    ".####.........####",
    ".####.........####",
    ".####.........####",
    ".####.........####",
    ".####.........####",
    ".####.........####",
    ".####.........####",
    ".####.........####",
    ".####.........####",
    "..####.......####.",
    "...#####...#####..",
    "....###########...",
    ".....#########....",

    "..................",
    ".......######.....",
    "......#######.....",
    "....#########.....",
    "....#########.....",
    "........#####.....",
    "........#####.....",
    "........#####.....",
    "........#####.....",
    "........#####.....",
    "........#####.....",
    "........#####.....",
    "........#####.....",
    "........#####.....",
    "........#####.....",
    "........#####.....",
    "........#####.....",
    "........#####.....",
    "........#####.....",
    "........#####.....",

    "..................",
    "....#########.....",
    "..#############...",
    "..##############..",
    "...........#####..",
    "...........#####..",
    "...........#####..",
    "...........#####..",
    "...........#####..",
    "..........#####...",
    ".........#####....",
    "........#####.....",
    ".......#####......",
    "......#####.......",
    ".....#####........",
    "...######.........",
    "..######..........",
    ".##############...",
    ".###############..",
    "..................",
/*
    "..................",
    "....########......",
    "..############....",
    "..##############..",
    "..........#######.",
    "............#####.",
    "............#####.",
    "...........######.",
    "......##########..",
    ".....###########..",
    ".....##########...",
    ".......#########..",
    "..........#######.",
    "............#####.",
    "............#####.",
    ".####......######.",
    ".###############..",
    "..#############...",
    ".....########.....",
    "..................",
*/
    "..................",
    ".....#######......",
    "...############...",
    "..##############..",
    "..###############.",
    "...........######.",
    "............#####.",
    "............#####.",
    "..........#######.",
    "......##########..",
    ".....###########..",
    ".....###########..",
    "......###########.",
    "...........######.",
    "............######",
    ".............#####",
    "............######",
    "..........#######.",
    ".###############..",
    ".##############...",



    "..................",
    "..........######..",
    ".........#######..",
    "........########..",
    ".......#########..",
    "......##########..",
    "......##########..",
    ".....####...####..",
    "....####....####..",
    "....####....####..",
    "...####.....####..",
    "..####......####..",
    "..####......####..",
    ".####.......####..",
    ".#################",
    ".#################",
    ".#################",
    "............####..",
    "............####..",
    "............####..",

    "..................",
    "..############....",
    "..############....",
    "..##########......",
    ".####.............",
    ".####.............",
    ".####.............",
    ".########.........",
    ".###########......",
    "..############....",
    "...###########....",
    "..........#####...",
    "...........#####..",
    "...........#####..",
    "...........#####..",
    "...........#####..",
    "..........######..",
    ".##############...",
    ".#############....",
    "..##########......",

    "..................",
    ".........######...",
    ".......#########..",
    ".....#########....",
    "....######........",
    "...#####..........",
    "...####...........",
    "..########........",
    "..###########.....",
    ".#############....",
    ".##############...",
    ".#####.....#####..",
    ".####.......#####.",
    ".####........####.",
    ".####........####.",
    ".#####......#####.",
    "..#####....#####..",
    "...############...",
    "....##########....",
    "......######......",

    "..................",
    ".###############..",
    ".################.",
    "..###############.",
    "............#####.",
    "............####..",
    "...........#####..",
    "...........####...",
    "..........####....",
    ".........####.....",
    ".........####.....",
    "........####......",
    ".......####.......",
    "......####........",
    "......####........",
    ".....####.........",
    ".....####.........",
    "....####..........",
    "....####..........",
    ".....##...........",

    "..................",
    "......#######.....",
    "....###########...",
    "...#############..",
    "..#####.....#####.",
    "..####.......####.",
    "..####.......####.",
    "..####.......####.",
    "...#####...#####..",
    "....###########...",
    "....##########....",
    "...############...",
    "..#####....#####..",
    ".#####......#####.",
    ".####........####.",
    ".####........####.",
    ".#####......#####.",
    "..##############..",
    "...############...",
    ".....########.....",

    "..................",
    "......#######.....",
    "....###########...",
    "...#############..",
    "..#####....######.",
    ".#####.......#####",
    ".####........#####",
    ".####.........####",
    ".#####.......#####",
    ".#####.......#####",
    "..################",
    "...###############",
    ".............#####",
    ".............#####",
    "............#####.",
    "...........#####..",
    ".........######...",
    "....##########....",
    ".....########.....",
    "..................",

    "..................",
    "........###.......",
    ".......#####......",
    ".......######.....",
    "......#######.....",
    "......########....",
    ".....####..###....",
    ".....####..####...",
    "....####....###...",
    "....####....####..",
    "....####....####..",
    "...#############..",
    "...##############.",
    "...##############.",
    "..####.......#####",
    "..###.........####",
    ".####.........####",
    ".###...........###",
    ".###...........###",
    "..................",

    "..................",
    "..############....",
    ".###############..",
    ".################.",
    ".####.......######",
    ".####........#####",
    ".####........#####",
    ".####........#####",
    ".####.......#####.",
    ".###############..",
    ".###############..",
    ".###############..",
    ".####.......#####.",
    ".####........#####",
    ".####........#####",
    ".####........#####",
    ".####.......######",
    ".################.",
    ".###############..",
    ".#############....",

    "..................",
    ".......#########..",
    "....#############.",
    "...############...",
    "..#####...........",
    "..####............",
    ".####.............",
    ".####.............",
    ".####.............",
    ".####.............",
    ".####.............",
    ".####.............",
    ".####.............",
    ".####.............",
    "..####............",
    "..####............",
    "...######.........",
    "....#############.",
    ".....############.",
    "........########..",

    "..................",
    ".###########......",
    ".##############...",
    ".###############..",
    ".####......######.",
    ".####.......######",
    ".####........#####",
    ".####.........####",
    ".####.........####",
    ".####.........####",
    ".####.........####",
    ".####.........####",
    ".####.........####",
    ".####.........####",
    ".####........#####",
    ".####.......######",
    ".####......######.",
    ".###############..",
    ".#############....",
    ".###########......",

    "..................",
    ".##############...",
    ".###############..",
    ".##############...",
    ".####.............",
    ".####.............",
    ".####.............",
    ".####.............",
    ".####.............",
    ".#############....",
    ".#############....",
    ".##########.......",
    ".####.............",
    ".####.............",
    ".####.............",
    ".####.............",
    ".####.............",
    ".##############...",
    ".###############..",
    ".###############..",

    "..................",
    ".##############...",
    ".###############..",
    ".##############...",
    ".####.............",
    ".####.............",
    ".####.............",
    ".####.............",
    ".####.............",
    ".#############....",
    ".#############....",
    ".##########.......",
    ".####.............",
    ".####.............",
    ".####.............",
    ".####.............",
    ".####.............",
    ".####.............",
    ".####.............",
    ".####............."
  )
}

