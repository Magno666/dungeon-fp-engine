/*
 * Sprouted Pixel Dungeon — first person renderer
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
package com.github.dachhack.sprout;

/**
 * Turns a dungeon grid into a triangle mesh for the first person view.
 *
 * Deliberately knows nothing about Dungeon, Level, Terrain, Android or GL:
 * it takes a plain "is this cell a wall" array and returns plain arrays.
 * That is what lets it be tested on a desktop, which matters because the
 * build machine cannot run the game.
 *
 * Vertex layout is x, y, z, u, v — stride 5 — matching
 * NoosaScript.drawElements3D().
 *
 * Axes follow the game grid: +x is east, +z is south, +y is up. Cell
 * (col, row) sits at (col * tile, 0, row * tile).
 */
public class GridMesh {

	public static final int STRIDE = 5;
	private static final int VERTS_PER_QUAD = 4;
	private static final int INDICES_PER_QUAD = 6;

	/** Interleaved x,y,z,u,v. */
	public float[] vertices;
	public short[] indices;
	public int quads;

	private int v;   // write cursor into vertices
	private int i;   // write cursor into indices
	private float[] uv = { 0f, 0f, 1f, 1f };   // rect for the quad being written

	/**
	 * @param wall     one entry per cell, row-major, true where solid
	 * @param width    cells per row
	 * @param tile     world size of one cell
	 * @param height   wall/ceiling height
	 * @param ceiling  emit a ceiling over every open cell
	 * @param uv       four floats per cell (u0,v0,u1,v1) picking that cell's
	 *                 tile out of the atlas, or null for the whole texture
	 * @param ceilingUv four floats used for every ceiling, or null
	 */
	public static GridMesh build( boolean[] wall, int width, float tile,
			float height, boolean ceiling ) {
		return build( wall, width, tile, height, ceiling, null, null );
	}

	/** Which faces to emit. Walls and floors are drawn separately so they
	 *  can be lit differently: Pixel Dungeon's floor tile is 27% luminance
	 *  against a 61% wall, which reads fine at 16px from above and reads as
	 *  a glowing wall over a black void in first person. */
	public static final int GROUND = 1;

	/** Wall faces that look north or south. Split from the east-west ones
	 *  so the two can be lit apart: every face in this mesh shares one
	 *  brightness, and with a single WALLS mesh a corner had none. Two
	 *  perpendicular walls came out the same colour, so up close a wall
	 *  filled the screen as a flat rectangle with no shape to read. This
	 *  is Minecraft's trick -- a fixed multiplier per face orientation --
	 *  and it costs one extra draw call, not a shader. */
	public static final int WALLS_NS = 2;

	/** The ceiling is its own face for the same reason, one step further:
	 *  it is drawn with the WALL tile, and at the floor's brightness that
	 *  tile blows out to flat white -- 0.65 luminance times 1.85 is 1.20,
	 *  clipped. A dungeon should not have a lightbox for a roof. */
	public static final int CEILING = 4;

	/** Water is its own face because the flat game does not draw it with a
	 *  tile at all: Terrain.WATER's frame in the sheet is fully
	 *  TRANSPARENT, a hole for the animated water layer underneath to show
	 *  through. First person hides that layer, so those cells came out as
	 *  holes in the floor -- roughly a hundred per level. Split out, they
	 *  can be given a solid tile and their own colour. */
	public static final int WATER = 8;

	/** Wall faces that look east or west. See {@link #WALLS_NS}. */
	public static final int WALLS_EW = 16;

	/** Both wall orientations. Callers that do not care keep working. */
	public static final int WALLS = WALLS_NS | WALLS_EW;

	public static final int ALL = GROUND | WALLS | CEILING | WATER;

	public static GridMesh build( boolean[] wall, int width, float tile,
			float height, boolean ceiling, float[] uv, float[] ceilingUv ) {
		return build( wall, width, tile, height, ceiling, uv, ceilingUv, ALL );
	}

	public static GridMesh build( boolean[] wall, int width, float tile,
			float height, boolean ceiling, float[] uv, float[] ceilingUv, int faces ) {
		return build( wall, width, tile, height, ceiling, uv, ceilingUv, faces, null );
	}

