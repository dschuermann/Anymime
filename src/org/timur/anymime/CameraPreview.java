/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package org.timur.anymime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.MotionEvent;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Matrix;
import android.graphics.YuvImage;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.widget.TextView;
import android.widget.Toast;
import android.content.res.Configuration;
import android.content.ComponentName;

public class CameraPreview extends Activity {
  private static String TAG = "CameraPreview";
  private static boolean D = true;

  protected Preview mPreview = null;
  protected String sentKeyFingerprint = null;
  protected String receivedKeyFingerprint = null;
  protected volatile Camera mCamera = null;
  protected volatile boolean snapPicture = false;
  volatile boolean cameraPreviewActive = false;

  Camera.AutoFocusCallback autoFocusCallBack = null;
  CameraPreview context = null;
  int numberOfCameras;
  int defaultCameraId;
  int cameraCurrentlyLocked;
  volatile boolean focusInProgress=false;
  volatile boolean pauseApp=false;
  int originalOrientation = 0;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    context = this;
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

    Intent intent = getIntent();
    if(intent==null) {
      return;
    }

    Bundle extrasBundle = intent.getExtras();
    if(extrasBundle==null) {
      return;
    }

    sentKeyFingerprint = extrasBundle.getString("sentKeyFingerprint");
    receivedKeyFingerprint = extrasBundle.getString("receivedKeyFingerprint");
    originalOrientation = extrasBundle.getInt("orientation");

    if(D) Log.i(TAG, "onCreate show camera preview originalOrientation="+originalOrientation);
    // Create a RelativeLayout container that will hold a SurfaceView,
    // and set it as the content of our activity.
    //SurfaceView renderView = (SurfaceView)findViewById(R.id.surface);
    //if(D) Log.i(TAG, "onCreate renderView="+renderView);
    mPreview = new Preview(this);
    setContentView(mPreview);
    mPreview.originalOrientation = originalOrientation;

    //addContentView(overlayView, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
    //overlayView.setVisibility(View.VISIBLE);

    // Find the total number of cameras available
    numberOfCameras = Camera.getNumberOfCameras();

    // Find the ID of the default camera
    CameraInfo cameraInfo = new CameraInfo();
    for (int i = 0; i < numberOfCameras; i++) {
      Camera.getCameraInfo(i, cameraInfo);
      if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK)
        defaultCameraId = i;
    }

    // autofocus logic
    autoFocusCallBack = new Camera.AutoFocusCallback() {
      @Override
      public void onAutoFocus(boolean success, Camera camera) {
        focusInProgress=true;
        if(D) Log.i(TAG, "autofocus callback autofocused="+success);
        if(success) {
          if(snapPicture) {
            snapPicture=false;
            if(!pauseApp) {
              if(D) Log.i(TAG, "autofocus callback snapPicture...");
              mPreview.snapPicture(mCamera);
              //if(D) Log.i(TAG, "Inside autofocus callback. snapPicture... mPreview.origBitmap="+mPreview.origBitmap);
            }
          }
        }

        if(mCamera!=null && !mPreview.snappingPicture() && !pauseApp /*&& Preview.origBitmap==null*/) {
          //if(D) Log.i(TAG, "Inside autofocus renew call to mCamera.autoFocus()");
          mCamera.autoFocus(autoFocusCallBack);
        }
        else {
          if(D) Log.i(TAG, "Inside autofocus switch focusInProgress off and exit");
          focusInProgress=false;
        }
      }
    };

    focusInProgress=false;
    if(D) Log.i(TAG, "onCreate start focus thread");
    new Thread() {
      @Override
      public void run() {
        while(!pauseApp) {
          if(D) Log.i(TAG, "onCreate sleep... focusInProgress="+focusInProgress);
          while(mCamera==null || focusInProgress || pauseApp || !cameraPreviewActive || mPreview.snappingPicture()) {
            try { Thread.sleep(100); } catch (Exception ex) { }
          }
          try {
            if(mCamera!=null && !pauseApp) {
              if(D) Log.i(TAG, "onCreate autoFocus...");
              mCamera.autoFocus(autoFocusCallBack);   // todo: Nexus One: java.lang.RuntimeException: autoFocus failed (but not always)
                                                      // todo: GN: java.lang.RuntimeException: autoFocus failed (always)
              //if(D) Log.i(TAG, "onCreate autoFocus success");   // todo: we can't be sure about "autoFocus success" at this point
            }
          } catch(Exception ex) {
            if(D) Log.i(TAG, "onCreate autofocus ex=",ex);

            // todo: needs to run on uithread
          	//	Toast.makeText(context, "Failed to activate autofocus", Toast.LENGTH_SHORT).show();
          }
          try { Thread.sleep(500); } catch (Exception ex) { }
        }
      }
    }.start();
  }

  @Override
  protected void onResume() {
    super.onResume();

    // Open the default i.e. the first rear facing camera.
    mCamera = Camera.open();
    cameraCurrentlyLocked = defaultCameraId;
    if(mPreview!=null)
      mPreview.setCamera(mCamera);
    else {
      Log.e(TAG, "onResume mPreview==null");
    }
    if(D) Log.i(TAG, "onResume");
  }

  @Override
  protected void onPause() {
    if(D) Log.i(TAG, "onPause");
    pauseApp=true;

    // Because the Camera object is a shared resource, it's very
    // important to release it when the activity is paused.
    if(mCamera != null) {
      cameraPreviewActive=false;
      mPreview.setCamera(null);
      mCamera.stopPreview();
      mCamera.setPreviewCallback(null);
      mCamera.release();
      mCamera = null;
    }

    super.onPause();
    if(D) Log.i(TAG, "onPause done");
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    if(D) Log.i(TAG, "onCreateOptionsMenu... mCamera="+mCamera);
    snapPicture=true; // will be evaluated in autoFocusCallBack
    return true;
  }
}

