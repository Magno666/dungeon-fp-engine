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
package com.github.dachhack.sprout.scenes;

import java.io.IOException;
import java.util.ArrayList;

import com.github.dachhack.sprout.Assets;
import com.github.dachhack.sprout.Badges;
import com.github.dachhack.sprout.Dungeon;
import com.github.dachhack.sprout.FirstPerson;
import com.github.dachhack.sprout.Adornos;
import com.github.dachhack.sprout.DungeonTilemap;
import com.github.dachhack.sprout.FogOfWar;
import com.github.dachhack.sprout.ShatteredPixelDungeon;
import com.github.dachhack.sprout.Statistics;
import com.github.dachhack.sprout.actors.Actor;
import com.github.dachhack.sprout.actors.blobs.Blob;
import com.github.dachhack.sprout.actors.mobs.Mob;
import com.github.dachhack.sprout.effects.BannerSprites;
import com.github.dachhack.sprout.effects.BlobEmitter;
import com.github.dachhack.sprout.effects.EmoIcon;
import com.github.dachhack.sprout.effects.Flare;
import com.github.dachhack.sprout.effects.FloatingText;
import com.github.dachhack.sprout.effects.Ripple;
import com.github.dachhack.sprout.effects.SpellSprite;
import com.github.dachhack.sprout.items.Heap;
import com.github.dachhack.sprout.items.Honeypot;
import com.github.dachhack.sprout.items.Item;
import com.github.dachhack.sprout.items.bags.PotionBandolier;
import com.github.dachhack.sprout.items.bags.ScrollHolder;
import com.github.dachhack.sprout.items.bags.SeedPouch;
import com.github.dachhack.sprout.items.bags.WandHolster;
import com.github.dachhack.sprout.items.potions.Potion;
import com.github.dachhack.sprout.items.wands.WandOfBlink;
import com.github.dachhack.sprout.levels.Level;
import com.github.dachhack.sprout.levels.RegularLevel;
import com.github.dachhack.sprout.levels.features.Chasm;
import com.github.dachhack.sprout.plants.Plant;
import com.github.dachhack.sprout.sprites.CharSprite;
import com.github.dachhack.sprout.sprites.DiscardedItemSprite;
import com.github.dachhack.sprout.sprites.HeroSprite;
import com.github.dachhack.sprout.sprites.ItemSprite;
import com.github.dachhack.sprout.sprites.PlantSprite;
import com.github.dachhack.sprout.ui.AttackIndicator;
import com.github.dachhack.sprout.ui.Banner;
import com.github.dachhack.sprout.ui.BusyIndicator;
import com.github.dachhack.sprout.ui.GameLog;
import com.github.dachhack.sprout.ui.HealthIndicator;
import com.github.dachhack.sprout.ui.LootIndicator;
import com.github.dachhack.sprout.ui.QuickSlotButton;
import com.github.dachhack.sprout.ui.ResumeIndicator;
import com.github.dachhack.sprout.ui.StatusPane;
import com.github.dachhack.sprout.ui.Toast;
import com.github.dachhack.sprout.ui.Toolbar;
import com.github.dachhack.sprout.ui.Window;
import com.github.dachhack.sprout.utils.GLog;
import com.github.dachhack.sprout.windows.WndBag;
import com.github.dachhack.sprout.windows.WndBag.Mode;
import com.github.dachhack.sprout.windows.WndGame;
import com.github.dachhack.sprout.windows.WndStory;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import com.watabou.noosa.Group;
import com.watabou.noosa.SkinnedBlock;
import com.watabou.noosa.Visual;
import com.watabou.noosa.audio.Music;
import com.watabou.noosa.audio.Sample;
import com.watabou.noosa.particles.Emitter;
import com.watabou.utils.Random;
import com.github.dachhack.sprout.FirstPersonControls;
import com.github.dachhack.sprout.Minimap;
import com.github.dachhack.sprout.Targeting;
import com.github.dachhack.sprout.Billboards;
import com.github.dachhack.sprout.Feel;
import com.watabou.noosa.Gizmo;

public class GameScene extends PixelScene {

	private static final String TXT_WELCOME = "Welcome to the level %d of Pixel Dungeon!";
	private static final String TXT_WELCOME_BACK = "Welcome back to the level %d of Pixel Dungeon!";

