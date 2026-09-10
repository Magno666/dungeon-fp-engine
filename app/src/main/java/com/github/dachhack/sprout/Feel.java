/*
 * Sprouted Pixel Dungeon — first person tuning
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

import com.watabou.noosa.Mesh3D;

/**
 * Every number worth turning, in one place.
 *
 * The brief asked for exactly this and it took far too long to arrive:
 * "un solo archivo de configuración, con nombres claros, para que yo los
 * ajuste sin buscar por todo el código". They had spread across eight
 * files instead.
 *
 * <h3>How this works</h3>
 * These fields are the source of truth. {@link #apply()} pushes them into
 * the classes that use them, and FirstPerson.install calls it every time a
 * level is built — so editing a value here is enough, and the fields over
 * in FirstPersonControls, Billboards and the rest are caches that get
 * overwritten. Change them there and your change is gone on the next
 * floor; change them here and it sticks.
 *
 * <h3>Where the numbers came from</h3>
 * Anything marked "Coto Vedado" is copied from the user's own browser FPS
 * at /root/proyectos/roblox-juego, already tuned on a real phone. Copy
 * more from there only after checking the semantics match: that game is
 * analog and continuous, this one is discrete grid steps, so a small stick
 * push that merely scales speed there commits a whole tile here.
 */
public class Feel {

	private Feel() {}

	// ---- walking ---------------------------------------------------------

	/** Seconds to glide from one cell to the next. The eye eases toward the
	 *  cell rather than snapping, which is most of what stops a grid game
	 *  from feeling like a slideshow. 0 disables the interpolation. */
	public static float stepSeconds = 0.18f;

	/** Milliseconds between steps while the stick is held. Below roughly
	 *  200 it reads as skating rather than walking. */
	public static float stepRepeatMs = 260f;

	/** Step interval multiplier once running. */
	public static float runMultiplier = 0.72f;

	/** Steps held before breaking into a run on their own. */
	public static int stepsBeforeRunning = 3;

	/** Push past this fraction of the ring to run immediately. Coto Vedado
	 *  runs past 0.9. */
	public static float runPush = 0.9f;

	/** Degrees per second the view turns when the heading follows movement.
	 *  Only used when {@link FirstPerson#headingFollowsMovement} is on. */
	public static float turnSpeed = 540f;

	// ---- head and eye ----------------------------------------------------

	/** Eye height in world units. TILE is 3 and the ceiling is at 3.2. */
	public static float eyeHeight = 1.6f;

	/** Vertical travel of the head while walking. Coto Vedado walks at
	 *  0.040. Low on purpose: a lot of this is what makes people put the
	 *  phone down. */
	public static float headBobAmplitude = 0.040f;

	/** Bobs per cell walked. Two is one dip per footfall. */
	public static float headBobCycles = 2f;

	/** Kick given to the view when the hero is hit, in the flat game's
	 *  shake pixels; Camera3D turns those into degrees. */
	public static float hurtShake = 3.5f;
	public static float hurtShakeSeconds = 0.28f;

	// ---- looking ---------------------------------------------------------

	/** Degrees of view per 100 px of drag, no smoothing. Coto Vedado's
	 *  touch look, LOOK = 0.0062 rad/px. */
	public static float lookSensitivity = 35.5f;

	/** How far up and down the view can tip. Coto Vedado clamps at 77.3. */
	public static float pitchLimit = 77f;

	/** A touch that never travels this far, in DP, counts as a tap rather
	 *  than a look — and taps are how you attack and aim. Measured as
	 *  |dx|+|dy|, which is stricter than a true distance. */
	public static float tapSlopDp = 15f;

	// ---- the stick -------------------------------------------------------

	/** Ring radius as a fraction of the shorter screen side, so the control
	 *  is the same size under the thumb on every phone. */
	public static float stickRingFraction = 0.15f;

	/** Knob radius as a fraction of the ring. */
	public static float stickKnobFraction = 0.4f;

	/** Dead zone as a fraction of the ring radius. Coto Vedado uses 0.16,
	 *  but a small push there only scales speed while here it commits a
	 *  whole tile, so this stays a little wider. */
	public static float stickDeadZoneFraction = 0.18f;

	/** How far outside the ring a thumb can land and still grab it. A fixed
	 *  stick you have to hit exactly is worse than no stick. */
	public static float stickActivationRadius = 1.45f;

	/** Hysteresis before the held direction flips, as a ratio between the
	 *  two axes. At 1.0 a thumb resting near 45 degrees chatters between
	 *  two directions and fires a step on every flicker. */
	public static float stickSwitchMargin = 1.4f;

