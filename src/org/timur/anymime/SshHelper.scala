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

import java.util.Properties
import java.io.File
import java.io.FileInputStream

import android.util.Log
import android.app.Activity
import android.content.Context

import com.jcraft.jsch.JSch
import com.jcraft.jsch.ChannelSftp

object SshHelper {
  private val TAG = "SshHelper"
  private val D = true

  class MyLogger extends com.jcraft.jsch.Logger {
    val name = new java.util.Hashtable[java.lang.Integer,String]()
    name.put(new java.lang.Integer(1), "DEBUG: ")
    name.put(new java.lang.Integer(2), "INFO: ")
    name.put(new java.lang.Integer(3), "WARN: ")
    name.put(new java.lang.Integer(4), "ERROR: ")
    name.put(new java.lang.Integer(5), "FATAL: ")

    def isEnabled(level:Int) :Boolean = {
      return true
    }

    def log(level:Int, message:String) {
      if(D) Log.i(TAG, "jsch "+name.get(new java.lang.Integer(level))+" "+message)
    }
  }

  // todo: we may want to put this (potentially long running) method in it's own service

  def scp(hostname:String, username:String, publicKeyPath:String, sendFilePath:String, remoteDirectory:String, remoteFileName:String) :Unit = {
    // note: the caller has to prompt exception messages to the user

    if(D) Log.i(TAG, "scp username="+username+" hostname="+hostname+" publicKeyPath="+publicKeyPath+" sendFilePath="+sendFilePath+" remoteFileName="+remoteFileName)
    JSch.setLogger(new MyLogger)
    val jsch = new JSch
    try {
      jsch.addIdentity(publicKeyPath)
    } catch {
      case jschex:com.jcraft.jsch.JSchException =>
      Log.e(TAG, "scp jsch.addIdentity() com.jcraft.jsch.JSchException ",jschex)
      throw jschex
    }
    //if(D) Log.i(TAG, "scp jsch.addIdentity() done")

    val session = jsch.getSession(username, hostname, 22)
    val properties = new Properties
    properties.put("StrictHostKeyChecking", "no")
    session.setConfig(properties)
    try {
      if(D) Log.i(TAG, "scp session.connect() ...")
      session.connect
    } catch {
      case jschex:com.jcraft.jsch.JSchException =>
      Log.e(TAG, "scp session.connect() com.jcraft.jsch.JSchException ",jschex)
      throw jschex
    }

    if(D) Log.i(TAG, "scp connected send sendFilePath="+sendFilePath+" remoteFileName="+remoteFileName+" remoteDirectory="+remoteDirectory)
    val channelSftp = session.openChannel("sftp").asInstanceOf[ChannelSftp]
    try {
      channelSftp.connect
      if(D) Log.i(TAG, "scp channelSftp.connect done")
    } catch {
      case jschex:com.jcraft.jsch.JSchException =>
        Log.e(TAG, "scp session.connect() com.jcraft.jsch.JSchException ",jschex)
        throw jschex
      case ex:Exception =>
        Log.e(TAG, "scp session.connect() Exception ",ex)
        throw ex
    }

    try {
      if(D) Log.i(TAG, "scp channelSftp.cd(remoteDirectory="+remoteDirectory+") ...")
      channelSftp.cd(remoteDirectory)
      if(D) Log.i(TAG, "scp new File("+sendFilePath+") ...")
      val localFile = new File(sendFilePath)
      if(D) Log.i(TAG, "scp localFile="+localFile+" ...")
      if(localFile!=null) {
        if(D) Log.i(TAG, "scp channelSftp.put remoteFileName="+remoteFileName+" ...")
        channelSftp.put(new FileInputStream(localFile), remoteFileName)
        if(D) Log.i(TAG, "scp channelSftp.put() done")
      }
    } catch {
      case jschex:com.jcraft.jsch.JSchException =>
        if(D) Log.i(TAG, "com.jcraft.jsch.JSchException ",jschex)
        Log.e(TAG, "scp com.jcraft.jsch.JSchException ",jschex)
        channelSftp.disconnect
        session.disconnect         
        throw jschex
    }

    channelSftp.disconnect
    session.disconnect         
    if(D) Log.i(TAG, "scp session.disconnect() done")
  } 
}

