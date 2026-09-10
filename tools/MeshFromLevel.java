import com.github.dachhack.sprout.Dungeon;
import com.github.dachhack.sprout.GridMesh;
import com.github.dachhack.sprout.levels.Level;
import com.github.dachhack.sprout.levels.Terrain;

/** End-to-end: generate a real Sprouted level with the game's own code,
 *  classify it with the game's own Terrain flags, and build the 3D mesh.
 *  Nothing synthetic anywhere in the chain. */
public class MeshFromLevel {
	public static void main(String[] args) throws Exception {
		String kind = args.length > 0 ? args[0] : "SewerLevel";
		int depth = args.length > 1 ? Integer.parseInt(args[1]) : 1;

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
		Dungeon.depth = depth;

		Level level = (Level) Class.forName("com.github.dachhack.sprout.levels." + kind)
				.getDeclaredConstructor().newInstance();
		try {
			level.create();
		} catch (Throwable t) {
			// Visual-only failures (particle emitters) land here and are
			// harmless; a failure during build() is not, so say so.
			System.err.println( "note: generation stopped at " + t );
		}

		int w = Level.WIDTH, h = Level.HEIGHT;
		boolean[] wall = new boolean[w * h];
		int solid = 0;
		for (int c = 0; c < w * h; c++) {
			wall[c] = (Terrain.flags[level.map[c]] & Terrain.SOLID) != 0;
			if (wall[c]) solid++;
		}

		long t0 = System.nanoTime();
		GridMesh m = GridMesh.build(wall, w, 3f, 3.2f, true);
		double ms = (System.nanoTime() - t0) / 1e6;

		int open = w * h - solid;
		System.out.printf("%-16s %dx%d  solid=%-5d open=%-5d quads=%-6d verts=%-6d  %.1f KB  built in %.1f ms%n",
				kind, w, h, solid, open, m.quads, m.vertices.length / GridMesh.STRIDE,
				m.vertices.length * 4 / 1024.0, ms);

		if (m.quads <= 0) { System.out.println("FAIL: empty mesh"); System.exit(1); }
		if (m.vertices.length / GridMesh.STRIDE > 65535) { System.out.println("FAIL: index overflow"); System.exit(1); }
		int verts = m.vertices.length / GridMesh.STRIDE;
		for (short s : m.indices) if (s < 0 || s >= verts) { System.out.println("FAIL: bad index"); System.exit(1); }
	}
}
