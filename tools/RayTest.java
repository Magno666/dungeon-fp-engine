import com.github.dachhack.sprout.FirstPerson;
import com.github.dachhack.sprout.DungeonTilemap3D;

/** Tap-to-cell in first person. This replaces the only part of the game's
 *  aiming that assumed a top-down map, so it had better be right. */
public class RayTest {
	static int fails = 0;
	static final float W = 1920, H = 886;                 // a landscape phone
	static final float ASPECT = W / H, FOVY = 50f;
	static final int MAPW = 48, MAPR = 48;
	static final float T = DungeonTilemap3D.TILE;

	static void check(String what, boolean ok, String detail) {
		System.out.printf("%-56s %s%s%n", what, ok ? "ok" : "FAIL", ok ? "" : "  " + detail);
		if (!ok) fails++;
	}

	static int cast(float sx, float sy, float ex, float ez, float yaw, float pitch) {
		return FirstPerson.cellFromRay(sx, sy, W, H, FOVY, ASPECT,
				ex, 1.6f, ez, yaw, pitch, MAPW, MAPR);
	}

	static int col(int cell) { return cell % MAPW; }
	static int row(int cell) { return cell / MAPW; }

	public static void main(String[] a) {
		float ex = 10 * T, ez = 20 * T;   // standing on cell (10,20)

		// Looking dead level, the floor is never met.
		check("level gaze hits nothing", cast(W/2, H/2, ex, ez, 0, 0) == -1, "");

		// Looking up hits nothing either.
		check("looking up hits nothing", cast(W/2, H/2, ex, ez, 0, 20f) == -1, "");

		// Tilted down and facing north (-Z), the centre of the screen lands
		// on the same column, on a row ahead of us.
		int c = cast(W/2, H/2, ex, ez, 0f, -30f);
		check("looking down the centre lands ahead, same column",
				c >= 0 && col(c) == 10 && row(c) < 20, c < 0 ? "missed" : col(c) + "," + row(c));

		// Steeper means closer; shallower means further.
		int near = cast(W/2, H/2, ex, ez, 0f, -60f);
		int far  = cast(W/2, H/2, ex, ez, 0f, -20f);
		check("steeper angle lands closer",
				near >= 0 && far >= 0 && (20 - row(near)) < (20 - row(far)),
				"near row " + (near<0?-1:row(near)) + " far row " + (far<0?-1:row(far)));

		// Tapping lower on the screen is closer than tapping higher.
		int low  = cast(W/2, H * 0.9f, ex, ez, 0f, -30f);
		int high = cast(W/2, H * 0.6f, ex, ez, 0f, -30f);
		check("lower on screen is nearer than higher",
				low >= 0 && high >= 0 && (20 - row(low)) < (20 - row(high)),
				"low " + (low<0?-1:row(low)) + " high " + (high<0?-1:row(high)));

		// Turning 90 degrees points west: same row, smaller column.
		int west = cast(W/2, H/2, ex, ez, 90f, -30f);
		check("yaw 90 lands west, same row",
				west >= 0 && row(west) == 20 && col(west) < 10,
				west < 0 ? "missed" : col(west) + "," + row(west));

		// And 180 points south.
		int south = cast(W/2, H/2, ex, ez, 180f, -30f);
		check("yaw 180 lands south, same column",
				south >= 0 && col(south) == 10 && row(south) > 20,
				south < 0 ? "missed" : col(south) + "," + row(south));

		// Tapping right of centre lands right of the heading.
		int right = cast(W * 0.75f, H/2, ex, ez, 0f, -30f);
		check("right of centre lands to the east when facing north",
				right >= 0 && col(right) > 10, right < 0 ? "missed" : col(right) + "," + row(right));

		// Straight down at your feet is the cell you are standing on.
		int feet = cast(W/2, H/2, ex, ez, 0f, -90f);
		check("straight down is the cell you stand on",
				feet >= 0 && col(feet) == 10 && row(feet) == 20,
				feet < 0 ? "missed" : col(feet) + "," + row(feet));

		// A ray that leaves the map returns -1 rather than a wrong cell.
		check("off the map returns -1",
				cast(W/2, H/2, 0f, 0f, 180f, -0.5f) == -1, "");

		System.out.println(fails == 0 ? "=== RAYTEST PASSED" : "=== RAYTEST FAILED (" + fails + ")");
		System.exit(fails == 0 ? 0 : 1);
	}
}
