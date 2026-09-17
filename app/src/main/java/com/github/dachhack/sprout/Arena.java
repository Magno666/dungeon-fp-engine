/* PixelDoomgeon — arena de jefes. GPL-3.0-or-later */
package com.github.dachhack.sprout;

import com.github.dachhack.sprout.actors.Actor;
import com.github.dachhack.sprout.items.Item;
import com.github.dachhack.sprout.actors.hero.Hero;
import com.github.dachhack.sprout.actors.hero.HeroClass;
import com.github.dachhack.sprout.actors.mobs.Mob;
import com.github.dachhack.sprout.levels.Level;
import com.github.dachhack.sprout.scenes.GameScene;
import com.github.dachhack.sprout.scenes.StartScene;
import com.watabou.noosa.Game;

/**
 * Pelea contra un jefe, sin bajar veinticinco pisos para llegar.
 *
 * Vive aqui, dentro del juego, y no en la capa del navegador: asi viaja
 * dentro del APK igual que el resto. Lo que NO viaja es la manera de
 * encenderla -- no hay boton de arena en la pantalla de titulo. Se
 * enciende desde la pagina /arena/, que manda su eleccion en la URL, y
 * es la propia pagina la que antes cambia el cajon de guardado para que
 * insignias, ranking y partida de la arena vivan aparte. Sin eso,
 * cualquiera que se pusiera roto para matar a Yog desbloquearia logros y
 * ensuciaria las estadisticas del juego de verdad.
 */
public final class Arena {

    private Arena() {}

    /** True mientras se juega en la arena. */
    public static boolean activa = false;

    /** El jefe que se esta peleando, para la pantalla de resultado. */
    public static String jefeActual = "";

    /** Celda del jefe a la que hay que encarar, o -1 si ya se hizo. */
    private static int celdaAMirar = -1;

    /** Lo que falta entregar, en cuanto haya escena que lo aguante. */
    private static String objetosPendientes = "";
    private static int mejoraPendiente = 0;

    /**
     * Monta la pelea y salta a ella.
     *
     * @param jefe  clave corta: goo, tengu, dm300, rey, yog...
     * @param clase warrior, mage, rogue, huntress
     * @param roto  true para equipo maximo; false para lo que da la clase
     */
    public static void iniciar(String jefe, String clase, boolean roto) {
        iniciar(jefe, clase, roto, "", 0);
    }

    /**
     * @param objetos claves separadas por coma, tal como las manda la pagina
     * @param mejora  cuantos niveles subirle a lo que se pueda mejorar
     */
    public static void iniciar(String jefe, String clase, boolean roto,
                               String objetos, int mejora) {

        activa = true;
        jefeActual = jefe;

        StartScene.curClass = claseDe(clase);
        Dungeon.init();

        Dungeon.depth = profundidadDe(jefe);
        Level nivel = nivelDe(jefe);
        // Asignarlo ANTES de crearlo: varios createMobs() hablan de
        // Dungeon.level.mobs mientras se estan construyendo, y con
        // Dungeon.level todavia en null eso es un null.mobs.
        Dungeon.level = nivel;
        nivel.create();

        if (roto) {
            equiparRoto(Dungeon.hero);
        }
        // NO se entregan aqui. Item.collect() acaba tocando el quickslot,
        // que es interfaz, y la escena todavia no existe: sale un
        // null.width y la arena entera se cae antes de empezar. Se dejan
        // pendientes para cuando haya escena.
        objetosPendientes = objetos;
        mejoraPendiente = mejora;

        // El jefe se mete en la lista del nivel, no con GameScene.add: la
        // escena no existe todavia y GameScene.add le pide un sprite. Asi
        // entra igual que los bichos que pone el propio piso, y switchLevel
        // -- que llama a Actor.init() -- lo despierta con los demas.
        ponerJefe(jefe, nivel.entrance);

        Dungeon.switchLevel(nivel, nivel.entrance);
        // Frente al jefe, no en la puerta del piso. Varios jefes nacen en
        // su propia sala al otro extremo del nivel -- Yog entre ellos -- y
        // aparecer en la entrada de las Salas a buscarlo por los pasillos
        // no es una arena, es una caminata.
        plantarseFrenteAlJefe();

        Game.switchScene(GameScene.class);
    }