class Preview extends ViewGroup implements SurfaceHolder.Callback {
  private final String TAG = "PreviewCamera";
  private static boolean D = true;

  public int originalOrientation = 0;
  SurfaceView mSurfaceView;
  SurfaceHolder mHolder;
  Size mPreviewSize;
  List<Size> mSupportedPreviewSizes;
  Camera mCamera;
  CameraPreview cameraPreview = null;
  volatile boolean snapPicture=false;
  Bitmap origBitmap = null;

  Preview(Context setContext) {
    super(setContext);
    cameraPreview = (CameraPreview)setContext;
    mSurfaceView = new SurfaceView(setContext);
    addView(mSurfaceView);
    mHolder = mSurfaceView.getHolder();
    mHolder.addCallback(this);
    mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
  }

  public void setCamera(Camera camera) {
    mCamera = camera;
    if (mCamera != null) {
      mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
      requestLayout();
    }
  }

  public void switchCamera(Camera camera) {
    if(D) Log.i(TAG, "switchCamera() ... camera="+camera);
    if(camera!=null) {
      setCamera(camera);
      try {
        camera.setPreviewDisplay(mHolder);
      } catch (IOException exception) {
        Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
      }
      Camera.Parameters parameters = camera.getParameters();
      parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
      requestLayout();

      try {
        if(D) Log.i(TAG, "switchCamera() parameters for focus_mode_macro...");
        //parameters.set("orientation", "portrait");
        parameters.set("picture-format","yuv420sp"); // or "yuv422sp" "yuv420sp" "yuv422i-yuyv" "rgb565" "jpeg"
        parameters.setColorEffect(Camera.Parameters.EFFECT_MONO);
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);
        camera.setParameters(parameters);
        //camera.setDisplayOrientation(90);
      } catch(java.lang.RuntimeException rex) {
        // no support for FOCUS_MODE_MACRO
        if(D) Log.i(TAG, "switchCamera() try parameters without focus_mode_macro...");
        parameters = camera.getParameters();
        parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        requestLayout();
        //parameters.set("orientation", "portrait");
        parameters.set("picture-format","yuv420sp"); // or "yuv422sp" "yuv420sp"(also NV12?) "yuv422i-yuyv" "rgb565" "jpeg"
        parameters.setColorEffect(Camera.Parameters.EFFECT_MONO);
        camera.setParameters(parameters);
        //camera.setDisplayOrientation(90);
      }
    }
    if(D) Log.i(TAG, "switchCamera() done");
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
      // We purposely disregard child measurements because act as a
      // wrapper to a SurfaceView that centers the camera preview instead
      // of stretching it.
      final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
      final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
      if(D) Log.i(TAG, "onMeasure suggested w="+width+" h="+height);
      setMeasuredDimension(width, height);

