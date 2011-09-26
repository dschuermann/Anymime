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
import java.io.File

import android.app.Activity
import android.app.AlertDialog
import android.app.AlertDialog.Builder
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Window
import android.view.View
import android.view.MenuItem
import android.view.ContextMenu
import android.widget.Toast
import android.widget.ListView
import android.widget.TextView
import android.widget.EditText
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.webkit.MimeTypeMap
	
class ShowSelectedFilesActivity extends Activity {
  private val TAG = "ShowSelectedFilesActivity"
  private val D = true

  private val REQUEST_SELECT_FILE = 1
  private val REQUEST_SELECTED_SLOT = 2

  private var context:Context = null
  private var selectedFilesStringArrayList:ArrayList[String] = null
  private var listView:ListView = null
  private var fileListAdapter:FileListAdapter = null

  private val PREFS_SETTINGS = "org.timur.anymime.settings"
  private var prefSettings:SharedPreferences = null
  private var prefSettingsEditor:SharedPreferences.Editor = null

  private var mTitleLeftView:TextView = null
  private var mTitleRightView:TextView = null
  private var selectedSlot = 0
  private var selectedSlotName = ""

  override def onCreate(savedInstanceState:Bundle) {
    super.onCreate(savedInstanceState)
    if(D) Log.i(TAG, "onCreate")
    context = this

    val customTitleSupported = requestWindowFeature(Window.FEATURE_CUSTOM_TITLE)

    setContentView(R.layout.file_select)

    if(customTitleSupported)
      getWindow.setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title)
    mTitleLeftView = findViewById(R.id.title_left_text).asInstanceOf[TextView]
    mTitleRightView = findViewById(R.id.title_right_text).asInstanceOf[TextView]
    if(mTitleLeftView!=null)
      mTitleLeftView.setText("Files for delivery")

    selectedFilesStringArrayList = null
    val intent = getIntent
    if(intent!=null) {
      val bundle = intent.getExtras
      if(bundle!=null) {
        if(D) Log.i(TAG, "onCreate getting selectedFilesStringArrayList from getIntent.getExtras")
        selectedFilesStringArrayList = bundle.getStringArrayList("selectedFilesStringArrayList")
      }
    }
    if(selectedFilesStringArrayList==null) {
      if(D) Log.i(TAG, "onCreate create empty selectedFilesStringArrayList")
      selectedFilesStringArrayList = new ArrayList[String]()
    }

    if(D) Log.i(TAG, "onCreate selectedFilesStringArrayList.size="+selectedFilesStringArrayList.size)
    if(selectedFilesStringArrayList.size<1) {
      // todo: send a toast "no files yet selected" 
    }

    listView = findViewById(R.id.selectedFilesList).asInstanceOf[ListView]
    if(listView==null) {
      // todo: raus
    }

    // prepare access to prefSettings
    if(prefSettings==null) {
      prefSettings = getSharedPreferences(PREFS_SETTINGS, Context.MODE_WORLD_WRITEABLE)
      if(prefSettings!=null)
        prefSettingsEditor = prefSettings.edit
    }

    if(prefSettings!=null) {
      getSelectedSlot
    }

    fileListAdapter = new FileListAdapter(this, R.layout.file_list_entry)
		listView.setAdapter(fileListAdapter)

    updateAdapter

    listView.setOnItemClickListener(new OnItemClickListener() {
      override def onItemClick(adapterView:AdapterView[_], view:View, position:Int, id:Long) {
        // user has clicked into the conversation view
        var fileString = view.findViewById(R.id.invisibleText).asInstanceOf[TextView].getText.toString
        if(D) Log.i(TAG, "onCreate listView onItemClick position="+position+" fileString="+fileString)
        registerForContextMenu(view)
        view.setLongClickable(false)
        view.showContextMenu // -> onCreateContextMenu()
      }
    })


