/*
 * Sprouted Pixel Dungeon — first person minimap
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
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import com.watabou.noosa.Group;
import com.github.dachhack.sprout.scenes.PixelScene;
import com.watabou.noosa.Image;
import com.watabou.utils.PointF;

/**
 * The top-down map, back as a corner window.
 *
 * Pixel Dungeon never needed one: its game view *was* the map, and going
 * first person is what took that away and left a maze. This puts it back
 * by pointing a second, small camera at the game's own DungeonTilemap and
 * FogOfWar — so it is not an approximation of the map, it is the map,
 * with the same tiles and the same explored/remembered shading the flat
 * game draws. Level.visited is filled every turn by Dungeon regardless of
 * who is looking at it.
 */
public class Minimap {

	/** Fraction of the shorter screen side the window takes. */
	public static float sizeFraction = 0.34f;

	/** Distance from the right edge and from what sits above, in pixels. */
	public static float marginDp = 5.33f;

	public static float marginPx() {
		return marginDp * Math.max( 1f, Game.density );
	}

	/** Height of StatusPane, in UI units. The menu button lives in its
	 *  top-right corner, which is exactly where a corner minimap wants to
	 *  go, so the map starts below the bar instead of burying it. */
	public static float statusBarUi = 34f;

	/** Size of the heading arrow, in map tiles. */
	public static float markerTiles = 1.5f;

	/** How many dungeon tiles fit across the window. */
	public static float tilesAcross = 15f;

	public static boolean visible = true;

	/** Tap the map to swap between the corner window and a full-screen
	 *  one. A corner map shows fifteen tiles, which is enough to know
	 *  which way the corridor bends and nowhere near enough to find the
	 *  stairs -- and the stairs are the one thing a first person crawler
	 *  cannot show you from where you stand. */
	public static boolean expanded = false;

	/** Fraction of the screen the expanded map takes. */
	public static float expandedFraction = 0.92f;

	/** Where the window ended up, in screen pixels, for hit testing. */
	private static float winX, winY, winSide;

	/** True if a screen point falls inside the map window. */
	public static boolean hit( float x, float y ) {
		return visible && cam != null
			&& x >= winX && x <= winX + winSide
			&& y >= winY && y <= winY + winSide;
	}

	/**
	 * The dungeon cell under a screen point, or -1 if that point is not on
	 * a cell the hero has seen.
	 *
	 * Only explored cells count. Handing back an unseen one would let the
	 * map walk the hero into rooms he has no business knowing about, and
	 * the mask already blanks them, so the player would be tapping black.
	 */
	public static int cellAt( float sx, float sy ) {

		if (cam == null || Dungeon.level == null || !hit( sx, sy )) {
			return -1;
		}

		PointF p = cam.screenToCamera( (int)sx, (int)sy );
		int col = (int)Math.floor( p.x / DungeonTilemap.SIZE );
		int row = (int)Math.floor( p.y / DungeonTilemap.SIZE );

		int width = Level.getWidth();
		int rows = Dungeon.level.map.length / width;
		if (col < 0 || col >= width || row < 0 || row >= rows) {
			return -1;
		}

		int cell = row * width + col;
		boolean seen = (Dungeon.level.visited != null && Dungeon.level.visited[cell])
			|| (Dungeon.level.mapped != null && Dungeon.level.mapped[cell]);
		return seen ? cell : -1;
	}

	/** Swap between corner and full screen. Rebuilds, because the camera's
	 *  size and zoom are set when it is made. */
	public static void toggle() {
		if (lastParent == null) {
			return;
		}
		expanded = !expanded;
		install( lastParent );
	}

	/** Whoever installed us last, so a tap can rebuild without being
	 *  handed the scene graph from the input layer. */
	private static Group lastParent;

	private static Camera cam;
	private static Group group;
	private static DungeonTilemap tiles;
	private static Image marker;

	/** Stair markers, drawn only on the expanded map. Without them the
	 *  stairs are one tile among thousands and the map answers "where am
	 *  I" but not "where do I go", which is the question you opened it
	 *  for. Only shown once the cell is known, same rule as everything
	 *  else on this map. */
	private static Image downMark, upMark;

	/** A copy of the level map with everything unexplored blanked out. */
	private static int[] masked;
	private static boolean uploaded;

