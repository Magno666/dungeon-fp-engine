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

import com.github.dachhack.sprout.actors.buffs.Vertigo;
import com.github.dachhack.sprout.levels.Level;
import com.watabou.gltextures.SmartTexture;
import com.watabou.gltextures.TextureCache;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Camera3D;
import com.watabou.noosa.Game;
import com.watabou.noosa.Group;
import com.github.dachhack.sprout.scenes.PixelScene;
import com.github.dachhack.sprout.actors.mobs.Mob;
import com.watabou.noosa.Mesh3D;
import com.watabou.utils.PointF;

/**
 * Owns the first person view, so GameScene needs only three lines of hook.
 *
 * {@link #enabled} switches the whole thing. With it false the game renders
 * exactly as it always has — flat tilemap, fog of war, 2D sprites — which
 * keeps the original build one boolean away. It currently ships ON.
 *
 * Facing lives here rather than on the Hero on purpose. Char has no notion
 * of direction — turnTo() only flips a sprite horizontally — so orientation
 * is presentation state and never enters the logic layer.
 */
public class FirstPerson {

	public static boolean enabled = true;

	/** Camera height above the floor, in world units. TILE is 3. */
	public static float eyeHeight = 1.6f;

	/** Vertical travel of the head while walking, in world units. Starts
	 *  deliberately very low: a little sells the walk, a lot is
	 *  what makes people put the phone down. It rides on how far the eye
	 *  still is from the cell it is heading to, so it settles by itself
	 *  when you stop instead of needing its own timer. */
	/** Cuanto se mece la cabeza de lado a lado al caminar, en unidades de
	 *  mundo. Coto Vedado lo tiene en 0.022 caminando, ya ajustado a mano.
	 *  Va a media frecuencia del cabeceo vertical, que es lo que convierte
	 *  dos subidas y bajadas en el ocho de una zancada. */
	public static float swayAmplitude = 0.022f;

	/** Cuantos grados se mece la vista mientras dura el Vertigo. Solo
	 *  afecta a la camara: la direccion logica no se toca. */
	public static float vertigoSway = 7f;

	private static float vertigoPhase = 0f;

	public static float headBobAmplitude = 0.040f;

	/** Bobs per cell walked. Two is one dip per footfall. */
	public static float headBobCycles = 2f;

	private static float bobPhase = 0f;

	/** How far the torch reaches, in world units. TILE is 3, so 17 is a bit
	 *  under six cells. The old 26 lit most of a room flat, which reads as
	 *  a corridor with a texture rather than a dungeon: the dark closing in
	 *  is most of what makes a first person crawler feel like one. */
	public static float torchRange = 17f;

	/** Where the falloff starts. Below this the light is at full strength. */
	public static float torchNear = 5f;

	/** Curve of the light falloff; Feel owns the value. */
	public static float torchFalloff = 2.6f;

	/** How far the reach wanders as the flame gutters, in world units. Set
	 *  to 0 for a steady lamp. */
	public static float torchFlicker = 1.3f;

	private static float flickerPhase = 0f;

	/** Kick given to the view when the hero is hit. Shake magnitude is in
	 *  the flat game's pixels; Camera3D turns it into degrees. */
	public static float hurtShake = 3.5f;
	public static float hurtShakeSeconds = 0.28f;

	/**
	 * Where a point in the dungeon lands on screen, in UI camera units.
	 *
	 * Damage numbers and alert icons are 2D text positioned in flat map
	 * coordinates, which in first person put them nowhere near the monster
	 * they belong to -- so they were hidden. Projecting the monster's cell
	 * through the same matrix the world is drawn with puts them back over
	 * the right creature, as flat screen text, which is what a 3D game
	 * does with damage numbers anyway.
	 *
	 * @return null when the point is behind the camera or off the map
	 */
	public static PointF projectToUi( int cell, float worldY ) {

		if (!enabled || camera == null || Dungeon.level == null
				|| cell < 0 || cell >= Dungeon.level.map.length) {
			return null;
		}
		Camera ui =
			PixelScene.uiCamera;
		if (ui == null) {
			return null;
		}

		int width = Level.getWidth();
		float wx = DungeonTilemap3D.worldX( cell, width );
		float wz = DungeonTilemap3D.worldZ( cell, width );

		float[] m = camera.matrix;
		float cw = m[3] * wx + m[7] * worldY + m[11] * wz + m[15];
		if (cw <= 0.05f) {
			return null;                      // behind the eye
		}
		float cx = m[0] * wx + m[4] * worldY + m[8]  * wz + m[12];
		float cy = m[1] * wx + m[5] * worldY + m[9]  * wz + m[13];

		int sx = (int)((cx / cw * 0.5f + 0.5f) * Game.width);
		int sy = (int)((1f - (cy / cw * 0.5f + 0.5f)) * Game.height);

		return ui.screenToCamera( sx, sy );
	}

	// ---- turning to face something ------------------------------------

	/** Degrees per second the view swings when the hero acts on a target. */
	public static float aimTurnSpeed = 480f;

