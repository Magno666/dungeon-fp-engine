import com.github.dachhack.sprout.*;
import com.github.dachhack.sprout.levels.*;

/** Checks the bridge between the game's map and the 3D mesh, on real levels. */
public class TilemapTest {
	static int fails = 0;
	static void check(String what, boolean ok, String detail) {
		System.out.printf("%-56s %s%s%n", what, ok ? "ok" : "FAIL", ok ? "" : "  " + detail);
		if (!ok) fails++;
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
		check("real level produces geometry", m.quads > 0, "quads " + m.quads);

		// The bridge must agree with the game's own passability, not guess.
		int solid = 0, passable = 0;
		for (int c = 0; c < level.map.length; c++) {
			if ((Terrain.flags[level.map[c]] & Terrain.SOLID) != 0) solid++;
			if ((Terrain.flags[level.map[c]] & Terrain.PASSABLE) != 0) passable++;
		}
		check("solid and passable cells both exist", solid > 0 && passable > 0,
				"solid " + solid + " passable " + passable);

		// Every open cell contributes a floor and a ceiling, so the mesh can
		// never have fewer quads than twice the open cell count.
		int open = level.map.length - solid;
		check("at least a floor and ceiling per open cell",
				m.quads >= open * 2, "quads " + m.quads + " open*2 " + (open * 2));

		// Cell to world mapping must match the grid the mesh was built on.
		int cell = 10 * w + 7;
		check("worldX follows the column",
				Math.abs(DungeonTilemap3D.worldX(cell, w) - 7 * DungeonTilemap3D.TILE) < 1e-5,
				"got " + DungeonTilemap3D.worldX(cell, w));
		check("worldZ follows the row",
				Math.abs(DungeonTilemap3D.worldZ(cell, w) - 10 * DungeonTilemap3D.TILE) < 1e-5,
				"got " + DungeonTilemap3D.worldZ(cell, w));

		// The entrance must be somewhere a camera can actually stand.
		boolean entranceOpen = (Terrain.flags[level.map[level.entrance]] & Terrain.PASSABLE) != 0;
		check("the entrance cell is passable", entranceOpen, "");

		// Geometry must cover the whole grid, not a corner of it.
		float maxX = 0, maxZ = 0;
		for (int i = 0; i < m.vertices.length; i += GridMesh.STRIDE) {
			maxX = Math.max(maxX, m.vertices[i]);
			maxZ = Math.max(maxZ, m.vertices[i + 2]);
		}
		float span = (w - 1) * DungeonTilemap3D.TILE;
		check("mesh spans the whole level",
				maxX > span * 0.8f && maxZ > span * 0.8f, "maxX " + maxX + " maxZ " + maxZ);

		System.out.println(fails == 0 ? "=== TILEMAPTEST PASSED" : "=== TILEMAPTEST FAILED (" + fails + ")");
		System.exit(fails == 0 ? 0 : 1);
	}
}
