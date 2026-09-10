/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Distance fog for the first person view
 * Copyright (C) 2026 Leonel Garcia
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package com.watabou.noosa;

import com.watabou.glscripts.Script;
import com.watabou.glwrap.Uniform;

/**
 * NoosaScript with distance fog.
 *
 * Without it the first person view shows the entire level at once, which
 * reads as a flat collage rather than a corridor: a dungeon is supposed to
 * end a few tiles ahead of you. The plain NoosaScript is left alone, so
 * every 2D Visual keeps using the shader it always used.
 *
 * Depth comes from gl_Position.w, which under a perspective projection is
 * the distance along the view axis. Near enough, and free.
 */
public class NoosaScript3D extends NoosaScript {

	public Uniform uFogColor;
	public Uniform uFogRange;

	private float fogR = 0.04f, fogG = 0.04f, fogB = 0.06f;
	private float fogNear = 4f, fogFar = 26f;

	public NoosaScript3D() {
		super();
		uFogColor = uniform( "uFogColor" );
		uFogRange = uniform( "uFogRange" );
	}

	/**
	 * Colour to fade into, and the distances where fog starts and finishes.
	 * Uploaded immediately: Script.use() only runs when the script class
	 * changes, so setting these as fields would leave every draw in a frame
	 * using whatever the last caller of the previous frame left behind.
	 */
	public void fog( float r, float g, float b, float near, float far ) {
		fogR = r; fogG = g; fogB = b;
		fogNear = near; fogFar = far;
		uFogColor.value4f( fogR, fogG, fogB, 1f );
		uFogRange.value2f( fogNear, fogFar );
	}

	/**
	 * NoosaScript uploads the camera matrix only when the camera reference
	 * changes, and Game clears that cache once a frame on the 2D script
	 * instance only. This one never gets cleared, and a 3D camera mutates
	 * its matrix in place every frame — so without this the whole world
	 * renders forever from wherever the eye was on the very first draw.
	 */
	@Override
	public void camera( Camera camera ) {
		resetCamera();
		super.camera( camera );
	}

	public static NoosaScript3D get() {
		return Script.use( NoosaScript3D.class );
	}

	@Override
	protected String shader() {
		return SHADER;
	}

	private static final String SHADER =

		"uniform mat4 uCamera;" +
		"uniform mat4 uModel;" +
		"attribute vec4 aXYZW;" +
		"attribute vec2 aUV;" +
		"varying vec2 vUV;" +
		"varying float vDepth;" +
		"void main() {" +
		"  gl_Position = uCamera * uModel * aXYZW;" +
		"  vUV = aUV;" +
		"  vDepth = gl_Position.w;" +
		"}" +

		"//\n" +

		"precision mediump float;" +
		"varying vec2 vUV;" +
		"varying float vDepth;" +
		"uniform sampler2D uTex;" +
		"uniform vec4 uColorM;" +
		"uniform vec4 uColorA;" +
		"uniform vec4 uFogColor;" +
		"uniform vec2 uFogRange;" +
		"void main() {" +
		"  vec4 c = texture2D( uTex, vUV ) * uColorM + uColorA;" +
		// A billboard is a rectangle but a monster is not. With depth
		// testing on, the transparent corners would still write depth and
		// punch an invisible block out of the wall behind. These sprites
		// have hard edges, so discarding is exact -- and it makes draw
		// order stop mattering, which blending never would.
		"  if (c.a < 0.5) discard;" +
		"  float lit = clamp( (uFogRange.y - vDepth) / (uFogRange.y - uFogRange.x), 0.0, 1.0 );" +
		"  gl_FragColor = vec4( mix( uFogColor.rgb, c.rgb, lit ), c.a );" +
		"}";
}