	private static float aimYaw;
	private static boolean aiming;

	/**
	 * Swing the view to look at a cell.
	 *
	 * Hooked into CharSprite.turnTo, which the game already calls whenever
	 * the hero attacks, throws or zaps. The heading otherwise only follows
	 * MOVEMENT, and attacking does not move you -- so fighting with the
	 * attack button left the camera pointing wherever you last walked.
	 * Verified on Goo in the emulator: fourteen attacks facing a wall, Goo
	 * charged its area attack, and not one of the red warning tiles was on
	 * screen. The telegraph existed only in the log.
	 */
	public static void faceCell( int cell ) {

		if (!enabled || Dungeon.hero == null || Dungeon.level == null
				|| cell < 0 || cell >= Dungeon.level.map.length) {
			return;
		}
		int width = Level.getWidth();
		int dx = (cell % width) - (Dungeon.hero.pos % width);
		int dz = (cell / width) - (Dungeon.hero.pos / width);
		if (dx == 0 && dz == 0) {
			return;
		}
		aimYaw = (float)Math.toDegrees( Math.atan2( -dx, -dz ) );
		aiming = true;
	}

	/** Let go the moment the player looks around: their thumb wins. */
	public static void cancelAim() {
		// Si el jugador mueve la vista, manda el: se suelta tambien el
		// enderezado del camino.
		caminoPedido = false;
		aiming = false;
	}

	/** Called from CharSprite.flash when the hero takes a hit. */
	public static void hurt() {
		if (!enabled || Camera.main == null) {
			return;
		}
		// Routed through Camera.main because that is the camera the whole
		// game already shakes, and Camera3D mirrors it.
		Camera.main.shake( hurtShake, hurtShakeSeconds );
	}

	/** Build marker, printed to the game log so a screenshot says which
	 *  version it came from. */
	public static final String BUILD = "FP build v33";

	/** Degrees. 0 looks north (-Z), increasing turns west. */
	public static float yaw = 0f;

	/** Slightly downward, the way you actually walk a corridor. It also
	 *  puts more floor on screen, and the floor is what taps land on. */
	public static float pitch = -10f;

	/** Degrees per second the view swings round to a new heading. Only used
	 *  when headingFollowsMovement is on. */
	public static float turnSpeed = 540f;

	/**
	 * Turn the view towards whichever way the last step went. That was a
	 * stand-in for having no controls; with a stick and free look the
	 * player aims the view themselves, and this only fights them.
	 */
	public static boolean headingFollowsMovement = false;

	/**
	 * Enderezar la vista hacia donde caminas, pero SOLO cuando el camino lo
	 * pediste tocando una casilla.
	 *
	 * Pedido en reddit: "if I click on the floor the camera should
	 * automatically align perpendicular to the walls in the direction I am
	 * going". Al tocar una casilla lejana el heroe recorre el camino solo y
	 * la camara se queda donde la dejaste, asi que acabas bajando el pasillo
	 * de lado o de espaldas.
	 *
	 * No es lo mismo que headingFollowsMovement, que gira con CUALQUIER
	 * paso: eso le quitaria la camara de las manos a quien se mueve con el
	 * stick, y mirar tiene que seguir siendo libre. La diferencia esta en
	 * quien pidio el movimiento, no en que hubo movimiento.
	 */
	public static boolean enderezarAlCaminar = true;

	private static boolean caminoPedido = false;

	/** El jugador toco una casilla para ir hasta ella. */
	public static void caminoPorToque() {
		caminoPedido = true;
	}

	/** El paso lo dio el stick o el teclado: la vista no se toca. */
	public static void caminoPorMando() {
		caminoPedido = false;
	}

	/**
	 * Seconds to slide from one cell to the next. The logic moves the hero
	 * instantly; only the camera eases, so nothing about turn order changes.
	 * 0.18 is the value the Godot spike settled on.
	 *
	 * Keep FirstPersonControls.repeatMs at or below this. The whole illusion
	 * of walking in a turn based game is that the eye is still travelling
	 * when the next turn is taken, so it never comes to rest.
	 */
	public static float stepSeconds = 0.18f;

	/**
	 * Curva del desplazamiento. En false vuelve al suavizado exponencial
	 * de antes.
	 *
	 * El exponencial arranca de golpe: con el ritmo de cuadro tipico,
	 * k = 1 - exp(-elapsed / (stepSeconds/3)) mete casi la mitad del paso
	 * en el PRIMER cuadro y luego se arrastra. Medido cuadro a cuadro: 95,
	 * 159, 182, 200, 211, 217, 220, 222... Eso no se lee como caminar, se
	 * lee como un tiron seguido de una frenada. The_Neto06 en
	 * r/PixelDungeon: "siento que el movimiento se ve un poco raro. Tal vez
	 * sea por la forma en que salta cada paso".
	 *
	 * La curva suave (smoothstep) sale y llega con velocidad cero, asi que
	 * el paso acelera y frena en vez de saltar. Cuesta un poco de latencia
	 * al principio del paso -- se paga con gusto.
	 */
	public static boolean stepSuave = true;