	private static final String TXT_CHASM = "Your steps echo across the dungeon.";
	private static final String TXT_WATER = "You hear water splashing around you.";
	private static final String TXT_GRASS = "The smell of vegetation is thick in the air.";
	private static final String TXT_DARK = "You can hear enemies moving in the darkness...";
	private static final String TXT_SECRETS = "The atmosphere hints that this floor hides many secrets.";

	static GameScene scene;

	private SkinnedBlock water;
	private DungeonTilemap tiles;
	private FogOfWar fog;
	private boolean firstPersonHidesFlatWorld = false;

	/** The level's own decorations, which are loose children of the scene
	 *  rather than a group. See addVisuals below. */
	private ArrayList<Gizmo> adornos = new ArrayList<Gizmo>();
	private HeroSprite hero;

	private GameLog log;

	private BusyIndicator busy;

	private static CellSelector cellSelector;

	private Group terrain;
	private Group ripples;
	private Group plants;
	private Group heaps;
	private Group mobs;
	private Group emitters;
	private Group effects;
	private Group gases;
	private Group spells;
	private Group statuses;
	private Group emoicons;

	private Toolbar toolbar;
	private Toast prompt;

	private AttackIndicator attack;
	private LootIndicator loot;
	private ResumeIndicator resume;

	@Override
	public void create() {

		Music.INSTANCE.play(Assets.TUNE, true);
		Music.INSTANCE.volume(1f);

		ShatteredPixelDungeon.lastClass(Dungeon.hero.heroClass.ordinal());

		super.create();
		Camera.main.zoom(defaultZoom + ShatteredPixelDungeon.zoom());

		scene = this;

		terrain = new Group();
		add(terrain);

		water = new SkinnedBlock(Level.getWidth() * DungeonTilemap.SIZE,
				Level.HEIGHT * DungeonTilemap.SIZE, Dungeon.level.waterTex());
		terrain.add(water);

		ripples = new Group();
		terrain.add(ripples);

		tiles = new DungeonTilemap();
		terrain.add(tiles);

		// First person view. Off unless FirstPerson.enabled, in which case
		// the flat tilemap and the water sheet are hidden and the same
		// Dungeon.level.map is drawn as geometry instead.
		FirstPerson.install(terrain);
		if (FirstPerson.enabled) {
			FirstPersonControls.install(this, uiCamera);
			Targeting.install(terrain, this,
				FirstPerson.camera(), uiCamera);
		}
		if (FirstPerson.enabled) {
			tiles.visible = false;
			water.visible = false;
		}
		firstPersonHidesFlatWorld = FirstPerson.enabled;

		// Every level type hangs decorations straight off the scene -- the
		// dripping pipes of the sewers, the prison torches, the city smoke,
		// the ore veins, the halls streams. They sit in flat map coordinates
		// like every other 2D layer, but because they are loose children and
		// not a named group, they were the one thing the first person switch
		// below never got to hide: their particles went through the
		// perspective camera and landed all over the screen, unrelated to
		// the wall they belong to. Reported from the sewers, but it was all
		// nine level types. Remembered here so they can be dealt with.
		int antesDeAdornos = members.size();
		Dungeon.level.addVisuals(this);
		adornos = new ArrayList<Gizmo>(
			members.subList(antesDeAdornos, members.size()));

		plants = new Group();
		add(plants);

		int size = Dungeon.level.plants.size();
		for (int i = 0; i < size; i++) {
			addPlantSprite(Dungeon.level.plants.valueAt(i));
		}

		heaps = new Group();
		add(heaps);

		size = Dungeon.level.heaps.size();
		for (int i = 0; i < size; i++) {
			addHeapSprite(Dungeon.level.heaps.valueAt(i));
		}

		emitters = new Group();
		effects = new Group();
		emoicons = new Group();

		mobs = new Group();
		add(mobs);

		for (Mob mob : Dungeon.level.mobs) {
			addMobSprite(mob);
			if (Statistics.amuletObtained) {
				mob.beckon(Dungeon.hero.pos);
			}
		}

		add(emitters);
		add(effects);

		gases = new Group();
		add(gases);

		for (Blob blob : Dungeon.level.blobs.values()) {
			blob.emitter = null;
			addBlobSprite(blob);
		}

		fog = new FogOfWar(Level.getWidth(), Level.HEIGHT);
		fog.updateVisibility(Dungeon.visible, Dungeon.level.visited,
				Dungeon.level.mapped);
		add(fog);

		// Every remaining world layer is drawn flat with the 2D camera and
		// would sit on top of the 3D view. The fog alone would black out
		// most of the screen. The UI is untouched -- it has its own camera.
		if (firstPersonHidesFlatWorld) {
			// The flat view WAS the map; first person took it away. Put it
			// back as a corner window before hiding the full-screen one.
			Minimap.install(this);
			fog.visible = false;
			ripples.visible = false;
			// Blobs now get world-space marks from Billboards, so the flat
			// gas layer can go: drawn in map coordinates it put fire and
			// Goo's attack warning somewhere unrelated to the danger, which
			// is worse than not drawing them at all.
			gases.visible = false;
			plants.visible = false;
			heaps.visible = false;
			mobs.visible = false;
			// These are positioned in 2D tile-world coordinates and drawn
			// through Camera.main, which now follows a hidden map. Left
			// visible, a damage number for a monster four tiles east floats
			// far off to the side of where its billboard actually is --
			// worse than absent, because it points at nothing.
			// `statuses` and `spells` do not exist yet; they are hidden
			// right after they are built, below.
			emoicons.visible = false;
			effects.visible = false;
			// CellEmitter.get() pours into this one -- fire, embers, poison.
			// Same flat coordinates, same problem.
			emitters.visible = false;
			// Los adornos si se pueden salvar: Adornos le da a cada uno su
			// propia camara y la mueve cada cuadro para que sus particulas
			// caigan sobre la pared de la que salen. Si no logra averiguar
			// a que casilla va cada uno, se esconden como antes.
			Adornos.install(adornos);
			if (!Adornos.colocados()) {
				for (Gizmo g : adornos) {
					g.visible = false;
				}
			}
		}

		brightness(ShatteredPixelDungeon.brightness());

		spells = new Group();
		add(spells);

		statuses = new Group();
		add(statuses);

		if (firstPersonHidesFlatWorld) {
			spells.visible = false;
			// Damage numbers come back, but as screen text: CharSprite now
			// projects the creature's cell through the 3D camera, so they
			// belong to the UI camera rather than the hidden map camera.
			statuses.camera = uiCamera;
		}

		add(emoicons);

		hero = new HeroSprite();
		hero.place(Dungeon.hero.pos);
		hero.updateArmor();
		mobs.add(hero);

		add(new HealthIndicator());

		add(cellSelector = new CellSelector(tiles));
		// TouchArea.onSignal cancels the touch signal for anyone downstream,
		// and cellSelector covers the whole screen. Leaving it live would
		// swallow every stick push and look drag before the first person
		// controls saw them. select() still works, so spell targeting is
		// unaffected -- taps reach it through GameScene.handleCell.
		cellSelector.active = !FirstPerson.enabled;

		StatusPane sb = new StatusPane();
		sb.camera = uiCamera;
		sb.setSize(uiCamera.width, 0);
		add(sb);

		toolbar = new Toolbar();
		toolbar.camera = uiCamera;
		// Toolbar.layout anchors wait/search/info to the LEFT edge of its
		// rect and the quickslots to the RIGHT edge. Given the full width of
		// a landscape phone that leaves a two-thousand-pixel hole between
		// them, which is why only the backpack looked like it was there.
		// In first person the bar gets just the width its buttons need and
		// sits on the right, clear of the thumbstick.
		if (FirstPerson.enabled) {
			float w = Math.min(FirstPersonControls
				.toolbarWidthUi, uiCamera.width);
			toolbar.setRect(uiCamera.width - w,
					uiCamera.height - toolbar.height(), w, toolbar.height());
		} else {
			toolbar.setRect(0, uiCamera.height - toolbar.height(), uiCamera.width,
					toolbar.height());
		}
		add(toolbar);

		attack = new AttackIndicator();
		attack.camera = uiCamera;
		attack.setPos(uiCamera.width - attack.width(),
				toolbar.top() - attack.height());
		add(attack);

		loot = new LootIndicator();
		loot.camera = uiCamera;
		add(loot);

		resume = new ResumeIndicator();
		resume.camera = uiCamera;
		add(resume);

		layoutTags();

		log = new GameLog();
		log.camera = uiCamera;
		log.setRect(0, toolbar.top(), attack.left(), 0);
		add(log);

		// GLog is a Signal with GameLog as its only listener, so anything
		// logged before this point is dispatched to nobody and lost. The
		// build marker has to be announced here, not up where the first
		// person view is installed.
		if (FirstPerson.enabled) {
			GLog.i(FirstPerson.BUILD);
		}

		if (Dungeon.depth < Statistics.deepestFloor)
			GLog.i(TXT_WELCOME_BACK, Dungeon.depth);
		else
			GLog.i(TXT_WELCOME, Dungeon.depth);
		Sample.INSTANCE.play(Assets.SND_DESCEND);
		switch (Dungeon.level.feeling) {
		case CHASM:
			GLog.w(TXT_CHASM);
			break;
		case WATER:
			GLog.w(TXT_WATER);
			break;
		case GRASS:
			GLog.w(TXT_GRASS);
			break;
		case DARK:
			GLog.w(TXT_DARK);
			break;
		default:
		}
		if (Dungeon.level instanceof RegularLevel
				&& ((RegularLevel) Dungeon.level).secretDoors > Random
						.IntRange(3, 4)) {
			GLog.w(TXT_SECRETS);
		}

		busy = new BusyIndicator();
		busy.camera = uiCamera;
		busy.x = 1;
		busy.y = sb.bottom() + 1;
		add(busy);

		switch (InterlevelScene.mode) {
		case RESURRECT:
			WandOfBlink.appear(Dungeon.hero, Dungeon.level.entrance);
			new Flare(8, 32).color(0xFFFF66, true).show(hero, 2f);
			break;
		case RETURN:
			WandOfBlink.appear(Dungeon.hero, Dungeon.hero.pos);
			break;
		case FALL:
			Chasm.heroLand();
			break;
		case PALANTIR:
			WndStory.showChapter(WndStory.ID_ZOT);
			break;
		case DESCEND:
			switch (Dungeon.depth) {
			case 1:
				WndStory.showChapter(WndStory.ID_SEWERS);
				break;
			case 6:
				WndStory.showChapter(WndStory.ID_PRISON);
				break;
			case 11:
				WndStory.showChapter(WndStory.ID_CAVES);
				break;
			case 16:
				WndStory.showChapter(WndStory.ID_METROPOLIS);
				break;
			}
			
		case JOURNAL:
			switch (Dungeon.depth) {
			case 50:
				WndStory.showChapter(WndStory.ID_SAFELEVEL);
				break;
			case 51:
				WndStory.showChapter(WndStory.ID_SOKOBAN1);
				break;
			case 52:
				WndStory.showChapter(WndStory.ID_SOKOBAN2);
				break;
			case 53:
				WndStory.showChapter(WndStory.ID_SOKOBAN3);
				break;
			case 54:
				WndStory.showChapter(WndStory.ID_SOKOBAN4);
				break;
			case 55:
				WndStory.showChapter(WndStory.ID_TOWN);
				break;
			}
			
			if (Dungeon.hero.isAlive() && Dungeon.depth != 22) {
				Badges.validateNoKilling();
			}
			break;
		default:
		}

		ArrayList<Item> dropped = Dungeon.droppedItems.get(Dungeon.depth);
		if (dropped != null) {
			for (Item item : dropped) {
				int pos = Dungeon.level.randomRespawnCell();
				if (item instanceof Potion) {
					((Potion) item).shatter(pos);
				} else if (item instanceof Plant.Seed) {
					Dungeon.level.plant((Plant.Seed) item, pos);
				} else if (item instanceof Honeypot) {
					Dungeon.level.drop(((Honeypot) item).shatter(null, pos),
							pos);
				} else {
					Dungeon.level.drop(item, pos);
				}
			}
			Dungeon.droppedItems.remove(Dungeon.depth);
		}

		Camera.main.target = hero;
		fadeIn();
	}

