/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * 3D mesh Visual
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import android.opengl.GLES20;

import com.watabou.gltextures.SmartTexture;

/**
 * Draws an indexed triangle mesh with depth testing, through the same
 * NoosaScript everything else uses.
 *
 * Depth testing is switched on for this draw and off again straight after,
 * so every 2D Visual in the engine -- the whole UI included -- keeps
 * behaving exactly as it did.
 */
public class Mesh3D extends Visual {

	protected SmartTexture texture;

	/** Colour the distance fades into, and where it starts and ends. */
	/** The dungeon's air, shared by every 3D mesh in it.
	 *
	 *  Static because it is one atmosphere, not a property of each mesh:
	 *  a torch that reached three cells on the floor and eight on the walls
	 *  would read as a bug. FirstPerson drives these to make the light
	 *  breathe. */
	public static float fogR = 0.035f, fogG = 0.032f, fogB = 0.045f;
	public static float fogNear = 5f, fogFar = 17f;

	/** Curve of the light falloff. See NoosaScript3D.fog. */
	public static float fogFalloff = 2.6f;

	/** Per-mesh: false draws it through everything in front of it. */
	public boolean depthTest = true;

	protected FloatBuffer verticesBuffer;
	protected ShortBuffer indicesBuffer;
	protected int indexCount;

	public Mesh3D( SmartTexture texture, float[] vertices, short[] indices ) {
		super( 0, 0, 0, 0 );

		this.texture = texture;

		verticesBuffer = ByteBuffer.
			allocateDirect( vertices.length * Float.SIZE / 8 ).
			order( ByteOrder.nativeOrder() ).
			asFloatBuffer();
		verticesBuffer.put( vertices );
		verticesBuffer.position( 0 );

		indicesBuffer = ByteBuffer.
			allocateDirect( indices.length * Short.SIZE / 8 ).
			order( ByteOrder.nativeOrder() ).
			asShortBuffer();
		indicesBuffer.put( indices );
		indicesBuffer.position( 0 );

		indexCount = indices.length;
	}

	@Override
	public void draw() {

		super.draw();

		if (indexCount == 0) {
			return;
		}

		NoosaScript3D script = NoosaScript3D.get();

		texture.bind();

		script.fog( fogR, fogG, fogB, fogNear, fogFar, fogFalloff );

		// The fog is radial now, so the shader needs the eye itself; the
		// camera matrix alone cannot give it back without an inverse.
		Camera cam = camera();
		if (cam instanceof Camera3D) {
			Camera3D c3 = (Camera3D)cam;
			script.eye( c3.eyeX, c3.eyeY, c3.eyeZ );
		}

		script.camera( cam );
		script.uModel.valueM4( matrix );
		script.lighting( rm, gm, bm, am, ra, ga, ba, aa );

		// A mesh drawn without depth testing shows through whatever is in
		// front of it. That is what Mind Vision needs: the buff marks every
		// mob in the level as seen, and without this the wall between you
		// and them wins the depth test and the whole ability does nothing.
		if (depthTest) {
			GLES20.glEnable( GLES20.GL_DEPTH_TEST );
		}

		indicesBuffer.position( 0 );
		script.drawElements3D( verticesBuffer, indicesBuffer, indexCount );

		GLES20.glDisable( GLES20.GL_DEPTH_TEST );
	}

	@Override
	public void destroy() {
		super.destroy();
		verticesBuffer = null;
		indicesBuffer = null;
		texture = null;
	}
}
