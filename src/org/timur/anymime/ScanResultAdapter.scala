/*
 * Copyright (C) 2011 Timur Mehrvarz
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.timur.anymime

import java.util.ArrayList

import scala.collection.mutable.HashMap
import scala.collection.mutable.ListBuffer

import android.content.Context
import android.util.Log
import android.view.View
import android.content.SharedPreferences
import android.widget.TextView
import android.widget.ArrayAdapter
import android.view.LayoutInflater
import android.view.ViewGroup
import android.graphics.Bitmap
import android.graphics.Color

class ScanResultAdapter(context:Context, messageResourceId:Int)
  extends ArrayAdapter[String](context, messageResourceId) {

  private val TAG = "ScanResultAdapter"
  private val D = Static.DBGLOG

  private var detectedCharList = new ArrayList[Char]()
  private var scannedBitmapList = new ArrayList[Bitmap]()
  private var errorDetected = new ArrayList[Boolean]()

  var errorMarkerViewList = new ArrayList[View]()
  var scannedResultViewList = new ArrayList[View]()
  var wantedResultViewList = new ArrayList[View]()

  override def getCount() :Int = {
		return detectedCharList.size / 8    // 8 is the number of characters per listview line
	}

  override def clear() {
    //if(D) Log.i(TAG, "clear")
    detectedCharList.clear
	}

  override def getItem(id:Int) :String = {
    //if(D) Log.i(TAG, "getCount ="+deviceMap.size)
    if(id<detectedCharList.size)
  		return ""+detectedCharList.get(id)
    return null
	}


  override def getView(position:Int, setView:View, parentViewGroup:ViewGroup) :View = {
    //if(D) Log.i(TAG, "getView position="+position+" charString="+charString+" index="+index)

    var view = setView
    if(view == null) {
    //if(D) Log.i(TAG, "getView position="+position+" inflate a new view")
      val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
      if(layoutInflater!=null) {
        view = layoutInflater.inflate(messageResourceId, null)
        if(view!=null) {
          if(position*8+7<detectedCharList.size) {
            errorMarkerViewList.add(view.findViewById(R.id.errorMarker))
            scannedResultViewList.add(view.findViewById(R.id.scannedResults))
            wantedResultViewList.add(view.findViewById(R.id.wantedResults))
          }
        }
      }
      //if(D) Log.i(TAG, "getView("+position+") view="+view+" from layoutInflater")
    }

    if(view == null) {
      Log.e(TAG, "getView view==null abort")
      return null
    }

    if(D) Log.i(TAG, "getView("+position+") ...")
    if(position*8+7<detectedCharList.size) {
      val colorError = Color.argb(0xff,0xff,0x20,0x20)
 
      {  
        if(errorDetected.get(position*8+0)) {
          val errorMarkerView = view.findViewById(R.id.errorMarker01).asInstanceOf[View]
          if(errorMarkerView!=null)
            errorMarkerView.setBackgroundColor(colorError)
        }
        val bitmap = view.findViewById(R.id.scannedCharacter01).asInstanceOf[View]
        if(bitmap != null)
          bitmap.setBackgroundDrawable(new android.graphics.drawable.BitmapDrawable(context.getResources, 
            scannedBitmapList.get(position*8+0)))
        val wantedCharacterView = view.findViewById(R.id.wantedCharacter01).asInstanceOf[TextView]
        if(wantedCharacterView != null)
          wantedCharacterView.setText(""+detectedCharList.get(position*8+0))
      }

      {
        if(errorDetected.get(position*8+1)) {
          val errorMarkerView = view.findViewById(R.id.errorMarker02).asInstanceOf[View]
          if(errorMarkerView!=null)
            errorMarkerView.setBackgroundColor(colorError)
        }
        val bitmap = view.findViewById(R.id.scannedCharacter02).asInstanceOf[View]
        if(bitmap != null)
          bitmap.setBackgroundDrawable(new android.graphics.drawable.BitmapDrawable(context.getResources, 
            scannedBitmapList.get(position*8+1)))
        val wantedCharacterView = view.findViewById(R.id.wantedCharacter02).asInstanceOf[TextView]
        if(wantedCharacterView != null)
          wantedCharacterView.setText(""+detectedCharList.get(position*8+1))
      }

      {  
        val bitmap = view.findViewById(R.id.scannedCharacter03).asInstanceOf[View]
        if(bitmap != null)
          bitmap.setBackgroundDrawable(new android.graphics.drawable.BitmapDrawable(context.getResources, 
            scannedBitmapList.get(position*8+2)))
        if(errorDetected.get(position*8+2)) {
          val errorMarkerView = view.findViewById(R.id.errorMarker03).asInstanceOf[View]
          if(errorMarkerView!=null)
            errorMarkerView.setBackgroundColor(colorError)
        }
        val wantedCharacterView = view.findViewById(R.id.wantedCharacter03).asInstanceOf[TextView]
        if(wantedCharacterView != null)
          wantedCharacterView.setText(""+detectedCharList.get(position*8+2))
      }

      {  
        val bitmap = view.findViewById(R.id.scannedCharacter04).asInstanceOf[View]
        if(bitmap != null)
          bitmap.setBackgroundDrawable(new android.graphics.drawable.BitmapDrawable(context.getResources, 
            scannedBitmapList.get(position*8+3)))
        if(errorDetected.get(position*8+3)) {
          val errorMarkerView = view.findViewById(R.id.errorMarker04).asInstanceOf[View]
          if(errorMarkerView!=null)
            errorMarkerView.setBackgroundColor(colorError)
        }
        val wantedCharacterView = view.findViewById(R.id.wantedCharacter04).asInstanceOf[TextView]
        if(wantedCharacterView != null)
          wantedCharacterView.setText(""+detectedCharList.get(position*8+3))
      }

      {  
        val bitmap = view.findViewById(R.id.scannedCharacter05).asInstanceOf[View]
        if(bitmap != null)
          bitmap.setBackgroundDrawable(new android.graphics.drawable.BitmapDrawable(context.getResources, 
            scannedBitmapList.get(position*8+4)))
        if(errorDetected.get(position*8+4)) {
          val errorMarkerView = view.findViewById(R.id.errorMarker05).asInstanceOf[View]
          if(errorMarkerView!=null)
            errorMarkerView.setBackgroundColor(colorError)
        }
        val wantedCharacterView = view.findViewById(R.id.wantedCharacter05).asInstanceOf[TextView]
        if(wantedCharacterView != null)
          wantedCharacterView.setText(""+detectedCharList.get(position*8+4))
      }

      {  
        val bitmap = view.findViewById(R.id.scannedCharacter06).asInstanceOf[View]
        if(bitmap != null)
          bitmap.setBackgroundDrawable(new android.graphics.drawable.BitmapDrawable(context.getResources, 
            scannedBitmapList.get(position*8+5)))
        if(errorDetected.get(position*8+5)) {
          val errorMarkerView = view.findViewById(R.id.errorMarker06).asInstanceOf[View]
          if(errorMarkerView!=null)
            errorMarkerView.setBackgroundColor(colorError)
        }
        val wantedCharacterView = view.findViewById(R.id.wantedCharacter06).asInstanceOf[TextView]
        if(wantedCharacterView != null)
          wantedCharacterView.setText(""+detectedCharList.get(position*8+5))
      }

      {  
        val bitmap = view.findViewById(R.id.scannedCharacter07).asInstanceOf[View]
        if(bitmap != null)
          bitmap.setBackgroundDrawable(new android.graphics.drawable.BitmapDrawable(context.getResources, 
            scannedBitmapList.get(position*8+6)))
        if(errorDetected.get(position*8+6)) {
          val errorMarkerView = view.findViewById(R.id.errorMarker07).asInstanceOf[View]
          if(errorMarkerView!=null)
            errorMarkerView.setBackgroundColor(colorError)
        }
        val wantedCharacterView = view.findViewById(R.id.wantedCharacter07).asInstanceOf[TextView]
        if(wantedCharacterView != null)
          wantedCharacterView.setText(""+detectedCharList.get(position*8+6))
      }

      {  
        val bitmap = view.findViewById(R.id.scannedCharacter08).asInstanceOf[View]
        if(bitmap != null)
          bitmap.setBackgroundDrawable(new android.graphics.drawable.BitmapDrawable(context.getResources, 
            scannedBitmapList.get(position*8+7)))
        if(errorDetected.get(position*8+7)) {
          val errorMarkerView = view.findViewById(R.id.errorMarker08).asInstanceOf[View]
          if(errorMarkerView!=null)
            errorMarkerView.setBackgroundColor(colorError)
        }
        val wantedCharacterView = view.findViewById(R.id.wantedCharacter08).asInstanceOf[TextView]
        if(wantedCharacterView != null)
          wantedCharacterView.setText(""+detectedCharList.get(position*8+7))
      }
    }

    return view
  }

  ///////// added funtionality

  def add(detectedChar:Char, scannedBitmap:Bitmap, errorFlag:Boolean) {
    //if(D) Log.i(TAG, "add detectedChar="+detectedChar+" scannedBitmap="+scannedBitmap)
    detectedCharList.add(detectedChar)
    scannedBitmapList.add(scannedBitmap)
    errorDetected.add(errorFlag)
  }

  def setMaxCount(maxCount:Int) {
    if(D) Log.i(TAG, "setMaxCount("+maxCount+") detectedCharList.size="+detectedCharList.size)
    while(detectedCharList.size>maxCount) {
      detectedCharList.remove(maxCount)
      scannedBitmapList.remove(maxCount)
      errorDetected.remove(maxCount)
      if(D) Log.i(TAG, "setMaxCount("+maxCount+") detectedCharList.size="+detectedCharList.size)
    }
  }
}

