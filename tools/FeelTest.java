/*
 * Sprouted Pixel Dungeon — tuning wiring lint
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
import java.util.*;
import java.util.regex.*;

/**
 * Every knob in Feel must actually reach something.
 *
 * A central config file trades one problem for another: instead of numbers
 * scattered across eight files, you get numbers that look authoritative and
 * quietly do nothing because apply() was never updated. That failure is
 * invisible -- the value is right there in the file, spelled correctly,
 * and turning it changes nothing.
 *
 * So: every public field declared in Feel has to appear inside apply().
 */
public class FeelTest {

	public static void main( String[] args ) throws Exception {

		Path feel = Paths.get(
			"app/src/main/java/com/github/dachhack/sprout/Feel.java" );
		String src = new String( Files.readAllBytes( feel ), StandardCharsets.UTF_8 );

		String body = applyBody( src );
		if (body.isEmpty()) {
			System.out.println( "  FAIL  could not find apply() in Feel.java" );
			System.out.println( "=== FEELTEST FAILED" );
			System.exit( 1 );
		}

		List<String> fields = fields( src );
		if (fields.isEmpty()) {
			System.out.println( "  FAIL  found no fields in Feel.java -- the lint is broken" );
			System.out.println( "=== FEELTEST FAILED" );
			System.exit( 1 );
		}

		int bad = 0;
		for (String f : fields) {
			// Word boundary, so `waterR` cannot be satisfied by `waterRange`.
			if (!Pattern.compile( "\\b" + Pattern.quote( f ) + "\\b" )
					.matcher( body ).find()) {
				System.out.println( "  FAIL  Feel." + f
					+ " is declared but never wired in apply() -- turning it does nothing" );
				bad++;
			}
		}

		System.out.printf( "%-56s %s%n",
			fields.size() + " tunables, all wired through apply()",
			bad == 0 ? "ok" : "FAILED" );

		if (bad > 0) {
			System.out.println();
			System.out.println( "=== FEELTEST FAILED (" + bad + " of " + fields.size() + ")" );
			System.exit( 1 );
		}
		System.out.println();
		System.out.println( "=== FEELTEST PASSED (" + fields.size() + " tunables)" );
	}

	/** Public static fields of Feel, including `float a = 1, b = 2;` runs. */
	private static List<String> fields( String src ) {
		List<String> out = new ArrayList<String>();
		Matcher m = Pattern.compile(
			"(?m)^\\s*public\\s+static\\s+(?:float|int|boolean)\\s+([^;]+);" )
			.matcher( strip( src ) );
		while (m.find()) {
			for (String part : m.group( 1 ).split( "," )) {
				String name = part.trim().split( "\\s*=" )[0].trim();
				if (!name.isEmpty()) {
					out.add( name );
				}
			}
		}
		return out;
	}

	private static String applyBody( String src ) {
		int at = src.indexOf( "public static void apply()" );
		if (at < 0) return "";
		int open = src.indexOf( '{', at );
		if (open < 0) return "";
		int depth = 0;
		for (int i = open; i < src.length(); i++) {
			char c = src.charAt( i );
			if (c == '{') depth++;
			else if (c == '}') {
				depth--;
				if (depth == 0) return src.substring( open, i );
			}
		}
		return "";
	}

	/** Remove comments so a field named only in prose does not count. */
	private static String strip( String src ) {
		src = src.replaceAll( "(?s)/\\*.*?\\*/", "" );
		src = src.replaceAll( "(?m)//.*$", "" );
		return src;
	}
}