	public static void install( Group parent ) {

		lastParent = parent;
		clear();

		if (!visible || !FirstPerson.enabled || Dungeon.level == null) {
			return;
		}

		int m = (int)marginPx();
		float uiZoom = PixelScene.uiCamera != null
			? PixelScene.uiCamera.zoom : 1f;

		int side;
		int left, top;
		float zoom;

		if (expanded) {
			// Big enough to hold the whole floor. Sprouted's levels run
			// large, so the zoom comes from the level's own width rather
			// than a fixed tile count -- a 48 wide map and an 80 wide one
			// both have to fit.
			side = (int)(Math.min( Game.width, Game.height ) * expandedFraction);
			int rows = Dungeon.level.map.length / Level.getWidth();
			int span = Math.max( Level.getWidth(), rows );
			zoom = side / (float)(span * DungeonTilemap.SIZE);
			left = (Game.width - side) / 2;
			top = (Game.height - side) / 2;
		} else {
			side = (int)(Math.min( Game.width, Game.height ) * sizeFraction);
			zoom = side / (tilesAcross * DungeonTilemap.SIZE);
			left = Game.width - side - m;
			top = (int)(statusBarUi * uiZoom) + m;
		}

		winX = left; winY = top; winSide = side;

		cam = new Camera( left, top,
			(int)(side / zoom), (int)(side / zoom), zoom );
		Camera.add( cam );

		group = new Group();
		group.camera = cam;
		parent.add( group );

		// Note: DungeonTilemap's constructor assigns a private static
		// `instance`, so this second one takes it over from the full-screen
		// tilemap. Harmless here -- `instance` is only read by tile(index)
		// to clone a tile for the discover animation, and both tilemaps
		// share the level's texture and tileset -- but it is why a third
		// one should not be added casually.
		tiles = new DungeonTilemap();
		// Tilemap.draw() reads the `camera` FIELD, not the camera() method,
		// so unlike every Image it does not inherit the group's camera --
		// a null field silently falls back to Camera.main. That is how the
		// minimap came out drawn at the world camera's zoom across the
		// right half of the screen, painting over the 3D view. It has to be
		// set on the tilemap itself.
		tiles.camera = cam;
		group.add( tiles );

		// No second FogOfWar here. Its texture registers itself in
		// TextureCache under the single key FogOfWar.class, so a second
		// instance replaces the first and one of the two GL textures is
		// never freed -- a leak on every level change. Blanking the
		// unexplored cells in a copy of the map does the same job with
		// no extra texture at all.
		masked = new int[Dungeon.level.map.length];
		remask();

		// An arrow, not a dot. On a first person map the one thing you
		// cannot work out from the walls is which way you are facing, and a
		// 3px square answered neither question -- it was too small to find
		// and had no heading to give.
		marker = new Image( HudTextures.arrow() );
		marker.camera = cam;
		float size = markerTiles * DungeonTilemap.SIZE;
		float s = size / marker.texture.width;
		marker.scale.set( s, s );
		// Rotate about the middle: origin is applied unscaled on both sides
		// of the rotation, so the centre lands on x + width/2 whatever the
		// scale is.
		marker.origin.set( marker.texture.width / 2f, marker.texture.height / 2f );
		marker.hardlight( 1f, 0.87f, 0.27f );
		group.add( marker );

		if (expanded) {
			downMark = stairMark( Dungeon.level.exit,     0.40f, 1f, 0.45f );
			upMark   = stairMark( Dungeon.level.entrance, 0.55f, 0.70f, 1f );
		}

		update();
	}

	public static void update() {

		if (cam == null || Dungeon.hero == null) {
			return;
		}

		float x = DungeonTilemap.tileToWorld( Dungeon.hero.pos ).x;
		float y = DungeonTilemap.tileToWorld( Dungeon.hero.pos ).y;

		if (expanded) {
			// Hold the whole floor still. Following the hero here would put
			// him in the middle of a window that is already showing every
			// room, and push half the level off the edge for no reason.
			int rows = Dungeon.level.map.length / Level.getWidth();
			cam.focusOn( Level.getWidth() * DungeonTilemap.SIZE / 2f,
			             rows * DungeonTilemap.SIZE / 2f );
		} else {
			cam.focusOn( x + DungeonTilemap.SIZE / 2f, y + DungeonTilemap.SIZE / 2f );
		}

		marker.x = x + DungeonTilemap.SIZE / 2f - marker.texture.width / 2f;
		marker.y = y + DungeonTilemap.SIZE / 2f - marker.texture.height / 2f;

		// The arrow is drawn pointing at -Y, which is north on the map, and
		// Noosa's rotation is clockwise on screen while yaw counts the other
		// way -- yaw 0 is north, -90 is east. Hence the negation.
		marker.angle = -FirstPerson.yaw;
	}

	/** A dot on one cell, or null if the hero has not seen that cell yet. */
	private static Image stairMark( int cell, float r, float g, float b ) {

		if (cell < 0 || cell >= Dungeon.level.map.length) {
			return null;
		}
		boolean seen = (Dungeon.level.visited != null && Dungeon.level.visited[cell])
			|| (Dungeon.level.mapped != null && Dungeon.level.mapped[cell]);
		if (!seen) {
			return null;
		}

		Image dot = new Image( HudTextures.disc() );
		dot.camera = cam;
		float size = DungeonTilemap.SIZE * 3f;
		float sc = size / dot.texture.width;
		dot.scale.set( sc, sc );
		PointF at = DungeonTilemap.tileToWorld( cell );
		dot.x = at.x + DungeonTilemap.SIZE / 2f - size / 2f;
		dot.y = at.y + DungeonTilemap.SIZE / 2f - size / 2f;
		dot.hardlight( r, g, b );
		group.add( dot );
		return dot;
	}

	/** Called when the terrain or what the hero can see changes. */
	public static void refresh() {
		remask();
	}

	private static void remask() {

		if (tiles == null || masked == null || Dungeon.level == null) {
			return;
		}

		int[] real = Dungeon.level.map;
		boolean[] visited = Dungeon.level.visited;
		boolean[] mapped = Dungeon.level.mapped;

		boolean changed = false;
		for (int c = 0; c < masked.length; c++) {
			boolean known = (visited != null && c < visited.length && visited[c])
				|| (mapped != null && c < mapped.length && mapped[c]);
			int want = known ? real[c] : Terrain.CHASM;
			if (masked[c] != want) {
				masked[c] = want;
				changed = true;
			}
		}

		// Tilemap.map() reallocates the whole quad set, and this runs on
		// every visibility change -- so only pay for it when a cell really
		// moved between explored and unexplored.
		if (changed || !uploaded) {
			tiles.map( masked, Level.getWidth() );
			uploaded = true;
		}
	}

	public static void clear() {

		downMark = null;
		upMark = null;

		if (cam != null) {
			Camera.remove( cam );
			cam = null;
		}
		if (group != null) {
			group.killAndErase();
			group = null;
		}
		tiles = null;
		marker = null;
		masked = null;
		uploaded = false;
	}
}
