/*
 * Sprouted Pixel Dungeon — first person controls
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
import com.github.dachhack.sprout.scenes.GameScene;
import com.watabou.input.Touchscreen;
import com.watabou.input.Touchscreen.Touch;
import com.watabou.noosa.Game;
import com.watabou.noosa.Group;
import com.watabou.utils.PointF;
import com.watabou.utils.Signal;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Image;

/**
 * Left thumb walks, right thumb looks — the scheme the Godot spike was
 * built to calibrate, ported to the real game.
 *
 * The numbers below are the ones that came out of that spike's feel.gd
 * after testing on a phone, not fresh guesses.
 *
 * Nothing here moves the hero. The stick works out which adjacent cell a
 * push means and hands it to GameScene.handleCell — the same entry point
 * a tap on the flat map used. Turn order, actor scheduling and every rule
 * underneath are untouched, so this stays a turn based grid game that
 * merely handles like a shooter.
 */
public class FirstPersonControls implements Signal.Listener<Touch> {

	// ---- feel, straight out of the spike -------------------------------

	/** Finger travel before a direction registers. */
	public static float deadZonePx = 26f;

	/** Dead zone as a fraction of the ring radius, used when the ring is
	 *  on screen. Keeps the neutral centre proportional to the control.
	 *  Coto Vedado uses 16%, but there a small push merely scales speed --
	 *  here it COMMITS A WHOLE TILE, so this stays a little wider. */
	public static float deadZoneFraction = 0.18f;

	/** A perpendicular direction must beat the held one by this much before
	 *  the stick switches. Without it a thumb near 45 degrees chatters and
	 *  fires spurious steps. */
	public static float switchMargin = 1.4f;

	/**
	 * Milliseconds between steps while the stick is held.
	 *
	 * This is what decides whether holding the stick reads as walking or as
	 * pressing a button over and over. It has to be no longer than the time
	 * the camera takes to cross a tile (FirstPerson.stepSeconds), or the
	 * eye arrives, stops, waits, and lurches again. Matched, the motion
	 * never breaks and the turns resolve underneath without being felt.
	 */
	/** Milliseconds between steps while the stick is held. 175 was about
	 *  six cells a second, which reads as skating rather than walking in a
	 *  turn-based game -- and it is faster than the game resolves a busy
	 *  turn, so the real pace kept being decided by the dungeon instead of
	 *  by this number, which is what made it feel uneven. */
	public static float repeatMs = 260f;

	/**
	 * Hold the stick a moment and the pace picks up, the way tapping a far
	 * cell makes the hero run rather than stroll. 1.0 disables it.
	 */
	public static float runMultiplier = 0.72f;

	/** Steps held before the run speed kicks in. */
	public static int stepsBeforeRunning = 3;

	/** Degrees of view per 100 px of drag, with no smoothing.
	 *
	 *  35.5 is Coto Vedado's touch look (LOOK = 0.0062 rad/px), which is
	 *  already tuned on a real phone and is the user's own taste. The old
	 *  22 was a number I picked. */
	public static float lookSensitivity = 35.5f;

	/** How far up and down the view can tip, in degrees. */
	public static float pitchLimit = 77f;

	/** Total width the toolbar is given in first person, in UI units --
	 *  roughly the sum of its buttons, so they group instead of spreading
	 *  to opposite corners of a landscape screen. */
	public static float toolbarWidthUi = 134f;

	/** Push past this fraction of the ring to break into a run, the way
	 *  Coto Vedado does it (run past 0.9). Running by reaching a step count
	 *  alone means you cannot choose to hurry. */
	public static float runPush = 0.9f;

	/** A touch that never travels this far counts as a tap, not a drag.
	 *  In DP, because a hard-coded pixel count means one thing on a 720p
	 *  phone and another on a 1440p one. Measured as |dx|+|dy|, which is
	 *  stricter than a real distance, so a diagonal thumb roll of 12px on
	 *  each axis already spends 24 of it -- at the old flat 24px almost no
	 *  real thumb tap survived, and taps are how you attack and aim. */
	public static float tapSlopDp = 15f;

	public static float tapSlopPx() {
		return tapSlopDp * Math.max( 1f, Game.density );
	}

	/** Radius of the drawn ring, in SCREEN pixels. Everything the stick
	 *  measures -- dead zone, activation, knob travel -- is in screen
	 *  pixels, so the drawing is too, and only the final position is
	 *  converted to UI units. Mixing the two is how a stick ends up
	 *  responding somewhere other than where it is painted.
	 *  Given as a fraction of the shorter screen side so the stick is the
	 *  same size under the thumb on every phone. */
	public static float ringFraction = 0.15f;

