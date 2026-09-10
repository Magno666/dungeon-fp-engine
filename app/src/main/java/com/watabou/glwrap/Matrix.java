/*
 * Copyright (C) 2012-2015 Oleg Dolya
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

package com.watabou.glwrap;

public class Matrix {

	public static final float G2RAD = 0.01745329251994329576923690768489f;
	
	public static float[] clone( float[] m ) {
		
		int n = m.length;
		float[] res = new float[n];
		do {
			res[--n] = m[n];
		} while (n > 0);
		
		return res;
	}
	
	public static void copy( float[] src, float[] dst ) {
		
		int n = src.length;
		do {
			dst[--n] = src[n];
		} while (n > 0);

	}
	
	public static void setIdentity( float[] m ) {
		for (int i=0 ; i < 16 ; i++) {
			m[i] = 0f;
		}
		for (int i = 0; i < 16; i += 5) {
			m[i] = 1f;
		}
	}

	public static void rotate( float[] m, float a ) {
		a *= G2RAD;
		float sin = (float)Math.sin( a );
		float cos = (float)Math.cos( a );
		float m0 = m[0];
		float m1 = m[1];
		float m4 = m[4];
		float m5 = m[5];
		m[0] = m0 * cos + m4 * sin;
		m[1] = m1 * cos + m5 * sin;
		m[4] = -m0 * sin + m4 * cos;
		m[5] = -m1 * sin + m5 * cos;
    }
	
	public static void skewX( float[] m, float a ) {
		double t = Math.tan( a * G2RAD );
		m[4] += -m[0] * t;
		m[5] += -m[1] * t;
    }
	
	public static void skewY( float[] m, float a ) {
		double t = Math.tan( a * G2RAD );
		m[0] += m[4] * t;
		m[1] += m[5] * t;
    }
	
	public static void scale( float[] m, float x, float y ) {
		m[0] *= x;
		m[1] *= x;
		m[2] *= x;
		m[3] *= x;
		m[4] *= y;
		m[5] *= y;
		m[6] *= y;
		m[7] *= y;
	//	android.opengl.Matrix.scaleM( m, 0, x, y, 1 );
	}
	
	public static void translate( float[] m, float x, float y ) {
		m[12] += m[0] * x + m[4] * y;
		m[13] += m[1] * x + m[5] * y;
	}
	
	/** result = left * right. Column-major, like the rest of this class. */
	public static void multiply( float[] left, float right[], float[] result ) {
		for (int c = 0; c < 4; c++) {
			for (int r = 0; r < 4; r++) {
				float sum = 0f;
				for (int k = 0; k < 4; k++) {
					sum += left[k * 4 + r] * right[c * 4 + k];
				}
				result[c * 4 + r] = sum;
			}
		}
	}

	// ------------------------------------------------------------------
	//  3D additions.
	//
	//  Noosa only ever needed 2D, but its shader already runs a full
	//  mat4 pipeline (gl_Position = uCamera * uModel * aXYZW), so a
	//  perspective camera needs no new shader -- only these operations
	//  and vertices that carry a Z. Nothing above this line changes.
	//
	//  Written in plain Java rather than delegating to android.opengl.Matrix
	//  so the camera maths can be unit tested off-device.
	// ------------------------------------------------------------------

	public static void perspective( float[] m, float fovYDegrees, float aspect,
			float near, float far ) {
		float f = (float)(1.0 / Math.tan( fovYDegrees * G2RAD / 2.0 ));
		for (int i = 0; i < 16; i++) {
			m[i] = 0f;
		}
		m[0] = f / aspect;
		m[5] = f;
		m[10] = (far + near) / (near - far);
		m[11] = -1f;
		m[14] = (2f * far * near) / (near - far);
	}

	/** m = m * R, rotating aDegrees around the given axis. */
	public static void rotate3( float[] m, float aDegrees, float x, float y, float z ) {
		float len = (float)Math.sqrt( x * x + y * y + z * z );
		if (len == 0f) {
			return;
		}
		x /= len; y /= len; z /= len;

		float a = aDegrees * G2RAD;
		float c = (float)Math.cos( a );
		float s = (float)Math.sin( a );
		float t = 1f - c;

		float[] r = new float[16];
		r[0] = t * x * x + c;
		r[1] = t * x * y + s * z;
		r[2] = t * x * z - s * y;
		r[4] = t * x * y - s * z;
		r[5] = t * y * y + c;
		r[6] = t * y * z + s * x;
		r[8] = t * x * z + s * y;
		r[9] = t * y * z - s * x;
		r[10] = t * z * z + c;
		r[15] = 1f;

		float[] out = new float[16];
		multiply( m, r, out );
		copy( out, m );
	}

	/** m = m * T. */
	public static void translate3( float[] m, float x, float y, float z ) {
		for (int i = 0; i < 4; i++) {
			m[12 + i] += m[i] * x + m[4 + i] * y + m[8 + i] * z;
		}
	}

	/** m = m * S. */
	public static void scale3( float[] m, float x, float y, float z ) {
		for (int i = 0; i < 4; i++) {
			m[i] *= x;
			m[4 + i] *= y;
			m[8 + i] *= z;
		}
	}
}