	private static Camera3D camera;
	private static DungeonTilemap3D mesh;      // walls facing north/south
	private static DungeonTilemap3D wallsEW;   // walls facing east/west
	private static DungeonTilemap3D ground;    // floors
	private static DungeonTilemap3D roof;      // ceilings
	private static DungeonTilemap3D water;     // pools

	/**
	 * Brightness of floor and ceiling against the walls. The source art is
	 * a 27% luminance floor under a 61% wall -- correct for a 16px top-down
	 * tile, wrong when the floor fills half the screen and the wall towers
	 * over it. Lifting the ground and easing the walls back is what makes
	 * the two read as one room.
	 */
	public static float groundBrightness = 1.25f;

	/** Added after the multiply -- the shader is c * uColorM + uColorA.
	 *  A pure multiply of 1.85 lifted the dark floor tile to a readable
	 *  0.51, but it also drove every bright decoration past 1.0 and flatten-
	 *  ed it to white: cave grass and Halls tilework lost all their detail.
	 *  Multiplying less and adding a floor gets the same 0.53 on the dark
	 *  tile while a 0.62 decoration lands at 0.96 instead of clipping. */
	public static float groundLift = 0.18f;
	public static float wallBrightness = 0.82f;

	/** Brightness of east-west walls as a fraction of the north-south ones.
	 *  A dungeon has no sun, but an eye with no shading cannot tell two
	 *  perpendicular walls apart, and a wall seen up close stops being a
	 *  surface and becomes a rectangle of colour. Minecraft uses 0.8 and
	 *  0.6 for its two vertical orientations -- the same 0.75 ratio. */
	public static float wallSideContrast = 0.75f;

	/** Field of view in degrees, pushed into the camera on install. */
	public static float fieldOfView = 50f;

	/** The ceiling is drawn with the WALL tile, so at the floor's
	 *  brightness it clipped to flat white and lit the corridor like an
	 *  office. It is the surface you never look at directly, so it can be
	 *  the darkest thing in the room. */
	public static float ceilingBrightness = 0.45f;

	/** Water is drawn with the plain floor tile, tinted. Its own frame in
	 *  the sheet is transparent, so before this every pool was a hole. */
	public static float waterR = 0.30f, waterG = 0.55f, waterB = 0.95f;

	/** Cuanto sube y baja la lamina de agua, en unidades de mundo, y a que
	 *  ritmo en ciclos por segundo.
	 *
	 *  Sin esto el agua es una baldosa azul y plana, y se lee como suelo
	 *  pintado. Pasa porque el juego plano no dibuja el agua con un tile:
	 *  el de Terrain.WATER es transparente, un hueco para una capa animada
	 *  que en 3D no existe, asi que estas casillas se rellenaron con un
	 *  color solido. El color solo no basta -- The_Neto06 en r/PixelDungeon
	 *  pregunto "por que el piso es azul en algunas partes? bug o feature?".
	 *
	 *  Se mueve en vez de hundirse: hundir la lamina abre una rendija en la
	 *  orilla, porque las casillas de agua no llevan cara de suelo y se
	 *  veria a traves. Oscilando alrededor de cero la rendija nunca pasa de
	 *  la amplitud, y el movimiento es lo que de verdad dice "liquido". */
	public static float waterWave = 0.025f;
	public static float waterWaveSpeed = 0.55f;

	private static float waterTime = 0f;
	private static Group meshParent;
	private static boolean terrainDirty = false;
	private static int lastPos = -1;
	private static float targetYaw = 0f;
	private static float camX, camZ;        // where the eye actually is
	private static float desdeX, desdeZ;    // donde empezo el paso en curso
	private static float pasoT = 0f;        // segundos dentro del paso
	private static boolean placed = false;  // false until the first frame

	public static Camera3D camera() {
		return camera;
	}

	public static void install( Group terrain ) {

		reset();

		if (!enabled) {
			return;
		}

		// Feel owns every tunable number; this pushes them out to the
		// classes that read them. Editing a value in one of those classes
		// is overwritten on the next level -- edit Feel instead.
		Feel.apply();

		camera = new Camera3D( Game.width, Game.height );
		camera.fov = fieldOfView;
		// The game shakes Camera.main at every dramatic beat; mirror it.
		camera.shakeSource = Camera.main;
		Camera.add( camera );

		SmartTexture tex = TextureCache.get( Dungeon.level.tilesTex() );

		ground = DungeonTilemap3D.current( tex, GridMesh.GROUND );
		mesh = DungeonTilemap3D.current( tex, GridMesh.WALLS_NS );
		wallsEW = DungeonTilemap3D.current( tex, GridMesh.WALLS_EW );
		roof = DungeonTilemap3D.current( tex, GridMesh.CEILING );
		water = DungeonTilemap3D.current( tex, GridMesh.WATER );
		applyBrightness();

		// Draw order comes from the gizmo tree, not the camera list, so
		// putting these in terrain keeps them behind every UI layer.
		ground.camera = camera;
		mesh.camera = camera;
		wallsEW.camera = camera;
		roof.camera = camera;
		water.camera = camera;
		meshParent = terrain;
		terrain.add( ground );
		terrain.add( roof );
		terrain.add( water );
		terrain.add( mesh );
		terrain.add( wallsEW );

		Billboards.install( terrain );
		Billboards.installTerrain( camera );

		update();
	}

