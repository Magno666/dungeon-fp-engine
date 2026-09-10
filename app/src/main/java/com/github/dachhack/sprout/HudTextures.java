/*
 * Sprouted Pixel Dungeon — procedural HUD shapes
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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import com.watabou.gltextures.SmartTexture;
import com.watabou.gltextures.TextureCache;

/**
 * The three shapes the first person HUD needs and Noosa cannot draw.
 *
 * ColorBlock is a quad, so a thumbstick built from it is a square and a
 * heading marker is a dot with no heading. These are drawn once into
 * bitmaps and kept in TextureCache under their own keys.
 *
 * Deliberately plain: white, antialiased, no styling. Colour and opacity
 * are set per Image by the caller, and the real art is not mine to draw.
 *
 * Caching matters here for a reason learned the hard way -- TextureCache
 * keys are global, so a class used as a key means one texture for the
 * whole app. These use distinct String keys and are created once.
 */
public class HudTextures {

	private static final String KEY_RING  = "fp_hud_ring";
	private static final String KEY_DISC  = "fp_hud_disc";
	private static final String KEY_ARROW = "fp_hud_arrow";

	/** Bitmap resolution. Scaled down in use, so oversample for smoothness. */
	private static final int SIZE = 128;

	/** Ring thickness as a fraction of the radius. */
	public static float ringThickness = 0.13f;

	/** A hollow circle: the base the thumb rests in. */
	public static SmartTexture ring() {
		if (TextureCache.contains( KEY_RING )) {
			return TextureCache.get( KEY_RING );
		}
		Bitmap bmp = blank();
		Canvas c = new Canvas( bmp );
		Paint p = paint();
		p.setStyle( Paint.Style.STROKE );
		float stroke = SIZE * ringThickness * 0.5f;
		p.setStrokeWidth( stroke );
		c.drawCircle( SIZE / 2f, SIZE / 2f, SIZE / 2f - stroke, p );
		return store( KEY_RING, bmp );
	}

	/** A filled circle: the knob, and the dot on the map. */
	public static SmartTexture disc() {
		if (TextureCache.contains( KEY_DISC )) {
			return TextureCache.get( KEY_DISC );
		}
		Bitmap bmp = blank();
		Canvas c = new Canvas( bmp );
		Paint p = paint();
		p.setStyle( Paint.Style.FILL );
		c.drawCircle( SIZE / 2f, SIZE / 2f, SIZE / 2f - 1f, p );
		return store( KEY_DISC, bmp );
	}

	/**
	 * A triangle pointing at -Y, which is up on screen and north on the
	 * map. Noosa rotates an Image clockwise about its origin, and the map
	 * marker sets that origin to the centre, so this only has to agree on
	 * which way zero degrees points.
	 */
	public static SmartTexture arrow() {
		if (TextureCache.contains( KEY_ARROW )) {
			return TextureCache.get( KEY_ARROW );
		}
		Bitmap bmp = blank();
		Canvas c = new Canvas( bmp );
		Paint p = paint();
		p.setStyle( Paint.Style.FILL );

		Path path = new Path();
		path.moveTo( SIZE / 2f, 4f );                  // tip
		path.lineTo( SIZE - 10f, SIZE - 8f );          // right foot
		path.lineTo( SIZE / 2f, SIZE * 0.72f );        // notch, so it reads
		path.lineTo( 10f, SIZE - 8f );                 // left foot
		path.close();
		c.drawPath( path, p );

		return store( KEY_ARROW, bmp );
	}

	private static Bitmap blank() {
		Bitmap bmp = Bitmap.createBitmap( SIZE, SIZE, Bitmap.Config.ARGB_8888 );
		bmp.eraseColor( 0x00000000 );
		return bmp;
	}

	private static Paint paint() {
		Paint p = new Paint();
		p.setAntiAlias( true );
		p.setColor( 0xFFFFFFFF );
		return p;
	}

	private static SmartTexture store( String key, Bitmap bmp ) {
		// The rest of the game is pixel art on NEAREST, but these are smooth
		// curves scaled to whatever the screen is, so they get LINEAR. The
		// filter is per texture, so nothing else is affected.
		//
		// Not premultiplied: Noosa blends SRC_ALPHA/ONE_MINUS_SRC_ALPHA
		// (Game.java), so fading one of these by dropping `am` alone works
		// the way it does everywhere else in the game. Declaring it
		// premultiplied would leave the colour at full strength while the
		// alpha fell, and a 22%-opacity ring would burn in solid white.
		SmartTexture tx = new SmartTexture( bmp,
			com.watabou.glwrap.Texture.LINEAR,
			com.watabou.glwrap.Texture.CLAMP, false );
		TextureCache.add( key, tx );
		return tx;
	}
}
