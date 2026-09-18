#!/usr/bin/env python3
"""
El icono de lanzador de PixelDoomgeon. GPL-3.0-or-later

El que habia era el cofre verde de Sprouted, byte a byte en las cinco
densidades. Con el nombre cambiado y el mismo icono, en el cajon de
aplicaciones seguia pareciendo el juego de dachhack -- justo la confusion
que hay que evitar antes de distribuir algo hecho sobre trabajo ajeno.

Este es el mismo pasillo en perspectiva de un punto que usa la pagina.
Se dibuja NATIVO a cada tamano y no escalando un 16x16: 72 no es multiplo
de 16, asi que reescalar dejaria filas de pixeles de distinto grosor y se
notaria justo en la densidad mas comun.

  python3 tools/icono.py           escribe las cinco densidades
"""
import os

from PIL import Image

# mdpi 48, hdpi 72, xhdpi 96, xxhdpi 144, xxxhdpi 192
DENSIDADES = {
    "mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192,
}

RES = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "app", "src", "main", "res")

# Las cuatro superficies tienen que separarse a 48 px o el icono se lee
# como un moño: con el techo y el suelo casi del mismo tono solo quedan
# los dos triangulos de las paredes y la profundidad se pierde.
MURO_LUZ    = (224, 168, 58, 255)     # el oro de la pagina
MURO_SOMBRA = (120, 87, 31, 255)
TECHO       = (18, 16, 11, 255)       # casi negro
SUELO       = (110, 92, 52, 255)      # claro, para que no se funda con el
LEJOS       = (232, 238, 233, 255)    # la luz al final


def pintar(lado):
    centro = lado / 2 - 0.5
    medio = lado / 8              # medio ancho del hueco del fondo
    img = Image.new("RGBA", (lado, lado))
    px = img.load()
    for y in range(lado):
        for x in range(lado):
            dx, dy = x - centro, y - centro
            if abs(dx) <= medio and abs(dy) <= medio:
                borde = abs(dx) > medio - max(1, lado // 32) \
                     or abs(dy) > medio - max(1, lado // 32)
                px[x, y] = MURO_LUZ if borde else LEJOS
            elif abs(dx) > abs(dy):
                px[x, y] = MURO_LUZ if dx < 0 else MURO_SOMBRA
            else:
                px[x, y] = TECHO if dy < 0 else SUELO
    return img


if __name__ == "__main__":
    for dens, lado in DENSIDADES.items():
        carpeta = os.path.join(RES, "drawable-" + dens)
        os.makedirs(carpeta, exist_ok=True)
        destino = os.path.join(carpeta, "ic_launcher.png")
        pintar(lado).save(destino)
        print("%-9s %3dpx  %s" % (dens, lado, destino))