	@Override
	public void destroy() {

		// Without this the static billboard map holds every Mob and Heap of
		// the finished level, and through Char.sprite a chain of textures,
		// for as long as the process lives.
		FirstPerson.reset();
		Adornos.reset();
		FirstPersonControls.uninstall();
		Minimap.clear();
		Targeting.clear();

		freezeEmitters = false;

		scene = null;
		Badges.saveGlobal();

		super.destroy();
	}

	@Override
	public synchronized void pause() {
		try {
			Dungeon.saveAll();
			Badges.saveGlobal();
		} catch (IOException e) {
			//
		}
	}

	@Override
	public synchronized void update() {
		if (Dungeon.hero == null) {
			return;
		}

		super.update();

		// Despues de super.update(): los adornos se encienden y se apagan
		// solos ahi segun Dungeon.visible, y esto solo recoloca y apaga los
		// que quedan detras del ojo.
		Adornos.update();


		// Keep the first person camera on the hero's cell. No-op when off.
		FirstPerson.update();
		FirstPersonControls.update();
		Minimap.update();
		Targeting.update(
			FirstPerson.yaw);

		if (!freezeEmitters)
			water.offset(0, -5 * Game.elapsed);

		Actor.process();

		if (Dungeon.hero.ready && !Dungeon.hero.paralysed) {
			log.newLine();
		}

		if (tagAttack != attack.active || tagLoot != loot.visible
				|| tagResume != resume.visible) {

			boolean atkAppearing = attack.active && !tagAttack;
			boolean lootAppearing = loot.visible && !tagLoot;
			boolean resAppearing = resume.visible && !tagResume;

			tagAttack = attack.active;
			tagLoot = loot.visible;
			tagResume = resume.visible;

			if (atkAppearing || lootAppearing || resAppearing)
				layoutTags();
		}

		cellSelector.enable(Dungeon.hero.ready);
	}

