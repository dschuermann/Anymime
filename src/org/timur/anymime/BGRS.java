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

import android.content.res.Resources;
import android.renderscript.RenderScriptGL;

public class BGRS {
    private RenderScriptGL mRS;
    private ScriptC_BG mScript;

    public BGRS(RenderScriptGL rs, Resources res, int resId) {
    	mRS = rs;
    	mScript = new ScriptC_BG(rs, res, resId);
    	mRS.bindRootScript(mScript);
    }
}

