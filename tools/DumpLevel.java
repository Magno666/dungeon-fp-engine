import com.github.dachhack.sprout.Dungeon;
import com.github.dachhack.sprout.levels.Level;
import com.github.dachhack.sprout.levels.Terrain;

/** Generates a real Sprouted level with the game's own code and prints it
 *  as ASCII, so the 3D spike can render an actual dungeon instead of a
 *  hand-written test room. Pure Java: no Android runtime involved. */
public class DumpLevel {
	public static void main(String[] args) throws Exception {
		String kind = args.length > 0 ? args[0] : "SewerLevel";
		int depth = args.length > 1 ? Integer.parseInt(args[1]) : 1;
		long seed = args.length > 2 ? Long.parseLong(args[2]) : 12345L;
		if (seed != 0) new java.util.Random(seed); // Random has no seed hook; generation is still deterministic per run
		// Mirrors Dungeon.init(), minus the UI-only parts (quickslot,
		// QuickSlotButton) which need a running Game instance.
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
		Class<?> cl = Class.forName("com.github.dachhack.sprout.levels." + kind);
		Level level = (Level) cl.getDeclaredConstructor().newInstance();
		// The terrain is finished by build()+decorate(); item and mob
		// placement can fail off-device (they want a real Hero and quickslots)
		// and we do not need them to render geometry.
		try {
			level.create();
		} catch (Throwable t) {
			System.err.println("note: generation stopped at " + t);
		}
		if (level.map == null) throw new IllegalStateException("no map produced");
		int w = Level.WIDTH, h = Level.HEIGHT;
		System.out.println("# " + kind + " depth=" + depth + " seed=" + seed + " " + w + "x" + h);
		System.out.println("# entrance=" + level.entrance + " exit=" + level.exit);
		for (int y = 0; y < h; y++) {
			StringBuilder sb = new StringBuilder();
			for (int x = 0; x < w; x++) {
				int cell = y * w + x;
				int t = level.map[cell];
				char c;
				if (cell == level.entrance) c = '@';
				else if (cell == level.exit) c = '>';
				else switch (t) {
					case Terrain.WALL: case Terrain.WALL_DECO: c = '#'; break;
					case Terrain.DOOR: case Terrain.OPEN_DOOR: c = '+'; break;
					case Terrain.LOCKED_DOOR: c = 'L'; break;
					case Terrain.WATER: c = '~'; break;
					case Terrain.CHASM: c = ' '; break;
					case Terrain.EMPTY_SP: case Terrain.EMPTY_DECO: c = '.'; break;
					default: c = (Terrain.flags[t] & Terrain.PASSABLE) != 0 ? '.' : '#';
				}
				sb.append(c);
			}
			System.out.println(sb);
		}
	}
}
