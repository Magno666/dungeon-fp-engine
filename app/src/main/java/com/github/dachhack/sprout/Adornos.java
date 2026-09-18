/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2015 Evan Debenham
 *
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

import java.util.ArrayList;

import com.github.dachhack.sprout.levels.Level;
import com.github.dachhack.sprout.levels.Terrain;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import com.watabou.noosa.Gizmo;
import com.watabou.utils.PointF;

/**
 * Los adornos de nivel, puestos donde toca en la vista en primera persona.
 *
 * Cada nivel cuelga sus decoraciones directamente de la escena: la tuberia
 * que gotea en las alcantarillas, la antorcha de la prision, el humo de la
 * ciudad, la veta de las cuevas, el chorro de los salones, y el viento y la
 * corriente que Level pone en los pozos. Todas sueltan particulas en
 * coordenadas de mapa, que al pasar por la camara en perspectiva acababan
 * repartidas por la pantalla sin relacion con la pared de la que salen.
 *
 * <h3>Como se colocan sin tocar el codigo de los niveles</h3>
 *
 * No hay que mover ni una particula. A cada adorno se le da su propia
 * camara 2D, y esa camara se desplaza y se escala cada cuadro para que el
 * punto de mapa de su casilla caiga justo donde el render 3D dibuja esa
 * casilla. Todo lo que el adorno emita -- este dentro de un Emitter o de un
 * Group, que de las dos formas los hay -- sale colocado solo.
 *
 * <h3>De donde sale la casilla de cada adorno</h3>
 *
 * El campo pos de cada decoracion es privado y son diez clases distintas.
 * Pero addVisuals las crea siempre recorriendo el mapa en orden de indice,
 * asi que la lista de casillas se puede reconstruir desde fuera repitiendo
 * ese recorrido. Como los niveles no coinciden en si llaman a super (los de
 * jefe no lo hacen) ni en que baldosa usan (Salones pide la 63 a pelo), se
 * prueban las cuatro combinaciones y se acepta la que da exactamente tantas
 * casillas como adornos hay.
 *
 * Si ninguna cuadra no se adivina: se esconden, que es lo que hacian antes
 * de esto y sigue siendo mejor que pintarlas en cualquier sitio.
 */
public class Adornos {

	/** Altura a la que cuelga el adorno, como fraccion del muro: 0 es el
	 *  suelo y 1 el techo. Las tuberias y las antorchas van arriba. */
	public static float altura = 0.62f;

	/** Un adorno a mas casillas que esto no se dibuja. Ahorra proyectar
	 *  veinte cosas por cuadro cuando casi ninguna se ve. Ojo: en casillas,
	 *  que una casilla son DungeonTilemap3D.TILE unidades de mundo -- tres,
	 *  no una. Confundirlas dejaba todo escondido mas alla de cuatro pasos. */
	public static float distanciaMaxima = 12f;

	private static final ArrayList<Gizmo> piezas = new ArrayList<Gizmo>();
	private static int[] celdas;
	private static Camera[] camaras;
	private static boolean colocados = false;

	public static void reset() {
		piezas.clear();
		celdas = null;
		camaras = null;
		colocados = false;
	}

	/** True cuando se supo a que casilla va cada adorno. Si es false el
	 *  llamante debe esconderlos. */
	public static boolean colocados() {
		return colocados;
	}

	public static int cuantos() {
		return piezas.size();
	}

	/** Diagnostico: cuantos se estan dibujando ahora mismo. */
	public static int visibles() {
		int n = 0;
		for (Gizmo g : piezas) {
			if (g != null && g.visible) {
				n++;
			}
		}
		return n;
	}

	/** Diagnostico: el estado de un adorno por su casilla. */
	public static String comoEsta(int celda) {
		for (int i = 0; i < piezas.size(); i++) {
			if (celdas[i] == celda) {
				Camera c = camaras[i];
				// Donde deberia caer en pantalla, para poder contrastarlo
				// contra los pixeles de una captura.
				com.watabou.utils.PointF enMapa =
					DungeonTilemap.tileCenterToWorld(celda);
				int px = (int) ((enMapa.x - c.scroll.x) * c.zoom + c.x);
				int py = (int) ((enMapa.y - c.scroll.y) * c.zoom + c.y);
				return "visible=" + piezas.get(i).visible
					+ " zoom=" + c.zoom
					+ " pantalla=" + px + "," + py
					+ " hijos=" + ((com.watabou.noosa.Group) piezas.get(i)).countLiving();
			}
		}
		return "no esta en la lista";
	}

	public static void install(ArrayList<Gizmo> adornos) {
		reset();
		if (!FirstPerson.enabled || adornos == null || adornos.isEmpty()
				|| Dungeon.level == null) {
			return;
		}

		int[] halladas = deducirCeldas(adornos.size());
		if (halladas == null) {
			return;
		}

		piezas.addAll(adornos);
		celdas = halladas;
		camaras = new Camera[piezas.size()];
		for (int i = 0; i < piezas.size(); i++) {
			// Ancho y alto completos y zoom 1 al construirla: asi
			// screenWidth/screenHeight quedan a pantalla entera y el tijeretazo
			// que hace NoosaScript.camera() no recorta nada por mucho que
			// luego se cambie el zoom.
			camaras[i] = new Camera(0, 0, Game.width, Game.height, 1);
			piezas.get(i).camera = camaras[i];
		}
		colocados = true;
	}

