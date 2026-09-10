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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.github.dachhack.sprout.actors.Char;
import com.github.dachhack.sprout.actors.buffs.MindVision;
import com.github.dachhack.sprout.actors.blobs.Blob;
import com.github.dachhack.sprout.actors.mobs.Mob;
import com.github.dachhack.sprout.items.Heap;
import com.github.dachhack.sprout.plants.Plant;
import com.github.dachhack.sprout.levels.Level;
import com.watabou.noosa.Billboard;
import com.watabou.noosa.Group;
import com.github.dachhack.sprout.levels.Terrain;
import com.github.dachhack.sprout.actors.blobs.ConfusionGas;
import com.github.dachhack.sprout.actors.blobs.CorruptGas;
import com.github.dachhack.sprout.actors.blobs.Fire;
import com.github.dachhack.sprout.actors.blobs.GooWarn;
import com.github.dachhack.sprout.actors.blobs.ParalyticGas;
import com.github.dachhack.sprout.actors.blobs.StenchGas;
import com.github.dachhack.sprout.actors.blobs.ToxicGas;
import com.github.dachhack.sprout.actors.blobs.Web;
import com.watabou.gltextures.SmartTexture;
import com.watabou.gltextures.TextureCache;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Image;
import com.watabou.noosa.TextureFilm;
import android.graphics.RectF;

/**
 * Draws every visible monster and dropped item as a camera-facing quad.
 *
 * The sprites are the game's own, read straight off each mob's CharSprite —
 * same texture, same animation frame. Nothing about the mobs is changed or
 * even asked to change; this only looks at what the 2D layer already
 * computed and draws it standing up instead of lying flat.
 *
 * They are top-down drawings seen from the front, so they will look odd.
 * That is the point: it lets size, height and distance be judged before a
 * single new sprite is drawn.
 */
public class Billboards {

	/** How tall a monster with a reference-sized sprite stands, in world
	 *  units. TILE is 3 and the eye is at 1.6, so 2.0 was a rat as tall as
	 *  a man, filling the screen from the next cell over. */
	public static float height = 1.15f;

	/** Sprite height, in texture pixels, that `height` describes. The game
	 *  draws most mobs at 16 but not all -- a gnoll is 15, a scorpion 18,
	 *  DM-300 is 21, Yog larger still -- and that is the only size signal
	 *  the logic layer offers without being asked to change. Scaling by it
	 *  is rough, but a boss that towers over a rat beats every monster in
	 *  the dungeon standing exactly as tall as every other. */
	public static float referencePx = 16f;

	/**
	 * Cells covered by a blob get a coloured mark standing in them.
	 *
	 * Blobs are drawn by the 2D gas layer in flat map coordinates, which in
	 * first person means they appear somewhere unrelated to the danger.
	 * That is not just ugly, it breaks a boss: Goo telegraphs its charged
	 * attack by seeding GooWarn in the nine cells around itself, and the
	 * whole point is that you get one turn to step out of them. Marked in
	 * the world, that warning works again.
	 */
	public static int maxBlobMarks = 80;

	/** How tall a blob mark stands. Low enough to read as ground. */
	public static float blobMarkHeight = 0.55f;

	/** How tall a dropped item is. Loot you cannot see is loot you cannot
	 *  pick up, but an item the size of a monster reads as a monster. */
	public static float itemHeight = 0.9f;

	/** Plants sit low, and reading as ground cover is the point. */
	public static float plantHeight = 0.7f;

	/**
	 * Terrain that is a THING standing on the floor rather than the floor
	 * itself, and so has to be drawn upright.
	 *
	 * Statues, bookshelves and barricades are already SOLID, so the mesh
	 * draws them as walls and they stand up for free. These two are
	 * PASSABLE -- you walk over the cell -- so they were being painted flat
	 * into the floor: the level sign read as a decal you were standing on
	 * instead of a board at eye level.
	 */
	public static int[] upright = {
		Terrain.SIGN,
		Terrain.ALCHEMY,
		// High grass is PASSABLE | LOS_BLOCKING: you walk through it and it
		// blocks sight. The mesh classifies walls by SOLID alone, so it was
		// being painted flat and the tactic Pixel Dungeon builds on -- hide
		// in the grass, the thing hunting you loses you -- had no visual
		// meaning at all. Spotted by Normal-Insect-8220 on r/PixelDungeon.
		Terrain.HIGH_GRASS,
	};

	/** How tall those stand, in world units. */
	public static float propHeight = 1.3f;

	/** High grass stands taller, because it has to actually obscure. */
	public static float grassHeight = 1.9f;

	/** Height for one upright terrain type. */
	public static float uprightHeight( int terrain ) {
		return terrain == Terrain.HIGH_GRASS ? grassHeight : propHeight;
	}

	public static boolean isUpright( int terrain ) {
		for (int t : upright) {
			if (t == terrain) return true;
		}
		return false;
	}

	/** Lift off the floor, to stop the feet z-fighting with it. */
	public static float lift = 0.02f;