    /**
     * Entrar viendo al jefe. En una arena, aparecer de cara a una pared
     * mientras la cosa que viniste a pelear resopla a tu espalda no es
     * tension, es desorientacion.
     */
    private static void plantarseFrenteAlJefe() {
        Mob jefe = null;
        for (Mob m : Dungeon.level.mobs.toArray(new Mob[0])) {
            if (esJefe(m)) { jefe = m; break; }
        }
        if (jefe == null) return;

        int w = Level.getWidth();

        // De lejos hacia cerca, no al reves: a dos casillas el billboard
        // del jefe llena la pantalla y se lee como una textura verde, no
        // como un bicho. A cinco se le ve entero y da la talla.
        //
        // Un sitio desde el que DE VERDAD se le vea. Contar casillas no
        // basta: a cuatro de distancia puede haber un muro en medio, y
        // entonces apareces mirando con toda precision hacia una pared con
        // el jefe detras. Se prueban candidatos y se le pregunta al propio
        // campo de vision del juego cual funciona.
        int elegida = -1;
        int col0 = jefe.pos % w, fil0 = jefe.pos / w;
        int filas = Dungeon.level.map.length / w;

        // Primero, justo al SUR del jefe y en su misma columna: es el
        // encuadre mas limpio, con el jefe al fondo del pasillo.
        //
        // Aqui me equivoque antes: di por hecho que el yaw de arranque era
        // 0 (norte) y que colocado al sur ya lo tendrias de frente, asi
        // que en esta rama me saltaba el encarado. Medido con el rayo de
        // la pantalla: el yaw de arranque es 180, o sea mirando al sur --
        // de espaldas al jefe. Cero de 361 puntos de pantalla daban con
        // el. Se encara SIEMPRE, venga de la rama que venga.
        // Distancias probadas en orden de cuan bien se lee el jefe: tres
        // casillas es el punto dulce -- se le ve entero y ocupa pantalla.
        // A cinco es una figurita, a dos es una textura contra el ojo.
        final int[] DISTANCIAS = { 3, 4, 2, 5 };

        for (int i = 0; i < DISTANCIAS.length && elegida < 0; i++) {
            int r = DISTANCIAS[i];
            int fil = fil0 + r;
            if (fil >= filas) break;
            int c = fil * w + col0;
            if (!Level.passable[c] || Actor.findChar(c) != null) continue;
            Dungeon.hero.pos = c;
            Dungeon.observe();
            if (Level.fieldOfView[jefe.pos]) elegida = c;
        }

        // Si esa columna no sirve -- muro, agua, la sala no da -- cualquier
        // sitio con linea de vision, y ahi si se encara con faceCell.
        for (int i = 0; i < DISTANCIAS.length && elegida < 0; i++) {
            int r = DISTANCIAS[i];
            for (int dc = -r; dc <= r && elegida < 0; dc++) {
                for (int df = -r; df <= r; df++) {
                    if (Math.max(Math.abs(dc), Math.abs(df)) != r) continue;
                    int col = col0 + dc, fil = fil0 + df;
                    if (col < 0 || col >= w || fil < 0 || fil >= filas) continue;
                    int c = fil * w + col;
                    if (!Level.passable[c] || Actor.findChar(c) != null) continue;

                    Dungeon.hero.pos = c;
                    Dungeon.observe();
                    if (Level.fieldOfView[jefe.pos]) { elegida = c; break; }
                }
            }
        }

        if (elegida >= 0) {
            Dungeon.hero.pos = elegida;
        }
        if (Dungeon.hero.sprite != null) {
            Dungeon.hero.sprite.place(Dungeon.hero.pos);
        }
        Dungeon.observe();

        // Se deja pendiente, no se hace ya: la escena todavia no existe y
        // al construirse FirstPerson.reset() borraria el giro.
        celdaAMirar = jefe.pos;
    }

    /**
     * Se llama una vez por cuadro. En cuanto la camara existe, encara al
     * jefe y se apaga. Usa faceCell, que es la manera que el propio juego
     * tiene de girar la vista hacia algo -- poner el yaw a mano no sirve,
     * porque update() lo vuelve a mover cada cuadro.
     */
    public static void aplicarMirada() {

        if (FirstPerson.camera() == null) {
            return;
        }

        if (objetosPendientes.length() > 0) {
            String lista = objetosPendientes;
            int m = mejoraPendiente;
            objetosPendientes = "";
            mejoraPendiente = 0;
            darObjetos(Dungeon.hero, lista, m);
        }

        if (celdaAMirar < 0) {
            return;
        }
        FirstPerson.faceCell(celdaAMirar);
        // Y la vista mas nivelada que de costumbre. Con el cabeceo normal
        // el centro de la pantalla cae en el suelo y el jefe queda en la
        // mitad de arriba; medido con el rayo, en DM-300 el centro daba en
        // una losa a tres casillas. Aqui lo que importa es el bicho.
        FirstPerson.pitch = -2f;
        celdaAMirar = -1;
    }