	/** Follows the hero's cell. Called once per frame by GameScene. */
	public static void update() {

		if (!enabled || camera == null || Dungeon.hero == null) {
			return;
		}

		rebuildIfDirty();

		int width = Level.getWidth();
		int pos = Dungeon.hero.pos;

		// The game moves the hero by tapping a cell, and Char has no facing,
		// so the heading is derived here from the step just taken. Walking
		// turns you to look where you went, which is enough to explore with
		// before there are dedicated turn controls.
		if (lastPos >= 0 && pos != lastPos) {
			int dx = (pos % width) - (lastPos % width);
			int dz = (pos / width) - (lastPos / width);
			if (dx != 0 || dz != 0) {
				float rumbo = (float)Math.toDegrees( Math.atan2( -dx, -dz ) );
				if (headingFollowsMovement) {
					targetYaw = rumbo;
				}
				if (enderezarAlCaminar && caminoPedido) {
					// Se apunta igual que cuando el heroe ataca algo, y no
					// con el seguimiento por cuadro de arriba: aquel deja de
					// girar en cuanto el heroe llega, asi que un camino de
					// una sola casilla se quedaba a mitad -- medido en vivo,
					// 27 grados de los 90 que tocaban. El apuntado termina
					// el giro pase lo que pase con el camino.
					aimYaw = rumbo;
					aiming = true;
				}
			}
		}
		if (headingFollowsMovement) {
			yaw = approach( yaw, targetYaw, turnSpeed * Game.elapsed );
		}

		// El camino pedido termina cuando el heroe llega. Va DESPUES de
		// mirar el paso: si no, el ultimo paso de un camino corto llega en
		// el mismo cuadro en que se acaba la accion y se quedaria sin girar.
		if (caminoPedido && Dungeon.hero.curAction == null) {
			caminoPedido = false;
		}
		if (aiming) {
			yaw = approach( yaw, aimYaw, aimTurnSpeed * Game.elapsed );
			float left = aimYaw - yaw;
			while (left > 180f) left -= 360f;
			while (left < -180f) left += 360f;
			if (Math.abs( left ) < 1f) {
				aiming = false;
			}
		}

		int previous = lastPos;
		lastPos = pos;

		float goalX = DungeonTilemap3D.worldX( pos, width );
		float goalZ = DungeonTilemap3D.worldZ( pos, width );

		// A teleport is not a step. Measured cell to cell, not against the
		// eased eye -- the eye lags on purpose, and adding that lag to a
		// legitimate diagonal step was enough to trip the threshold and
		// snap mid-walk.
		boolean teleported = false;
		if (previous >= 0 && pos != previous) {
			int dc = Math.abs( (pos % width) - (previous % width) );
			int dr = Math.abs( (pos / width) - (previous / width) );
			teleported = dc > 1 || dr > 1;
		}

		if (!placed || teleported) {
			camX = goalX;
			camZ = goalZ;
			placed = true;
		} else if (stepSeconds > 0f) {

			// Un cambio de casilla empieza un paso nuevo. Se arranca desde
			// donde este el ojo AHORA, no desde la casilla anterior: si el
			// paso anterior no habia terminado, seguir desde el punto medio
			// es lo que hace que caminar sostenido no de tirones.
			if (pos != previous) {
				desdeX = camX;
				desdeZ = camZ;
				pasoT = 0f;
			}

			if (stepSuave) {
				pasoT += Game.elapsed;
				float u = pasoT / stepSeconds;
				if (u > 1f) {
					u = 1f;
				}
				// smoothstep: sale y llega con velocidad cero.
				float e = u * u * (3f - 2f * u);
				camX = desdeX + (goalX - desdeX) * e;
				camZ = desdeZ + (goalZ - desdeZ) * e;
			} else {
				// Suavizado exponencial: un cuadro rapido y uno lento
				// cubren el mismo terreno, pero arranca de golpe.
				float k = 1f - (float)Math.exp( -Game.elapsed / (stepSeconds / 3f) );
				camX += (goalX - camX) * k;
				camZ += (goalZ - camZ) * k;
			}
		} else {
			camX = goalX;
			camZ = goalZ;
		}

		// How far the eye still has to travel, as a fraction of a cell. Near
		// zero when standing still, so the bob fades out on its own.
		float dx = goalX - camX;
		float dz = goalZ - camZ;
		float travel = (float)Math.sqrt( dx * dx + dz * dz )
			/ DungeonTilemap3D.TILE;
		if (travel > 1f) {
			travel = 1f;
		}

		// Two detuned sines, so the flame wanders instead of pulsing on a
		// beat. A single sine reads as a machine breathing.
		flickerPhase += Game.elapsed;
		float flick = (float)(Math.sin( flickerPhase * 7.3f )
			+ 0.6f * Math.sin( flickerPhase * 17.1f )) / 1.6f;
		Mesh3D.fogNear = torchNear;
		Mesh3D.fogFalloff = torchFalloff;
		Mesh3D.fogFar  = torchRange + flick * torchFlicker;

		bobPhase += travel * headBobCycles * Game.elapsed * 12f;
		float bob = (float)Math.sin( bobPhase ) * headBobAmplitude * travel;

		// Balanceo lateral, a la MITAD de frecuencia que el vertical: dos
		// pisadas por vaiven. Eso es lo que dibuja el ocho que hace la
		// cabeza al andar; sin el, el cabeceo solo se lee como un ascensor.
		// Sale de Coto Vedado, que es donde estos numeros estan probados a
		// mano en un telefono: 0.022 caminando.
		//
		// Se mueve por la derecha de la vista. La horizontal del frente es
		// (-sin yaw, -cos yaw), asi que su perpendicular es
		// (-cos yaw, sin yaw) -- producto punto cero.
		float sway = (float)Math.sin( bobPhase * 0.5f ) * swayAmplitude * travel;
		double yr = Math.toRadians( yaw );
		float rx = -(float)Math.cos( yr );
		float rz =  (float)Math.sin( yr );

		camera.eyeX = camX + rx * sway;
		camera.eyeZ = camZ + rz * sway;
		camera.eyeY = eyeHeight + bob;
		// Vertigo: el mundo se mece.
		//
		// El juego ya manda el paso a una casilla al azar cuando estas
		// mareado (Char.move), y en vista cenital eso se lee solo: ves a tu
		// personaje trastabillar a otra baldosa. En primera persona no se
		// ve nada -- el mundo se desliza hacia donde no querias y parece
		// que los controles se rompieron. justbleachmyeyes en
		// r/PixelDungeon: "I got really thrown off when a ghost applied
		// vertigo and I wasn't moving in the direction I was looking".
		//
		// Se mece SOLO la camara, no FirstPerson.yaw: la direccion logica
		// sigue siendo la tuya, asi que el mareo se ve pero no se suma al
		// desvio que el juego ya aplica. Dos senos desafinados para que
		// divague en vez de pulsar a compas, como el titileo de la
		// antorcha.
		float mareo = 0f;
		if (Dungeon.hero != null
				&& Dungeon.hero.buff( Vertigo.class ) != null) {
			vertigoPhase += Game.elapsed;
			mareo = (float)(Math.sin( vertigoPhase * 1.7f )
				+ 0.7f * Math.sin( vertigoPhase * 2.9f )) / 1.7f;
		} else {
			vertigoPhase = 0f;
		}

		camera.yaw = yaw + mareo * vertigoSway;
		camera.pitch = pitch + mareo * vertigoSway * 0.45f;

		Billboards.update( camera.yaw, camera );
	}