	public static float ringRadiusPx() {
		return Math.min( Game.width, Game.height ) * ringFraction;
	}

	/** Radius of the knob, as a fraction of the ring. */
	public static float knobFraction = 0.4f;

	public static float knobRadiusPx() {
		return ringRadiusPx() * knobFraction;
	}

	/** Distance from the left edge to the ring's centre. */
	public static float baseMarginPx() {
		return ringRadiusPx() * 1.35f;
	}

	/** Move on all eight compass points, as the flat game does, instead of
	 *  only forward/back/left/right. */
	public static boolean eightWay = true;

	/** Clearance kept between the ring and the toolbar above which it sits. */
	public static float toolbarGapPx = 24f;

	/** How far outside the ring a thumb can land and still grab the stick.
	 *  A fixed stick you have to hit exactly is worse than no stick. */
	public static float activationRadius = 1.45f;

	/** Opacity at rest and while held. It is always on screen -- you cannot
	 *  learn a control you cannot see -- so at rest it has to be faint
	 *  enough to ignore. */
	public static float restOpacity = 0.22f;
	public static float activeOpacity = 0.45f;

	// ---- state ---------------------------------------------------------

	private static FirstPersonControls instance;

	private Touch stick;
	private float originX, originY;
	private int stickDir = -1;
	private float repeatAccum;
	private int stepsHeld;

	private Touch look;
	private float lookLastX, lookLastY;
	private boolean lookMoved;

	private Group hud;
	private Image ring;
	private Image knob;

	public static void install( Group parent, Camera ui ) {
		uninstall();
		// stickDirection stays pure and pixel-based so it can be tested off
		// the device; the dead zone is sized to the ring here instead.
		deadZonePx = ringRadiusPx() * deadZoneFraction;

		instance = new FirstPersonControls();
		instance.buildHud( parent, ui );
		Touchscreen.event.add( instance );
	}

	/**
	 * A stick you cannot see is a stick you cannot learn. Drawn on the UI
	 * camera so it sits flat on the screen, and only while a thumb is down.
	 */
	private void buildHud( Group parent, Camera ui ) {
		hud = new Group();
		hud.camera = ui;
		parent.add( hud );

		ring = new Image( HudTextures.ring() );
		knob = new Image( HudTextures.disc() );

		// Always drawn. The stick sits in one place and stays there, so the
		// thumb can find it without looking away from the dungeon.
		for (Image img : new com.watabou.noosa.Image[]{ ring, knob }) {
			img.am = restOpacity;
			img.aa = 0f;
			hud.add( img );
		}

		layoutStick();
	}

	public static void uninstall() {
		if (instance != null) {
			Touchscreen.event.remove( instance );
			if (instance.hud != null) {
				instance.hud.killAndErase();
				instance.hud = null;
			}
			instance = null;
		}
	}

	/** Called once a frame so a held stick keeps stepping. */
	public static void update() {
		if (instance != null) {
			instance.tick();
		}
	}

	private void tick() {

		reconcile();
		layoutStick();

		if (stick == null || stickDir < 0) {
			return;
		}
		repeatAccum += Game.elapsed * 1000f;

		float interval = repeatMs;
		boolean pushedHard = false;
		if (stick != null) {
			float vx = stick.current.x - baseX();
			float vy = stick.current.y - baseY();
			pushedHard = (vx * vx + vy * vy)
				>= (ringRadiusPx() * runPush) * (ringRadiusPx() * runPush);
		}
		if (pushedHard || stepsHeld >= stepsBeforeRunning) {
			interval *= runMultiplier;
		}

		if (repeatAccum >= interval) {
			// Only spend the interval on a step that actually happened.
			// Dropping it silently when the hero is mid-action turns a held
			// stick into a stutter instead of a walk.
			if (step( stickDir )) {
				// Subtract rather than zero. A turn the dungeon takes longer
				// to resolve than `interval` used to cost its own time PLUS
				// a fresh full interval on top, so a crowded room walked
				// visibly slower than an empty corridor. Carrying the
				// overflow keeps the cadence even.
				repeatAccum -= interval;
				// ...but never bank more than one step, or coming out of a
				// long pause would fire a burst.
				if (repeatAccum > interval) {
					repeatAccum = interval;
				}
				stepsHeld++;
			}
		}
	}