	private boolean tagAttack = false;
	private boolean tagLoot = false;
	private boolean tagResume = false;

	private void layoutTags() {

		float pos = tagAttack ? attack.top() : toolbar.top();

		if (tagLoot) {
			loot.setPos(uiCamera.width - loot.width(), pos - loot.height());
			pos = loot.top();
		}

		if (tagResume) {
			resume.setPos(uiCamera.width - resume.width(),
					pos - resume.height());
		}
	}

	@Override
	protected void onBackPressed() {
		if (!cancel()) {
			add(new WndGame());
		}
	}

	@Override
	protected void onMenuPressed() {
		if (Dungeon.hero.ready) {
			selectItem(null, WndBag.Mode.ALL, null);
		}
	}

	public void brightness(boolean value) {
		water.rm = water.gm = water.bm = tiles.rm = tiles.gm = tiles.bm = value ? 1.5f
				: 1.0f;
		if (value) {
			fog.am = +2f;
			fog.aa = -1f;
		} else {
			fog.am = +1f;
			fog.aa = 0f;
		}
	}

	private void addHeapSprite(Heap heap) {
		ItemSprite sprite = heap.sprite = (ItemSprite) heaps
				.recycle(ItemSprite.class);
		sprite.revive();
		sprite.link(heap);
		heaps.add(sprite);
	}

