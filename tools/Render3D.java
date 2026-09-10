/*
 * Sprouted Pixel Dungeon — off-device renderer
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

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

import com.github.dachhack.sprout.Dungeon;
import com.github.dachhack.sprout.GridMesh;
import com.github.dachhack.sprout.levels.Level;
import com.github.dachhack.sprout.levels.Terrain;
import com.watabou.glwrap.Matrix;

/**
 * Draws the first person view to a PNG, on a machine with no GPU and no
 * Android.
 *
 * This server has no /dev/kvm, so there is no emulator and there are no
 * real screenshots. Everything up to the GL call is plain Java, though:
 * the level generator, Terrain.SOLID, GridMesh, and Matrix. So this feeds
 * the real level into the real mesh builder, projects it with the real
 * projection, samples the game's own tiles0.png, and applies the same fog
 * and brightness the fragment shader does.
 *
 * What it is: the geometry the phone draws, drawn by hand.
 * What it is not: a screenshot. No HUD, no monsters, no animation, and a
 * software rasteriser instead of GLES. Treat a difference between this
 * and the phone as a bug in one of them, not as artistic licence.
 */
public class Render3D {

	// ---- the game's own numbers, copied from the classes that own them --
	static final float TILE   = 3f;      // DungeonTilemap3D.TILE
	static final float HEIGHT = 3.2f;    // DungeonTilemap3D.HEIGHT
	static final float EYE    = 1.6f;    // FirstPerson.eyeHeight
	static final float FOV    = 50f;     // Camera3D.fov
	static final float NEAR   = 0.2f;
	static final float FAR    = 60f;
	static float GROUND_BRIGHT = 1.85f;   // FirstPerson.groundBrightness
	static float GROUND_ADD    = 0f;      // FirstPerson.groundLift
	static final float WALL_BRIGHT   = 0.82f;
	static final float CEIL_BRIGHT   = 0.45f;   // FirstPerson.ceilingBrightness
	static final float FOG_R = 0.035f, FOG_G = 0.032f, FOG_B = 0.045f;
	static final float FOG_NEAR = 5f, FOG_FAR = 17f;   // FirstPerson.torchNear/torchRange
	static final int TILE_PX = 16;       // DungeonTilemap.SIZE

	static int W = 1920, H = 864;

	static float[] depth;
	static int[] pixels;
	static BufferedImage atlas;
	static int atlasW, atlasH;

