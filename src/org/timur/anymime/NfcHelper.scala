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

import android.util.Log
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.tech.NfcF
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice

import java.nio.charset.Charset
import java.util.Locale

object NfcHelper {
  private val TAG = "NfcHelper"
  private val D = true

  def newTextRecord(text:String, locale:Locale, encodeInUtf8:Boolean) : NdefRecord = {
    val langBytes = locale.getLanguage.getBytes(Charset.forName("US-ASCII"))
    val utfEncoding = if(encodeInUtf8) Charset.forName("UTF-8") else Charset.forName("UTF-16")
    val textBytes = text.getBytes(utfEncoding)
    val utfBit = if(encodeInUtf8) 0 else (1 << 7)
    val statusByte = (utfBit + langBytes.length).asInstanceOf[Byte]
    val statusByteArray = new Array[Byte](1)
    statusByteArray(0) = statusByte
    var data = new Array[Byte](1 + langBytes.length + textBytes.length)
    System.arraycopy(statusByteArray, 0, data, 0, 1)
    System.arraycopy(langBytes, 0, data, 1, langBytes.length)
    System.arraycopy(textBytes, 0, data, 1+langBytes.length, textBytes.length)
    return new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new Array[Byte](0), data)
  }

  // as a result of NfcAdapter.ACTION_NDEF_DISCOVERED
  def checkForNdefAction(context:Context, intent:Intent) :String = {
    if(D) Log.i(TAG, "checkForNdefAction intent="+intent+" ####")

    if(intent==null || intent.getAction!=NfcAdapter.ACTION_NDEF_DISCOVERED) {
      if(D) Log.i(TAG, "checkForNdefAction intent.getAction(="+intent.getAction+") != ACTION_NDEF_DISCOVERED -> ABORT ####")
        // note: sometimes we get "android.intent.action.MAIN" here
      return null
    }

    def rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
    if(rawMsgs==null || rawMsgs.length<=0) {
      if(D) Log.i(TAG, "checkForNdefAction no rawMsgs.length -> ABORT ####")
      return null
    }

    if(D) Log.i(TAG, "checkForNdefAction rawMsgs.length="+rawMsgs.length)
    val msg = rawMsgs.apply(0).asInstanceOf[NdefMessage]
    val ndefRecords = msg.getRecords

    var ndefRecord = ndefRecords.apply(0)
    if(D) Log.i(TAG, "checkForNdefAction ndefRecord="+ndefRecord)
    if(ndefRecord==null || ndefRecord.getTnf!=NdefRecord.TNF_WELL_KNOWN) {
      if(D) Log.i(TAG, "checkForNdefAction ndefRecord.getTnf!=NdefRecord.TNF_WELL_KNOWN -> ABORT ####")
      return null
    }

    if(D) Log.i(TAG, "checkForNdefAction ndefRecord.getTnf==NdefRecord.TNF_WELL_KNOWN")
    val payloadByteArray = ndefRecord.getPayload

    // payloadByteArray[0] contains the "Status Byte Encodings" field, per the
    // NFC Forum "Text Record Type Definition" section 3.2.1.
    // if (Bit_7 == 0): text UTF-8 encoded if (Bit_7 == 1): UTF-16
    // Bit_6 is reserved for future use and must be set to zero.
    // Bits 5 to 0 are the length of the IANA language code.

    val textEncoding = if ((payloadByteArray(0) & 0200) == 0) "UTF-8" else "UTF-16"
    val languageCodeLength = payloadByteArray(0) & 0077
    val result = new String(payloadByteArray, languageCodeLength+1, payloadByteArray.length-languageCodeLength-1, textEncoding)
    if(D) Log.i(TAG, "checkForNdefAction resolveIntent0 textEncoding="+textEncoding+" languageCodeLength="+languageCodeLength+" result="+result)
    return result

/*
    // btAddress plausibility check
    if(result==null || !result.startsWith("bt=") /*|| result.indexOf(":")!=2*/) {
      if(D) Log.i(TAG, "checkForNdefAction btAddress ["+result+"] plausibility check failed -> ABORT")
      return null
    }

    val btAddress = result.substring(3)
    if(D) Log.i(TAG, "checkForNdefAction verified btAddress="+btAddress)
    return btAddress
*/

/*
    // wifiAddress plausibility check
    if(result==null || !result.startsWith("p2pWifi=") /*|| result.indexOf(":")!=2*/) {
      if(D) Log.i(TAG, "checkForNdefAction wifiAddress ["+result+"] plausibility check failed -> ABORT ####")
      return null
    }

    val wifiAddress = result.substring(8)
    if(D) Log.i(TAG, "checkForNdefAction verified remote wifiAddress="+wifiAddress)
    return wifiAddress
*/
  }
}