    private static HeroClass claseDe(String c) {
        if ("mage".equals(c))     return HeroClass.MAGE;
        if ("rogue".equals(c))    return HeroClass.ROGUE;
        if ("huntress".equals(c)) return HeroClass.HUNTRESS;
        return HeroClass.WARRIOR;
    }

    /** La profundidad importa: los jefes y el botin escalan con ella. */
    private static int profundidadDe(String jefe) {
        if ("tengu".equals(jefe))     return 10;
        if ("dm300".equals(jefe))     return 15;
        if ("rey".equals(jefe))       return 20;
        if ("yog".equals(jefe))       return 25;
        // Los de Sprouted viven mas abajo y se llega a ellos por portal.
        // Las profundidades salen de Dungeon.newXLevel, para que escalen
        // igual que si hubieras llegado jugando.
        if ("shadowyog".equals(jefe)) return 35;
        if ("tenguden".equals(jefe))  return 36;
        if ("skeleton".equals(jefe))  return 37;
        if ("crab".equals(jefe))      return 38;
        if ("thief".equals(jefe))     return 40;
        if ("zot".equals(jefe))       return 99;
        return 5;
    }

    /**
     * Cada nivel se construye con new, no por reflexion: TeaVM tira lo que
     * nadie nombra, y un nivel invocado por su nombre en texto no lo
     * nombra nadie.
     */
    private static Level nivelDe(String jefe) {
        if ("tengu".equals(jefe)) {
            return new com.github.dachhack.sprout.levels.PrisonBossLevel();
        }
        if ("dm300".equals(jefe)) {
            return new com.github.dachhack.sprout.levels.CavesBossLevel();
        }
        if ("rey".equals(jefe)) {
            return new com.github.dachhack.sprout.levels.CityBossLevel();
        }
        if ("yog".equals(jefe)) {
            return new com.github.dachhack.sprout.levels.HallsBossLevel();
        }
        if ("shadowyog".equals(jefe)) {
            return new com.github.dachhack.sprout.levels.InfestBossLevel();
        }
        if ("tenguden".equals(jefe)) {
            return new com.github.dachhack.sprout.levels.TenguDenLevel();
        }
        if ("skeleton".equals(jefe)) {
            return new com.github.dachhack.sprout.levels.SkeletonBossLevel();
        }
        if ("crab".equals(jefe)) {
            return new com.github.dachhack.sprout.levels.CrabBossLevel();
        }
        if ("thief".equals(jefe)) {
            return new com.github.dachhack.sprout.levels.ThiefBossLevel();
        }
        // "tower" (MinesBossLevel) queda fuera de la pagina: el nivel se
        // genera en 32ms en la JVM pero en el navegador la pagina no
        // termina de cargar, y solo con este jefe. Sin causa encontrada
        // todavia, asi que no se ofrece -- mejor once jefes que funcionan
        // que doce con uno que cuelga la pestaña.
        if ("zot".equals(jefe)) {
            return new com.github.dachhack.sprout.levels.ZotBossLevel();
        }
        return new com.github.dachhack.sprout.levels.SewerBossLevel();
    }

    /**
     * Pone al jefe al lado del heroe si el nivel no lo trajo puesto.
     *
     * Hace falta porque varios no nacen con el piso: PrisonBossLevel tiene
     * createMobs() vacio a proposito -- Tengu no existe hasta que abres la
     * arena con la llave -- y en un sandbox no vamos a pedirle al jugador
     * que busque una llave para poder pelear.
     */
    private static void ponerJefe(String jefe, int cerca) {

        for (Mob m : Dungeon.level.mobs.toArray(new Mob[0])) {
            if (esJefe(m)) {
                return;
            }
        }

        Mob m = crearJefe(jefe);
        if (m == null) return;

        int w = Level.getWidth();
        // En anillos, para no plantarlo pegado a la cara: se quiere ver al
        // jefe entero al entrar, no su textura a un palmo del ojo.
        int[] anillo = { -w * 3, w * 3, -3, 3, -w * 2, w * 2, -2, 2, -w, w, -1, 1 };
        for (int d : anillo) {
            int c = cerca + d;
            if (c > 0 && c < Dungeon.level.map.length
                && Level.passable[c] && Actor.findChar(c) == null) {
                m.pos = c;
                Dungeon.level.mobs.add(m);
                return;
            }
        }
    }

