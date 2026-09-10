import com.github.dachhack.sprout.*;
import com.github.dachhack.sprout.levels.*;
import com.watabou.glwrap.Matrix;

/** Camera and mesh must agree on world space. Puts the camera where the
 *  game would put it — the hero's entrance cell — and projects the real
 *  mesh through the real projection to see what would actually be on screen. */
public class ViewTest {
	static int fails = 0;
	static void check(String what, boolean ok, String detail) {
		System.out.printf("%-56s %s%s%n", what, ok ? "ok" : "FAIL", ok ? "" : "  " + detail);
		if (!ok) fails++;
	}

	/** Exactly what Camera3D.updateMatrix() composes. */
	static float[] mvp(float ex, float ey, float ez, float yaw, float pitch, float aspect) {
		float[] proj = new float[16], view = new float[16], out = new float[16];
		Matrix.perspective(proj, 75f, aspect, 0.05f, 200f);
		Matrix.setIdentity(view);
		Matrix.rotate3(view, -pitch, 1, 0, 0);
		Matrix.rotate3(view, -yaw, 0, 1, 0);
		Matrix.translate3(view, -ex, -ey, -ez);
		Matrix.multiply(proj, view, out);
		return out;
	}

	public static void main(String[] a) throws Exception {
		com.github.dachhack.sprout.items.Generator.initArtifacts();
		// reset() fills the category weights. Without it the first painter
		// that asks for a random item throws, build() stops PART WAY THROUGH
		// painting, and every unpainted cell stays 0 == Terrain.CHASM. These
		// suites were quietly asserting against half-built levels.
		com.github.dachhack.sprout.items.Generator.reset();
		com.github.dachhack.sprout.actors.Actor.clear();
		com.github.dachhack.sprout.actors.Actor.resetNextID();
		com.watabou.utils.PathFinder.setMapSize(Level.WIDTH, Level.HEIGHT);
		com.github.dachhack.sprout.items.scrolls.Scroll.initLabels();
		com.github.dachhack.sprout.items.potions.Potion.initColors();
		com.github.dachhack.sprout.items.wands.Wand.initWoods();
		com.github.dachhack.sprout.items.rings.Ring.initGems();
		com.github.dachhack.sprout.Statistics.reset();
		com.github.dachhack.sprout.Journal.reset();
		Dungeon.pars = new int[100];
		Dungeon.hero = new com.github.dachhack.sprout.actors.hero.Hero();
		Dungeon.depth = 1;

		Level level = new SewerLevel();
		try {
			level.create();
		} catch (Throwable t) {
			// Visual-only failures (particle emitters) land here and are
			// harmless; a failure during build() is not, so say so.
			System.err.println( "note: generation stopped at " + t );
		}
		int w = Level.WIDTH;
		GridMesh m = DungeonTilemap3D.mesh(level.map, w);

		// where FirstPerson.update() would put the camera
		float ex = DungeonTilemap3D.worldX(level.entrance, w);
		float ez = DungeonTilemap3D.worldZ(level.entrance, w);
		float ey = 1.6f;

		// the camera must land inside the meshed volume, not off in space
		float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
		float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		for (int i = 0; i < m.vertices.length; i += GridMesh.STRIDE) {
			minX = Math.min(minX, m.vertices[i]);     maxX = Math.max(maxX, m.vertices[i]);
			minZ = Math.min(minZ, m.vertices[i + 2]); maxZ = Math.max(maxZ, m.vertices[i + 2]);
		}
		check("camera lands inside the mesh bounds",
				ex > minX && ex < maxX && ez > minZ && ez < maxZ,
				"eye " + ex + "," + ez + " bounds x " + minX + ".." + maxX);
		check("eye sits between floor and ceiling",
				ey > 0f && ey < DungeonTilemap3D.HEIGHT, "y " + ey);

		// project every vertex and see what a frame would contain
		int total = m.vertices.length / GridMesh.STRIDE;
		int onScreen = 0, inFront = 0;
		for (int yawStep = 0; yawStep < 4; yawStep++) {
			float[] mat = mvp(ex, ey, ez, yawStep * 90f, 0f, 16f / 9f);
			int seen = 0;
			for (int i = 0; i < m.vertices.length; i += GridMesh.STRIDE) {
				float x = m.vertices[i], y = m.vertices[i + 1], z = m.vertices[i + 2];
				float cx = mat[0]*x + mat[4]*y + mat[8]*z + mat[12];
				float cy = mat[1]*x + mat[5]*y + mat[9]*z + mat[13];
				float cw = mat[3]*x + mat[7]*y + mat[11]*z + mat[15];
				if (cw > 0) {
					inFront++;
					if (Math.abs(cx) <= cw && Math.abs(cy) <= cw) { onScreen++; seen++; }
				}
			}
			if (seen == 0) {
				check("facing " + (yawStep * 90) + " has geometry on screen", false, "nothing visible");
			}
		}
		check("every facing shows some geometry", fails == 0, "");
		check("some vertices are in front of the camera", inFront > 0, "" + inFront);
		check("not everything is on screen at once",
				onScreen < total * 4, "onScreen " + onScreen + " of " + (total * 4));
		System.out.printf("   (%d verts; across 4 facings %d in front, %d on screen)%n",
				total, inFront, onScreen);

		// standing inside a wall would mean the eye height or grid is wrong
		boolean entranceSolid = (Terrain.flags[level.map[level.entrance]] & Terrain.SOLID) != 0;
		check("the camera is not inside solid rock", !entranceSolid, "");

		System.out.println(fails == 0 ? "=== VIEWTEST PASSED" : "=== VIEWTEST FAILED (" + fails + ")");
		System.exit(fails == 0 ? 0 : 1);
	}
}