	/**
	 * A touch we are holding may never send its UP: Signal is a stack, so a
	 * Window opened mid-gesture puts its full-screen blocker in front of us
	 * and cancels the event. Without this the stick stays stuck down and the
	 * hero walks in one direction forever. The pointer table is the truth.
	 */
	private void reconcile() {
		if (stick != null && !Touchscreen.pointers.containsValue( stick )) {
			stick = null;
			stickDir = -1;
			stepsHeld = 0;
		}
		if (look != null && !Touchscreen.pointers.containsValue( look )) {
			look = null;
		}
	}

	/** Where the ring's centre sits, in screen pixels. */
	private static float baseX() {
		return baseMarginPx();
	}

	private static float baseY() {
		// Above the toolbar, not on it. The wait/search/info buttons sit in
		// the bottom-left corner -- exactly where a thumbstick wants to go --
		// and a ring drawn over them eats every tap meant for them.
		float overToolbar = GameScene.toolbarTopPx() - ringRadiusPx() - toolbarGapPx;
		return Math.min( Game.height - baseMarginPx(), overToolbar );
	}

	private void layoutStick() {

		if (ring == null || hud == null || hud.camera == null) {
			return;
		}

		float zoom = hud.camera.zoom;
		if (zoom <= 0f) {
			return;
		}

		boolean held = stick != null;
		ring.am = held ? activeOpacity : restOpacity;
		knob.am = held ? activeOpacity : restOpacity;

		// The textures are square and drawn at whatever size is asked for,
		// so scale is the requested diameter over the bitmap's own width.
		place( ring, baseX(), baseY(), ringRadiusPx() / zoom );

		float kx = baseX();
		float ky = baseY();
		if (held) {
			// The knob follows the thumb but stops at the ring's edge, so
			// the control reads as a stick rather than a dot wandering off.
			float vx = stick.current.x - baseX();
			float vy = stick.current.y - baseY();
			float len = (float)Math.sqrt( vx * vx + vy * vy );
			if (len > ringRadiusPx() && len > 0f) {
				float clamp = ringRadiusPx() / len;
				vx *= clamp;
				vy *= clamp;
			}
			kx += vx;
			ky += vy;
		}
		place( knob, kx, ky, knobRadiusPx() / zoom );
	}

	/** Centre an image on a screen point, sized to a radius in UI units. */
	private void place( Image img, float screenX, float screenY,
			float radiusUi ) {

		float s = radiusUi * 2f / img.texture.width;
		img.scale.set( s, s );

		PointF c = hud.camera.screenToCamera( (int)screenX, (int)screenY );
		img.x = c.x - radiusUi;
		img.y = c.y - radiusUi;
	}

	/**
	 * Touchscreen dispatches null for a move: it updates each live Touch's
	 * position in place and only signals that something changed. Reading
	 * touch.down straight away would throw on the first drag. TouchArea
	 * guards the same way, which is what gave it away.
	 */
	@Override
	public void onSignal( Touch touch ) {

		if (!FirstPerson.enabled) {
			return;
		}

		if (touch == null) {
			moved();
		} else if (touch.down) {
			begin( touch );
		} else {
			end( touch );
		}
	}

	/** Positions changed under us; re-read whichever thumbs are down. */
	private void moved() {

		if (stick != null) {
			evaluateStick( stick.current.x - originX, stick.current.y - originY );
		}

		if (look != null) {
			float dx = look.current.x - lookLastX;
			float dy = look.current.y - lookLastY;
			lookLastX = look.current.x;
			lookLastY = look.current.y;

			if (Math.abs( look.current.x - look.start.x )
					+ Math.abs( look.current.y - look.start.y ) > tapSlopPx()) {
				lookMoved = true;
			}

			// Looking never costs a turn and is never blocked. Yaw runs all
			// the way round; only pitch is stopped from tipping over.
			float perPx = lookSensitivity / 100f;
			FirstPerson.yaw -= dx * perPx;
			FirstPerson.yaw = FirstPerson.normalisedYaw();
			FirstPerson.pitch = Math.max( -75f,
				Math.min( 75f, FirstPerson.pitch - dy * perPx ) );
		}
	}