	/** Rotate towards a heading the short way round. */
	private static float approach( float from, float to, float step ) {
		float delta = to - from;
		while (delta > 180f) delta -= 360f;
		while (delta < -180f) delta += 360f;
		if (Math.abs( delta ) <= step) {
			return to;
		}
		return from + Math.signum( delta ) * step;
	}

	/**
	 * Which dungeon cell a screen point is pointing at.
	 *
	 * This is the whole of what first person changes about aiming. The game
	 * already routes every tap through DungeonTilemap.screenToTile and then
	 * GameScene.handleCell — movement, spell targeting, quickslots, the lot.
	 * Only the translation from pixel to cell assumed a top-down map, so
	 * only that is replaced: a ray from the eye through the tapped pixel,
	 * met with the floor.
	 *
	 * @return the cell, or -1 if the ray never reaches the floor
	 */
	public static int screenToCell( float sx, float sy ) {

		if (!enabled || camera == null || Dungeon.level == null) {
			return -1;
		}

		int width = Level.getWidth();

		// A monster stands up; the floor does not. Testing the sprite first
		// is what makes tapping one actually select it -- against the floor
		// alone, its head sends the ray skyward and its chest lands on the
		// cell behind, so only its feet ever worked.
		int mob = billboardUnderRay( sx, sy, width );
		if (mob >= 0) {
			return mob;
		}

		boolean[] solid = paraElRayo();

		return cellFromRay( sx, sy, Game.width, Game.height,
			camera.fovY(), camera.aspect(),
			camera.eyeX, camera.eyeY, camera.eyeZ,
			camera.yaw, camera.pitch,
			width, Dungeon.level.map.length / width, solid );
	}