	/**
	 * Reconstruye la lista de casillas repitiendo el recorrido de addVisuals.
	 * Devuelve null si ninguna combinacion da el numero de adornos que hay.
	 */
	private static int[] deducirCeldas(int cuantos) {
		for (int conPozos = 1; conPozos >= 0; conPozos--) {
			for (int baldosa = 0; baldosa < 2; baldosa++) {
				ArrayList<Integer> lista = new ArrayList<Integer>();
				if (conPozos == 1) {
					anadirPozos(lista);
				}
				anadirAdornos(lista, baldosa == 0 ? Terrain.WALL_DECO : 63);
				if (lista.size() == cuantos) {
					int[] fuera = new int[cuantos];
					for (int i = 0; i < cuantos; i++) {
						fuera[i] = lista.get(i).intValue();
					}
					return fuera;
				}
			}
		}
		return null;
	}

	/** El recorrido de Level.addVisuals: viento en cada pozo, y corriente
	 *  si justo encima del pozo hay agua. */
	private static void anadirPozos(ArrayList<Integer> lista) {
		int w = Level.getWidth();
		for (int i = 0; i < Level.getLength(); i++) {
			if (Dungeon.level.pit[i]) {
				lista.add(Integer.valueOf(i));
				if (i >= w && Dungeon.level.water[i - w]) {
					lista.add(Integer.valueOf(i - w));
				}
			}
		}
	}

	private static void anadirAdornos(ArrayList<Integer> lista, int baldosa) {
		for (int i = 0; i < Level.getLength(); i++) {
			if (Dungeon.level.map[i] == baldosa) {
				lista.add(Integer.valueOf(i));
			}
		}
	}

	/**
	 * Un cuadro. Va despues de que los adornos se hayan actualizado: ellos
	 * se encienden y se apagan solos segun Dungeon.visible, y aqui solo se
	 * apagan los que ademas quedan detras del ojo o demasiado lejos.
	 */
	public static void update() {
		if (!colocados || !FirstPerson.enabled) {
			return;
		}
		Camera3DDatos ojo = leerOjo();
		if (ojo == null) {
			return;
		}
		int w = Level.getWidth();
		for (int i = 0; i < piezas.size(); i++) {
			Gizmo pieza = piezas.get(i);
			if (pieza == null || !pieza.visible) {
				continue;
			}
			int celda = celdas[i];
			float wx = DungeonTilemap3D.worldX(celda, w);
			float wz = DungeonTilemap3D.worldZ(celda, w);

			float dx = wx - ojo.x;
			float dz = wz - ojo.z;
			float tope = distanciaMaxima * DungeonTilemap3D.TILE;
			if (dx * dx + dz * dz > tope * tope) {
				pieza.visible = false;
				continue;
			}

			float y = altura * DungeonTilemap3D.HEIGHT;
			if (!proyectar(ojo.matriz, wx, y, wz, punto)) {
				pieza.visible = false;
				continue;
			}
			float sx = punto[0];
			float sy = punto[1];

			// La escala sale medida, no de una constante: se proyecta un
			// segundo punto una casilla mas arriba y se mira cuanto se
			// separa en pantalla. Eso es lo que mide una casilla ahi, y una
			// casilla son DungeonTilemap.SIZE en coordenadas de mapa.
			//
			// Hacia ARRIBA y no hacia un lado: medir sobre el eje X falla
			// justo cuando se mira a lo largo de ese eje, porque entonces la
			// casilla se ve de canto y la separacion en pantalla es casi
			// cero. Daba escalas trescientas veces menores y los adornos
			// desaparecian. La vertical nunca se escorza con el giro.
			if (!proyectar(ojo.matriz, wx, y + DungeonTilemap3D.TILE, wz, punto)) {
				pieza.visible = false;
				continue;
			}
			float ladoCelda = Math.abs(punto[1] - sy);
			if (ladoCelda < 0.5f) {
				pieza.visible = false;
				continue;
			}
			float zoom = ladoCelda / DungeonTilemap.SIZE;

			// Y ahora la camara del adorno se coloca para que el punto de
			// mapa de su casilla caiga exactamente en (sx, sy).
			Camera cam = camaras[i];
			cam.zoom = zoom;
			PointF enMapa = DungeonTilemap.tileCenterToWorld(celda);
			cam.scroll.set(enMapa.x - (sx - cam.x) / zoom,
			               enMapa.y - (sy - cam.y) / zoom);
			// update() es la unica puerta publica a updateMatrix(). No hace
			// nada mas aqui: estas camaras no siguen a nadie ni tiemblan, y
			// no estan en Camera.all, asi que nadie mas las toca.
			cam.update();
		}
	}

	private static final float[] punto = new float[2];

	/** Proyecta un punto del mundo 3D a pixeles de pantalla. False si queda
	 *  detras del ojo. */
	private static boolean proyectar(float[] m, float x, float y, float z,
			float[] fuera) {
		float cw = m[3] * x + m[7] * y + m[11] * z + m[15];
		if (cw <= 0.05f) {
			return false;
		}
		float cx = m[0] * x + m[4] * y + m[8] * z + m[12];
		float cy = m[1] * x + m[5] * y + m[9] * z + m[13];
		fuera[0] = (cx / cw * 0.5f + 0.5f) * Game.width;
		fuera[1] = (1f - (cy / cw * 0.5f + 0.5f)) * Game.height;
		return true;
	}

	private static final Camera3DDatos datos = new Camera3DDatos();

	private static Camera3DDatos leerOjo() {
		com.watabou.noosa.Camera3D cam = FirstPerson.camera();
		if (cam == null) {
			return null;
		}
		datos.x = cam.eyeX;
		datos.z = cam.eyeZ;
		datos.matriz = cam.matrix;
		return datos;
	}

	private static class Camera3DDatos {
		float x, z;
		float[] matriz;
	}
}