	/** @param water cells to emit as WATER instead of GROUND, or null. */
	public static GridMesh build( boolean[] wall, int width, float tile,
			float height, boolean ceiling, float[] uv, float[] ceilingUv, int faces,
			boolean[] water ) {

		int rows = wall.length / width;
		GridMesh m = new GridMesh();

		// Count first so the arrays are allocated exactly once. A 48x48
		// level is a few thousand quads and this runs on level load.
		int count = 0;
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < width; col++) {
				if (wall[row * width + col]) {
					if ((faces & WALLS) != 0) {
						count += exposedFaces( wall, width, rows, col, row, faces );
					}
				} else {
					int floorFace = water != null && water[row * width + col]
						? WATER : GROUND;
					if ((faces & floorFace) != 0) {
						count++;
					}
					if (ceiling && (faces & CEILING) != 0) {
						count++;
					}
				}
			}
		}

		if (count * VERTS_PER_QUAD > 65535) {
			throw new IllegalStateException(
				"grid needs " + count + " quads, past the 16-bit index limit" );
		}

		m.quads = count;
		m.vertices = new float[count * VERTS_PER_QUAD * STRIDE];
		m.indices = new short[count * INDICES_PER_QUAD];

		float h = tile / 2f;
		float[] whole = { 0f, 0f, 1f, 1f };
		float[] ceil = ceilingUv != null ? ceilingUv : whole;

		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < width; col++) {
				float cx = col * tile;
				float cz = row * tile;
				int cell = row * width + col;
				float[] t = whole;
				if (uv != null) {
					t = new float[] { uv[cell * 4], uv[cell * 4 + 1],
					                  uv[cell * 4 + 2], uv[cell * 4 + 3] };
				}
				m.uv = t;

				if (!wall[row * width + col]) {
					int floorFace = water != null && water[cell] ? WATER : GROUND;
					if ((faces & floorFace) != 0) {
						// floor, seen from above
						m.quad( cx - h, 0f, cz + h,  cx + h, 0f, cz + h,
								cx + h, 0f, cz - h,  cx - h, 0f, cz - h );
					}
					if (ceiling && (faces & CEILING) != 0) {
						m.uv = ceil;
						m.quad( cx - h, height, cz - h,  cx + h, height, cz - h,
								cx + h, height, cz + h,  cx - h, height, cz + h );
						m.uv = t;
					}
					continue;
				}

				if ((faces & WALLS) == 0) {
					continue;
				}

				// Only the sides of a wall that face open space are drawn.
				// Everything inside a block of rock is never visible, and
				// skipping it is most of the reason this stays cheap.
				if ((faces & WALLS_NS) != 0
						&& open( wall, width, rows, col, row - 1 )) {  // north
					m.quad( cx - h, height, cz - h,  cx + h, height, cz - h,
							cx + h, 0f,     cz - h,  cx - h, 0f,     cz - h );
				}
				if ((faces & WALLS_NS) != 0
						&& open( wall, width, rows, col, row + 1 )) {  // south
					m.quad( cx + h, height, cz + h,  cx - h, height, cz + h,
							cx - h, 0f,     cz + h,  cx + h, 0f,     cz + h );
				}
				if ((faces & WALLS_EW) != 0
						&& open( wall, width, rows, col - 1, row )) {  // west
					m.quad( cx - h, height, cz + h,  cx - h, height, cz - h,
							cx - h, 0f,     cz - h,  cx - h, 0f,     cz + h );
				}
				if ((faces & WALLS_EW) != 0
						&& open( wall, width, rows, col + 1, row )) {  // east
					m.quad( cx + h, height, cz - h,  cx + h, height, cz + h,
							cx + h, 0f,     cz + h,  cx + h, 0f,     cz - h );
				}
			}
		}

		return m;
	}

	private static boolean open( boolean[] wall, int width, int rows, int col, int row ) {
		if (col < 0 || col >= width || row < 0 || row >= rows) {
			return false;
		}
		return !wall[row * width + col];
	}

	/** Counts only the orientations {@code faces} asks for, so the arrays
	 *  are still allocated exactly once when walls are built in two passes. */
	private static int exposedFaces( boolean[] wall, int width, int rows, int col,
			int row, int faces ) {
		int n = 0;
		if ((faces & WALLS_NS) != 0) {
			if (open( wall, width, rows, col, row - 1 )) n++;
			if (open( wall, width, rows, col, row + 1 )) n++;
		}
		if ((faces & WALLS_EW) != 0) {
			if (open( wall, width, rows, col - 1, row )) n++;
			if (open( wall, width, rows, col + 1, row )) n++;
		}
		return n;
	}

	/** Four corners in order, wound so the face points at the viewer. */
	private void quad( float x0, float y0, float z0, float x1, float y1, float z1,
			float x2, float y2, float z2, float x3, float y3, float z3 ) {

		short base = (short)(v / STRIDE);

		vertex( x0, y0, z0, uv[0], uv[1] );
		vertex( x1, y1, z1, uv[2], uv[1] );
		vertex( x2, y2, z2, uv[2], uv[3] );
		vertex( x3, y3, z3, uv[0], uv[3] );

		indices[i++] = base;
		indices[i++] = (short)(base + 1);
		indices[i++] = (short)(base + 2);
		indices[i++] = base;
		indices[i++] = (short)(base + 2);
		indices[i++] = (short)(base + 3);
	}

	private void vertex( float x, float y, float z, float u, float w ) {
		vertices[v++] = x;
		vertices[v++] = y;
		vertices[v++] = z;
		vertices[v++] = u;
		vertices[v++] = w;
	}
}
