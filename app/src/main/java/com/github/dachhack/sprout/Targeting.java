/*
 * Sprouted Pixel Dungeon — first person aiming feedback
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

import com.github.dachhack.sprout.actors.mobs.Mob;
import com.github.dachhack.sprout.scenes.GameScene;
import com.watabou.noosa.Billboard;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import com.watabou.noosa.Group;
import com.watabou.noosa.Image;
import com.watabou.utils.PointF;
import com.github.dachhack.sprout.levels.Level;

/**
 * Shows what you are about to shoot.
 *
 * The flat game asks you to tap a cell and you can see the whole board, so
 * "pick a target" needs no help. In first person you are looking down a
 * corridor with no idea which of the shapes ahead the game will accept, and
 * a bow fired blind is a wasted turn. So while the game is waiting for a
 * target this rings every monster you can see and puts a mark on screen to
 * say the game is waiting at all.
 *
 * Nothing here selects anything. The tap still goes through
 * FirstPerson.screenToCell -> GameScene.handleCell exactly as before; this
 * only draws.
 */
public class Targeting {

	/** Size of the ring drawn around a target, in world units. */
	public static float ringSize = 1.1f;

	/** How far above the monster's feet the ring floats. */
	public static float ringLift = 0.15f;

	/** Size of the centre mark, as a fraction of the shorter screen side. */
	public static float markFraction = 0.035f;

	public static float opacity = 0.85f;

	private static Group world;
	private static Group hud;
	private static Image mark;
	private static Camera worldCam;

	private static final Map<Mob, Billboard> rings = new HashMap<Mob, Billboard>();

	public static void install( Group terrain, Group ui, Camera cam3d, Camera uiCam ) {

		clear();

		world = new Group();
		terrain.add( world );
		worldCam = cam3d;

		hud = new Group();
		hud.camera = uiCam;
		ui.add( hud );

		mark = new Image( HudTextures.ring() );
		mark.am = opacity;
		mark.aa = 0f;
		mark.hardlight( 1f, 0.85f, 0.3f );
		mark.visible = false;
		hud.add( mark );
	}

	public static void clear() {
		rings.clear();
		if (world != null) { world.killAndErase(); world = null; }
		if (hud != null) { hud.killAndErase(); hud = null; }
		mark = null;
		worldCam = null;
	}

	public static void update( float cameraYaw ) {

		if (world == null || hud == null) {
			return;
		}

		boolean on = GameScene.targeting() && FirstPerson.enabled
			&& Dungeon.level != null && Dungeon.hero != null;

		mark.visible = on;
		if (on) {
			placeMark();
		}

		// Drop rings whose monster died or left, always -- not only while
		// aiming -- so nothing is left hanging in the air after a kill.
		Iterator<Map.Entry<Mob, Billboard>> it = rings.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Mob, Billboard> e = it.next();
			Mob m = e.getKey();
			if (!on || !Dungeon.level.mobs.contains( m ) || !m.isAlive()
					|| m.sprite == null) {
				e.getValue().killAndErase();
				it.remove();
			}
		}

		if (!on) {
			return;
		}

		int width = Level.getWidth();
		for (Mob m : Dungeon.level.mobs) {
			if (m.sprite == null || m.pos >= Dungeon.visible.length
					|| !Dungeon.visible[m.pos]) {
				continue;
			}
			ring( m, width, cameraYaw );
		}
	}

	private static void ring( Mob m, int width, float cameraYaw ) {

		Billboard b = rings.get( m );
		if (b == null) {
			b = new Billboard( HudTextures.ring() );
			b.camera = worldCam;
			b.hardlight( 1f, 0.3f, 0.25f );
			world.add( b );
			rings.put( m, b );
		}

		b.sizeX = ringSize;
		b.sizeY = ringSize;
		b.worldX = DungeonTilemap3D.worldX( m.pos, width );
		b.worldZ = DungeonTilemap3D.worldZ( m.pos, width );
		// Above the head, so it does not sit on top of the sprite's face.
		b.worldY = Billboards.tallOf( m.sprite, Billboards.height ) + ringLift;
		b.faceYaw = cameraYaw;
	}

	private static void placeMark() {

		Camera ui = hud.camera;
		if (ui == null || ui.zoom <= 0f) {
			return;
		}

		float size = Math.min( Game.width, Game.height ) * markFraction / ui.zoom;
		float s = size * 2f / mark.texture.width;
		mark.scale.set( s, s );

		PointF c = ui.screenToCamera( Game.width / 2, Game.height / 2 );
		mark.x = c.x - size;
		mark.y = c.y - size;
	}
}
