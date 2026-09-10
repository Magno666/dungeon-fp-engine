/*
 * Sprouted Pixel Dungeon — scene construction order lint
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
 * Catches the crash that shipped in v17: a first-person hook touched
 * GameScene.statuses and .spells to hide them, but sat above the lines
 * that build them. They are instance fields, so they were null on every
 * single entry -- the menu worked and the game died the moment you
 * pressed play.
 *
 * javac cannot see this. Definite-assignment analysis covers locals, not
 * fields; a field read before its assignment is legal Java that reads
 * null. The other six suites are pure logic and never construct a scene.
 * So this walks the source of create() instead: every scene field that is
 * dereferenced must have been assigned on an earlier line.
 *
 * Deliberately dumb -- it is a lint over text, not a compiler. It only
 * looks at fields declared `private Group x;` and friends in the class,
 * and only inside the one method where the scene is built.
 */
public class SceneOrderTest {

	private static int checks = 0;
	private static int failures = 0;

	public static void main( String[] args ) throws Exception {

		Path root = Paths.get( "app/src/main/java/com/github/dachhack/sprout" );

		check( root.resolve( "scenes/GameScene.java" ), "create" );

		System.out.println();
		if (failures > 0) {
			System.out.println( "=== SCENEORDERTEST FAILED (" + failures + " of " + checks + ")" );
			System.exit( 1 );
		}
		System.out.println( "=== SCENEORDERTEST PASSED (" + checks + " checks)" );
	}

	private static void check( Path file, String method ) throws IOException {

		String src = new String( Files.readAllBytes( file ), StandardCharsets.UTF_8 );

		Set<String> fields = declaredFields( src );
		if (fields.isEmpty()) {
			fail( file + ": found no instance fields to check -- the lint is broken, not the code" );
			return;
		}

		List<String> body = methodBody( src, method );
		if (body.isEmpty()) {
			fail( file + ": could not find " + method + "()" );
			return;
		}

		// Assigned-so-far, walking the method top to bottom.
		Set<String> assigned = new HashSet<String>();

		Pattern assign = Pattern.compile( "(?:^|[^\\w.])(\\w+)\\s*=\\s*(?!=)" );
		Pattern use    = Pattern.compile( "(?:^|[^\\w.\"])(\\w+)\\s*\\." );

		for (String raw : body) {

			String line = strip( raw );
			if (line.isEmpty()) continue;

			// Uses are checked against the state *before* this line, so a
			// self-referential `x = new Thing(x)` still counts as a use.
			Matcher u = use.matcher( line );
			while (u.find()) {
				String name = u.group( 1 );
				if (!fields.contains( name )) continue;
				checks++;
				if (!assigned.contains( name )) {
					fail( file.getFileName() + " " + method + "(): `" + name
						+ "` is dereferenced before it is built -- it is null here"
						+ "\n        " + line.trim() );
				}
			}

			Matcher a = assign.matcher( line );
			while (a.find()) {
				String name = a.group( 1 );
				if (fields.contains( name )) {
					assigned.add( name );
				}
			}
		}

		if (failures == 0) {
			System.out.printf( "%-56s %s%n",
				"every scene field is built before it is touched", "ok" );
		}
	}

	/** `private Group mobs;` -> mobs. Only simple, uninitialised fields. */
	private static Set<String> declaredFields( String src ) {
		Set<String> out = new LinkedHashSet<String>();
		Matcher m = Pattern.compile(
			"(?m)^\\s*(?:private|protected|public)\\s+(?!static)(?:final\\s+)?"
			+ "([A-Z]\\w*)\\s+(\\w+)\\s*;" ).matcher( src );
		while (m.find()) {
			out.add( m.group( 2 ) );
		}
		return out;
	}

	/** The lines of a method, by brace depth from its opening line. */
	private static List<String> methodBody( String src, String name ) {

		List<String> out = new ArrayList<String>();
		String[] lines = src.split( "\n", -1 );

		int start = -1;
		Pattern sig = Pattern.compile( "\\b" + name + "\\s*\\(\\s*\\)\\s*\\{" );
		for (int i = 0; i < lines.length; i++) {
			if (sig.matcher( lines[i] ).find()) { start = i; break; }
		}
		if (start < 0) return out;

		int depth = 0;
		for (int i = start; i < lines.length; i++) {
			String line = strip( lines[i] );
			for (char c : line.toCharArray()) {
				if (c == '{') depth++;
				else if (c == '}') depth--;
			}
			if (i > start) out.add( lines[i] );
			if (depth <= 0 && i > start) break;
		}
		return out;
	}

	/** Drop line comments and string literals so they cannot match. */
	private static String strip( String line ) {
		int c = line.indexOf( "//" );
		if (c >= 0) line = line.substring( 0, c );
		line = line.replaceAll( "\"(\\\\.|[^\"\\\\])*\"", "\"\"" );
		if (line.trim().startsWith( "*" ) || line.trim().startsWith( "/*" )) return "";
		return line;
	}

	private static void fail( String msg ) {
		failures++;
		System.out.println( "  FAIL  " + msg );
	}
}
