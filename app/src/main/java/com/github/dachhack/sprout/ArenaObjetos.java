/* PixelDoomgeon — catalogo de la arena. GPL-3.0-or-later */
package com.github.dachhack.sprout;

import com.github.dachhack.sprout.items.Item;

/**
 * Cada objeto que la arena sabe entregar, construido con new.
 *
 * Generado del catalogo real del juego (Generator.Category.classes), no
 * escrito a mano: la lista de Sprouted no es la de Pixel Dungeon y a mano
 * se queda desfasada al primer cambio.
 *
 * Por que un if por objeto y no Class.forName: TeaVM tira del paquete
 * todo lo que nadie nombra, y una clase invocada desde una cadena de
 * texto no la nombra nadie. Saldria un catalogo que compila, se despliega
 * y devuelve null para todo.
 */
public final class ArenaObjetos {

    private ArenaObjetos() {}

    /** @param clave nombre simple de la clase, tal como lo manda la pagina */
    public static Item crear(String clave) {
        if (clave == null) return null;
        if ("Dagger".equals(clave)) return new com.github.dachhack.sprout.items.weapon.melee.Dagger();
        if ("Knuckles".equals(clave)) return new com.github.dachhack.sprout.items.weapon.melee.Knuckles();
        if ("Quarterstaff".equals(clave)) return new com.github.dachhack.sprout.items.weapon.melee.Quarterstaff();
        if ("Spear".equals(clave)) return new com.github.dachhack.sprout.items.weapon.melee.Spear();
        if ("Mace".equals(clave)) return new com.github.dachhack.sprout.items.weapon.melee.Mace();
        if ("Sword".equals(clave)) return new com.github.dachhack.sprout.items.weapon.melee.Sword();
        if ("Longsword".equals(clave)) return new com.github.dachhack.sprout.items.weapon.melee.Longsword();
        if ("BattleAxe".equals(clave)) return new com.github.dachhack.sprout.items.weapon.melee.BattleAxe();
        if ("WarHammer".equals(clave)) return new com.github.dachhack.sprout.items.weapon.melee.WarHammer();
        if ("Glaive".equals(clave)) return new com.github.dachhack.sprout.items.weapon.melee.Glaive();
        if ("ShortSword".equals(clave)) return new com.github.dachhack.sprout.items.weapon.melee.ShortSword();
        if ("Dart".equals(clave)) return new com.github.dachhack.sprout.items.weapon.missiles.Dart();
        if ("Javelin".equals(clave)) return new com.github.dachhack.sprout.items.weapon.missiles.Javelin();
        if ("IncendiaryDart".equals(clave)) return new com.github.dachhack.sprout.items.weapon.missiles.IncendiaryDart();
        if ("CurareDart".equals(clave)) return new com.github.dachhack.sprout.items.weapon.missiles.CurareDart();
        if ("Shuriken".equals(clave)) return new com.github.dachhack.sprout.items.weapon.missiles.Shuriken();
        if ("Boomerang".equals(clave)) return new com.github.dachhack.sprout.items.weapon.missiles.Boomerang();
        if ("Tamahawk".equals(clave)) return new com.github.dachhack.sprout.items.weapon.missiles.Tamahawk();
        if ("Spork".equals(clave)) return new com.github.dachhack.sprout.items.weapon.melee.Spork();
        if ("ClothArmor".equals(clave)) return new com.github.dachhack.sprout.items.armor.ClothArmor();
        if ("LeatherArmor".equals(clave)) return new com.github.dachhack.sprout.items.armor.LeatherArmor();
        if ("MailArmor".equals(clave)) return new com.github.dachhack.sprout.items.armor.MailArmor();
        if ("ScaleArmor".equals(clave)) return new com.github.dachhack.sprout.items.armor.ScaleArmor();
        if ("PlateArmor".equals(clave)) return new com.github.dachhack.sprout.items.armor.PlateArmor();
        if ("PotionOfHealing".equals(clave)) return new com.github.dachhack.sprout.items.potions.PotionOfHealing();
        if ("PotionOfExperience".equals(clave)) return new com.github.dachhack.sprout.items.potions.PotionOfExperience();
        if ("PotionOfToxicGas".equals(clave)) return new com.github.dachhack.sprout.items.potions.PotionOfToxicGas();
        if ("PotionOfParalyticGas".equals(clave)) return new com.github.dachhack.sprout.items.potions.PotionOfParalyticGas();
        if ("PotionOfLiquidFlame".equals(clave)) return new com.github.dachhack.sprout.items.potions.PotionOfLiquidFlame();
        if ("PotionOfLevitation".equals(clave)) return new com.github.dachhack.sprout.items.potions.PotionOfLevitation();
        if ("PotionOfStrength".equals(clave)) return new com.github.dachhack.sprout.items.potions.PotionOfStrength();
        if ("PotionOfMindVision".equals(clave)) return new com.github.dachhack.sprout.items.potions.PotionOfMindVision();
        if ("PotionOfPurity".equals(clave)) return new com.github.dachhack.sprout.items.potions.PotionOfPurity();
        if ("PotionOfInvisibility".equals(clave)) return new com.github.dachhack.sprout.items.potions.PotionOfInvisibility();
        if ("PotionOfMight".equals(clave)) return new com.github.dachhack.sprout.items.potions.PotionOfMight();
        if ("PotionOfFrost".equals(clave)) return new com.github.dachhack.sprout.items.potions.PotionOfFrost();
        if ("PotionOfMending".equals(clave)) return new com.github.dachhack.sprout.items.potions.PotionOfMending();
        if ("PotionOfOverHealing".equals(clave)) return new com.github.dachhack.sprout.items.potions.PotionOfOverHealing();
        if ("Egg".equals(clave)) return new com.github.dachhack.sprout.items.Egg();
        if ("ScrollOfIdentify".equals(clave)) return new com.github.dachhack.sprout.items.scrolls.ScrollOfIdentify();
        if ("ScrollOfTeleportation".equals(clave)) return new com.github.dachhack.sprout.items.scrolls.ScrollOfTeleportation();
        if ("ScrollOfRemoveCurse".equals(clave)) return new com.github.dachhack.sprout.items.scrolls.ScrollOfRemoveCurse();
        if ("ScrollOfUpgrade".equals(clave)) return new com.github.dachhack.sprout.items.scrolls.ScrollOfUpgrade();
        if ("ScrollOfRecharging".equals(clave)) return new com.github.dachhack.sprout.items.scrolls.ScrollOfRecharging();
        if ("ScrollOfMagicMapping".equals(clave)) return new com.github.dachhack.sprout.items.scrolls.ScrollOfMagicMapping();
        if ("ScrollOfRage".equals(clave)) return new com.github.dachhack.sprout.items.scrolls.ScrollOfRage();
        if ("ScrollOfTerror".equals(clave)) return new com.github.dachhack.sprout.items.scrolls.ScrollOfTerror();
        if ("ScrollOfLullaby".equals(clave)) return new com.github.dachhack.sprout.items.scrolls.ScrollOfLullaby();
        if ("ScrollOfMagicalInfusion".equals(clave)) return new com.github.dachhack.sprout.items.scrolls.ScrollOfMagicalInfusion();
        if ("ScrollOfPsionicBlast".equals(clave)) return new com.github.dachhack.sprout.items.scrolls.ScrollOfPsionicBlast();
        if ("ScrollOfMirrorImage".equals(clave)) return new com.github.dachhack.sprout.items.scrolls.ScrollOfMirrorImage();
        if ("ScrollOfRegrowth".equals(clave)) return new com.github.dachhack.sprout.items.scrolls.ScrollOfRegrowth();
        if ("WandOfTeleportation".equals(clave)) return new com.github.dachhack.sprout.items.wands.WandOfTeleportation();
        if ("WandOfSlowness".equals(clave)) return new com.github.dachhack.sprout.items.wands.WandOfSlowness();
        if ("WandOfFirebolt".equals(clave)) return new com.github.dachhack.sprout.items.wands.WandOfFirebolt();
        if ("WandOfRegrowth".equals(clave)) return new com.github.dachhack.sprout.items.wands.WandOfRegrowth();
        if ("WandOfPoison".equals(clave)) return new com.github.dachhack.sprout.items.wands.WandOfPoison();
        if ("WandOfBlink".equals(clave)) return new com.github.dachhack.sprout.items.wands.WandOfBlink();
        if ("WandOfLightning".equals(clave)) return new com.github.dachhack.sprout.items.wands.WandOfLightning();
        if ("WandOfAmok".equals(clave)) return new com.github.dachhack.sprout.items.wands.WandOfAmok();
        if ("WandOfTelekinesis".equals(clave)) return new com.github.dachhack.sprout.items.wands.WandOfTelekinesis();
        if ("WandOfFlock".equals(clave)) return new com.github.dachhack.sprout.items.wands.WandOfFlock();
        if ("WandOfMagicMissile".equals(clave)) return new com.github.dachhack.sprout.items.wands.WandOfMagicMissile();
        if ("WandOfDisintegration".equals(clave)) return new com.github.dachhack.sprout.items.wands.WandOfDisintegration();
        if ("WandOfAvalanche".equals(clave)) return new com.github.dachhack.sprout.items.wands.WandOfAvalanche();
        if ("RingOfAccuracy".equals(clave)) return new com.github.dachhack.sprout.items.rings.RingOfAccuracy();
        if ("RingOfEvasion".equals(clave)) return new com.github.dachhack.sprout.items.rings.RingOfEvasion();
        if ("RingOfElements".equals(clave)) return new com.github.dachhack.sprout.items.rings.RingOfElements();
        if ("RingOfForce".equals(clave)) return new com.github.dachhack.sprout.items.rings.RingOfForce();
        if ("RingOfFuror".equals(clave)) return new com.github.dachhack.sprout.items.rings.RingOfFuror();
        if ("RingOfHaste".equals(clave)) return new com.github.dachhack.sprout.items.rings.RingOfHaste();
        if ("RingOfMagic".equals(clave)) return new com.github.dachhack.sprout.items.rings.RingOfMagic();
        if ("RingOfMight".equals(clave)) return new com.github.dachhack.sprout.items.rings.RingOfMight();
        if ("RingOfSharpshooting".equals(clave)) return new com.github.dachhack.sprout.items.rings.RingOfSharpshooting();
        if ("RingOfTenacity".equals(clave)) return new com.github.dachhack.sprout.items.rings.RingOfTenacity();
        if ("RingOfWealth".equals(clave)) return new com.github.dachhack.sprout.items.rings.RingOfWealth();
        if ("CapeOfThorns".equals(clave)) return new com.github.dachhack.sprout.items.artifacts.CapeOfThorns();
        if ("ChaliceOfBlood".equals(clave)) return new com.github.dachhack.sprout.items.artifacts.ChaliceOfBlood();
        if ("CloakOfShadows".equals(clave)) return new com.github.dachhack.sprout.items.artifacts.CloakOfShadows();
        if ("HornOfPlenty".equals(clave)) return new com.github.dachhack.sprout.items.artifacts.HornOfPlenty();
        if ("MasterThievesArmband".equals(clave)) return new com.github.dachhack.sprout.items.artifacts.MasterThievesArmband();
        if ("SandalsOfNature".equals(clave)) return new com.github.dachhack.sprout.items.artifacts.SandalsOfNature();
        if ("TalismanOfForesight".equals(clave)) return new com.github.dachhack.sprout.items.artifacts.TalismanOfForesight();
        if ("TimekeepersHourglass".equals(clave)) return new com.github.dachhack.sprout.items.artifacts.TimekeepersHourglass();
        if ("AlchemistsToolkit".equals(clave)) return new com.github.dachhack.sprout.items.artifacts.AlchemistsToolkit();
        if ("RingOfDisintegration".equals(clave)) return new com.github.dachhack.sprout.items.artifacts.RingOfDisintegration();
        if ("RingOfFrost".equals(clave)) return new com.github.dachhack.sprout.items.artifacts.RingOfFrost();
        if ("DriedRose".equals(clave)) return new com.github.dachhack.sprout.items.artifacts.DriedRose();
        if ("Food".equals(clave)) return new com.github.dachhack.sprout.items.food.Food();
        if ("Pasty".equals(clave)) return new com.github.dachhack.sprout.items.food.Pasty();
        if ("MysteryMeat".equals(clave)) return new com.github.dachhack.sprout.items.food.MysteryMeat();
        return null;
    }
}
