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

import com.github.dachhack.sprout.scenes.PixelScene;
import com.github.dachhack.sprout.ui.Window;
import com.github.dachhack.sprout.windows.WndMessage;
import com.watabou.noosa.BitmapText;
import com.watabou.noosa.Game;
import com.watabou.noosa.Group;
import com.watabou.noosa.TouchArea;
import com.watabou.noosa.ui.Button;

/**
 * "Hay una version nueva", abajo del todo en la pantalla de titulo.
 *
 * Una linea y nada mas. Va donde la version, no en medio ni en una ventana
 * que haya que cerrar: quien abre el juego quiere jugar, no atender un
 * aviso. Al tocarla sale de donde bajarla.
 */
public class AvisoActualizacion extends Button {

	private BitmapText texto;

	public AvisoActualizacion(float anchoPantalla, float abajo) {
		super();
		texto = new BitmapText("hay una version nueva: "
			+ Actualizacion.nombre(), PixelScene.font1x);
		texto.measure();
		texto.hardlight(0xE0A83A);
		add(texto);

		width = texto.width();
		height = texto.height();
		texto.x = x = (anchoPantalla - width) / 2;
		texto.y = y = abajo - height - 2;
	}

	@Override
	protected void layout() {
		super.layout();
		if (texto != null) {
			texto.x = x;
			texto.y = y;
		}
	}

	@Override
	protected void onClick() {
		String donde = Actualizacion.donde();
		StringBuilder sb = new StringBuilder();
		sb.append("Version ").append(Actualizacion.nombre())
		  .append(" disponible. Tienes la ").append(Game.version).append(".");
		if (Actualizacion.nota().length() > 0) {
			sb.append("\n\n").append(Actualizacion.nota());
		}
		// El enlace se ensena para copiarlo: abrir el navegador desde aqui
		// necesitaria permisos distintos en cada plataforma, y esto no vale
		// tanto como para pedirlos.
		sb.append("\n\n").append(donde.length() > 0 ? donde
			: "pixeldoomgeon.jaliscomundial.com");
		Window w = new WndMessage(sb.toString());
		Game.scene().add(w);
	}
}