	private static Group group;
	private static final Map<Object, Billboard> boards = new HashMap<Object, Billboard>();
	private static final java.util.List<Billboard> props = new java.util.ArrayList<Billboard>();
	private static final java.util.List<Billboard> blobMarks = new java.util.ArrayList<Billboard>();

	public static void install( Group parent ) {
		clear();
		group = new Group();
		parent.add( group );
	}

	/**
	 * Stand the upright terrain up. Built once per level rather than every
	 * frame: walking 2304 cells looking for two signs is not something to
	 * do sixty times a second, and this only changes when terrain does.
	 */
	public static void installTerrain( Camera camera ) {

		if (group == null || Dungeon.level == null) {
			return;
		}

		for (Billboard b : props) {
			b.killAndErase();
		}
		props.clear();

		SmartTexture tex =
			TextureCache.get( Dungeon.level.tilesTex() );
		TextureFilm film = new TextureFilm(
			tex, DungeonTilemap.SIZE, DungeonTilemap.SIZE );

		int[] map = Dungeon.level.map;
		int width = Level.getWidth();

		for (int cell = 0; cell < map.length; cell++) {
			if (!isUpright( map[cell] )) {
				continue;
			}
			RectF r = film.get( map[cell] );
			if (r == null) {
				continue;
			}
			float tall = uprightHeight( map[cell] );
			Billboard b = new Billboard( tex );
			b.uv( r );
			b.camera = camera;
			b.sizeY = tall;
			b.sizeX = tall;
			b.worldX = DungeonTilemap3D.worldX( cell, width );
			b.worldZ = DungeonTilemap3D.worldZ( cell, width );
			b.worldY = lift;
			group.add( b );
			props.add( b );
		}
	}

	public static void clear() {
		boards.clear();
		props.clear();
		blobMarks.clear();
		if (group != null) {
			group.killAndErase();
			group = null;
		}
	}

	public static void update( float cameraYaw, Camera camera ) {

		if (group == null || Dungeon.level == null) {
			return;
		}

		int width = Level.getWidth();

		// Drop boards whose subject is gone: a killed monster, a picked up
		// item. Otherwise they would hang in the air forever.
		Iterator<Map.Entry<Object, Billboard>> it = boards.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Object, Billboard> e = it.next();
			Object key = e.getKey();
			boolean gone;
			if (key instanceof Char) {
				Char ch = (Char) key;
				gone = !Dungeon.level.mobs.contains( ch ) || !ch.isAlive() || ch.sprite == null;
			} else if (key instanceof Heap) {
				Heap heap = (Heap) key;
				gone = Dungeon.level.heaps.get( heap.pos ) != heap
					|| heap.sprite == null || heap.isEmpty();
			} else {
				Plant plant = (Plant) key;
				gone = Dungeon.level.plants.get( plant.pos ) != plant
					|| plant.sprite == null;
			}
			if (gone) {
				e.getValue().killAndErase();
				it.remove();
			}
		}

		// Iterated directly: the removal pass above already finished, and
		// nothing here mutates the collections, so the defensive copy was
		// only garbage in the hottest loop on the phone.
		boolean mindVision = Dungeon.hero != null
			&& Dungeon.hero.buff( MindVision.class ) != null;

		for (Mob mob : Dungeon.level.mobs) {
			if (mob.sprite == null || mob.sprite.texture == null) {
				continue;
			}
			Billboard b = place( mob, mob.sprite, mob.pos, height, width,
				cameraYaw, camera );

			// Standing on loot: lift the creature clear of it. A fly farmed
			// over a pile of potions used to overlap the pile into an
			// unreadable smear on one tile -- raised by Normal-Insect-8220,
			// whose suggestion this is: items stay low, creatures ride
			// above them.
			if (b != null && Dungeon.level.heaps.get( mob.pos ) != null) {
				b.worldY = lift + itemHeight * standOnLoot;
			}

			// Mind Vision marks every mob in the level as seen, walls and
			// all. Drawn normally the wall wins the depth test and the buff
			// does nothing, so anything sensed without a clear line gets
			// drawn through the stone as a faint ghost instead.
			boolean sensed = mindVision && b != null && b.visible
				&& !lineOfSight( Dungeon.hero.pos, mob.pos, width );
			if (b != null) {
				b.depthTest = !sensed;
				b.am = sensed ? ghostOpacity : 1f;
			}
		}

		for (Heap heap : Dungeon.level.heaps.values()) {
			if (heap == null || heap.sprite == null || heap.sprite.texture == null) {
				continue;
			}
			place( heap, heap.sprite, heap.pos, itemHeight, width, cameraYaw, camera );
		}

		markBlobs( width, cameraYaw, camera );

		// Terrain props face the camera like everything else.
		for (Billboard b : props) {
			b.faceYaw = cameraYaw;
		}

