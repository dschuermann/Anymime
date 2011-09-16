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

#pragma version(1)
#pragma rs java_package_name(org.timur.anymime);

#include "rs_graphics.rsh"

float4 bgColor; // Background color as xyzw 4-part float

static void drawBackground() {
	rsgClearColor(bgColor.x, bgColor.y, bgColor.z, bgColor.w);
}

void init() {
	bgColor = (float4) { 0.0f, 1.0f, 0.0f, 1.0f };
	rsDebug("Called init", rsUptimeMillis());
}

int root() {
	drawBackground();
	return 16;
}

