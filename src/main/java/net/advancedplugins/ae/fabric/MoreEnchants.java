package net.advancedplugins.ae.fabric;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Qoʻshimcha enchantlar. Ba'zilari taxminiy (aniq mexanika oʻrniga yaqin effekt).
 * Hit: zarba berilganda hujumchi yoki qurbonga effekt.
 * Passive: zirh kiyilganda doimiy effekt.
 * Effekti hali yoʻq enchantlar: ularga keyingi bosqichda effekt yoziladi.
 */
public final class MoreEnchants {

    private MoreEnchants() {
    }

    private record Hit(String name, RegistryEntry<StatusEffect> effect, int duration, float chance, boolean self) {
    }

    private record Passive(String name, RegistryEntry<StatusEffect> effect) {
    }

    private static final List<Hit> HITS = List.of(
            new Hit("twinge", StatusEffects.WEAKNESS, 80, 0.25f, false),
            new Hit("famine", StatusEffects.HUNGER, 200, 0.3f, false),
            new Hit("ravenous", StatusEffects.HUNGER, 200, 0.3f, false),
            new Hit("bleed", StatusEffects.WITHER, 60, 0.2f, false),
            new Hit("iceaspect", StatusEffects.SLOWNESS, 80, 0.3f, false),
            new Hit("strife", StatusEffects.MINING_FATIGUE, 80, 0.25f, false),
            new Hit("diminish", StatusEffects.WEAKNESS, 80, 0.25f, false),
            new Hit("interrupt", StatusEffects.MINING_FATIGUE, 60, 0.25f, false),
            new Hit("distance", StatusEffects.SLOWNESS, 60, 0.2f, false),
            new Hit("critical", StatusEffects.GLOWING, 100, 0.2f, false),
            new Hit("bluntforce", StatusEffects.SLOWNESS, 40, 0.2f, false),
            new Hit("berserk", StatusEffects.STRENGTH, 100, 0.3f, true),
            new Hit("featherweight", StatusEffects.SPEED, 100, 0.3f, true),
            new Hit("confuse", StatusEffects.NAUSEA, 80, 0.25f, false),
            new Hit("scare", StatusEffects.SLOWNESS, 60, 0.2f, false),
            new Hit("shatter", StatusEffects.WEAKNESS, 60, 0.25f, false),
            new Hit("disintegrate", StatusEffects.WITHER, 60, 0.2f, false),
            new Hit("disarm", StatusEffects.MINING_FATIGUE, 80, 0.25f, false),
            new Hit("disarmor", StatusEffects.MINING_FATIGUE, 80, 0.25f, false),
            new Hit("permafrost", StatusEffects.SLOWNESS, 80, 0.25f, false),
            new Hit("nulify", StatusEffects.WEAKNESS, 60, 0.2f, false),
            new Hit("barbarian", StatusEffects.STRENGTH, 100, 0.2f, true),
            new Hit("blacksmith", StatusEffects.HASTE, 100, 0.2f, true)
    );

    private static final List<Passive> PASSIVES = List.of(
            new Passive("forcefield", StatusEffects.RESISTANCE),
            new Passive("molten", StatusEffects.FIRE_RESISTANCE),
            new Passive("extinguish", StatusEffects.FIRE_RESISTANCE),
            new Passive("hardened", StatusEffects.RESISTANCE),
            new Passive("guardians", StatusEffects.RESISTANCE),
            new Passive("tank", StatusEffects.RESISTANCE),
            new Passive("immortal", StatusEffects.REGENERATION),
            new Passive("swordsman", StatusEffects.STRENGTH),
            new Passive("aegis", StatusEffects.SLOW_FALLING),
            new Passive("armored", StatusEffects.RESISTANCE),
            new Passive("obsidianshield", StatusEffects.RESISTANCE),
            new Passive("angelic", StatusEffects.ABSORPTION),
            new Passive("patch", StatusEffects.REGENERATION),
            new Passive("phoenix", StatusEffects.FIRE_RESISTANCE),
            new Passive("ward", StatusEffects.RESISTANCE),
            new Passive("reinforced", StatusEffects.RESISTANCE),
            new Passive("safeguard", StatusEffects.RESISTANCE),
            new Passive("lucid", StatusEffects.RESISTANCE),
            new Passive("undeadruse", StatusEffects.RESISTANCE),
            new Passive("chunky", StatusEffects.RESISTANCE),
            new Passive("block", StatusEffects.RESISTANCE),
            new Passive("nightowl", StatusEffects.NIGHT_VISION),
            new Passive("trickster", StatusEffects.SPEED),
            new Passive("dodge", StatusEffects.SPEED),
            new Passive("rocketescape", StatusEffects.JUMP_BOOST),
            new Passive("gears", StatusEffects.SPEED),
            new Passive("plummet", StatusEffects.SLOW_FALLING)
    );

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static boolean extraHit = false;

    private static int levelOf(LivingEntity holder, String name, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return holder.getWorld().getRegistryManager()
                .get(RegistryKeys.ENCHANTMENT)
                .getEntry(Identifier.of(AEFabric.MOD_ID, name))
                .map(entry -> EnchantmentHelper.getLevel(entry, stack))
                .orElse(0);
    }

    private static int armorLevel(LivingEntity holder, String name) {
        int best = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            best = Math.max(best, levelOf(holder, name, holder.getEquippedStack(slot)));
        }
        return best;
    }

    private static boolean proc(LivingEntity entity, float chance) {
        return entity.getRandom().nextFloat() < chance;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) {
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                for (Passive passive : PASSIVES) {
                    int level = armorLevel(player, passive.name());
                    if (level > 0) {
                        player.addStatusEffect(new StatusEffectInstance(
                                passive.effect(), 60, level - 1, true, false));
                    }
                }
            }
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, damageTaken, blocked) ->
                onHit(victim, source, damageTaken));
    }

    private static void onHit(LivingEntity victim, DamageSource source, float damageTaken) {
        if (!(source.getAttacker() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }

        // Qurbon zirhidagi enchantlar hujumchiga taʼsir qiladi.
        if (armorLevel(victim, "wither") > 0 && proc(victim, 0.2f)) {
            attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 0, false, true));
        }
        if (armorLevel(victim, "frozen") > 0 && proc(victim, 0.2f)) {
            attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 0, false, true));
        }
        if (armorLevel(victim, "curse") > 0 && proc(victim, 0.2f)) {
            attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0, false, true));
        }

        ItemStack weapon = attacker.getMainHandStack();
        if (weapon.isEmpty()) {
            return;
        }

        for (Hit hit : HITS) {
            int level = levelOf(attacker, hit.name(), weapon);
            if (level > 0 && proc(attacker, hit.chance())) {
                LivingEntity target = hit.self() ? attacker : victim;
                target.addStatusEffect(new StatusEffectInstance(hit.effect(), hit.duration(), level - 1, false, true));
            }
        }

        int vampire = levelOf(attacker, "vampire", weapon);
        if (vampire > 0) {
            attacker.heal(damageTaken * 0.15f * vampire);
        }

        int spark = levelOf(attacker, "spark", weapon);
        if (spark > 0 && proc(attacker, 0.2f)) {
            victim.setOnFireFor(3 * spark);
        }

        int doubleStrike = levelOf(attacker, "doublestrike", weapon);
        if (doubleStrike > 0 && !extraHit) {
            extraHit = true;
            try {
                victim.damage(source, damageTaken * 0.5f * doubleStrike);
            } finally {
                extraHit = false;
            }
        }
    }
}