    AndrTools.buttonCallback(this, R.id.buttonClearAll) { () =>
      if(D) Log.i(TAG, "onClick buttonClearAll")
      val dialogClickListener = new DialogInterface.OnClickListener() {
        override def onClick(dialog:DialogInterface, whichButton:Int) {
          whichButton match {
            case DialogInterface.BUTTON_POSITIVE =>
              fileListAdapter.clear
              fileListAdapter.notifyDataSetChanged
              selectedFilesStringArrayList.clear
              persistArrayList(selectedFilesStringArrayList)
            case DialogInterface.BUTTON_NEGATIVE =>
              // do nothing
          }
        }
      }

      new AlertDialog.Builder(context).setTitle("Remove all files from this selection?")
                                      .setPositiveButton("Yes",dialogClickListener)
                                      .setNegativeButton("No", dialogClickListener)
                                      .show
    }


    AndrTools.buttonCallback(this, R.id.buttonSelectSlot) { () =>
      if(D) Log.i(TAG, "onClick buttonSelectSlot")
      val intent = new Intent(context, classOf[ShowSelectedSlotActivity])
      startActivityForResult(intent, REQUEST_SELECTED_SLOT) // -> onActivityResult()
    }

    AndrTools.buttonCallback(this, R.id.buttonRenameSlot) { () =>
      if(D) Log.i(TAG, "onClick buttonRenameSlot")

      val editText = new EditText(context)
      editText.setText(selectedSlotName)

      val dialogClickListener = new DialogInterface.OnClickListener() {
        override def onClick(dialog:DialogInterface, whichButton:Int) {
          whichButton match {
            case DialogInterface.BUTTON_POSITIVE =>
              if(D) Log.i(TAG, "onClick BUTTON_POSITIVE")
              selectedSlotName = editText.getText.toString
              if(mTitleRightView!=null)
                mTitleRightView.setText("Slot "+(selectedSlot+1)+" "+selectedSlotName)
              prefSettingsEditor.putString("fileSlotName"+selectedSlot, selectedSlotName)
              prefSettingsEditor.commit
            case DialogInterface.BUTTON_NEGATIVE =>
              if(D) Log.i(TAG, "onClick BUTTON_NEGATIVE")
          }
        }
      }

      new AlertDialog.Builder(context)
          .setTitle("Rename file slot")
          .setView(editText)
          .setMessage("Use a name that best describes the use-case for the files in this slot.")
          .setIcon(android.R.drawable.ic_menu_edit)
          .setPositiveButton("OK",dialogClickListener)
          .setNegativeButton("Cancel",dialogClickListener)
          .show
    }

    AndrTools.buttonCallback(this, R.id.buttonAdd) { () =>
      if(D) Log.i(TAG, "onClick buttonAdd")
      letUserPickAFile
    }
    