	/** Opacity of the stick at rest and while held. It is always on screen,
	 *  so at rest it has to be faint enough to ignore. */
	public static float stickRestOpacity = 0.22f;
	public static float stickActiveOpacity = 0.45f;

	// ---- light -----------------------------------------------------------

	/** How far the torch reaches, in world units. TILE is 3, so 17 is just
	 *  under six cells. The dark closing in is most of what makes a first
	 *  person crawler feel like one. */
	public static float torchRange = 17f;

	/** Distance at which the falloff starts. Nearer than this is full
	 *  strength -- which is why this has to stay small. At 5 the light was
	 *  flat for the first 1.7 cells, so the wall you are standing against
	 *  had no gradient at all: its centre, 1.5 away, and its corners, 3
	 *  away, came out the same colour, and a face that fills the screen
	 *  with one colour is not a surface, it is a rectangle. Starting the
	 *  falloff almost at the eye gives a near wall the radial shading a
	 *  torch would actually cast. */
	public static float torchNear = 0.5f;

	/** How sharply the light dies with distance. 1 is a straight ramp and
	 *  looks like fog on a plain; a torch is nearer to inverse-square. This
	 *  is what gives a wall in front of your face a gradient instead of one
	 *  flat tone. */
	public static float torchFalloff = 2.6f;

	/** How far the reach wanders as the flame gutters. 0 for a steady lamp. */
	public static float torchFlicker = 1.3f;

	/** The colour the world fades into. Not pure black: a slight blue keeps
	 *  the dark from looking like a hole in the screen. */
	public static float darkR = 0.035f, darkG = 0.032f, darkB = 0.045f;

	// ---- surfaces --------------------------------------------------------

	/** Floor brightness. The source art is drawn for a top-down view where
	 *  a dark floor reads fine; head-on it needs lifting. Multiply and add
	 *  together, because a pure multiply big enough to lift the dark tile
	 *  also drives every bright decoration past white. */
	public static float floorMultiply = 1.25f;
	public static float floorAdd = 0.18f;

	/** Wall brightness. Eased back so walls stop out-glowing the floor. */
	public static float wallBrightness = 0.82f;

	/** How much darker an east-west wall is than a north-south one, as a
	 *  fraction. This is the whole reason a corner reads as a corner: with
	 *  one brightness for every wall, two perpendicular faces are the same
	 *  colour and a wall up close is a flat rectangle. 1.0 turns it off. */
	public static float wallSideContrast = 0.75f;

	/** Horizontal field of view in degrees, read as vertical in landscape.
	 *  Narrower feels like a corridor and makes a near wall swallow the
	 *  screen; wider fish-eyes the tiles. Camera3D holds the portrait
	 *  correction, this is the number worth turning. */
	public static float fieldOfView = 50f;

	/** Ceiling brightness. It is drawn with the wall tile, so at the
	 *  floor's setting it clips to flat white and lights the corridor like
	 *  an office. */
	public static float ceilingBrightness = 0.45f;

	/** Tint for water. Its own tile in the sheet is transparent — a hole
	 *  for the flat game's animated water layer — so first person draws it
	 *  with the plain floor tile in this colour. */
	public static float waterR = 0.30f, waterG = 0.55f, waterB = 0.95f;

	// ---- creatures and things --------------------------------------------

	/** How tall a creature with a reference-sized sprite stands. Scaled per
	 *  mob by its own sprite height, so a boss towers over a rat. */
	public static float mobHeight = 1.15f;

	/** Sprite height in texture pixels that {@link #mobHeight} describes. */
	public static float mobReferencePx = 16f;

	/** Height of a dropped item. Loot you cannot see is loot you cannot
	 *  pick up, but an item the size of a monster reads as a monster. */
	public static float itemHeight = 0.9f;

	/** Plants sit low; reading as ground cover is the point. */
	public static float plantHeight = 0.7f;

	/** Height of upright terrain — signs, alchemy pots. */
	public static float propHeight = 1.3f;

	/** Height of high grass. It is PASSABLE | LOS_BLOCKING, so it has to
	 *  actually obscure or hiding in it stops meaning anything. */
	public static float grassHeight = 1.9f;

	/** How solid a monster sensed through a wall by Mind Vision looks. */
	public static float ghostOpacity = 0.45f;

	/** How far a creature standing on a pile of loot is raised above it,
	 *  as a fraction of item height, so the tile stays readable. */
	public static float standOnLoot = 0.55f;