	public static void main( String[] args ) throws Exception {

		String kind  = args.length > 0 ? args[0] : "SewerLevel";
		int    seed  = args.length > 1 ? Integer.parseInt( args[1] ) : 4;
		String out   = args.length > 2 ? args[2] : "shot.png";
		String sheet = args.length > 3 && !args[3].isEmpty() ? args[3] : sheetFor( kind );

		atlas = ImageIO.read( new File( sheet ) );
		atlasW = atlas.getWidth();
		atlasH = atlas.getHeight();

		Level level = generate( kind, seed );
		int width = Level.getWidth();
		int[] map = level.map;

		// Same UV rects TextureFilm hands DungeonTilemap3D: the sheet cut
		// into 16x16 frames, indexed by terrain value.
		int cols = atlasW / TILE_PX;
		int frames = cols * (atlasH / TILE_PX);
		float[] uv = new float[map.length * 4];
		for (int c = 0; c < map.length; c++) {
			int t = map[c];
			if (t >= frames) t = Terrain.EMPTY;    // Terrain runs past the sheet
			frameUv( t, cols, uv, c * 4 );
		}
		float[] ceilUv = new float[4];
		frameUv( Terrain.WALL, cols, ceilUv, 0 );

		// Terrain.WATER's frame is transparent -- see DungeonTilemap3D.
		boolean[] water = new boolean[map.length];
		for (int c = 0; c < map.length; c++) {
			water[c] = (Terrain.flags[map[c]] & Terrain.LIQUID) != 0;
			if (water[c]) frameUv( Terrain.EMPTY, cols, uv, c * 4 );
		}

		boolean[] wall = new boolean[map.length];
		for (int c = 0; c < map.length; c++) {
			wall[c] = (Terrain.flags[map[c]] & Terrain.SOLID) != 0;
		}

		// The game's builder, unmodified, split the same way FirstPerson
		// splits it so floor and walls can be lit apart.
		GridMesh ground = GridMesh.build( wall, width, TILE, HEIGHT, true, uv, ceilUv, GridMesh.GROUND, water );
		GridMesh walls  = GridMesh.build( wall, width, TILE, HEIGHT, true, uv, ceilUv, GridMesh.WALLS, water );
		GridMesh roof   = GridMesh.build( wall, width, TILE, HEIGHT, true, uv, ceilUv, GridMesh.CEILING, water );
		GridMesh pools  = GridMesh.build( wall, width, TILE, HEIGHT, true, uv, ceilUv, GridMesh.WATER, water );

		String mode = args.length > 5 ? args[5] : "corridor";
		if (args.length > 6) GROUND_BRIGHT = Float.parseFloat( args[6] );
		if (args.length > 7) GROUND_ADD    = Float.parseFloat( args[7] );
		int stand = viewpoint( level, wall, width, mode );
		// DungeonTilemap3D.worldX/worldZ: the CENTRE of a cell is col*TILE,
		// not col*TILE + TILE/2. GridMesh spans each cell from cx-TILE/2 to
		// cx+TILE/2 around that same centre. Adding half a tile here put
		// the eye inside the wall next door.
		float eyeX = (stand % width) * TILE;
		float eyeZ = (stand / width) * TILE;
		float yaw  = bestYaw( wall, width, stand );
		// A room read straight down an axis looks like a corridor. Turning
		// off the grid puts two walls and a corner in frame instead of one
		// flat face, which is what makes a still read as a place.
		if (mode.equals( "room" )) yaw += 38f;
		if (mode.equals( "junction" )) yaw += 20f;

		// A few degrees down. The eye is at 1.6 of a 3.2 ceiling, so a level
		// camera splits the frame evenly between floor and roof; tipping it
		// slightly gives the floor -- where the game happens -- more room.
		float pitch = args.length > 4 ? Float.parseFloat( args[4] ) : -7f;
		float[] cam = cameraMatrix( eyeX, EYE, eyeZ, yaw, pitch );

		// Two passes over the SAME generated level, so a lighting change can
		// actually be compared. Levels are not reproducible between runs --
		// Random has no seed hook -- so A/B has to happen inside one run.
		// args: [6][7] = brightness/lift for pass A, [8][9] for pass B.
		float[][] settings = args.length > 9
			? new float[][]{ { GROUND_BRIGHT, GROUND_ADD },
			                 { Float.parseFloat( args[8] ), Float.parseFloat( args[9] ) } }
			: new float[][]{ { GROUND_BRIGHT, GROUND_ADD } };

		for (int pass = 0; pass < settings.length; pass++) {

			pixels = new int[W * H];
			depth  = new float[W * H];
			int bg = rgb( FOG_R, FOG_G, FOG_B );
			for (int i = 0; i < pixels.length; i++) { pixels[i] = bg; depth[i] = Float.MAX_VALUE; }

			drawMesh( ground, cam, settings[pass][0], 1f, 1f, 1f, settings[pass][1] );
			drawMesh( pools,  cam, 1f, 0.30f, 0.55f, 0.95f, 0f );   // FirstPerson.waterR/G/B
			drawMesh( roof,   cam, CEIL_BRIGHT, 1f, 1f, 1f, 0f );
			drawMesh( walls,  cam, WALL_BRIGHT, 1f, 1f, 1f, 0f );

			BufferedImage img = new BufferedImage( W, H, BufferedImage.TYPE_INT_RGB );
			img.setRGB( 0, 0, W, H, pixels, 0, W );
			ImageIO.write( img, "png", new File(
				pass == 0 ? out : out.replace( ".png", "_B.png" ) ) );
		}

		System.out.printf( "%s seed %d -> %s  (cell %d, yaw %.0f, %d+%d quads)%n",
			kind, seed, out, stand, yaw, ground.quads, walls.quads );
	}

	static void frameUv( int index, int cols, float[] dst, int at ) {
		int fx = index % cols, fy = index / cols;
		dst[at]     = (fx * TILE_PX)       / (float)atlasW;
		dst[at + 1] = (fy * TILE_PX)       / (float)atlasH;
		dst[at + 2] = ((fx + 1) * TILE_PX) / (float)atlasW;
		dst[at + 3] = ((fy + 1) * TILE_PX) / (float)atlasH;
	}

