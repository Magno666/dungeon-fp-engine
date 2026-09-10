/*
 * Sprouted Pixel Dungeon — translation layer
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
package com.github.dachhack.sprout;

import com.watabou.noosa.Game;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Translates the game without touching the game.
 *
 * This fork has no localisation system: its 631 strings are written by
 * hand inside 242 classes, and 204 of those are in the logic layer that
 * the project rules say not to touch. Rewriting them all would mean
 * editing actors, items and levels for something that is presentation.
 *
 * So instead of changing where the text is written, this changes where it
 * is READ. Every string the game shows passes through one of two places:
 *
 *   Utils.format( fmt, args )   — anything with a %d or %s in it
 *   BitmapText.text( str )      — everything else, on its way to the screen
 *
 * Both are hooked to call {@link #t}. The game keeps its English source
 * exactly as dachhack wrote it, the diff against upstream stays clean,
 * and adding a third language later costs one more file.
 *
 * Formatted strings are translated BEFORE the arguments are substituted,
 * so the dictionary holds "Welcome to the level %d of Pixel Dungeon!" and
 * not whichever floor the player happens to be on.
 */
public class Lang {

	/** Where the dictionaries live, under assets. */
	private static final String DIR = "lang/";

	/**
	 * Accented characters the bitmap font cannot draw, and what to use
	 * instead.
	 *
	 * Watabou's font atlas is BitmapText.Font.LATIN_FULL: space through
	 * , plain ASCII. There is no glyph for a, e, i, o, u with an
	 * accent, none for n-tilde, and none for the opening marks Spanish
	 * puts at the start of a question or an exclamation. Font.get returns
	 * null for anything outside that set, and BitmapText.measure walks
	 * straight into it -- which took the title screen down mid-build the
	 * first time the dictionary said "Clasificacion" with an accent.
	 *
	 * Folding is a stopgap and it costs correct spelling: the game reads
	 * "Clasificacion" and "Estas ciego!" rather than the proper forms.
	 * The real fix is drawing the missing glyphs into the five font
	 * atlases, which is pixel art in five sizes and belongs to Fase 3.
	 * Until then, unspelled Spanish beats a crash.
	 */
	private static final String CON_ACENTO = "áéíóúüñÁÉÍÓÚÜÑ¡¿ªº°";
	private static final String SIN_ACENTO = "aeiouunAEIOUUN!?ao ";

	/** Two-letter code of the language in use, or null for the original. */
	private static String current;

	private static Map<String, String> dict = new HashMap<String, String>();

	/** Strings asked for that the dictionary did not have. Used by the
	 *  MissingText tool to report what still needs translating. */
	private static final Map<String, Boolean> missing = new HashMap<String, Boolean>();

	public static boolean collectMissing = false;

	/**
	 * Load a language. Pass null, or a code with no file, to run in the
	 * game's original English.
	 */
	public static void use( String code ) {

		current = null;
		dict = new HashMap<String, String>();

		if (code == null || code.equals( "en" )) {
			return;
		}

		try {
			InputStream in = Game.instance.getAssets().open( DIR + code + ".txt" );
			BufferedReader r = new BufferedReader( new InputStreamReader( in, "UTF-8" ) );

			String line, source = null;
			while ((line = r.readLine()) != null) {
				if (line.startsWith( "#" ) || line.trim().isEmpty()) {
					continue;
				}
				if (line.startsWith( "en:" )) {
					source = unescape( line.substring( 3 ) );
				} else if (line.startsWith( "tr:" ) && source != null) {
					dict.put( source, fold( unescape( line.substring( 3 ) ) ) );
					source = null;
				}
			}
			r.close();
			current = code;

		} catch (Exception e) {
			// A missing or broken dictionary must never take the game down;
			// it just means everything stays in English.
			android.util.Log.i( "Lang", "no dictionary for " + code );
		}
	}

	/**
	 * Swaps every character the font cannot draw for one it can. Applied
	 * when the dictionary loads, so it costs nothing per frame and the
	 * .txt on disk keeps its proper spelling for whoever edits it.
	 */
	public static String fold( String s ) {

		if (s == null) {
			return null;
		}

		StringBuilder out = null;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt( i );
			int at = CON_ACENTO.indexOf( c );
			if (at < 0 && c < 128) {
				if (out != null) {
					out.append( c );
				}
				continue;
			}
			if (out == null) {
				out = new StringBuilder( s.length() );
				out.append( s, 0, i );
			}
			// Anything else non-ASCII would be a null glyph too, so it is
			// dropped rather than trusted.
			out.append( at >= 0 ? SIN_ACENTO.charAt( at ) : '?' );
		}
		return out == null ? s : out.toString();
	}

	/** The language in use, or null when running untranslated. */
	public static String current() {
		return current;
	}

	/** Pick a language from the phone's own setting, once, at startup. */
	public static void useSystemLanguage() {
		String code = Locale.getDefault().getLanguage();
		use( code.equals( "es" ) ? "es" : null );
	}

	/**
	 * Translate one string, or hand back exactly what it was given.
	 *
	 * Deliberately total: an unknown string is returned unchanged rather
	 * than blanked or marked, so a half-finished dictionary leaves a
	 * playable game in mixed languages instead of a broken one.
	 */
	public static String t( String str ) {

		if (str == null || dict.isEmpty()) {
			return str;
		}
		String hit = dict.get( str );
		if (hit != null) {
			return hit;
		}

		// The game capitalises a name when it starts a sentence, so "Grey
		// rat golpeo a you" came out half translated: the format string
		// was in the dictionary under "%s hit %s" and the name was not,
		// because the dictionary holds "grey rat" and the game asked for
		// "Grey rat". Retry in lower case and put the capital back.
		if (str.length() > 1 && Character.isUpperCase( str.charAt( 0 ) )) {
			String lower = Character.toLowerCase( str.charAt( 0 ) ) + str.substring( 1 );
			hit = dict.get( lower );
			if (hit != null && hit.length() > 0) {
				return Character.toUpperCase( hit.charAt( 0 ) ) + hit.substring( 1 );
			}
		}
		if (collectMissing) {
			missing.put( str, Boolean.TRUE );
		}
		return str;
	}

	/** Everything asked for and not found, for the translation tooling. */
	public static String[] missing() {
		return missing.keySet().toArray( new String[0] );
	}

	/** \n in the file means a real newline; the game uses them in windows. */
	private static String unescape( String s ) {
		return s.replace( "\\n", "\n" );
	}
}
