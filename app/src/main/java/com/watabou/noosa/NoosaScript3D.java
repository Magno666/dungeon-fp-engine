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
 * Depth is the straight-line distance from the eye, not gl_Position.w.
 * The w route is one instruction cheaper and it was wrong in a way that
 * only shows up close: w is depth ALONG THE VIEW AXIS, so every point of
 * a wall square-on to the camera carries the same w and the whole face
 * shades to a single flat value. Standing against a wall, that face fills
 * the screen — and it filled it with one colour, which reads as a
 * rectangle rather than as stone. Radial distance costs a varying and a
 * length() per fragment, and gives a near wall the falloff a torch casts.
 */
public class NoosaScript3D extends NoosaScript {

	public Uniform uFogColor;
	public Uniform uFogRange;
	public Uniform uEye;

	private float fogR = 0.04f, fogG = 0.04f, fogB = 0.06f;
	private float fogNear = 4f, fogFar = 26f;
	private float falloff = 2.6f;

	public NoosaScript3D() {
		super();
		uFogColor = uniform( "uFogColor" );
		uFogRange = uniform( "uFogRange" );
		uEye = uniform( "uEye" );
	}

	/**
	 * Colour to fade into, and the distances where fog starts and finishes.
	 * Uploaded immediately: Script.use() only runs when the script class
	 * changes, so setting these as fields would leave every draw in a frame
	 * using whatever the last caller of the previous frame left behind.
	 */
	public void fog( float r, float g, float b, float near, float far ) {
		fog( r, g, b, near, far, falloff );
	}

	/**
	 * @param curve how sharply the light dies with distance. 1 is the plain
	 *              linear ramp; higher bends it towards what a torch really
	 *              does. Linear was the reason a near wall stayed flat even
	 *              after the fog went radial: across the face of a wall you
	 *              are standing against, distance only runs from 1.5 to 3,
	 *              and on a 0.5-to-17 ramp that is nine percent of
	 *              brightness — real, measurable, and invisible. Raising it
	 *              to a power spends the range where the eye is looking.
	 */
	public void fog( float r, float g, float b, float near, float far, float curve ) {
		fogR = r; fogG = g; fogB = b;
		fogNear = near; fogFar = far; falloff = curve;
		uFogColor.value4f( fogR, fogG, fogB, 1f );
		uFogRange.value4f( fogNear, fogFar, falloff, 0f );
	}

	/**
	 * Where the eye is, in world units. Uploaded immediately for the same
	 * reason {@link #fog} is: the script instance outlives a single draw.
	 */
	public void eye( float x, float y, float z ) {
		// vec4 rather than vec3: com.watabou.glwrap.Uniform has no value3f,
		// and a fourth float costs nothing next to editing the engine.
		uEye.value4f( x, y, z, 1f );
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
		"varying vec3 vWorld;" +
		"void main() {" +
		"  vec4 world = uModel * aXYZW;" +
		"  gl_Position = uCamera * world;" +
		"  vUV = aUV;" +
		"  vWorld = world.xyz;" +
		"}" +

		"//\n" +

		// highp where the GPU has it. mediump is fp16 on Mali (max ~65504)
		// and distance() below squares world coordinates, so on a big
		// level the torch falloff would break up on Mali and only there.
		// Preprocessor lines must end in a newline, hence the explicit ones.
		"#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
		"precision highp float;\n" +
		"#else\n" +
		"precision mediump float;\n" +
		"#endif\n" +
		"varying vec2 vUV;" +
		"varying vec3 vWorld;" +
		"uniform vec4 uEye;" +
		"uniform sampler2D uTex;" +
		"uniform vec4 uColorM;" +
		"uniform vec4 uColorA;" +
		"uniform vec4 uFogColor;" +
		"uniform vec4 uFogRange;" +
		"void main() {" +
		"  vec4 c = texture2D( uTex, vUV ) * uColorM + uColorA;" +
		// A billboard is a rectangle but a monster is not. With depth
		// testing on, the transparent corners would still write depth and
		// punch an invisible block out of the wall behind. These sprites
		// have hard edges, so discarding is exact -- and it makes draw
		// order stop mattering, which blending never would.
		"  if (c.a < 0.5) discard;" +
		"  float d = distance( uEye.xyz, vWorld );" +
		"  float lit = clamp( (uFogRange.y - d) / (uFogRange.y - uFogRange.x), 0.0, 1.0 );" +
		"  lit = pow( lit, uFogRange.z );" +
		"  gl_FragColor = vec4( mix( uFogColor.rgb, c.rgb, lit ), c.a );" +
		"}";
}