    AndrTools.buttonCallback(this, R.id.buttonDone) { () =>
      if(D) Log.i(TAG, "onClick buttonDone")
      setActivityResponse
      finish
    }
  }


  //////////////////////////////////////////////// context menu: open/view, remove, close

  val CONTEXTMENU_VIEW = 1
  val CONTEXTMENU_REMOVE = 2
  val CONTEXTMENU_CLOSE = 3
  
  var contextMenuFullPath:String = null
  var contextMenuFileName:String = null

  override def onCreateContextMenu(menu:ContextMenu, view:View, menuInfo:ContextMenu.ContextMenuInfo) :Unit = {
    if(view==null)
      return
    
    contextMenuFullPath = view.findViewById(R.id.invisibleText).asInstanceOf[TextView].getText.toString
    if(contextMenuFullPath==null)
      return

    contextMenuFileName = view.findViewById(R.id.visibleText).asInstanceOf[TextView].getText.toString
    if(contextMenuFileName==null)
      return

    if(D) Log.i(TAG, "onCreateContextMenu contextMenuFileName="+contextMenuFileName)
    menu.setHeaderTitle(contextMenuFileName)
    menu.add(0, CONTEXTMENU_VIEW, 0, "View / Open")
    menu.add(0, CONTEXTMENU_REMOVE, 0, "Remove from selection")
    menu.add(0, CONTEXTMENU_CLOSE, 0, "Close")
  }

  override def onContextItemSelected(menuItem:MenuItem) :Boolean = {
    val itemId = menuItem.getItemId
    //Log.d(TAG, "onContextItemSelected menuItem.getItemId="+itemId)

    itemId match {
      case CONTEXTMENU_VIEW =>
        // open contextMenuFileName
        val processFileIntent = new Intent(Intent.ACTION_VIEW)
        val selectedUri = Uri.fromFile(new File(contextMenuFullPath))
        if(D) Log.i(TAG, "onContextItemSelected contextMenuFullPath="+contextMenuFullPath+" selectedUri="+selectedUri)
        val contextMenuFileNameStringLower = contextMenuFileName.toLowerCase
        val lastIdxOfDot = contextMenuFileNameStringLower.lastIndexOf(".")
        val extension = if(lastIdxOfDot>=0) contextMenuFileNameStringLower.substring(lastIdxOfDot+1) else null
        if(extension!=null) {
          val mimeTypeMap = MimeTypeMap.getSingleton()
          var mimeTypeFromExtension = mimeTypeMap.getMimeTypeFromExtension(extension)
          if(extension=="asc") mimeTypeFromExtension="application/pgp"
          // note: .html files may contain xhtml context (=> application/xhtml+xml)
          if(D) Log.i(TAG, "onContextItemSelected extension="+extension+" mimeType="+mimeTypeFromExtension)
          processFileIntent.setDataAndType(selectedUri,mimeTypeFromExtension)

        } else {
          if(D) Log.i(TAG, "onContextItemSelected extension=null mimeType=*/*")
          processFileIntent.setDataAndType(selectedUri,"*/*")
        }

        if(D) Log.i(TAG, "onContextItemSelected startActivity processFileIntent="+processFileIntent)
        startActivity(Intent.createChooser(processFileIntent,"Apply action..."))
        return true

      case CONTEXTMENU_REMOVE =>
        val idxArrayList = selectedFilesStringArrayList.indexOf(contextMenuFullPath)
        if(idxArrayList>=0) {
          fileListAdapter.remove(contextMenuFullPath)
          fileListAdapter.notifyDataSetChanged
          selectedFilesStringArrayList.remove(idxArrayList)
          persistArrayList(selectedFilesStringArrayList)
        }
        return true

      case CONTEXTMENU_CLOSE =>
        return true

      case _ =>
        return super.onContextItemSelected(menuItem)
    }

    return false
  }

  //////////////////////////////////////////////// adding a file to the list

  private def letUserPickAFile() {
    val intent = new Intent(Intent.ACTION_GET_CONTENT)
    intent.setType("*/*")
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

    val title = "Select a file to send"
    intent.putExtra(Intent.EXTRA_TITLE,title)
    intent.putExtra("explorer_title", title)

    try {
      startActivityForResult(Intent.createChooser(intent, title), REQUEST_SELECT_FILE) // -> onActivityResult
    } catch {
      case ex:Exception =>
        ex.printStackTrace()
        val errMsg = ex.getMessage
        Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show
    }
  }

  override def onActivityResult(requestCode:Int, resultCode:Int, intent:Intent) {
    if(D) Log.i(TAG, "onActivityResult resultCode="+resultCode+" requestCode="+requestCode)
    requestCode match {
      case REQUEST_SELECT_FILE =>
        if(D) Log.i(TAG, "REQUEST_SELECT_FILE intent="+intent)
        if(resultCode!=Activity.RESULT_OK) {
          Log.e(TAG, "REQUEST_SELECT_FILE resultCode!=Activity.RESULT_OK -> no files selected")

        } else if(intent==null) {
          Log.e(TAG, "REQUEST_SELECT_FILE intent==null -> no files selected")

        } else {
          if(intent.getData!=null) {
            def getPath(uri:Uri) :String = {
              val projection:Array[String] = Array("_data")  //{ MediaStore.Images.Media.DATA }
              val cursor = managedQuery(uri, projection, null, null, null)
              if(cursor!=null) {
                val column_index = cursor.getColumnIndexOrThrow("_data") // (MediaStore.Images.Media.DATA)
                cursor.moveToFirst()
                return cursor.getString(column_index)
              }
              return null
            }

            val selectFileUri = intent.getData
            var selectedPath = getPath(selectFileUri) // MEDIA GALLERY
            if(selectedPath==null)
              selectedPath = selectFileUri.getPath    // FILE Manager

            // todo: don't add selectedPath if already in selectedFilesStringArrayList (?)

            Log.e(TAG, "REQUEST_SELECT_FILE add="+selectedPath)
            fileListAdapter add selectedPath
            fileListAdapter.notifyDataSetChanged
            selectedFilesStringArrayList add selectedPath
            persistArrayList(selectedFilesStringArrayList)
            Log.e(TAG, "REQUEST_SELECT_FILE selectedFilesStringArrayList="+selectedFilesStringArrayList)
            
          } else {
            if(D) Log.i(TAG, "REQUEST_SELECT_FILE no response")
          }
        }

      case REQUEST_SELECTED_SLOT =>
        if(D) Log.i(TAG, "REQUEST_SELECTED_SLOT resultCode="+resultCode)
        if(resultCode==Activity.RESULT_OK) {
          getArrayListSelectedFileStrings
          updateAdapter
        }
    }
  }

  private def getSelectedSlot() {
    val selectedSlotString = prefSettings.getString("selectedSlot", null)
    selectedSlot = if(selectedSlotString!=null) selectedSlotString.toInt else 0
    if(selectedSlot<0 || selectedSlot>ShowSelectedSlotActivity.MAX_SLOTS)
      selectedSlot = 0
    selectedSlotName = prefSettings.getString("fileSlotName"+selectedSlot, "")
    if(D) Log.i(TAG, "onCreate getSelectedSlot selectedSlot="+selectedSlot)
    if(mTitleRightView!=null)
      mTitleRightView.setText("Slot "+(selectedSlot+1)+" "+selectedSlotName)
  }

  private def getArrayListSelectedFileStrings() {
    if(prefSettings!=null) {
      getSelectedSlot
      selectedFilesStringArrayList.clear
      // read the lists of selected files
      var commaSeparatedString = prefSettings.getString("fileSlot"+selectedSlot, null)
      if(D) Log.i(TAG, "getArrayListSelectedFileStrings commaSeparatedString="+commaSeparatedString)
      if(commaSeparatedString!=null) {
        commaSeparatedString = commaSeparatedString.trim
        if(commaSeparatedString.size>0) {
          val resultArray = commaSeparatedString split ","
          if(resultArray!=null) {
            if(D) Log.i(TAG,"getArrayListSelectedFileStrings prefSettings selectedFilesStringArrayList resultArray.size="+resultArray.size)
            for(filePathString <- resultArray) {
              if(filePathString!=null) {
                selectedFilesStringArrayList add filePathString.trim
              }
            }
          }
        }
      }
    }
  }

  private def updateAdapter() {
    if(D) Log.i(TAG, "updateAdapter selectedFilesStringArrayList.size="+selectedFilesStringArrayList.size)
    fileListAdapter.clear
    if(selectedFilesStringArrayList.size>0) {
      val iterator = selectedFilesStringArrayList.iterator 
      while(iterator.hasNext)
        fileListAdapter.add(iterator.next)
    }
    fileListAdapter.notifyDataSetChanged
  }


  private def persistArrayList(arrayList:ArrayList[String]) {
    if(prefSettings!=null && prefSettingsEditor!=null) {
      val iterator = arrayList.iterator 
      var stringBuilder = new StringBuilder()
      while(iterator.hasNext) {
        if(stringBuilder.size>0)
          stringBuilder append ","
        stringBuilder append iterator.next
      }
      if(D) Log.i(TAG, "persistArrayList stringBuilder="+stringBuilder.toString)
      prefSettingsEditor.putString("fileSlot"+selectedSlot,stringBuilder.toString)
      prefSettingsEditor.commit
    }
  }

  //////////////////////////////////////////////// leaving this activity and handing back the list

	override def onBackPressed() {
    if(D) Log.i(TAG, "onBackPressed()")
		setActivityResponse
    super.onBackPressed
	}
	
	private def setActivityResponse() {
		setResult(Activity.RESULT_OK)
	}
}