	/**
	 * What stops a tap's ray.
	 *
	 * Solid was not enough: high grass is passable, so the ray went clean
	 * through a bush and picked a floor cell behind it. Tapping a bush in
	 * front of you then walked you the long way round to its far side,
	 * because the cell you actually selected was back there. Reported from
	 * the first floor and it was exactly this.
	 *
	 * Anything that blocks sight stops the ray now, on top of what already
	 * did. It has to be the union of the two and not either one: of the six
	 * sight-blocking terrains only high grass was missing, but solid also
	 * covers the stairs and statues, which block nothing and still must be
	 * tappable rather than shot through.
	 *
	 * Level keeps both arrays current -- burn a bush or open a door and
	 * they change with it -- so there is nothing here to cache or stale.
	 */
	private static boolean[] paraElRayo() {
		int n = Dungeon.level.map.length;
		boolean[] tapa = new boolean[n];
		for (int c = 0; c < n; c++) {
			tapa[c] = Level.solid[c] || Level.losBlocking[c];
		}
		return tapa;
	}

	/**
	 * The ray cast itself, with no engine state, so it can be tested off
	 * the device. Screen pixel in, dungeon cell out, or -1 when the ray
	 * never meets the floor or lands outside the map.
	 */
	public static int cellFromRay( float sx, float sy, float screenW, float screenH,
			float fovY, float aspect, float eyeX, float eyeY, float eyeZ,
			float yawDeg, float pitchDeg, int width, int rows ) {
		return cellFromRay( sx, sy, screenW, screenH, fovY, aspect,
			eyeX, eyeY, eyeZ, yawDeg, pitchDeg, width, rows, null );
	}

	public static int cellFromRay( float sx, float sy, float screenW, float screenH,
			float fovY, float aspect, float eyeX, float eyeY, float eyeZ,
			float yawDeg, float pitchDeg, int width, int rows, boolean[] solid ) {

		if (screenW <= 0f || screenH <= 0f || width <= 0 || rows <= 0) {
			return -1;
		}

		// Screen pixel to a direction in camera space.
		float ndcX = 2f * sx / screenW - 1f;
		float ndcY = 1f - 2f * sy / screenH;
		float t = (float)Math.tan( Math.toRadians( fovY / 2.0 ) );

		float dx = ndcX * t * aspect;
		float dy = ndcY * t;
		float dz = -1f;

		// Camera space to world: undo the view rotation, pitch then yaw.
		double p = Math.toRadians( pitchDeg );
		float cp = (float)Math.cos( p ), sp = (float)Math.sin( p );
		float ry = dy * cp - dz * sp;
		float rz = dy * sp + dz * cp;

		double y = Math.toRadians( yawDeg );
		float cy = (float)Math.cos( y ), sy2 = (float)Math.sin( y );
		float wx = dx * cy + rz * sy2;
		float wz = -dx * sy2 + rz * cy;
		float wy = ry;

		// Meet the floor. Looking level or upward never lands anywhere.
		if (wy >= -1e-5f) {
			return -1;
		}
		float dist = -eyeY / wy;

		// Walk the ray to where it meets the floor, stopping at the first
		// wall in the way. Without this a tap goes clean through rock and
		// picks a cell in the room beyond — the hero would path towards
		// somewhere they cannot see, and a wand could target through a wall.
		if (solid != null) {
			// La casilla donde esta el ojo nunca tapa. Parece obvio y no lo
			// era: una puerta es SOLID y ademas se puede pisar, asi que
			// parado en el marco el primer paso del rayo daba con la propia
			// casilla y CUALQUIER toque devolvia donde ya estabas -- no se
			// podia caminar tocando. Al sumar lo que tapa la vista le
			// pasaria lo mismo a la hierba alta, que se pisa mucho mas.
			int propia = cellAt( eyeX, eyeZ, width, rows );
			float stepLen = DungeonTilemap3D.TILE / 4f;
			for (float march = stepLen; march < dist; march += stepLen) {
				int c = cellAt( eyeX + wx * march, eyeZ + wz * march, width, rows );
				if (c < 0) {
					return -1;
				}
				if (c != propia && solid[c]) {
					return c;
				}
			}
		}

		return cellAt( eyeX + wx * dist, eyeZ + wz * dist, width, rows );
	}

