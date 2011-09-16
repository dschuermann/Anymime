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

import android.app.Activity
import android.app.ListActivity
import android.app.AlertDialog
import android.app.AlertDialog.Builder
import android.content.DialogInterface
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.CheckBox
import android.widget.ArrayAdapter
import android.widget.Toast
import android.widget.ListView
import android.widget.TextView
import android.widget.ImageView
import android.view.KeyEvent
import android.view.Window
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice

class PgpKeySigningPopupActivity extends Activity {
  private val TAG = "PgpKeySigningPopupActivity"
  private val D = true

  override def onCreate(savedInstanceState:Bundle) {
    super.onCreate(savedInstanceState)

    /////////////////////////////////// TODO: NOT YET IMPLEMENTED AT ALL

    new AlertDialog.Builder(this)
          .setTitle("Sorry")
          .setMessage("This functionality has not been implemented yet")
          .setPositiveButton("OK",
             new DialogInterface.OnClickListener() {
               override def onClick(dialog:DialogInterface, whichButton:Int) {
                 whichButton match {
                   case DialogInterface.BUTTON_POSITIVE =>
                     finish
                 }
               }
             }
           )
          .show     
  }
}

