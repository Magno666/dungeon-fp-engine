/*
 * Pixel Dungeon
 * Copyright (C) 2012-2014  Oleg Dolya
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

import javax.microedition.khronos.opengles.GL10;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

import com.github.dachhack.sprout.scenes.GameScene;
import com.github.dachhack.sprout.scenes.PixelScene;
import com.github.dachhack.sprout.scenes.TitleScene;
import com.watabou.noosa.Game;
import com.watabou.noosa.audio.Music;
import com.watabou.noosa.audio.Sample;

public class ShatteredPixelDungeon extends Game {

	public ShatteredPixelDungeon() {
		super(TitleScene.class);

		// 0.2.4
		com.watabou.utils.Bundle
				.addAlias(
						com.github.dachhack.sprout.items.weapon.enchantments.Shock.class,
						"com.github.dachhack.sprout.items.weapon.enchantments.Piercing");
		com.watabou.utils.Bundle
				.addAlias(
						com.github.dachhack.sprout.items.weapon.enchantments.Shock.class,
						"com.github.dachhack.sprout.items.weapon.enchantments.Swing");

		com.watabou.utils.Bundle
				.addAlias(
						com.github.dachhack.sprout.items.scrolls.ScrollOfMagicalInfusion.class,
						"com.github.dachhack.sprout.items.scrolls.ScrollOfWeaponUpgrade");

	}

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Load the translation before any scene builds, so the title screen
		// is already in the phone's language. Falls back to the game's own
		// English if there is no dictionary for it.
		Lang.useSystemLanguage();

		// La telemetria del APK, por reflexion a proposito.
		//
		// TelemetriaAndroid habla java.net. Nombrarla aqui directamente
		// obliga a TeaVM a arrastrar todo eso al bundle del navegador --
		// 84 KB medidos -- para codigo que ahi no corre jamas, porque la
		// web pone su propio enviador. Por reflexion, TeaVM no ve la
		// referencia y la descarta entera; en Android resuelve y corre.
		// minifyEnabled esta en false, asi que nada la renombra.
		try {
			Class.forName("com.github.dachhack.sprout.TelemetriaAndroid")
				.getMethod("instalar").invoke(null);
		} catch (Throwable e) {
			// En el navegador no existe y no pasa nada: web.Progreso pone
			// el suyo en el primer cuadro.
		}

		/*
		 * if (android.os.Build.VERSION.SDK_INT >= 19) {
		 * getWindow().getDecorView().setSystemUiVisibility(
		 * View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
		 * View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
		 * View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
		 * View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
		 * | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY ); }
		 */

		updateImmersiveMode();

		DisplayMetrics metrics = new DisplayMetrics();
		instance.getWindowManager().getDefaultDisplay().getMetrics(metrics);
		boolean landscape = metrics.widthPixels > metrics.heightPixels;

		// The first person view wants landscape: upright, a phone is a tall
		// slot and the horizontal field of view collapses. This preference
		// is applied at startup and overrides the manifest, which is why
		// setting screenOrientation there alone did nothing.
		boolean wantLandscape = Preferences.INSTANCE.getBoolean(
			Preferences.KEY_LANDSCAPE, FirstPerson.enabled);
		if (wantLandscape != landscape) {
			landscape(wantLandscape);
		}

		Music.INSTANCE.enable(music());
		Sample.INSTANCE.enable(soundFx());

		// Los ejes invertidos se guardan como preferencia, pero el campo
		// que los usa arranca en false. Sin esto quedan guardados y sin
		// aplicar hasta que vuelvas a tocar la casilla -- que parece que
		// el ajuste no funciona.
		FirstPersonControls.invertirX = invertX();
		FirstPerson.enderezarAlCaminar = enderezar();
		FirstPersonControls.punteroLibre = punteroLibre();
		FirstPersonControls.invertirY = invertY();

		Sample.INSTANCE.load(Assets.SND_CLICK, Assets.SND_BADGE,
				Assets.SND_GOLD,

				Assets.SND_STEP, Assets.SND_WATER, Assets.SND_OPEN,
				Assets.SND_UNLOCK, Assets.SND_ITEM, Assets.SND_DEWDROP,
				Assets.SND_HIT, Assets.SND_MISS,

				Assets.SND_DESCEND, Assets.SND_EAT, Assets.SND_READ,
				Assets.SND_LULLABY, Assets.SND_DRINK, Assets.SND_SHATTER,
				Assets.SND_ZAP, Assets.SND_LIGHTNING, Assets.SND_LEVELUP,
				Assets.SND_DEATH, Assets.SND_CHALLENGE, Assets.SND_CURSED,
				Assets.SND_EVOKE, Assets.SND_TRAP, Assets.SND_TOMB,
				Assets.SND_ALERT, Assets.SND_MELD, Assets.SND_BOSS,
				Assets.SND_BLAST, Assets.SND_PLANT, Assets.SND_RAY,
				Assets.SND_BEACON, Assets.SND_TELEPORT, Assets.SND_CHARMS,
				Assets.SND_MASTERY, Assets.SND_PUFF, Assets.SND_ROCKS,
				Assets.SND_BURNING, Assets.SND_FALLING, Assets.SND_GHOST,
				Assets.SND_SECRET, Assets.SND_BONES, Assets.SND_BEE,
				Assets.SND_DEGRADE, Assets.SND_MIMIC);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {

		super.onWindowFocusChanged(hasFocus);

		if (hasFocus) {
			updateImmersiveMode();
		}
	}

	public static void switchNoFade(Class<? extends PixelScene> c) {
		PixelScene.noFade = true;
		switchScene(c);
	}

	/*
	 * ---> Prefernces
	 */

	public static void landscape(boolean value) {
		Game.instance
				.setRequestedOrientation(value ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
						: ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		Preferences.INSTANCE.put(Preferences.KEY_LANDSCAPE, value);
	}

	public static boolean landscape() {
		return width > height;
	}

	public static void scaleUp(boolean value) {
		Preferences.INSTANCE.put(Preferences.KEY_SCALE_UP, value);
		switchScene(TitleScene.class);
	}

	// *** IMMERSIVE MODE ****

	private static boolean immersiveModeChanged = false;

	@SuppressLint("NewApi")
	public static void immerse(boolean value) {
		Preferences.INSTANCE.put(Preferences.KEY_IMMERSIVE, value);

		instance.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				updateImmersiveMode();
				immersiveModeChanged = true;
			}
		});
	}

	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		super.onSurfaceChanged(gl, width, height);

		if (immersiveModeChanged) {
			requestedReset = true;
			immersiveModeChanged = false;
		}
	}

	@SuppressLint("NewApi")
	public static void updateImmersiveMode() {
		if (android.os.Build.VERSION.SDK_INT >= 19) {
			try {
				// Sometime NullPointerException happens here
				instance.getWindow()
						.getDecorView()
						.setSystemUiVisibility(
								immersed() ? View.SYSTEM_UI_FLAG_LAYOUT_STABLE
										| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
										| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
										| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
										| View.SYSTEM_UI_FLAG_FULLSCREEN
										| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
										: 0);
			} catch (Exception e) {
				reportException(e);
			}
		}
	}

	public static boolean immersed() {
		return Preferences.INSTANCE
				// Defaults ON. The theme already hides the status bar, but the
				// navigation bar stayed and ate the bottom of the screen --
				// on a 1280x720 phone that strip is where the toolbar's
				// quickslots live, so the player was handed a row of items
				// sliced in half. A first person crawler wants the glass.
				.getBoolean(Preferences.KEY_IMMERSIVE, true);
	}

	// *****************************

	public static boolean scaleUp() {
		return Preferences.INSTANCE.getBoolean(Preferences.KEY_SCALE_UP, true);
	}

	/**
	 * Invertir los ejes de la camara. Pedido por SeaOfBullshit en
	 * r/PixelDungeon, y es lo normal: mucha gente vuela y mira con el eje
	 * al reves y no lo puede evitar.
	 *
	 * Se guarda con las demas preferencias, asi que se pone una vez y se
	 * queda. Tenerlo que activar en cada partida seria peor que no tenerlo.
	 */
	public static void invertX(boolean value) {
		Preferences.INSTANCE.put(Preferences.KEY_INVERT_X, value);
		FirstPersonControls.invertirX = value;
	}

	public static boolean invertX() {
		return Preferences.INSTANCE.getBoolean(Preferences.KEY_INVERT_X, false);
	}

	public static void invertY(boolean value) {
		Preferences.INSTANCE.put(Preferences.KEY_INVERT_Y, value);
		FirstPersonControls.invertirY = value;
	}

	public static boolean invertY() {
		return Preferences.INSTANCE.getBoolean(Preferences.KEY_INVERT_Y, false);
	}

	/**
	 * Enderezar la vista hacia donde caminas al tocar una casilla.
	 *
	 * Encendido de salida: tocas una casilla lejana, el heroe recorre el
	 * camino solo y sin esto acabas bajando el pasillo de lado. Solo afecta
	 * al caminar por toque -- el stick nunca te quita la camara.
	 */
	public static void enderezar(boolean value) {
		Preferences.INSTANCE.put(Preferences.KEY_ENDEREZAR, value);
		FirstPerson.enderezarAlCaminar = value;
	}

	public static boolean enderezar() {
		return Preferences.INSTANCE.getBoolean(Preferences.KEY_ENDEREZAR, true);
	}

	/**
	 * No capturar el raton.
	 *
	 * Apagado de salida: capturarlo es lo correcto para mirar en primera
	 * persona. Pero con el puntero preso el cursor desaparece y hay que
	 * darle a Esc para tocar la barra o la mochila, y a quien juega en
	 * computadora eso le estorba. Pedido en la caja de comentarios.
	 */
	public static void punteroLibre(boolean value) {
		Preferences.INSTANCE.put(Preferences.KEY_PUNTERO, value);
		FirstPersonControls.punteroLibre = value;
	}

	public static boolean punteroLibre() {
		return Preferences.INSTANCE.getBoolean(Preferences.KEY_PUNTERO, false);
	}

	public static void zoom(int value) {
		Preferences.INSTANCE.put(Preferences.KEY_ZOOM, value);
	}

	public static int zoom() {
		return Preferences.INSTANCE.getInt(Preferences.KEY_ZOOM, 0);
	}

	public static void music(boolean value) {
		Music.INSTANCE.enable(value);
		Preferences.INSTANCE.put(Preferences.KEY_MUSIC, value);
	}

	public static boolean music() {
		return Preferences.INSTANCE.getBoolean(Preferences.KEY_MUSIC, true);
	}

	public static void soundFx(boolean value) {
		Sample.INSTANCE.enable(value);
		Preferences.INSTANCE.put(Preferences.KEY_SOUND_FX, value);
	}

	public static boolean soundFx() {
		return Preferences.INSTANCE.getBoolean(Preferences.KEY_SOUND_FX, true);
	}

	public static void brightness(boolean value) {
		Preferences.INSTANCE.put(Preferences.KEY_BRIGHTNESS, value);
		if (scene() instanceof GameScene) {
			((GameScene) scene()).brightness(value);
		}
	}

	public static boolean brightness() {
		return Preferences.INSTANCE.getBoolean(Preferences.KEY_BRIGHTNESS,
				false);
	}

	public static void lastClass(int value) {
		Preferences.INSTANCE.put(Preferences.KEY_LAST_CLASS, value);
	}

	public static int lastClass() {
		return Preferences.INSTANCE.getInt(Preferences.KEY_LAST_CLASS, 0);
	}

	public static void challenges(int value) {
		Preferences.INSTANCE.put(Preferences.KEY_CHALLENGES, value);
	}

	public static int challenges() {
		return Preferences.INSTANCE.getInt(Preferences.KEY_CHALLENGES, 0);
	}

	public static void quickSlots(int value) {
		Preferences.INSTANCE.put(Preferences.KEY_QUICKSLOTS, value);
	}

	public static int quickSlots() {
		return Preferences.INSTANCE.getInt(Preferences.KEY_QUICKSLOTS, 1);
	}

	public static void intro(boolean value) {
		Preferences.INSTANCE.put(Preferences.KEY_INTRO, value);
	}

	public static boolean intro() {
		return Preferences.INSTANCE.getBoolean(Preferences.KEY_INTRO, true);
	}

	public static void version(int value) {
		Preferences.INSTANCE.put(Preferences.KEY_VERSION, value);
	}

	public static int version() {
		return Preferences.INSTANCE.getInt(Preferences.KEY_VERSION, 0);
	}

	/*
	 * <--- Preferences
	 */

	public static void reportException(Throwable tr) {
		Log.e("PD", Log.getStackTraceString(tr));
	}
}