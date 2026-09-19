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

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Manda la telemetria desde el APK.
 *
 * Aparte de Telemetria a proposito: esa vive en el juego y la comparten
 * las dos plataformas, y esta habla java.net, que el puerto web no puede
 * compilar. Quien la enchufa es la actividad de Android, a la que el
 * navegador nunca llega.
 *
 * Cada envio abre su propio hilo. Son dos o tres por partida y en una red
 * mala pueden tardar segundos: hacerlo en el hilo del juego congelaria la
 * pantalla justo al cambiar de piso. Si falla, se pierde y ya -- ningun
 * dato de estos vale una reintento que gaste bateria.
 */
public final class TelemetriaAndroid {

	private TelemetriaAndroid() {}

	public static String url = "https://pixeldoomgeon.jaliscomundial.com/api/progreso";

	/** Tope de envios por sesion. Un fallo que dispare en bucle no puede
	 *  convertirse en un martilleo al servidor desde mil telefonos. */
	public static int tope = 40;

	private static int mandados;

	public static void instalar() {
		instalar("android");
	}

	/** El escritorio reusa esto: tambien habla java.net y no tenia sentido
	 *  escribir el mismo hilo dos veces. */
	public static void instalar(String plataforma) {
		Telemetria.plataforma = plataforma;
		Telemetria.dispositivo = aparato();
		Actualizacion.cual = plataforma;
		Actualizacion.buscador = new Actualizacion.Buscador() {
			@Override
			public String leer(String url) {
				return TelemetriaAndroid.leer(url);
			}
		};
		Telemetria.enviador = new Telemetria.Enviador() {
			@Override
			public void mandar(final String json) {
				if (mandados >= tope) {
					return;
				}
				mandados++;
				new Thread(new Runnable() {
					@Override
					public void run() {
						enviar(json);
					}
				}, "telemetria").start();
			}
		};
		// Se arranca YA, no cuando se pinta el titulo: la consulta va en su
		// propio hilo y tarda lo que tarde la red. Preguntarla al construir
		// la pantalla y mirar la respuesta en la linea siguiente era mirar
		// antes de que contestara -- el aviso no salia nunca.
		Actualizacion.buscar();
	}

	/** Leer una URL, para la consulta de version. Mismo trato que el
	 *  envio: tiempos cortos y en silencio si falla. */
	public static String leer(String url) {
		HttpURLConnection c = null;
		try {
			c = (HttpURLConnection) new URL(url).openConnection();
			c.setConnectTimeout(6000);
			c.setReadTimeout(6000);
			java.io.InputStream in = c.getInputStream();
			java.io.ByteArrayOutputStream bs = new java.io.ByteArrayOutputStream();
			byte[] buf = new byte[4096];
			int n;
			// Tope duro: el servidor es mio, pero si algun dia no lo es,
			// esto no puede tragarse un fichero de un giga.
			while ((n = in.read(buf)) > 0 && bs.size() < 64 * 1024) {
				bs.write(buf, 0, n);
			}
			in.close();
			return bs.toString("UTF-8");
		} catch (Exception e) {
			return null;
		} finally {
			if (c != null) {
				c.disconnect();
			}
		}
	}

	private static void enviar(String json) {
		HttpURLConnection c = null;
		try {
			c = (HttpURLConnection) new URL(url).openConnection();
			c.setRequestMethod("POST");
			c.setRequestProperty("Content-Type", "application/json");
			c.setConnectTimeout(6000);
			c.setReadTimeout(6000);
			c.setDoOutput(true);
			OutputStream os = c.getOutputStream();
			os.write(json.getBytes("UTF-8"));
			os.close();
			c.getResponseCode();      // hay que leerlo o no se manda
		} catch (Exception e) {
			// A proposito en silencio. Que no haya red no es un problema
			// del jugador y no tiene por que enterarse.
		} finally {
			if (c != null) {
				c.disconnect();
			}
		}
	}

	/** Modelo y version de Android. Nada mas: ni IMEI, ni cuenta, ni nada
	 *  que sirva para saber de quien es el telefono. */
	private static String aparato() {
		String modelo = android.os.Build.MODEL;
		if (modelo == null) {
			modelo = "?";
		}
		return (modelo + " / Android " + android.os.Build.VERSION.RELEASE);
	}
}