	private void addDiscardedSprite(Heap heap) {
		heap.sprite = (DiscardedItemSprite) heaps
				.recycle(DiscardedItemSprite.class);
		heap.sprite.revive();
		heap.sprite.link(heap);
		heaps.add(heap.sprite);
	}

	private void addPlantSprite(Plant plant) {
		(plant.sprite = (PlantSprite) plants.recycle(PlantSprite.class))
				.reset(plant);
	}

	private void addBlobSprite(final Blob gas) {
		if (gas.emitter == null) {
			gases.add(new BlobEmitter(gas));
		}
	}

	private void addMobSprite(Mob mob) {
		CharSprite sprite = mob.sprite();
		sprite.visible = Dungeon.visible[mob.pos];
		mobs.add(sprite);
		sprite.link(mob);
	}

	private void prompt(String text) {

		if (prompt != null) {
			prompt.killAndErase();
			prompt = null;
		}

		if (text != null) {
			prompt = new Toast(text) {
				@Override
				protected void onClose() {
					cancel();
				}
			};
			prompt.camera = uiCamera;
			prompt.setPos((uiCamera.width - prompt.width()) / 2,
					uiCamera.height - 60);
			add(prompt);
		}
	}

	private void showBanner(Banner banner) {
		banner.camera = uiCamera;
		banner.x = align(uiCamera, (uiCamera.width - banner.width) / 2);
		banner.y = align(uiCamera, (uiCamera.height - banner.height) / 3);
		add(banner);
	}

	// -------------------------------------------------------

	public static void add(Plant plant) {
		if (scene != null) {
			scene.addPlantSprite(plant);
		}
	}

	public static void add(Blob gas) {
		Actor.add(gas);
		if (scene != null) {
			scene.addBlobSprite(gas);
		}
	}

	public static void add(Heap heap) {
		if (scene != null) {
			scene.addHeapSprite(heap);
		}
	}

