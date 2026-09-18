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
import com.watabou.utils.Random;

/**
 * Hasta donde llega la gente, de que muere, y a cuantos cuadros le corre.
 *
 * Vive en el juego y no en el puerto web porque el APK necesita lo mismo.
 * Lo unico que cambia entre plataformas es COMO se manda, y eso lo pone
 * cada una en {@link #enviador}. Si nadie lo pone, esto no hace nada:
 * compilar el juego sin telemetria tiene que seguir funcionando.
 *
 * <h3>Quien es quien, sin saber quien es nadie</h3>
 *
 * Cada instalacion se inventa un numero al azar la primera vez y lo
 * guarda. Con eso se pueden juntar las partidas de una misma instalacion
 * -- cuantas jugo, si volvio al dia siguiente, hasta donde llego -- sin
 * correo, sin contrasena y sin nada que diga quien es la persona.
 *
 * Antes el servidor agrupaba por un hash de la IP, que es peor en los dos
 * sentidos: dos personas en la misma casa contaban como una, y la misma
 * persona contaba como dos en cuanto cambiaba de wifi a datos.
 *
 * El numero no viaja con nada mas. No hay forma de volver de el a una
 * persona, y borrar los datos del juego lo borra.
 */
public final class Telemetria {

	private Telemetria() {}

	/** Como se manda. Lo pone cada plataforma; sin esto no se manda nada. */
	public interface Enviador {
		void mandar(String json);
	}

	public static Enviador enviador;

	/** Que aparato es. Lo pone cada plataforma: el modelo en Android, el
	 *  navegador en la web. Nunca algo que identifique a la persona. */
	public static String dispositivo = "";

	/**
	 * De donde sale el reporte: "web", "android" o "pc".
	 *
	 * Va en TODOS los eventos y no solo en el de rendimiento. Sin esto no
	 * hay forma de contestar "los que bajaron el APK, lo estan jugando?"
	 * -- que es la primera pregunta en cuanto hay un APK repartido, y la
	 * respuesta estaba a la vista y aun asi no se podia dar.
	 */
	public static String plataforma = "web";

	private static String instalacion;

	/** El numero de esta instalacion. Se inventa la primera vez. */
	public static String instalacion() {
		if (instalacion != null) {
			return instalacion;
		}
		String guardado = Preferences.INSTANCE.getString(
			Preferences.KEY_INSTALACION, null);
		if (guardado == null || guardado.length() != 16) {
			StringBuilder sb = new StringBuilder(16);
			for (int i = 0; i < 16; i++) {
				sb.append("0123456789abcdef".charAt(Random.Int(16)));
			}
			guardado = sb.toString();
			Preferences.INSTANCE.put(Preferences.KEY_INSTALACION, guardado);
		}
		instalacion = guardado;
		return instalacion;
	}

	// ---- progreso ------------------------------------------------------

	private static int pisoVisto = 0;
	private static boolean estabaVivo = false;

	/**
	 * Una vez por cuadro. Mira el piso y si el heroe sigue vivo, y dispara
	 * cuando eso cambia.
	 *
	 * Se mira desde fuera en vez de poner un gancho dentro de la logica:
	 * un gancho seria mas limpio de leer y mucho mas facil de romper, y la
	 * regla del proyecto es que la logica no se toca.
	 */
	public static void revisar() {

		if (enviador == null || Dungeon.hero == null || Dungeon.level == null) {
			return;
		}
		// La arena no cuenta: es un sandbox con el equipo regalado, y
		// mezclarla con partidas de verdad arruina justo el dato que se
		// quiere medir.
		if (Arena.activa) {
			return;
		}

		int piso = Dungeon.depth;
		if (piso > 0 && piso != pisoVisto) {
			pisoVisto = piso;
			mandar("piso", piso, "");
		}

		boolean vivo = Dungeon.hero.isAlive();
		if (estabaVivo && !vivo) {
			String causa = Dungeon.resultDescription;
			mandar("muerte", piso, causa == null ? "" : causa);
		}
		estabaVivo = vivo;

		medirCuadros();
	}

	/** Al empezar otra partida, volver a contar desde cero. */
	public static void reiniciar() {
		pisoVisto = 0;
		estabaVivo = false;
		cuadros = 0;
		tiempo = 0f;
		peorCuadro = 0f;
		reportado = false;
	}

	// ---- rendimiento ---------------------------------------------------

	/** Cuantos segundos de juego se miden antes de reportar. */
	public static float ventanaSegundos = 45f;

	private static int cuadros;
	private static float tiempo;
	private static float peorCuadro;
	private static boolean reportado;

	/**
	 * Cuadros por segundo, una vez por partida.
	 *
	 * Se reporta el promedio Y el cuadro mas lento: un promedio de 60 con
	 * tirones de 200 ms se juega mucho peor que un 40 parejo, y con solo el
	 * promedio los dos se ven igual. En Android es donde importa -- los
	 * telefonos varian muchisimo y ahora mismo no hay forma de saber si
	 * alguien lo esta jugando a quince cuadros.
	 */
	private static void medirCuadros() {
		if (reportado) {
			return;
		}
		float dt = Game.elapsed;
		// Un cuadro de mas de medio segundo no es lentitud: es la pestana
		// en segundo plano, o el telefono bloqueado. Ensuciaria la medida.
		if (dt <= 0f || dt > 0.5f) {
			return;
		}
		cuadros++;
		tiempo += dt;
		if (dt > peorCuadro) {
			peorCuadro = dt;
		}
		if (tiempo >= ventanaSegundos && cuadros > 30) {
			reportado = true;
			int fps = (int) (cuadros / tiempo);
			int peor = (int) (1f / peorCuadro);
			mandarRendimiento(fps, peor);
		}
	}

	// ---- el envio ------------------------------------------------------

	private static void mandar(String evento, int piso, String causa) {
		enviador.mandar("{\"evento\":\"" + escapar(evento)
			+ "\",\"piso\":" + piso
			+ ",\"causa\":\"" + escapar(causa)
			+ "\",\"plataforma\":\"" + escapar(plataforma)
			+ "\",\"instalacion\":\"" + escapar(instalacion()) + "\"}");
	}

	private static void mandarRendimiento(int fps, int peor) {
		enviador.mandar("{\"evento\":\"rendimiento\""
			+ ",\"piso\":" + Math.max(1, Dungeon.depth)
			+ ",\"fps\":" + fps
			+ ",\"peor\":" + peor
			+ ",\"pantalla\":\"" + Game.width + "x" + Game.height + "\""
			+ ",\"plataforma\":\"" + escapar(plataforma) + "\""
			+ ",\"dispositivo\":\"" + escapar(dispositivo)
			+ "\",\"instalacion\":\"" + escapar(instalacion()) + "\"}");
	}

	/** JSON a mano porque el juego no trae con que. Solo hay que cuidar lo
	 *  que romperia la cadena; el servidor limpia el resto. */
	private static String escapar(String s) {
		if (s == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(s.length() + 8);
		for (int i = 0; i < s.length() && sb.length() < 120; i++) {
			char c = s.charAt(i);
			if (c == '"' || c == '\\') {
				sb.append('\\').append(c);
			} else if (c >= ' ' && c != 0x7f) {
				sb.append(c);
			} else {
				sb.append(' ');
			}
		}
		return sb.toString();
	}
}
