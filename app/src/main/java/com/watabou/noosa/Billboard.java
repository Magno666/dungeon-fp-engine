/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Camera-facing sprite for the first person view
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

import android.graphics.RectF;

import com.watabou.glwrap.Matrix;

/**
 * A flat quad that always turns to face the camera.
 *
 * This is how Doom drew every monster in 1993, and it is the right answer
 * here for the same reason: the art stays a drawing rather than a model,
 * and one image serves from every angle.
 *
 * The mesh itself is a unit square standing on the floor; where it goes
 * and how big it is live in the model matrix, so the vertex buffer is
 * only rewritten when the animation frame changes.
 */
public class Billboard extends Mesh3D {

	/** Where it stands, in world units. y is the base, not the centre. */
	public float worldX, worldY, worldZ;

	/** Size in world units. */
	public float sizeX = 1f, sizeY = 1f;

	/** Set to the camera's yaw each frame so the quad turns to face it. */
	public float faceYaw = 0f;

	private static final float[] UNIT_QUAD = {
		// x, y, z, u, v — a square standing on y=0, facing -Z
		-0.5f, 1f, 0f,  0f, 0f,
		 0.5f, 1f, 0f,  1f, 0f,
		 0.5f, 0f, 0f,  1f, 1f,
		-0.5f, 0f, 0f,  0f, 1f,
	};

	private static final short[] QUAD_INDICES = { 0, 1, 2, 0, 2, 3 };

	/**
	 * Two squares crossed at a right angle, both standing on y=0. For
	 * foliage: a single quad that swivels to face the camera reads as a
	 * flat card turning in place -- Leonel: "el que está levantado se
	 * mira como si fuera un cartón ahí nomás". A cross never turns, and
	 * from any angle one face is lit while the other shows its edge, so
	 * the tuft has depth. The art is untouched; only the geometry
	 * changed. Same trick Minecraft uses for grass and flowers.
	 */
	private static final float[] UNIT_CROSS = {
		// first blade, facing -Z
		-0.5f, 1f,  0f,   0f, 0f,
		 0.5f, 1f,  0f,   1f, 0f,
		 0.5f, 0f,  0f,   1f, 1f,
		-0.5f, 0f,  0f,   0f, 1f,
		// second blade, square to it, facing -X
		 0f,   1f, -0.5f, 0f, 0f,
		 0f,   1f,  0.5f, 1f, 0f,
		 0f,   0f,  0.5f, 1f, 1f,
		 0f,   0f, -0.5f, 0f, 1f,
	};

	private static final short[] CROSS_INDICES = {
		0, 1, 2, 0, 2, 3,
		4, 5, 6, 4, 6, 7,
	};

	/** True for the cross, which stands still instead of facing the camera. */
	private final boolean cruzado;

	public Billboard( com.watabou.gltextures.SmartTexture texture ) {
		this( texture, false );
	}

	public Billboard( com.watabou.gltextures.SmartTexture texture, boolean cruzado ) {
		super( texture,
			(cruzado ? UNIT_CROSS : UNIT_QUAD).clone(),
			(cruzado ? CROSS_INDICES : QUAD_INDICES).clone() );
		this.cruzado = cruzado;
	}

	public void texture( com.watabou.gltextures.SmartTexture texture ) {
		this.texture = texture;
	}

	/** Point at one frame of the source sprite sheet. */
	public void uv( RectF frame ) {
		int caras = cruzado ? 2 : 1;
		for (int cara = 0; cara < caras; cara++) {
			int base = cara * 20;   // 4 vertices x 5 floats
			verticesBuffer.position( base + 3 );
			verticesBuffer.put( frame.left );  verticesBuffer.put( frame.top );
			verticesBuffer.position( base + 8 );
			verticesBuffer.put( frame.right ); verticesBuffer.put( frame.top );
			verticesBuffer.position( base + 13 );
			verticesBuffer.put( frame.right ); verticesBuffer.put( frame.bottom );
			verticesBuffer.position( base + 18 );
			verticesBuffer.put( frame.left );  verticesBuffer.put( frame.bottom );
		}
		verticesBuffer.position( 0 );
	}

	@Override
	protected void updateMatrix() {
		Matrix.setIdentity( matrix );
		Matrix.translate3( matrix, worldX, worldY, worldZ );
		// La cruz no gira: girarla la delataria igual que al cuadro suelto.
		if (!cruzado) {
			Matrix.rotate3( matrix, faceYaw, 0f, 1f, 0f );
		}
		Matrix.scale3( matrix, sizeX, sizeY, sizeX );
	}
}
