/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * First-person camera addition
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

import com.watabou.glwrap.Matrix;

/**
 * A perspective camera for Noosa.
 *
 * Noosa was written for a top-down 2D game, but its shader already runs
 * gl_Position = uCamera * uModel * aXYZW with a real mat4 and a vec4
 * position. So a first-person view needs no new shader and no new GL
 * pipeline: it only needs this class to put a projection * view matrix
 * into the same Camera.matrix the renderer already uploads.
 *
 * Screen-space cameras (PixelScene.uiCamera and everything drawn through
 * it) are untouched, which is why the inventory and toolbar keep working.
 */
public class Camera3D extends Camera {

	// Position in world units. One dungeon tile is TILE_SIZE across.
	public float eyeX = 0f;
	public float eyeY = 0f;
	public float eyeZ = 0f;

	/** Degrees. Yaw turns left/right, pitch looks up/down. */
	public float yaw = 0f;
	public float pitch = 0f;

	/** Field of view in degrees. Read as horizontal when the screen is
	 *  taller than wide, see lockHorizontalFov. In landscape this is the
	 *  vertical angle: 50 gives about 90 degrees across on a 19.5:9 screen,
	 *  which is roughly where a dungeon crawler sits. */
	public float fov = 50f;

	/**
	 * A phone held upright is a tall slot: at 9:19.5, a 75 degree vertical
	 * field of view leaves only 39 degrees horizontally, and the dungeon
	 * reads as if seen through a letterbox turned on its side. Holding the
	 * horizontal angle fixed and letting the vertical grow is the lesser
	 * evil -- though a first person crawler really wants landscape.
	 */
	public boolean lockHorizontalFov = true;
	// The surface has a 16-bit depth buffer, and precision falls off with
	// near/far ratio. 0.05/200 leaves under one depth unit of separation at
	// the fog line, which is finer than the gap that keeps a monster's feet
	// off the floor. The eye sits 1.6 above a 3 unit tile, so nothing is
	// ever this close, and fog ends well before 60.
	public float near = 0.2f;

	/**
	 * Camera whose shake this one mirrors -- normally Camera.main.
	 *
	 * The game already shakes the screen at every moment that deserves it:
	 * the Dwarf Lich appearing, Yog's fists landing, DM-300 stomping,
	 * falling down a chasm. All of it calls Camera.main.shake(), which in
	 * first person moves a camera nobody is looking through, so every one
	 * of those moments landed silently. Reading it from here means the
	 * whole game keeps working, unmodified, and the shakes come back.
	 *
	 * shakeX/shakeY are protected, and this class is in the same package,
	 * so the field is readable without touching Camera.
	 */
	public Camera shakeSource;

	/** Degrees of view rotation per pixel of 2D shake. The flat game shakes
	 *  in pixels; a first person view has to shake in angle, or a big hit
	 *  slides the world sideways instead of jolting the head. */
	public float shakeDegreesPerPixel = 0.32f;
	public float far = 60f;

	private final float[] projection = new float[16];
	private final float[] view = new float[16];

	public Camera3D( int width, int height ) {
		super( 0, 0, width, height, 1f );
	}

	public float aspect() {
		return height == 0 ? 1f : (float)width / (float)height;
	}

	/** The vertical angle actually used, after the portrait correction. */
	public float fovY() {
		float aspect = aspect();
		if (lockHorizontalFov && aspect < 1f && aspect > 0f) {
			double half = Math.toRadians( fov / 2.0 );
			return (float)(2.0 * Math.toDegrees( Math.atan( Math.tan( half ) / aspect ) ));
		}
		return fov;
	}

	@Override
	protected void updateMatrix() {

		float aspect = aspect();
		float fovY = fovY();
		Matrix.perspective( projection, fovY, aspect, near, far );

		// View is the inverse of the camera's own transform, so it is built
		// by negating everything and applying it in the opposite order.
		float shakeYaw = 0f, shakePitch = 0f;
		if (shakeSource != null) {
			shakeYaw   = shakeSource.shakeX * shakeDegreesPerPixel;
			shakePitch = shakeSource.shakeY * shakeDegreesPerPixel;
		}

		Matrix.setIdentity( view );
		Matrix.rotate3( view, -(pitch + shakePitch), 1f, 0f, 0f );
		Matrix.rotate3( view, -(yaw + shakeYaw), 0f, 1f, 0f );
		Matrix.translate3( view, -eyeX, -eyeY, -eyeZ );

		Matrix.multiply( projection, view, matrix );
	}

	/** Point the camera at a world position without moving it. */
	public void lookAt( float x, float y, float z ) {
		float dx = x - eyeX;
		float dy = y - eyeY;
		float dz = z - eyeZ;
		yaw = (float)Math.toDegrees( Math.atan2( -dx, -dz ) );
		float flat = (float)Math.sqrt( dx * dx + dz * dz );
		pitch = (float)Math.toDegrees( Math.atan2( dy, flat ) );
	}
}