	/** Camera3D.updateMatrix, line for line. */
	static float[] cameraMatrix( float ex, float ey, float ez, float yaw, float pitch ) {
		float[] proj = new float[16], view = new float[16], m = new float[16];
		float aspect = (float)W / (float)H;
		Matrix.perspective( proj, FOV, aspect, NEAR, FAR );
		Matrix.setIdentity( view );
		Matrix.rotate3( view, -pitch, 1f, 0f, 0f );
		Matrix.rotate3( view, -yaw,   0f, 1f, 0f );
		Matrix.translate3( view, -ex, -ey, -ez );
		Matrix.multiply( proj, view, m );
		return m;
	}

	/**
	 * Where to stand. Three framings, because fifteen shots down the middle
	 * of a straight corridor is one shot fifteen times.
	 *
	 *  corridor - the longest sightline in the level
	 *  room     - the middle of the largest open floor area
	 *  junction - a cell with the most ways out of it
	 */
	static int viewpoint( Level level, boolean[] wall, int width, String mode ) {

		int best = level.entrance, bestScore = -1;
		int rows = wall.length / width;

		for (int c = 0; c < wall.length; c++) {
			if (wall[c]) continue;
			int score;

			if (mode.equals( "room" )) {
				// open cells in a 5x5 block around it
				score = 0;
				int cx = c % width, cy = c / width;
				for (int dy = -2; dy <= 2; dy++) {
					for (int dx = -2; dx <= 2; dx++) {
						int x = cx + dx, y = cy + dy;
						if (x >= 0 && y >= 0 && x < width && y < rows
								&& !wall[y * width + x]) score++;
					}
				}
				// among equally open cells prefer one with a long view too
				score = score * 30 + runLength( wall, width, c, 0 )
					+ runLength( wall, width, c, 1 );

			} else if (mode.equals( "junction" )) {
				int ways = 0, reach = 0;
				for (int d = 0; d < 4; d++) {
					int r = runLength( wall, width, c, d );
					if (r > 0) ways++;
					reach += r;
				}
				score = ways * 100 + reach;

			} else {
				score = 0;
				for (int d = 0; d < 4; d++) score += runLength( wall, width, c, d );
			}

			if (score > bestScore) { bestScore = score; best = c; }
		}
		return best;
	}

	static float bestYaw( boolean[] wall, int width, int cell ) {
		int best = 0, bestRun = -1;
		for (int d = 0; d < 4; d++) {
			int r = runLength( wall, width, cell, d );
			if (r > bestRun) { bestRun = r; best = d; }
		}
		// FirstPerson's headings: 0 north, 1 east, 2 south, 3 west.
		return new float[]{ 0f, -90f, 180f, 90f }[best];
	}

	static int runLength( boolean[] wall, int width, int cell, int dir ) {
		int dx = dir == 1 ? 1 : dir == 3 ? -1 : 0;
		int dy = dir == 2 ? 1 : dir == 0 ? -1 : 0;
		int x = cell % width, y = cell / width, n = 0;
		while (n < 24) {
			x += dx; y += dy;
			if (x < 0 || y < 0 || x >= width || y >= wall.length / width) break;
			if (wall[y * width + x]) break;
			n++;
		}
		return n;
	}

	// ---- rasteriser -----------------------------------------------------

	static void drawMesh( GridMesh m, float[] cam, float bright,
			float tr, float tg, float tb, float add ) {
		float[] v = m.vertices;
		short[] idx = m.indices;
		float[] clip = new float[(v.length / GridMesh.STRIDE) * 4];

		for (int i = 0, o = 0; i < v.length; i += GridMesh.STRIDE, o += 4) {
			project( cam, v[i], v[i + 1], v[i + 2], clip, o );
		}
		for (int t = 0; t + 2 < idx.length; t += 3) {
			clipAndDraw( clip, v, idx[t] & 0xFFFF, idx[t + 1] & 0xFFFF,
				idx[t + 2] & 0xFFFF, bright * tr, bright * tg, bright * tb, add );
		}
	}

	static void project( float[] m, float x, float y, float z, float[] out, int at ) {
		out[at]     = m[0] * x + m[4] * y + m[8]  * z + m[12];
		out[at + 1] = m[1] * x + m[5] * y + m[9]  * z + m[13];
		out[at + 2] = m[2] * x + m[6] * y + m[10] * z + m[14];
		out[at + 3] = m[3] * x + m[7] * y + m[11] * z + m[15];
	}