	/** Move on all eight compass points, as the flat game does. */
	public static boolean eightWayMovement = true;

	/** Height of the mark standing in a cell covered by a blob, and how
	 *  many are drawn at once. This is what makes Goo's charged-attack
	 *  warning readable, so it is not decoration. */
	public static float blobMarkHeight = 0.55f;
	public static int maxBlobMarks = 80;

	// ---- map and HUD -----------------------------------------------------

	/** Minimap size as a fraction of the shorter screen side, how many
	 *  tiles fit across it, and its margin in pixels. */
	public static float mapSizeFraction = 0.34f;
	public static float mapTilesAcross = 15f;
	public static float mapMarginPx = 14f;

	/** Size of the heading arrow on the map, in map tiles. */
	public static float mapMarkerTiles = 1.5f;

	/** Ring drawn around a monster you can shoot, in world units, and how
	 *  far above its feet it floats. */
	public static float targetRingSize = 1.1f;
	public static float targetRingLift = 0.15f;

	/**
	 * Push these values into the classes that use them.
	 *
	 * Called from FirstPerson.install, so it runs on every level. Anything
	 * added here has to be wired below or it will silently do nothing —
	 * which is the one failure mode a central config file introduces.
	 */
	public static void apply() {

		FirstPerson.stepSeconds       = stepSeconds;
		FirstPerson.turnSpeed         = turnSpeed;
		FirstPerson.eyeHeight         = eyeHeight;
		FirstPerson.headBobAmplitude  = headBobAmplitude;
		FirstPerson.headBobCycles     = headBobCycles;
		FirstPerson.hurtShake         = hurtShake;
		FirstPerson.hurtShakeSeconds  = hurtShakeSeconds;
		FirstPerson.torchRange        = torchRange;
		FirstPerson.torchNear         = torchNear;
		FirstPerson.torchFlicker      = torchFlicker;
		FirstPerson.groundBrightness  = floorMultiply;
		FirstPerson.groundLift        = floorAdd;
		FirstPerson.wallBrightness    = wallBrightness;
		FirstPerson.wallSideContrast  = wallSideContrast;
		FirstPerson.torchFalloff      = torchFalloff;
		FirstPerson.fieldOfView       = fieldOfView;
		FirstPerson.ceilingBrightness = ceilingBrightness;
		FirstPerson.waterR            = waterR;
		FirstPerson.waterG            = waterG;
		FirstPerson.waterB            = waterB;

		Mesh3D.fogR = darkR;
		Mesh3D.fogG = darkG;
		Mesh3D.fogB = darkB;

		FirstPersonControls.repeatMs           = stepRepeatMs;
		FirstPersonControls.runMultiplier      = runMultiplier;
		FirstPersonControls.stepsBeforeRunning = stepsBeforeRunning;
		FirstPersonControls.runPush            = runPush;
		FirstPersonControls.eightWay           = eightWayMovement;
		FirstPersonControls.lookSensitivity    = lookSensitivity;
		FirstPersonControls.pitchLimit         = pitchLimit;
		FirstPersonControls.tapSlopDp          = tapSlopDp;
		FirstPersonControls.ringFraction       = stickRingFraction;
		FirstPersonControls.knobFraction       = stickKnobFraction;
		FirstPersonControls.deadZoneFraction   = stickDeadZoneFraction;
		FirstPersonControls.activationRadius   = stickActivationRadius;
		FirstPersonControls.switchMargin       = stickSwitchMargin;
		FirstPersonControls.restOpacity        = stickRestOpacity;
		FirstPersonControls.activeOpacity      = stickActiveOpacity;

		Billboards.height         = mobHeight;
		Billboards.referencePx    = mobReferencePx;
		Billboards.itemHeight     = itemHeight;
		Billboards.plantHeight    = plantHeight;
		Billboards.propHeight     = propHeight;
		Billboards.grassHeight    = grassHeight;
		Billboards.ghostOpacity   = ghostOpacity;
		Billboards.standOnLoot    = standOnLoot;
		Billboards.blobMarkHeight = blobMarkHeight;
		Billboards.maxBlobMarks   = maxBlobMarks;

		Minimap.sizeFraction = mapSizeFraction;
		Minimap.tilesAcross  = mapTilesAcross;
		Minimap.marginPx     = mapMarginPx;
		Minimap.markerTiles  = mapMarkerTiles;

		Targeting.ringSize = targetRingSize;
		Targeting.ringLift = targetRingLift;
	}
}
