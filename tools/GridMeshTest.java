import com.github.dachhack.sprout.GridMesh;

/** Off-device check of the dungeon mesh builder. */
public class GridMeshTest {
	static int fails = 0;

	static void check(String what, boolean ok, String detail) {
		System.out.printf("%-56s %s%s%n", what, ok ? "ok" : "FAIL", ok ? "" : "  " + detail);
		if (!ok) fails++;
	}

	static boolean[] parse(String... rows) {
		int w = rows[0].length();
		boolean[] out = new boolean[w * rows.length];
		for (int r = 0; r < rows.length; r++)
			for (int c = 0; c < w; c++)
				out[r * w + c] = rows[r].charAt(c) == '#';
		return out;
	}

	public static void main(String[] a) {
		// One open cell ringed by wall: floor + ceiling + four inward faces.
		boolean[] room = parse("###", "#.#", "###");
		GridMesh m = GridMesh.build(room, 3, 2f, 3f, true);
		check("single cell room builds 6 quads", m.quads == 6, "got " + m.quads);
		check("vertex array sized to the quad count",
				m.vertices.length == 6 * 4 * GridMesh.STRIDE, "len " + m.vertices.length);
		check("index array sized to the quad count",
				m.indices.length == 6 * 6, "len " + m.indices.length);

		// Rock with no open neighbour must contribute nothing at all.
		boolean[] solid = parse("###", "###", "###");
		GridMesh none = GridMesh.build(solid, 3, 2f, 3f, true);
		check("fully enclosed rock emits no geometry", none.quads == 0, "got " + none.quads);

		// Ceiling off halves what an open cell costs.
		GridMesh noCeil = GridMesh.build(room, 3, 2f, 3f, false);
		check("ceiling flag removes exactly one quad", noCeil.quads == 5, "got " + noCeil.quads);

		// Two adjacent open cells: the wall between them does not exist, so
		// the shared side is never drawn twice.
		GridMesh pair = GridMesh.build(parse("####", "#..#", "####"), 4, 2f, 3f, false);
		// 2 floors + 6 exposed wall faces (2 north, 2 south, 1 west, 1 east)
		check("adjacent cells share no duplicate faces", pair.quads == 8, "got " + pair.quads);

		// Geometry lands where the grid says it should.
		float tile = 2f;
		GridMesh g = GridMesh.build(room, 3, tile, 3f, false);
		float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
		float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		for (int i = 0; i < g.vertices.length; i += GridMesh.STRIDE) {
			minX = Math.min(minX, g.vertices[i]);     maxX = Math.max(maxX, g.vertices[i]);
			minY = Math.min(minY, g.vertices[i + 1]); maxY = Math.max(maxY, g.vertices[i + 1]);
		}
		// the open cell is col 1 -> centre x = 2, so the room spans 1..3
		check("open cell spans one tile around its centre",
				Math.abs(minX - 1f) < 1e-5 && Math.abs(maxX - 3f) < 1e-5,
				"x " + minX + ".." + maxX);
		check("geometry sits between floor and wall height",
				Math.abs(minY) < 1e-5 && Math.abs(maxY - 3f) < 1e-5,
				"y " + minY + ".." + maxY);

		// Indices must never point outside the vertex array.
		int verts = g.vertices.length / GridMesh.STRIDE;
		boolean inRange = true;
		for (short s : g.indices) if (s < 0 || s >= verts) inRange = false;
		check("every index points at a real vertex", inRange, "");

		// UVs stay inside the unit square.
		boolean uvOk = true;
		for (int i = 0; i < g.vertices.length; i += GridMesh.STRIDE) {
			float u = g.vertices[i + 3], w = g.vertices[i + 4];
			if (u < 0 || u > 1 || w < 0 || w > 1) uvOk = false;
		}
		check("UVs stay inside the unit square", uvOk, "");

		// A realistic level must fit under the 16-bit index limit.
		int W = 48;
		boolean[] big = new boolean[W * W];
		for (int r = 0; r < W; r++)
			for (int c = 0; c < W; c++)
				big[r * W + c] = (r == 0 || c == 0 || r == W - 1 || c == W - 1 || (r % 7 == 0 && c % 5 == 0));
		GridMesh full = GridMesh.build(big, W, 3f, 3.2f, true);
		check("a 48x48 level fits in 16-bit indices",
				full.quads * 4 <= 65535, full.quads + " quads");
		System.out.println("   (48x48 level: " + full.quads + " quads, "
				+ full.vertices.length * 4 / 1024 + " KB of vertices)");

		System.out.println(fails == 0 ? "=== GRIDMESHTEST PASSED" : "=== GRIDMESHTEST FAILED (" + fails + ")");
		System.exit(fails == 0 ? 0 : 1);
	}
}