	/**
	 * The nearest monster whose standing sprite the ray passes through,
	 * treated as an upright cylinder — which is what a camera-facing quad
	 * is, from every angle.
	 *
	 * @return its cell, or -1 if the ray meets none
	 */
	/**
	 * The nearest billboard a tap passes through: monsters first, then
	 * items on the floor.
	 *
	 * Without items in here, tapping a sword lying in the next room casts
	 * straight past its sprite to the floor plane behind it and walks you
	 * to the wrong cell. Heaps are how you pick things up at a distance,
	 * open chests and buy from shops, so they have to be hittable.
	 */
	private static int billboardUnderRay( float sx, float sy, int width ) {

		float[] d = new float[3];
		if (!rayDirection( sx, sy, Game.width, Game.height,
				camera.fovY(), camera.aspect(), camera.yaw, camera.pitch, d )) {
			return -1;
		}

		float a = d[0] * d[0] + d[2] * d[2];
		if (a <= 0f) {
			return -1;
		}

		Hit hit = new Hit();

		for (Mob m : Dungeon.level.mobs) {
			if (m.sprite == null) {
				continue;
			}
			test( m.pos, Billboards.tallOf( m.sprite, Billboards.height ),
				d, a, width, hit );
		}

		// Second, and only where nothing else was hit nearer: a monster
		// standing on a pile has to win the tap, or you loot the floor
		// under something that is biting you.
		for (com.github.dachhack.sprout.items.Heap h : Dungeon.level.heaps.values()) {
			if (h == null || h.sprite == null) {
				continue;
			}
			test( h.pos, Billboards.tallOf( h.sprite, Billboards.itemHeight ),
				d, a, width, hit );
		}

		return hit.cell;
	}

	/** Nearest-hit accumulator, so the two passes above share one winner. */
	private static class Hit {
		int cell = -1;
		float t = Float.MAX_VALUE;
	}

	/** Ray against an upright cylinder standing on one cell. */
	private static void test( int pos, float tall, float[] d, float a,
			int width, Hit hit ) {

		if (pos < 0 || pos >= Dungeon.visible.length || !Dungeon.visible[pos]) {
			return;
		}

		float radius = DungeonTilemap3D.TILE * 0.3f;

		float ox = camera.eyeX - DungeonTilemap3D.worldX( pos, width );
		float oz = camera.eyeZ - DungeonTilemap3D.worldZ( pos, width );

		float b = 2f * (ox * d[0] + oz * d[2]);
		float c = ox * ox + oz * oz - radius * radius;
		float disc = b * b - 4f * a * c;
		if (disc < 0f) {
			return;
		}

		float root = (float)Math.sqrt( disc );
		float t = (-b - root) / (2f * a);
		if (t < 0f) {
			t = (-b + root) / (2f * a);
		}
		if (t < 0f || t >= hit.t) {
			return;
		}

		float y = camera.eyeY + d[1] * t;
		if (y < 0f || y > tall) {
			return;
		}

		hit.t = t;
		hit.cell = pos;
	}

	/** Screen pixel to a world-space ray direction. False if degenerate. */
	private static boolean rayDirection( float sx, float sy, float screenW, float screenH,
			float fovY, float aspect, float yawDeg, float pitchDeg, float[] out ) {

		if (screenW <= 0f || screenH <= 0f) {
			return false;
		}

		float ndcX = 2f * sx / screenW - 1f;
		float ndcY = 1f - 2f * sy / screenH;
		float t = (float)Math.tan( Math.toRadians( fovY / 2.0 ) );

		float dx = ndcX * t * aspect;
		float dy = ndcY * t;
		float dz = -1f;

		double p = Math.toRadians( pitchDeg );
		float cp = (float)Math.cos( p ), sp = (float)Math.sin( p );
		float ry = dy * cp - dz * sp;
		float rz = dy * sp + dz * cp;

		double y = Math.toRadians( yawDeg );
		float cy = (float)Math.cos( y ), sy2 = (float)Math.sin( y );

		out[0] = dx * cy + rz * sy2;
		out[1] = ry;
		out[2] = -dx * sy2 + rz * cy;
		return true;
	}

	private static int cellAt( float worldX, float worldZ, int width, int rows ) {
		int col = Math.round( worldX / DungeonTilemap3D.TILE );
		int row = Math.round( worldZ / DungeonTilemap3D.TILE );
		if (col < 0 || col >= width || row < 0 || row >= rows) {
			return -1;
		}
		return row * width + col;
	}

	/**
	 * The grid direction the view is closest to. Free look is continuous
	 * but the dungeon is not, so a push on the stick has to land on one of
	 * four ways: 0 north, 1 east, 2 south, 3 west.
	 */
	public static int facing() {
		int f = Math.round( -normalisedYaw() / 90f ) % 4;
		return f < 0 ? f + 4 : f;
	}

	/**
	 * Yaw folded into -180..180. Free look adds up without limit -- spin on
	 * the spot for a while and it reaches the thousands -- and nothing
	 * downstream benefits from carrying that around.
	 */
	public static float normalisedYaw() {
		float y = yaw % 360f;
		if (y > 180f) y -= 360f;
		if (y < -180f) y += 360f;
		return y;
	}