	/**
	 * Clip against the near plane before rasterising.
	 *
	 * Dropping any triangle with a vertex behind the eye is not good
	 * enough: the wall of the cell the camera stands in runs from in front
	 * of it to behind it, so the whole side face vanished and left a black
	 * notch at the edge of frame. GL clips; a hand-rolled rasteriser has to
	 * be told to. Six floats per vertex: clip xyzw, then uv.
	 */
	static void clipAndDraw( float[] clip, float[] v, int a, int b, int c,
			float br, float bg, float bb, float add ) {

		float[][] poly = new float[4][];
		int n = 0;
		int[] ix = { a, b, c };
		float[][] in = new float[3][];
		for (int k = 0; k < 3; k++) {
			int p = ix[k];
			in[k] = new float[]{ clip[p * 4], clip[p * 4 + 1], clip[p * 4 + 2],
				clip[p * 4 + 3], v[p * GridMesh.STRIDE + 3], v[p * GridMesh.STRIDE + 4] };
		}

		for (int k = 0; k < 3; k++) {
			float[] cur = in[k], nxt = in[(k + 1) % 3];
			boolean curIn = cur[3] >= NEAR, nxtIn = nxt[3] >= NEAR;
			if (curIn) {
				poly[n++] = cur;
			}
			if (curIn != nxtIn) {
				float t = (NEAR - cur[3]) / (nxt[3] - cur[3]);
				float[] mid = new float[6];
				for (int j = 0; j < 6; j++) {
					mid[j] = cur[j] + (nxt[j] - cur[j]) * t;
				}
				poly[n++] = mid;
			}
		}

		if (n < 3) return;
		raster( poly[0], poly[1], poly[2], br, bg, bb, add );
		if (n == 4) raster( poly[0], poly[2], poly[3], br, bg, bb, add );
	}

