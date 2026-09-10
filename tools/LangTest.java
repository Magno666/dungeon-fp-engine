/*
 * Sprouted Pixel Dungeon — dictionary lint
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
 * Checks the Spanish dictionary against the code it is meant to translate.
 *
 * Two ways a string dictionary fails, both silent:
 *
 *  1. The English side does not match the source exactly — one comma, one
 *     capital, one trailing space — so the lookup misses and the game
 *     quietly stays in English with no error anywhere.
 *
 *  2. The translation has different format markers than the original.
 *     That one is not silent: String.format throws at runtime, in front
 *     of the player, on whatever screen happens to use that line.
 */
public class LangTest {

	private static int checks = 0, failures = 0;

	public static void main( String[] args ) throws Exception {

		Path dictPath = Paths.get( "app/src/main/assets/lang/es.txt" );
		Path srcRoot  = Paths.get( "app/src/main/java" );

		List<String[]> pairs = pairs( dictPath );
		if (pairs.isEmpty()) {
			fail( "the dictionary has no en:/tr: pairs" );
			done();
		}

		String haystack = allSource( srcRoot );

		for (String[] p : pairs) {
			String en = p[0], tr = p[1];

			checks++;
			if (!haystack.contains( javaLiteral( en ) )) {
				fail( "no source string matches exactly:\n          en: " + en );
			}

			checks++;
			List<String> a = markers( en ), b = markers( tr );
			if (!a.equals( b )) {
				fail( "format markers differ -- this CRASHES at runtime:\n"
					+ "          en: " + en + "   " + a + "\n"
					+ "          tr: " + tr + "   " + b );
			}
		}

		Set<String> seen = new HashSet<String>();
		for (String[] p : pairs) {
			checks++;
			if (!seen.add( p[0] )) {
				fail( "duplicated entry: " + p[0] );
			}
		}

		System.out.printf( "%-56s %s%n",
			pairs.size() + " translations check out against the source",
			failures == 0 ? "ok" : "FAILED" );
		done();
	}

	private static void done() {
		System.out.println();
		if (failures > 0) {
			System.out.println( "=== LANGTEST FAILED (" + failures + " of " + checks + ")" );
			System.exit( 1 );
		}
		System.out.println( "=== LANGTEST PASSED (" + checks + " checks)" );
		System.exit( 0 );
	}

	private static List<String[]> pairs( Path p ) throws IOException {
		List<String[]> out = new ArrayList<String[]>();
		String en = null;
		for (String line : Files.readAllLines( p, StandardCharsets.UTF_8 )) {
			if (line.startsWith( "#" ) || line.trim().isEmpty()) continue;
			if (line.startsWith( "en:" )) {
				en = line.substring( 3 );
			} else if (line.startsWith( "tr:" ) && en != null) {
				out.add( new String[]{ en, line.substring( 3 ) } );
				en = null;
			}
		}
		return out;
	}

	/** Every .java concatenated, so a `contains` is one pass not thousands. */
	private static String allSource( Path root ) throws IOException {
		final StringBuilder sb = new StringBuilder( 1 << 22 );
		Files.walkFileTree( root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile( Path f, java.nio.file.attribute.BasicFileAttributes a ) throws IOException {
				if (f.toString().endsWith( ".java" )) {
					sb.append( new String( Files.readAllBytes( f ), StandardCharsets.UTF_8 ) );
					sb.append( '\n' );
				}
				return FileVisitResult.CONTINUE;
			}
		} );
		return sb.toString();
	}

	/** How the string would appear inside Java source, quotes and all. */
	private static String javaLiteral( String s ) {
		return '"' + s.replace( "\\", "\\\\" ).replace( "\"", "\\\"" ) + '"';
	}

	private static List<String> markers( String s ) {
		List<String> out = new ArrayList<String>();
		Matcher m = Pattern.compile( "%[-+ 0#,]*\\d*(?:\\.\\d+)?[a-zA-Z]" ).matcher( s );
		while (m.find()) out.add( m.group() );
		return out;
	}

	private static void fail( String msg ) {
		failures++;
		System.out.println( "  FAIL  " + msg );
	}
}