	public static void discard(Heap heap) {
		if (scene != null) {
			scene.addDiscardedSprite(heap);
		}
	}

	public static void add(Mob mob) {
		Dungeon.level.mobs.add(mob);
		Actor.add(mob);
		Actor.occupyCell(mob);
		scene.addMobSprite(mob);
	}

	public static void add(Mob mob, float delay) {
		Dungeon.level.mobs.add(mob);
		Actor.addDelayed(mob, delay);
		Actor.occupyCell(mob);
		scene.addMobSprite(mob);
	}

	public static void add(EmoIcon icon) {
		scene.emoicons.add(icon);
	}

	public static void effect(Visual effect) {
		scene.effects.add(effect);
	}

	public static Ripple ripple(int pos) {
		Ripple ripple = (Ripple) scene.ripples.recycle(Ripple.class);
		ripple.reset(pos);
		return ripple;
	}

	public static SpellSprite spellSprite() {
		return (SpellSprite) scene.spells.recycle(SpellSprite.class);
	}

	/**
	 * Diagnostico: cuantas capas de particulas siguen dibujandose en
	 * coordenadas de mapa. En primera persona tiene que dar 0 -- las que
	 * queden pintan a traves de la camara en perspectiva y sus particulas
	 * acaban en cualquier punto de la pantalla, sin relacion con la pared
	 * a la que pertenecen. Devuelve -1 si no hay escena.
	 */
	/** Cuantas capas planas de particulas tiene el nivel en total, se
	 *  esten dibujando o no. Junto a emisoresPlanosVisibles() dice de
	 *  cuantas se trata: un nivel de alcantarillas trae una por cada
	 *  tuberia, y llegan a ser veintitantas. */
	public static int emisoresPlanos() {
		return scene == null ? -1 : scene.adornos.size() + 1;
	}

	public static int emisoresPlanosVisibles() {
		if (scene == null) {
			return -1;
		}
		int n = 0;
		for (Gizmo g : scene.adornos) {
			// Con camara propia el adorno ya no se pinta en coordenadas de
			// mapa: Adornos la desplaza a donde el render 3D pone su
			// casilla. Los que cuentan son los que siguen sin ella.
			if (g != null && g.visible && g.camera == null) {
				n++;
			}
		}
		if (scene.emitters != null && scene.emitters.visible) {
			n++;
		}
		return n;
	}

	public static Emitter emitter() {
		if (scene != null) {
			Emitter emitter = (Emitter) scene.emitters.recycle(Emitter.class);
			emitter.revive();
			return emitter;
		} else {
			return null;
		}
	}

	public static FloatingText status() {
		return scene != null ? (FloatingText) scene.statuses
				.recycle(FloatingText.class) : null;
	}

	public static void pickUp(Item item) {
		scene.toolbar.pickup(item);
	}

	public static void updateMap() {
		FirstPerson.terrainChanged();
		if (scene != null) {
			scene.tiles.updated.set(0, 0, Level.getWidth(), Level.HEIGHT);
		}
	}

	public static void updateMap(int cell) {
		FirstPerson.terrainChanged();
		if (scene != null) {
			scene.tiles.updated.union(cell % Level.getWidth(), cell / Level.getWidth());
		}
	}

	public static void discoverTile(int pos, int oldValue) {
		if (scene != null) {
			scene.tiles.discover(pos, oldValue);
		}
	}

	public static void show(Window wnd) {
		cancelCellSelector();
		scene.add(wnd);
	}

	/** Screen Y where the toolbar starts, so the first person controls can
	 *  sit above it instead of on top of its buttons. Game.height when
	 *  there is no toolbar yet -- it is built late in create(), and the
	 *  stick is laid out every frame, so it corrects itself. */
	public static float toolbarTopPx() {
		if (scene == null || scene.toolbar == null || uiCamera == null) {
			return Game.height;
		}
		return scene.toolbar.top() * uiCamera.zoom;
	}

	/** True while any modal window is up. Lives here because Group.members
	 *  is protected and only the scene itself can look. */
	public static boolean windowOpen() {
		if (scene == null) {
			return false;
		}
		for (Gizmo g : scene.members) {
			if (g instanceof Window && g.exists) {
				return true;
			}
		}
		return false;
	}

