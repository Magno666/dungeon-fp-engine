/*
 * Sprouted Pixel Dungeon — JS port check
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

import com.github.dachhack.sprout.GridMesh;
import com.github.dachhack.sprout.levels.Terrain;
import java.nio.file.*;
import java.util.regex.*;

/**
 * Counts the faces GridMesh emits for an exported level, so the browser
 * demo's JavaScript port can be checked against the real thing.
 *
 * The web demo reimplements GridMesh in JS. That port is a copy, and
 * copies drift: change a face rule here and the demo keeps showing the
 * old geometry, with nothing to say so. Run this and the JS equivalent
 * (demo/portcheck.mjs) over the same exported map and the two lines of
 * numbers must match exactly.
 */
public class PortCheck {
	public static void main(String[] a) throws Exception {
		String j = new String(Files.readAllBytes(Paths.get(a[0])), "UTF-8");
		int width = Integer.parseInt(field(j, "width"));
		int[] map = ints(j);
		boolean[] wall = new boolean[map.length], water = new boolean[map.length];
		for (int c = 0; c < map.length; c++) {
			wall[c]  = (Terrain.flags[map[c]] & Terrain.SOLID) != 0;
			water[c] = (Terrain.flags[map[c]] & Terrain.LIQUID) != 0;
		}
		int[][] f = {{GridMesh.GROUND,0},{GridMesh.WATER,0},{GridMesh.CEILING,0},
		             {GridMesh.WALLS_NS,0},{GridMesh.WALLS_EW,0}};
		String[] names = {"GROUND","WATER","CEILING","WALLS_NS","WALLS_EW"};
		StringBuilder sb = new StringBuilder("{");
		for (int i = 0; i < f.length; i++) {
			GridMesh m = GridMesh.build(wall, width, 3f, 3.2f, true, null, null, f[i][0], water);
			if (i > 0) sb.append(',');
			sb.append('"').append(names[i]).append("\":").append(m.quads);
		}
		System.out.println(sb.append('}'));
	}
	static String field(String j, String k) {
		Matcher m = Pattern.compile("\"" + k + "\":\\s*(\\d+)").matcher(j);
		return m.find() ? m.group(1) : "0";
	}
	static int[] ints(String j) {
		Matcher m = Pattern.compile("\"map\":\\s*\\[([^\\]]*)\\]").matcher(j);
		m.find();
		String[] parts = m.group(1).split(",");
		int[] out = new int[parts.length];
		for (int i = 0; i < parts.length; i++) out[i] = Integer.parseInt(parts[i].trim());
		return out;
	}
}
