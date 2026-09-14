/*
 * Sprouted Pixel Dungeon — level exporter for the web demo
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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import com.github.dachhack.sprout.levels.Level;
import com.github.dachhack.sprout.levels.Terrain;

/**
 * Writes a real generated dungeon out as JSON for the browser demo.
 *
 * The point is that the web version walks a dungeon Sprouted actually
 * produced — same generator, same room grammar, same decoration — rather
 * than a hand-drawn approximation. Only the map is exported, about 6 KB;
 * the geometry is rebuilt in the browser by the JavaScript port of
 * GridMesh, so the two stay in step by construction.
 *
 * Three flags per cell are exported alongside the terrain id, because the
 * demo needs them and they belong to the game, not to the renderer:
 * solid (is it a wall), liquid (water, whose tile is transparent) and
 * losBlocking (high grass, which has to stand up).
 */
public class ExportLevel {

	public static void main( String[] args ) throws Exception {

		String kind = args.length > 0 ? args[0] : "SewerLevel";
		String out  = args.length > 1 ? args[1] : "level.json";

		java.lang.reflect.Method gen =
			Class.forName( "Render3D" ).getDeclaredMethod( "generate", String.class, int.class );
		gen.setAccessible( true );
		Level level = (Level)gen.invoke( null, kind, 1 );

		int width = Level.getWidth();
		int[] map = level.map;

		StringBuilder sb = new StringBuilder( 1 << 16 );
		sb.append( "{\n" );
		sb.append( "  \"kind\": \"" ).append( kind ).append( "\",\n" );
		sb.append( "  \"width\": " ).append( width ).append( ",\n" );
		sb.append( "  \"rows\": " ).append( map.length / width ).append( ",\n" );
		sb.append( "  \"entrance\": " ).append( level.entrance ).append( ",\n" );
		sb.append( "  \"exit\": " ).append( level.exit ).append( ",\n" );
		sb.append( "  \"tiles\": \"" ).append( sheetFor( kind ) ).append( "\",\n" );

		// The terrain ids themselves, for texturing.
		sb.append( "  \"map\": [" );
		for (int i = 0; i < map.length; i++) {
			if (i > 0) sb.append( ',' );
			sb.append( map[i] );
		}
		sb.append( "],\n" );

		// Classification straight from Terrain.flags, so the browser never
		// has to guess what counts as a wall.
		sb.append( "  \"solid\": \"" ).append( bits( map, Terrain.SOLID ) ).append( "\",\n" );
		sb.append( "  \"liquid\": \"" ).append( bits( map, Terrain.LIQUID ) ).append( "\",\n" );
		sb.append( "  \"grass\": \"" ).append( grassBits( map ) ).append( "\"\n" );
		sb.append( "}\n" );

		Files.write( Paths.get( out ), sb.toString().getBytes( StandardCharsets.UTF_8 ) );

		int open = 0;
		for (int c : map) if ((Terrain.flags[c] & Terrain.SOLID) == 0) open++;
		System.out.printf( "%s -> %s  (%d cells, %d open, %d KB)%n",
			kind, out, map.length, open, sb.length() / 1024 );
	}

	/** One character per cell, '1' or '0' -- compact and human-readable. */
	private static String bits( int[] map, int flag ) {
		StringBuilder b = new StringBuilder( map.length );
		for (int t : map) {
			b.append( (Terrain.flags[t] & flag) != 0 ? '1' : '0' );
		}
		return b.toString();
	}

	/** High grass: passable but sight-blocking, so it is drawn standing. */
	private static String grassBits( int[] map ) {
		StringBuilder b = new StringBuilder( map.length );
		for (int t : map) {
			b.append( t == Terrain.HIGH_GRASS ? '1' : '0' );
		}
		return b.toString();
	}

	private static String sheetFor( String kind ) {
		if (kind.startsWith( "Prison" )) return "tiles1.png";
		if (kind.startsWith( "Caves" ))  return "tiles2.png";
		if (kind.startsWith( "City" ))   return "tiles3.png";
		if (kind.startsWith( "Halls" ))  return "tiles4.png";
		return "tiles0.png";
	}
}
