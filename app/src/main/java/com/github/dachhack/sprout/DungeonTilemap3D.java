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

import com.github.dachhack.sprout.levels.Level;
import com.github.dachhack.sprout.levels.Terrain;
import android.graphics.RectF;

import com.watabou.gltextures.SmartTexture;
import com.watabou.noosa.Mesh3D;
import com.watabou.noosa.TextureFilm;

/**
 * The dungeon as geometry, in place of the flat DungeonTilemap.
 *
 * Reads exactly the same int[] the 2D tilemap reads — Dungeon.level.map —
 * and classifies it with the game's own Terrain flags. No level, actor or
 * item code is involved or changed; this is presentation only.
 */
public class DungeonTilemap3D extends Mesh3D {

	/** World size of one dungeon cell. */
	public static final float TILE = 3f;

	/** Floor to ceiling, in the same units. */
	public static final float HEIGHT = 3.2f;

	private DungeonTilemap3D( SmartTexture texture, GridMesh mesh ) {
		super( texture, mesh.vertices, mesh.indices );
	}

	public static DungeonTilemap3D of( int[] map, int width, SmartTexture texture,
			TextureFilm tiles, int faces ) {
		return new DungeonTilemap3D( texture, mesh( map, width, tiles, faces ) );
	}

	/** Built from the current level. GROUND or WALLS, so the two can be lit
	 *  apart -- see GridMesh for why they have to be. */
	public static DungeonTilemap3D current( SmartTexture texture, int faces ) {
		TextureFilm tiles = new TextureFilm( texture,
			DungeonTilemap.SIZE, DungeonTilemap.SIZE );
		return of( Dungeon.level.map, Level.getWidth(), texture, tiles, faces );
	}

	public static GridMesh mesh( int[] map, int width ) {
		return mesh( map, width, null );
	}

	public static GridMesh mesh( int[] map, int width, TextureFilm tiles ) {
		return mesh( map, width, tiles, GridMesh.ALL );
	}

	public static GridMesh mesh( int[] map, int width, TextureFilm tiles, int faces ) {

		boolean[] wall = new boolean[map.length];
		boolean[] water = new boolean[map.length];
		for (int cell = 0; cell < map.length; cell++) {
			wall[cell] = (Terrain.flags[map[cell]] & Terrain.SOLID) != 0;
			// Not just Terrain.WATER: the 16 tiles from WATER_TILES up are
			// the shoreline variants, and Terrain gives them all WATER's
			// flags. Every one of their frames is transparent, so asking
			// the game's own LIQUID flag catches the lot -- a hand-written
			// list of ids would have missed fifteen of them.
			water[cell] = (Terrain.flags[map[cell]] & Terrain.LIQUID) != 0;
		}

		// Without this every face samples the whole tile sheet, so each wall
		// shows the entire atlas -- bookshelves, water, grass and all. The
		// film hands back the rect for one tile, the same way the flat
		// DungeonTilemap picks its tiles.
		float[] uv = null;
		float[] ceilingUv = null;
		if (tiles != null) {
			uv = new float[map.length * 4];
			RectF fallback = tiles.get( Terrain.EMPTY );
			for (int cell = 0; cell < map.length; cell++) {
				// Terrain constants run to 75 but tiles0.png only holds 64
				// frames, so the Sokoban terrain returns null here. Without
				// this the mesh build throws on those levels.
				// Water's own frame is transparent -- the flat game shows the
				// animated water layer through it, and first person hides
				// that layer. Give those cells a solid tile; the WATER mesh
				// colours them separately.
				// Upright props (signs, alchemy pots) are drawn as standing
				// billboards by Billboards.installTerrain, so the floor
				// under them gets a plain tile instead of the same picture
				// painted flat -- otherwise the sign appears twice, once
				// standing and once as a decal you walk on.
				RectF r = water[cell] || Billboards.isUpright( map[cell] )
					? fallback : tiles.get( map[cell] );
				if (r == null) {
					r = fallback;
				}
				if (r == null) {
					continue;
				}
				uv[cell * 4]     = r.left;
				uv[cell * 4 + 1] = r.top;
				uv[cell * 4 + 2] = r.right;
				uv[cell * 4 + 3] = r.bottom;
			}
			RectF c = tiles.get( Terrain.WALL );
			ceilingUv = new float[] { c.left, c.top, c.right, c.bottom };
		}

		return GridMesh.build( wall, width, TILE, HEIGHT, true, uv, ceilingUv, faces, water );
	}

	/** Centre of a cell in world units, for placing the camera and actors. */
	public static float worldX( int cell, int width ) {
		return (cell % width) * TILE;
	}

	public static float worldZ( int cell, int width ) {
		return (cell / width) * TILE;
	}
}