    private static Mob crearJefe(String jefe) {
        if ("tengu".equals(jefe)) {
            return new com.github.dachhack.sprout.actors.mobs.Tengu();
        }
        if ("dm300".equals(jefe)) {
            return new com.github.dachhack.sprout.actors.mobs.DM300();
        }
        if ("rey".equals(jefe)) {
            return new com.github.dachhack.sprout.actors.mobs.King();
        }
        if ("yog".equals(jefe)) {
            return new com.github.dachhack.sprout.actors.mobs.Yog();
        }
        if ("shadowyog".equals(jefe)) {
            return new com.github.dachhack.sprout.actors.mobs.ShadowYog();
        }
        if ("tenguden".equals(jefe)) {
            return new com.github.dachhack.sprout.actors.mobs.TenguDen();
        }
        if ("skeleton".equals(jefe)) {
            return new com.github.dachhack.sprout.actors.mobs.SkeletonKing();
        }
        if ("crab".equals(jefe)) {
            return new com.github.dachhack.sprout.actors.mobs.CrabKing();
        }
        if ("thief".equals(jefe)) {
            return new com.github.dachhack.sprout.actors.mobs.ThiefKing();
        }
        if ("zot".equals(jefe)) {
            return new com.github.dachhack.sprout.actors.mobs.Zot();
        }
        return new com.github.dachhack.sprout.actors.mobs.Goo();
    }

    public static boolean esJefe(Mob m) {
        return m instanceof com.github.dachhack.sprout.actors.mobs.Goo
            || m instanceof com.github.dachhack.sprout.actors.mobs.PoisonGoo
            || m instanceof com.github.dachhack.sprout.actors.mobs.Tengu
            || m instanceof com.github.dachhack.sprout.actors.mobs.DM300
            || m instanceof com.github.dachhack.sprout.actors.mobs.King
            || m instanceof com.github.dachhack.sprout.actors.mobs.Yog
            || m instanceof com.github.dachhack.sprout.actors.mobs.ShadowYog
            || m instanceof com.github.dachhack.sprout.actors.mobs.TenguDen
            || m instanceof com.github.dachhack.sprout.actors.mobs.SkeletonKing
            || m instanceof com.github.dachhack.sprout.actors.mobs.CrabKing
            || m instanceof com.github.dachhack.sprout.actors.mobs.ThiefKing
            || m instanceof com.github.dachhack.sprout.actors.mobs.Zot;
    }

    /**
     * Mete en la mochila lo que se pidio desde la pagina, y equipa lo
     * primero que sirva de arma y de armadura -- en una arena nadie quiere
     * abrir el inventario antes de que el jefe le pegue.
     *
     * Todo entra identificado: la gracia de escoger una varita concreta se
     * pierde si te llega sin nombre y hay que probarla a ver que hace.
     */
    private static void darObjetos(Hero heroe, String objetos, int mejora) {

        if (objetos == null || objetos.length() == 0) return;

        com.github.dachhack.sprout.items.KindOfWeapon arma = null;
        com.github.dachhack.sprout.items.armor.Armor armadura = null;

        for (String clave : objetos.split(",")) {
            clave = clave.trim();
            if (clave.length() == 0) continue;

            Item it = ArenaObjetos.crear(clave);
            if (it == null) continue;

            it.identify();
            for (int i = 0; i < mejora; i++) {
                try { it.upgrade(); } catch (Throwable t) { break; }
            }
            it.collect();

            if (arma == null
                    && it instanceof com.github.dachhack.sprout.items.KindOfWeapon) {
                arma = (com.github.dachhack.sprout.items.KindOfWeapon) it;
            } else if (armadura == null
                    && it instanceof com.github.dachhack.sprout.items.armor.Armor) {
                armadura = (com.github.dachhack.sprout.items.armor.Armor) it;
            }
        }

        // Lo escogido gana sobre lo que trae la clase. Sin quitar primero
        // lo de fabrica, doEquip no hace nada -- el guerrero ya nace con
        // espada corta -- y te vas a pelear con Yog llevando en la mochila
        // el martillo que pediste.
        if (arma != null) {
            if (heroe.belongings.weapon != null) {
                heroe.belongings.weapon.doUnequip(heroe, true);
            }
            arma.doEquip(heroe);
        }
        if (armadura != null) {
            if (heroe.belongings.armor != null) {
                heroe.belongings.armor.doUnequip(heroe, true);
            }
            armadura.doEquip(heroe);
        }
    }

    /** Equipo de "ponerme roto": lo que trae la clase, subido al tope. */
    private static void equiparRoto(Hero heroe) {
        if (heroe.belongings.weapon != null) {
            for (int i = 0; i < 15; i++) heroe.belongings.weapon.upgrade();
        }
        if (heroe.belongings.armor != null) {
            for (int i = 0; i < 15; i++) heroe.belongings.armor.upgrade();
        }
        heroe.HT = 500;
        heroe.HP = heroe.HT;
        heroe.lvl = 30;
        heroe.STR = 25;
    }

}
