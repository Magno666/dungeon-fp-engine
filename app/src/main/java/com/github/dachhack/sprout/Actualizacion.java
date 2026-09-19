/*
 * Sprouted Pixel Dungeon
 * Copyright (C) 2016 Trashbox Bobylev
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

/**
 * Avisa de que hay una version nueva. Nada mas.
 *
 * No descarga, no instala, no toca nada del sistema: ensena un letrero en
 * la pantalla de titulo con el numero y de donde bajarla. Se decidio asi a
 * proposito -- un actualizador automatico significa que algo ajeno puede
 * reemplazar el juego en la maquina de alguien, y eso es justo lo que no
 * se quiere. El control de que sale y cuando se queda de este lado.
 *
 * En el navegador esto no existe: ahi la version nueva llega sola al
 * recargar.
 *
 * <h3>Por que nunca puede estorbar</h3>
 *
 * La consulta va en su propio hilo y con un limite corto. Si el servidor
 * no contesta, si no hay red, o si devuelve basura, no pasa nada: no hay
 * letrero y el juego arranca igual. Un aviso de actualizacion que impide
 * jugar es peor que no tener aviso.
 */
public final class Actualizacion {

	private Actualizacion() {}

	/** Quien sabe hablar con el servidor. Lo pone cada plataforma; sin
	 *  esto no se consulta nada. */
	public interface Buscador {
		/** @return el cuerpo de /api/version, o null */
		String leer(String url);
	}

	public static Buscador buscador;

	public static String url =
		"https://pixeldoomgeon.jaliscomundial.com/api/version";

	/** Que llave de versiones.json mirar: "android" o "pc". */
	public static String cual = "";

	private static volatile boolean buscada;
	private static volatile int codigoNuevo;
	private static volatile String nombreNuevo = "";
	private static volatile String donde = "";
	private static volatile String nota = "";

	/** Arranca la consulta. Vuelve enseguida. */
	public static void buscar() {
		if (buscada || buscador == null || cual.length() == 0) {
			return;
		}
		buscada = true;
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					mirar(buscador.leer(url));
				} catch (Throwable e) {
					// En silencio a proposito: ver el comentario de arriba.
				}
			}
		}, "actualizacion").start();
	}

	/**
	 * Lee el json a mano.
	 *
	 * Sin biblioteca: el juego no trae una para esto y meter una por tres
	 * campos no compensa. El formato lo escribo yo en el servidor, asi que
	 * no hay sorpresas -- y si las hubiera, cualquier fallo aqui acaba en
	 * el catch de arriba y el juego sigue.
	 */
	static void mirar(String cuerpo) {
		if (cuerpo == null) {
			return;
		}
		int i = cuerpo.indexOf("\"" + cual + "\"");
		if (i < 0) {
			return;
		}
		int fin = cuerpo.indexOf('}', i);
		String tramo = fin > i ? cuerpo.substring(i, fin) : cuerpo.substring(i);
		int codigo = (int) numero(tramo, "codigo");
		if (codigo <= Game.versionCode) {
			return;
		}
		codigoNuevo = codigo;
		nombreNuevo = texto(tramo, "nombre");
		donde = texto(tramo, "url");
		nota = texto(tramo, "nota");
	}

	private static double numero(String s, String clave) {
		int i = s.indexOf("\"" + clave + "\"");
		if (i < 0) {
			return -1;
		}
		i = s.indexOf(':', i);
		if (i < 0) {
			return -1;
		}
		int j = i + 1;
		while (j < s.length() && (Character.isDigit(s.charAt(j))
				|| s.charAt(j) == ' ' || s.charAt(j) == '-')) {
			j++;
		}
		try {
			return Double.parseDouble(s.substring(i + 1, j).trim());
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static String texto(String s, String clave) {
		int i = s.indexOf("\"" + clave + "\"");
		if (i < 0) {
			return "";
		}
		i = s.indexOf('"', s.indexOf(':', i) + 1);
		if (i < 0) {
			return "";
		}
		int j = s.indexOf('"', i + 1);
		return j > i ? s.substring(i + 1, j) : "";
	}

	public static boolean hayNueva() {
		return codigoNuevo > Game.versionCode;
	}

	public static String nombre() { return nombreNuevo; }
	public static String donde()  { return donde; }
	public static String nota()   { return nota; }
}