		for (Plant plant : Dungeon.level.plants.values()) {
			if (plant == null || plant.sprite == null || plant.sprite.texture == null) {
				continue;
			}
			place( plant, plant.sprite, plant.pos, plantHeight, width, cameraYaw, camera );
		}
	}

	/** Fraction of an item's height a creature is raised by when it stands
	 *  on a pile, so the two read as two things and not one blur. */
	public static float standOnLoot = 0.55f;

	/** How solid a monster sensed through a wall looks. */
	public static float ghostOpacity = 0.45f;

	/**
	 * Grid line of sight, using the game's own LOS_BLOCKING flag.
	 *
	 * Deliberately a fresh walk rather than reading Level.fieldOfView:
	 * MindVision overwrites that array with true for every mob, which is
	 * exactly the information this needs to distinguish.
	 */
	private static boolean lineOfSight( int from, int to, int width ) {

		int x0 = from % width, y0 = from / width;
		int x1 = to % width,   y1 = to / width;

		int dx = Math.abs( x1 - x0 ), dy = Math.abs( y1 - y0 );
		int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
		int err = dx - dy;
		int[] map = Dungeon.level.map;

		while (x0 != x1 || y0 != y1) {
			int e2 = err * 2;
			if (e2 > -dy) { err -= dy; x0 += sx; }
			if (e2 <  dx) { err += dx; y0 += sy; }
			if (x0 == x1 && y0 == y1) {
				break;
			}
			int cell = y0 * width + x0;
			if (cell < 0 || cell >= map.length
					|| (Terrain.flags[map[cell]] & Terrain.LOS_BLOCKING) != 0) {
				return false;
			}
		}
		return true;
	}

	/** Which blobs are worth marking, and in what colour. Anything not
	 *  listed is scenery -- water, foliage, wells -- and is left alone. */
	private static int blobColour( Blob blob ) {
		if (blob instanceof GooWarn)      return 0xFF3B2A;
		if (blob instanceof Fire)         return 0xFF8A1E;
		if (blob instanceof ToxicGas)     return 0x7FD13B;
		if (blob instanceof ParalyticGas) return 0xE8D24A;
		if (blob instanceof ConfusionGas) return 0xC97FE0;
		if (blob instanceof CorruptGas)   return 0x9B3BD1;
		if (blob instanceof StenchGas)    return 0xA8B03A;
		if (blob instanceof Web)          return 0xD8D8D8;
		return 0;
	}

	private static void markBlobs( int width, float cameraYaw,
			Camera camera ) {

		int used = 0;

		for (Blob blob : Dungeon.level.blobs.values()) {

			if (blob == null || blob.volume <= 0 || blob.cur == null) {
				continue;
			}
			int colour = blobColour( blob );
			if (colour == 0) {
				continue;
			}

			for (int cell = 0; cell < blob.cur.length && used < maxBlobMarks; cell++) {

				if (blob.cur[cell] <= 0
						|| cell >= Dungeon.visible.length || !Dungeon.visible[cell]) {
					continue;
				}

				Billboard b;
				if (used < blobMarks.size()) {
					b = blobMarks.get( used );
				} else {
					b = new Billboard( HudTextures.disc() );
					b.camera = camera;
					group.add( b );
					blobMarks.add( b );
				}
				used++;

				b.visible = true;
				b.hardlight( colour );
				b.am = 0.75f;
				b.sizeX = b.sizeY = blobMarkHeight;
				b.worldX = DungeonTilemap3D.worldX( cell, width );
				b.worldZ = DungeonTilemap3D.worldZ( cell, width );
				b.worldY = lift;
				b.faceYaw = cameraYaw;
			}
		}

		for (int i = used; i < blobMarks.size(); i++) {
			blobMarks.get( i ).visible = false;
		}
	}

	/** How tall this board stands. Public so the tap ray can test against
	 *  the height a thing is actually drawn at rather than assuming every
	 *  creature in the dungeon is the same size. */
	public static float tallOf( Image sprite, float base ) {
		if (sprite == null || sprite.texture == null) {
			return base;
		}
		float fh = sprite.frame().height() * sprite.texture.height;
		return fh > 0 ? base * (fh / referencePx) : base;
	}

	/**
	 * Position and size one board from the sprite the 2D layer already
	 * maintains: same texture, same animation frame, same visibility rule
	 * the flat game uses. Nothing here asks the actor or item to change.
	 */
	private static Billboard place( Object key, Image sprite, int pos,
			float tall, int width, float cameraYaw, Camera camera ) {

		Billboard b = boards.get( key );
		if (b == null) {
			b = new Billboard( sprite.texture );
			b.camera = camera;
			group.add( b );
			boards.put( key, b );
		}

		b.texture( sprite.texture );
		b.uv( sprite.frame() );

		float fw = sprite.frame().width() * sprite.texture.width;
		float fh = sprite.frame().height() * sprite.texture.height;

		// Size off the sprite the game already drew, so bigger creatures
		// stand taller, then keep the drawing's own proportions rather
		// than forcing a square.
		float h = tallOf( sprite, tall );
		b.sizeY = h;
		b.sizeX = fh > 0 ? h * (fw / fh) : h;

		b.worldX = DungeonTilemap3D.worldX( pos, width );
		b.worldZ = DungeonTilemap3D.worldZ( pos, width );
		b.worldY = lift;
		b.faceYaw = cameraYaw;
		b.visible = pos < Dungeon.visible.length && Dungeon.visible[pos];
		b.depthTest = true;
		b.am = 1f;
		return b;
	}
}