	private void begin( Touch touch ) {

		// A fixed stick: the thumb grabs it only near where it is drawn,
		// and the push is always measured from the ring's centre, not from
		// wherever the thumb landed. Everywhere else on the screen is look,
		// including the left side -- which is what makes turning around
		// while walking work.
		float dx = touch.current.x - baseX();
		float dy = touch.current.y - baseY();
		boolean onStick = dx * dx + dy * dy
			<= (ringRadiusPx() * activationRadius) * (ringRadiusPx() * activationRadius);

		if (onStick) {
			if (stick == null) {
				stick = touch;
				originX = baseX();
				originY = baseY();
				stickDir = -1;
				repeatAccum = 0f;
			}
		} else if (look == null) {
			look = touch;
			lookLastX = touch.current.x;
			lookLastY = touch.current.y;
			lookMoved = false;
		}
	}

	private void end( Touch touch ) {

		if (touch == stick) {
			stick = null;
			stickDir = -1;
			stepsHeld = 0;
			return;
		}

		if (touch == look) {
			// A drag was a look. A tap was aimed at something: hand it to
			// the game exactly as a tap on the flat map would have been.
			if (!lookMoved) {
				int cell = FirstPerson.screenToCell( touch.current.x, touch.current.y );
				if (cell >= 0) {
					GameScene.handleCell( cell );
				}
			}
			look = null;
		}
	}

	/**
	 * Which way a stick push points, with hysteresis so a thumb resting
	 * near 45 degrees does not chatter between two directions and fire a
	 * step on every flicker. Pure, so it can be tested off the device.
	 *
	 * @param held the direction currently held, or -1 for none
	 * @return 0 forward, 1 right, 2 back, 3 left, or -1 inside the dead zone
	 */
	public static int stickDirection( float vx, float vy, int held ) {

		if (Math.sqrt( vx * vx + vy * vy ) < deadZonePx) {
			return -1;
		}

		if (!eightWay) {
			int dir = Math.abs( vx ) > Math.abs( vy )
				? (vx > 0 ? 2 : 6)
				: (vy < 0 ? 0 : 4);
			return hold( dir, held, 4 );
		}

		// Eight sectors of 45 degrees, numbered clockwise from forward:
		// 0 ahead, 1 ahead-right, 2 right ... 7 ahead-left. Pixel Dungeon
		// moves on NEIGHBOURS8, so restricting the stick to four was
		// quietly removing diagonal movement from the game -- spotted from
		// a screenshot by Normal-Insect-8220 on r/PixelDungeon.
		double a = Math.atan2( vx, -vy );          // 0 = forward, clockwise
		int dir = (int)Math.round( Math.toDegrees( a ) / 45.0 );
		dir = ((dir % 8) + 8) % 8;

		return hold( dir, held, 8 );
	}

	/**
	 * Hysteresis: keep the held direction until the thumb is clearly into
	 * the next sector, so resting on a boundary does not chatter between
	 * two headings and spend a turn on every flicker.
	 *
	 * @param ways 8 or 4, so the wrap-around is measured on the right dial
	 */
	private static int hold( int dir, int held, int ways ) {

		if (held == -1 || dir == held) {
			return dir;
		}
		int step = Math.abs( dir - held );
		if (step > ways / 2) {
			step = ways - step;
		}
		// Only adjacent sectors are sticky; a deliberate swing straight
		// across answers at once.
		return step == 1 && switchMargin > 1f ? held : dir;
	}

	private void evaluateStick( float vx, float vy ) {

		int dir = stickDirection( vx, vy, stickDir );

		if (dir != stickDir) {
			stickDir = dir;
			stepsHeld = 0;
			// The first step is immediate, so a push answers at once. If the
			// hero happens to be mid-animation it is refused -- and then the
			// accumulator must start full, not empty, so the step lands the
			// instant they are free instead of costing a whole extra
			// interval on top of the wait.
			boolean took = dir != -1 && step( dir );
			repeatAccum = took ? 0f : repeatMs;
		}
	}

	/**
	 * Take one step, in a direction relative to where the body faces.
	 * 0 forward, 1 right, 2 back, 3 left.
	 */
	private boolean step( int localDir ) {

		if (Dungeon.hero == null || !Dungeon.hero.ready || Dungeon.level == null) {
			return false;
		}

		// A held stick keeps ticking on elapsed time alone, and a Window's
		// blocker only cancels touches that START inside it -- so without
		// this the hero walks on behind an open dialog until the finger
		// lifts. Step onto a story tile with the stick down and you would
		// read the sign while already three cells past it.
		if (GameScene.windowOpen()) {
			return false;
		}

		int cell = FirstPerson.neighbour( Dungeon.hero.pos, localDir, Level.getWidth() );
		if (cell < 0) {
			return false;
		}
		GameScene.handleCell( cell );
		return true;
	}
}
