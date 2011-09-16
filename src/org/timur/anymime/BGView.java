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

package org.timur.anymime;

import android.content.Context;
import android.renderscript.RSSurfaceView;
import android.renderscript.RenderScriptGL;
import android.util.Log;
import android.widget.Toast;

public class BGView extends RSSurfaceView {
  private String TAG = "BGView";
  private boolean D = true;

	private Context mContext;
	private BGRS mRenderScript;
	private RenderScriptGL mRS;

	public BGView(Context context) {
	    super(context);
	    mContext = context;
	    ensureRenderScript();
    }

	private void ensureRenderScript() {
	  try {
		  if (mRS == null) {
			  final RenderScriptGL.SurfaceConfig sc = new RenderScriptGL.SurfaceConfig();
			  mRS = createRenderScriptGL(sc);
		  }
		  if (mRenderScript == null) {
			  mRenderScript = new BGRS(mRS, mContext.getResources(), R.raw.bg);
		  }
    } catch(java.lang.NoClassDefFoundError ncldefex) {
      Log.e(TAG, "ensureRenderScript "+ncldefex);
      Toast.makeText(mContext, "RenderScript classes not available", Toast.LENGTH_LONG).show();
    }
	}
}