	public static void afterObserve() {
		if (scene != null) {
			Minimap.refresh();
			scene.fog.updateVisibility(Dungeon.visible, Dungeon.level.visited,
					Dungeon.level.mapped);

			// Copia y guarda de nulos. En Android esto nunca corre a media
			// transicion de piso: switchScene destruye la GameScene antes,
			// destroy() pone scene = null y el bucle se salta entero. En el
			// navegador el hilo de la transicion corre ANTES de que el
			// cambio de escena se procese, asi que scene sigue vivo y aqui
			// desfilan los bichos del nivel recien generado, que todavia no
			// tienen sprite -- el sprite se crea cuando la escena se arma.
			// Sin esto: null.visible, o ConcurrentModificationException
			// cuando la lista cambia debajo, y en los dos casos la pantalla
			// se queda congelada en "Descending..." / "Falling..." para
			// siempre. Leonel: "me cai a un chasm a proposito y nomas
			// decia falling".
			for (Mob mob : Dungeon.level.mobs.toArray( new Mob[0] )) {
				if (mob.sprite != null) {
					mob.sprite.visible = Dungeon.visible[mob.pos];
				}
			}
		}
	}

	public static void flash(int color) {
		scene.fadeIn(0xFF000000 | color, true);
	}

	public static void gameOver() {
		Banner gameOver = new Banner(
				BannerSprites.get(BannerSprites.Type.GAME_OVER));
		gameOver.show(0x000000, 1f);
		scene.showBanner(gameOver);

		Sample.INSTANCE.play(Assets.SND_DEATH);
	}

	public static void bossSlain() {
		if (Dungeon.hero.isAlive()) {
			Banner bossSlain = new Banner(
					BannerSprites.get(BannerSprites.Type.BOSS_SLAIN));
			bossSlain.show(0xFFFFFF, 0.3f, 5f);
			scene.showBanner(bossSlain);

			Sample.INSTANCE.play(Assets.SND_BOSS);
		}
	}
	
	public static void levelCleared() {
		if (Dungeon.hero.isAlive()) {
			Banner levelCleared = new Banner(
					BannerSprites.get(BannerSprites.Type.CLEARED));
			levelCleared.show(0xFFFFFF, 0.3f, 5f);
			scene.showBanner(levelCleared);

			Sample.INSTANCE.play(Assets.SND_BADGE);
		}
	}

	public static void handleCell(int cell) {
		cellSelector.select(cell);
	}

	public static void selectCell(CellSelector.Listener listener) {
		cellSelector.listener = listener;
		scene.prompt(listener.prompt());
	}

	/** True while the game is waiting for the player to pick a target --
	 *  a bow, a wand, a thrown dart. The default listener is the one that
	 *  just walks and attacks, so anything else means "choose something". */
	public static boolean targeting() {
		return cellSelector != null && cellSelector.listener != null
			&& cellSelector.listener != defaultCellListener;
	}

	private static boolean cancelCellSelector() {
		if (cellSelector.listener != null
				&& cellSelector.listener != defaultCellListener) {
			cellSelector.cancel();
			return true;
		} else {
			return false;
		}
	}

	public static WndBag selectItem(WndBag.Listener listener, WndBag.Mode mode,
			String title) {
		cancelCellSelector();

		WndBag wnd = mode == Mode.SEED ? WndBag.getBag(SeedPouch.class,
				listener, mode, title) : mode == Mode.SCROLL ? WndBag.getBag(
				ScrollHolder.class, listener, mode, title)
				: mode == Mode.POTION ? WndBag.getBag(PotionBandolier.class,
						listener, mode, title) : mode == Mode.WAND ? WndBag
						.getBag(WandHolster.class, listener, mode, title)
						: WndBag.lastBag(listener, mode, title);

		scene.add(wnd);

		return wnd;
	}

	static boolean cancel() {
		if (Dungeon.hero.curAction != null || Dungeon.hero.restoreHealth) {

			Dungeon.hero.curAction = null;
			Dungeon.hero.restoreHealth = false;
			return true;

		} else {

			return cancelCellSelector();

		}
	}

	public static void ready() {
		selectCell(defaultCellListener);
		QuickSlotButton.cancel();
	}

	private static final CellSelector.Listener defaultCellListener = new CellSelector.Listener() {
		@Override
		public void onSelect(Integer cell) {
			if (Dungeon.hero.handle(cell)) {
				Dungeon.hero.next();
			}
		}

		@Override
		public String prompt() {
			return null;
		}
	};
}