      if (mSupportedPreviewSizes != null) {
        mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
        if(D) Log.i(TAG, "onMeasure getOptimalPreviewSize w="+mPreviewSize.width+" h="+mPreviewSize.height);
      }
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    if (changed && getChildCount() > 0) {
      final View child = getChildAt(0);
      final int width = r - l;
      final int height = b - t;
      if(D) Log.i(TAG, "onLayout w="+width+" h="+height);

      int previewWidth = width;
      int previewHeight = height;
      if (mPreviewSize != null) {
        previewWidth = mPreviewSize.width;
        previewHeight = mPreviewSize.height;
      }

      // Center the child SurfaceView within the parent.
      if (width * previewHeight > height * previewWidth) {
        final int scaledChildWidth = previewWidth * height / previewHeight;
        child.layout((width - scaledChildWidth) / 2, 0,
                (width + scaledChildWidth) / 2, height);
      } else {
        final int scaledChildHeight = previewHeight * width / previewWidth;
        child.layout(0, (height - scaledChildHeight) / 2,
                width, (height + scaledChildHeight) / 2);
      }
    }
    if(D) Log.i(TAG, "onLayout done");
  }

  public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    // Now that the size is known, set up the camera parameters and begin
    // the preview.
    if(D) Log.i(TAG, "surfaceChanged w="+w+" h="+h+" format="+format);
    // format = android.graphics.PixelFormat
    // 4 = RGB_565 
    // 7 = RGBA_4444 
    // 1 = RGBA_8888 
    
    if(mCamera!=null) {
      Camera.Parameters parameters = mCamera.getParameters();
      parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
      requestLayout();

      //parameters.set("orientation", "portrait");
      // we need this, but setting it throws a runtimeexception on the LG tablet
      //parameters.set("picture-format","yuv420sp"); // or "yuv422sp" "yuv420sp" "yuv422i-yuyv" "rgb565" "jpeg"
      parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);
      //parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
      try {
        mCamera.setParameters(parameters);
      } catch(java.lang.RuntimeException rtex) {
        Log.e(TAG, "surfaceChanged mCamera.setParameters() w="+w+" h="+h+" rtex="+rtex);
      }
      //mCamera.setDisplayOrientation(90);
      mCamera.startPreview();
      cameraPreview.cameraPreviewActive=true;
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent motionEvent)
  { 
    if(D) Log.i(TAG, "onTouchEvent - motionEvent="+motionEvent+" snapPicture="+snapPicture);
    if(motionEvent.getAction()==MotionEvent.ACTION_DOWN && !snapPicture) {
      cameraPreview.snapPicture=true;
    }
    return true;
  }

  public void surfaceCreated(SurfaceHolder holder) {
    // The Surface has been created, acquire the camera and tell it where to draw
    if(D) Log.i(TAG, "surfaceCreated() ...");
    try {
      if(mCamera != null) {
        mCamera.setPreviewDisplay(holder);
        mCamera.setPreviewCallback(new Camera.PreviewCallback() {
          public void onPreviewFrame(byte[] imageData, Camera _camera) {
            if(snapPicture) {
              snapPicture=false;

              Camera.Parameters parameters = _camera.getParameters();
              int width = parameters.getPreviewSize().width;  // 720
              int height = parameters.getPreviewSize().height;  // 480
              if(D) Log.i(TAG, "onPreviewFrame - display pixels w="+width+" h="+height);

              // convert to Bitmap format
              int format = parameters.getPreviewFormat();
              if(format==ImageFormat.NV21 
                || format==842094169) {   // this is for ICS
                // YUV formats require more conversion
                if(D) Log.i(TAG, "onPreviewFrame - format="+format+" == ImageFormat.NV21");
                YuvImage yuvImage = new YuvImage(imageData, ImageFormat.NV21, width, height, null);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, outputStream);
                origBitmap = BitmapFactory.decodeByteArray(outputStream.toByteArray(), 0, outputStream.size());
              } else {
                if(D) Log.i(TAG, "onPreviewFrame - format == "+format+" (not ImageFormat.NV21="+ImageFormat.NV21+")");
                origBitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
              }

              // shrink image (from 720*480) to desired size 360*240
              if(D) Log.i(TAG, "onCreate scan camera bitmap shrink from width="+width+" height="+height+" origBitmap="+origBitmap);
              width=360; 
              height=240;
              if(D) Log.i(TAG, "onCreate scan camera bitmap shrink to   width="+width+" height="+height);
              Bitmap smallBitmap = Bitmap.createScaledBitmap(origBitmap, width, height, true);
              origBitmap.recycle(); origBitmap=null;

              // rotate 90 
              Matrix matrix = new Matrix();
              matrix.postRotate(90f,width/2,height/2);
              Bitmap rotatedBitmap = Bitmap.createBitmap(smallBitmap, 0, 0, width, height, matrix, true);
              smallBitmap.recycle(); smallBitmap = null;
              width = rotatedBitmap.getWidth();
              height = rotatedBitmap.getHeight();
              if(D) Log.i(TAG, "onCreate scan camera bitmap rotated to width="+width+" height="+height);


              if(D) Log.i(TAG, "onPreviewFrame - startActivity FingerprintOcrActivity");
              //Intent intent = new Intent(cameraPreview, FingerprintOcrActivity.class);
              Intent intent = new Intent();
              String mPackage = "org.timur.anymime";
              String mClass = ".FingerprintOcrActivity";
              intent.setComponent(new ComponentName(mPackage,mPackage+mClass));

              intent.putExtra("bitmap", rotatedBitmap);
              Bundle bundle = new Bundle();
              bundle.putString("sentKeyFingerprint", cameraPreview.sentKeyFingerprint);
              bundle.putString("receivedKeyFingerprint", cameraPreview.receivedKeyFingerprint);
              bundle.putInt("orientation",originalOrientation);
              intent.putExtras(bundle);
              cameraPreview.startActivity(intent);
              cameraPreview.finish();
            }
          }
        });
      }
    } catch(IOException exception) {
      Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
    }
  }

  public void surfaceDestroyed(SurfaceHolder holder) {
    // Surface will be destroyed when we return, so stop the preview.
    if (mCamera != null) {
      cameraPreview.cameraPreviewActive=false;
      mCamera.stopPreview();
    }
  }

  private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
    final double ASPECT_TOLERANCE = 0.1;
    double targetRatio = (double) w / h;
    if (sizes == null) return null;

    if(D) Log.i(TAG, "getOptimalPreviewSize w="+w+" h="+h);
    Size optimalSize = null;
    double minDiff = Double.MAX_VALUE;
    int targetHeight = h;

    // Try to find an size match aspect ratio and size
    for(Size size : sizes) {
      double ratio = (double) size.width / size.height;
      if(Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
      if(Math.abs(size.height - targetHeight) < minDiff) {
        optimalSize = size;
        minDiff = Math.abs(size.height - targetHeight);
      }
    }

    // Cannot find the one match the aspect ratio, ignore the requirement
    if(optimalSize == null) {
      minDiff = Double.MAX_VALUE;
      for(Size size : sizes) {
        if(Math.abs(size.height - targetHeight) < minDiff) {
          optimalSize = size;
          minDiff = Math.abs(size.height - targetHeight);
        }
      }
    }
    return optimalSize;
  }

  public void snapPicture(Camera _camera) {
    if(D) Log.i(TAG, "snapPicture...");
    snapPicture=true;
  }
  
  public boolean snappingPicture() {
    return snapPicture;
  }
}