	static void raster( float[] p0, float[] p1, float[] p2,
			float br, float bg, float bb, float add ) {

		float[][] p = { p0, p1, p2 };
		float[] sx = new float[3], sy = new float[3], iw = new float[3];
		float[] u = new float[3], vv = new float[3];
		for (int k = 0; k < 3; k++) {
			iw[k] = 1f / p[k][3];
			sx[k] = (p[k][0] * iw[k] * 0.5f + 0.5f) * W;
			sy[k] = (1f - (p[k][1] * iw[k] * 0.5f + 0.5f)) * H;
			u[k]  = p[k][4] * iw[k];
			vv[k] = p[k][5] * iw[k];
		}

		float area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0]);
		if (area == 0f) return;

		int minX = Math.max( 0, (int)Math.floor( Math.min( sx[0], Math.min( sx[1], sx[2] ) ) ) );
		int maxX = Math.min( W - 1, (int)Math.ceil(  Math.max( sx[0], Math.max( sx[1], sx[2] ) ) ) );
		int minY = Math.max( 0, (int)Math.floor( Math.min( sy[0], Math.min( sy[1], sy[2] ) ) ) );
		int maxY = Math.min( H - 1, (int)Math.ceil(  Math.max( sy[0], Math.max( sy[1], sy[2] ) ) ) );

		for (int py = minY; py <= maxY; py++) {
			for (int px = minX; px <= maxX; px++) {
				float x = px + 0.5f, y = py + 0.5f;
				float lc = ((sx[1] - sx[0]) * (y - sy[0]) - (x - sx[0]) * (sy[1] - sy[0])) / area;
				float lb = ((x - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (y - sy[0])) / area;
				float la = 1f - lb - lc;
				if (la < 0f || lb < 0f || lc < 0f) continue;

				float invW = la * iw[0] + lb * iw[1] + lc * iw[2];
				if (invW <= 0f) continue;
				float w = 1f / invW;

				int at = py * W + px;
				if (w >= depth[at]) continue;

				float tu = (la * u[0] + lb * u[1] + lc * u[2]) * w;
				float tv = (la * vv[0] + lb * vv[1] + lc * vv[2]) * w;

				int argb = sample( tu, tv );
				if (((argb >>> 24) & 0xFF) < 128) continue;   // the shader's discard

				// The shader is c * uColorM + uColorA, so this is both.
				float r  = ((argb >> 16) & 0xFF) / 255f * br + add;
				float g  = ((argb >> 8)  & 0xFF) / 255f * bg + add;
				float bl = (argb & 0xFF) / 255f * bb + add;

				float lit = clampf( (FOG_FAR - w) / (FOG_FAR - FOG_NEAR), 0f, 1f );
				r  = FOG_R + (r  - FOG_R) * lit;
				g  = FOG_G + (g  - FOG_G) * lit;
				bl = FOG_B + (bl - FOG_B) * lit;

				depth[at] = w;
				pixels[at] = rgb( r, g, bl );
			}
		}
	}

	static int sample( float u, float v ) {
		int x = (int)(u * atlasW);
		int y = (int)(v * atlasH);
		if (x < 0) x = 0; if (x >= atlasW) x = atlasW - 1;
		if (y < 0) y = 0; if (y >= atlasH) y = atlasH - 1;
		return atlas.getRGB( x, y );
	}

	static float clampf( float x, float lo, float hi ) {
		return x < lo ? lo : x > hi ? hi : x;
	}

	static int rgb( float r, float g, float b ) {
		int ri = (int)(clampf( r, 0f, 1f ) * 255f);
		int gi = (int)(clampf( g, 0f, 1f ) * 255f);
		int bi = (int)(clampf( b, 0f, 1f ) * 255f);
		return (ri << 16) | (gi << 8) | bi;
	}

	// ---- level generation, as in DumpLevel -------------------------------

	static Level generate( String kind, int seed ) throws Exception {

		// Mirrors Dungeon.init() minus the UI-only parts, exactly as
		// DumpLevel does. com.watabou.utils.Random has no seed hook -- it
		// goes through Math.random -- so runs are not reproducible; the
		// argument is only an attempt counter.
		com.github.dachhack.sprout.items.Generator.initArtifacts();
		// Without reset() the category weights are empty and Random.chances
		// throws the moment a painter asks for a random item. That aborts
		// build() PART WAY THROUGH PAINTING, and every cell not yet painted
		// stays 0 -- which is Terrain.CHASM, which draws black. The big dark
		// patches in earlier renders were half-generated levels, not a bug
		// in the game.
		com.github.dachhack.sprout.items.Generator.reset();
		com.github.dachhack.sprout.actors.Actor.clear();
		com.github.dachhack.sprout.actors.Actor.resetNextID();
		com.watabou.utils.PathFinder.setMapSize( Level.WIDTH, Level.HEIGHT );
		com.github.dachhack.sprout.items.scrolls.Scroll.initLabels();
		com.github.dachhack.sprout.items.potions.Potion.initColors();
		com.github.dachhack.sprout.items.wands.Wand.initWoods();
		com.github.dachhack.sprout.items.rings.Ring.initGems();
		com.github.dachhack.sprout.Statistics.reset();
		com.github.dachhack.sprout.Journal.reset();
		Dungeon.pars = new int[100];
		Dungeon.hero = new com.github.dachhack.sprout.actors.hero.Hero();
		Dungeon.depth = depthFor( kind );

		Level level = (Level)Class.forName(
			"com.github.dachhack.sprout.levels." + kind )
			.getDeclaredConstructor().newInstance();

		// Terrain is finished by build()+decorate(); item and mob placement
		// can fail off-device and none of it is geometry.
		try {
			level.create();
		} catch (Throwable t) {
			System.err.println( "note: generation stopped at " + t );
		}
		if (level.map == null) throw new IllegalStateException( "no map produced" );

		Dungeon.level = level;
		return level;
	}

	/** Each zone has its own tile sheet, exactly as Level.tilesTex() says. */
	static String sheetFor( String kind ) {
		String f = "tiles0.png";
		if (kind.startsWith( "Prison" )) f = "tiles1.png";
		else if (kind.startsWith( "Caves" )) f = "tiles2.png";
		else if (kind.startsWith( "City" ))  f = "tiles3.png";
		else if (kind.startsWith( "Halls" )) f = "tiles4.png";
		return "app/src/main/assets/" + f;
	}

	static int depthFor( String kind ) {
		if (kind.startsWith( "Prison" )) return 7;
		if (kind.startsWith( "Caves" ))  return 12;
		if (kind.startsWith( "City" ))   return 17;
		if (kind.startsWith( "Halls" ))  return 22;
		return 2;
	}
}