	/**
	 * The cell one step away, in a direction relative to the view.
	 * localDir is 0 forward, 1 right, 2 back, 3 left.
	 *
	 * @return the neighbouring cell, or -1 if it falls off the map
	 */
	/**
	 * The cell one step away in a direction relative to where you are
	 * looking, on all eight compass points.
	 *
	 * localDir is 0 ahead, 1 ahead-right, 2 right, and so on clockwise --
	 * the same numbering the stick produces. It is added to the heading, so
	 * "forward" always means where the camera points; look diagonally and
	 * the diagonals become your forward and sides, which is what a first
	 * person view leads a player to expect.
	 */
	public static int neighbour( int pos, int localDir, int width ) {

		if (Dungeon.level == null) {
			return -1;
		}

		int dir = ((facing8() + localDir) % 8 + 8) % 8;

		// Clockwise from north: N, NE, E, SE, S, SW, W, NW
		int[] dcol = {  0, +1, +1, +1,  0, -1, -1, -1 };
		int[] drow = { -1, -1,  0, +1, +1, +1,  0, -1 };

		int col = pos % width + dcol[dir];
		int row = pos / width + drow[dir];
		int rows = Dungeon.level.map.length / width;

		if (col < 0 || col >= width || row < 0 || row >= rows) {
			return -1;
		}
		return row * width + col;
	}

	/** Nearest of the eight compass points to the way the camera looks. */
	public static int facing8() {
		int f = Math.round( -normalisedYaw() / 45f ) % 8;
		return f < 0 ? f + 8 : f;
	}

	/**
	 * Rebuild the geometry after the terrain changes. A DOOR is SOLID and an
	 * OPEN_DOOR is not, so without this an opened door stays a wall you can
	 * walk through — and the same for a burned barricade, a dug wall or a
	 * revealed secret door.
	 */
	public static void terrainChanged() {
		terrainDirty = true;
	}

	/**
	 * Rebuilding is whole-level, so it must not happen per cell. A bomb
	 * calls updateMap once for every tile in its blast and fire once per
	 * burning tile per turn -- doing the work each time meant rescanning
	 * 2304 cells and allocating two fresh direct buffers, several times in
	 * one frame. Coalesced to at most once per frame instead.
	 */
	private static void rebuildIfDirty() {

		if (!terrainDirty) {
			return;
		}
		terrainDirty = false;

		if (!enabled || mesh == null || meshParent == null || Dungeon.level == null) {
			return;
		}

		Camera cam = mesh.camera;
		DungeonTilemap3D oldWalls = mesh;
		DungeonTilemap3D oldWallsEW = wallsEW;
		DungeonTilemap3D oldGround = ground;
		DungeonTilemap3D oldRoof = roof;
		DungeonTilemap3D oldWater = water;
		SmartTexture tex = TextureCache.get( Dungeon.level.tilesTex() );

		ground = DungeonTilemap3D.current( tex, GridMesh.GROUND );
		mesh = DungeonTilemap3D.current( tex, GridMesh.WALLS_NS );
		wallsEW = DungeonTilemap3D.current( tex, GridMesh.WALLS_EW );
		roof = DungeonTilemap3D.current( tex, GridMesh.CEILING );
		water = DungeonTilemap3D.current( tex, GridMesh.WATER );
		applyBrightness();
		ground.camera = cam;
		mesh.camera = cam;
		wallsEW.camera = cam;
		roof.camera = cam;
		water.camera = cam;
		meshParent.add( ground );
		meshParent.add( roof );
		meshParent.add( water );
		meshParent.add( mesh );
		meshParent.add( wallsEW );

		// Order matters: Gizmo.destroy() nulls the parent link and
		// killAndErase() only erases when there is one, so destroying first
		// leaves the dead mesh in the group forever -- walked every frame,
		// its slot never reusable.
		oldGround.killAndErase(); oldGround.destroy();
		oldRoof.killAndErase();   oldRoof.destroy();
		oldWater.killAndErase();  oldWater.destroy();
		oldWalls.killAndErase();  oldWalls.destroy();
		oldWallsEW.killAndErase(); oldWallsEW.destroy();

		// A burnt barricade or a dug wall can add or remove an upright prop,
		// and the mesh no longer draws those cells flat, so a stale prop
		// list means a sign that is gone from the floor and still standing.
		Billboards.installTerrain( cam );
	}

	private static void applyBrightness() {
		if (ground != null) {
			ground.rm = ground.gm = ground.bm = groundBrightness;
			ground.ra = ground.ga = ground.ba = groundLift;
		}
		if (mesh != null) {
			mesh.rm = mesh.gm = mesh.bm = wallBrightness;
		}
		if (wallsEW != null) {
			float side = wallBrightness * wallSideContrast;
			wallsEW.rm = wallsEW.gm = wallsEW.bm = side;
		}
		if (roof != null) {
			roof.rm = roof.gm = roof.bm = ceilingBrightness;
		}
		if (water != null) {
			water.rm = waterR; water.gm = waterG; water.bm = waterB;
			waterTime += Game.elapsed;
			water.y = waterWave * (float)Math.sin(
				waterTime * waterWaveSpeed * 2.0 * Math.PI );
		}
	}

	public static void reset() {
		Billboards.clear();
		if (camera != null) {
			Camera.remove( camera );
		}
		camera = null;
		mesh = null;
		wallsEW = null;
		ground = null;
		roof = null;
		water = null;
		meshParent = null;
		terrainDirty = false;
		lastPos = -1;
		yaw = 0f;
		targetYaw = 0f;
		placed = false;
	}
}
