package net.advancedplugins.ae.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AdvancedEnchantments effektlarini ae_enchants.json dan oʻqib ishlatadi.
 */
public final class AEEngine {

    private AEEngine() {
    }

    private record Lvl(int chance, List<String> effects) {
    }

    private record Ench(String name, String type, Map<Integer, Lvl> levels) {
    }

    private static final List<Ench> ENCHANTS = new ArrayList<>();
    private static final Random RNG = new Random();
    private static final Pattern RANDOM_NUMBER = Pattern.compile("<random number>(-?\\d+)-(-?\\d+)</random number>");
    private static final Pattern CHANCE_TAG = Pattern.compile("<chance>\\s*\\d+\\s*</chance>");
    private static boolean busy = false;
    private static float currentDamage = 0f;

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final EquipmentSlot[] HANDS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private static final Map<String, String> POTION_ALIAS = Map.of(
            "FAST_DIGGING", "haste",
            "SLOW_DIGGING", "mining_fatigue",
            "INCREASE_DAMAGE", "strength",
            "HEAL", "instant_health",
            "HARM", "instant_damage",
            "JUMP", "jump_boost",
            "CONFUSION", "nausea",
            "DAMAGE_RESISTANCE", "resistance",
            "SLOW", "slowness"
    );

    private static int safeInt(JsonElement el, int fallback) {
        try {
            return el.getAsInt();
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static void load() {
        try (InputStream in = AEEngine.class.getResourceAsStream("/ae_enchants.json")) {
            if (in == null) {
                AEFabric.LOGGER.error("ae_enchants.json topilmadi");
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                JsonObject obj = entry.getValue().getAsJsonObject();
                String type = obj.has("type") ? obj.get("type").getAsString() : "";
                Map<Integer, Lvl> levels = new HashMap<>();
                if (obj.has("levels") && obj.get("levels").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> l : obj.getAsJsonObject("levels").entrySet()) {
                        if (!l.getValue().isJsonObject()) {
                            continue;
                        }
                        JsonObject body = l.getValue().getAsJsonObject();
                        int chance = body.has("chance") ? safeInt(body.get("chance"), 100) : 100;
                        List<String> effects = new ArrayList<>();
                        if (body.has("effects") && body.get("effects").isJsonArray()) {
                            JsonArray arr = body.getAsJsonArray("effects");
                            for (JsonElement el : arr) {
                                if (el.isJsonPrimitive()) {
                                    effects.add(el.getAsString());
                                }
                            }
                        }
                        levels.put(Integer.parseInt(l.getKey()), new Lvl(chance, effects));
                    }
                }
                ENCHANTS.add(new Ench(entry.getKey(), type, levels));
            }
            AEFabric.LOGGER.info("AEEngine: {} ta enchant yuklandi", ENCHANTS.size());
        } catch (Exception ex) {
            AEFabric.LOGGER.error("ae_enchants.json o'qilmadi", ex);
        }
    }

    private static int levelOf(LivingEntity holder, String name, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return holder.getWorld().getRegistryManager()
                .get(RegistryKeys.ENCHANTMENT)
                .getEntry(Identifier.of(AEFabric.MOD_ID, name))
                .map(entry -> Math.min(5, EnchantmentHelper.getLevel(entry, stack)))
                .orElse(0);
    }

    private static boolean hasType(Ench ench, String keyword) {
        return ench.type().contains(keyword);
    }

    private static void fire(Ench ench, int level, LivingEntity holder, LivingEntity other, boolean holderIsAttacker) {
        Lvl def = ench.levels().get(level);
        if (def == null) {
            return;
        }
        if (holder.getRandom().nextInt(100) >= def.chance()) {
            return;
        }
        for (String raw : def.effects()) {
            apply(raw, holder, other, holderIsAttacker);
        }
    }

    private static String randomized(String raw) {
        Matcher m = RANDOM_NUMBER.matcher(raw);
        return m.replaceAll(match -> {
            int a = Integer.parseInt(match.group(1));
            int b = Integer.parseInt(match.group(2));
            return String.valueOf(b <= a ? a : a + RNG.nextInt(b - a + 1));
        });
    }

    private static void apply(String raw, LivingEntity holder, LivingEntity other, boolean holderIsAttacker) {
        String s = CHANCE_TAG.matcher(randomized(raw)).replaceAll("").trim();
        String[] tokens = s.split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) {
            return;
        }

        LivingEntity target = holder;
        if (s.contains("@Victim") || s.contains("%victim%")) {
            target = holderIsAttacker ? other : holder;
        } else if (s.contains("@Attacker") || s.contains("%attacker%")) {
            target = holderIsAttacker ? holder : other;
        }
        if (target == null) {
            return;
        }

        LivingEntity hitTarget = holderIsAttacker ? other : holder;
        LivingEntity defender = holderIsAttacker ? other : holder;

        String[] parts = tokens[0].split(":");
        switch (parts[0]) {
            case "POTION" -> {
                if (parts.length < 4) {
                    return;
                }
                RegistryEntry<StatusEffect> effect = effectFor(parts[1]);
                if (effect == null) {
                    return;
                }
                int amp = parseIntSafe(parts[2], 0);
                int dur = parseIntSafe(parts[3], 100);
                target.addStatusEffect(Fx.inst(effect, dur, amp, false, true));
                feedback(target, effect.value().isBeneficial());
            }
            case "ADD_HEALTH" -> {
                float amount = parts.length > 1 ? parseFloatSafe(parts[1], 1f) : 1f;
                target.heal(amount);
                feedback(target, true);
            }
            case "DO_HARM", "LIGHTNING" -> {
                float amount = parts[0].equals("LIGHTNING") ? 5f
                        : (parts.length > 1 ? parseFloatSafe(parts[1], 1f) : 1f);
                damageSafe(target, target.getDamageSources().magic(), amount);
                feedback(target, false);
            }
            case "BURN" -> target.setOnFireFor(parts.length > 1 ? parseIntSafe(parts[1], 2) : 2);
            case "EXTINGUISH" -> target.setFireTicks(0);
            case "PULL_AWAY" -> {
                double strength = parts.length > 1 ? parseFloatSafe(parts[1], 1f) * 0.4 : 0.4;
                target.takeKnockback(strength, holder.getX() - target.getX(), holder.getZ() - target.getZ());
            }
            case "PULL_CLOSER" -> {
                double strength = parts.length > 1 ? parseFloatSafe(parts[1], 1f) * 0.4 : 0.4;
                target.takeKnockback(strength, target.getX() - holder.getX(), target.getZ() - holder.getZ());
            }
            case "DECREASE_DAMAGE", "NEGATE_DAMAGE" -> {
                float pct = parts.length > 1 ? parseFloatSafe(parts[1], 15f) : 15f;
                defender.heal(currentDamage * pct / 100f);
            }
            case "HALF_DAMAGE" -> defender.heal(currentDamage * 0.5f);
            case "DOUBLE_DAMAGE" -> damageSafe(hitTarget, hitTarget.getDamageSources().magic(), currentDamage);
            case "INCREASE_DAMAGE" -> {
                float pct = parts.length > 1 ? parseFloatSafe(parts[1], 10f) : 10f;
                damageSafe(hitTarget, hitTarget.getDamageSources().magic(), currentDamage * pct / 100f);
            }
            case "CURE" -> {
                // Oʻyinchidagi barcha zararli effektlarni olib tashlaydi.
                for (StatusEffectInstance inst : new ArrayList<>(target.getStatusEffects())) {
                    if (!inst.getEffectType().value().isBeneficial()) {
                        target.removeStatusEffect(inst.getEffectType());
                    }
                }
                feedback(target, true);
            }
            case "EXP" -> {
                if (target instanceof PlayerEntity player) {
                    player.addExperience(parts.length > 1 ? parseIntSafe(parts[1], 1) : 1);
                }
            }
            case "ADD_FOOD" -> {
                if (target instanceof PlayerEntity player) {
                    player.getHungerManager().add(parts.length > 1 ? parseIntSafe(parts[1], 1) : 1, 0f);
                }
            }
            case "TELEPORT_BEHIND" -> {
                // Enchant egasi (holder) qurbon (other) orqasiga oʻtadi.
                if (other == null) {
                    return;
                }
                Vec3d back = other.getRotationVec(1f).multiply(-2.0);
                holder.requestTeleport(other.getX() + back.x, other.getY(), other.getZ() + back.z);
                feedback(holder, true);
            }
            case "DISARM" -> {
                ItemStack hand = target.getEquippedStack(EquipmentSlot.MAINHAND);
                if (!hand.isEmpty()) {
                    target.dropStack(hand.copy());
                    target.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                }
            }
            case "REMOVE_RANDOM_ARMOR" -> {
                EquipmentSlot slot = ARMOR[RNG.nextInt(ARMOR.length)];
                ItemStack piece = target.getEquippedStack(slot);
                if (!piece.isEmpty()) {
                    target.dropStack(piece.copy());
                    target.equipStack(slot, ItemStack.EMPTY);
                }
            }
            case "STEAL_HEALTH" -> {
                float amount = parts.length > 1 ? parseFloatSafe(parts[1], 1f) : 1f;
                damageSafe(hitTarget, hitTarget.getDamageSources().magic(), amount);
                holder.heal(amount);
            }
            default -> {
                // Hali qoʻllab-quvvatlanmaydi.
            }
        }
    }

    private static int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static float parseFloatSafe(String s, float fallback) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static RegistryEntry<StatusEffect> effectFor(String bukkitName) {
        String key = POTION_ALIAS.getOrDefault(bukkitName, bukkitName.toLowerCase());
        return Registries.STATUS_EFFECT.getEntry(Identifier.of("minecraft", key)).orElse(null);
    }

    private static void damageSafe(LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0f) {
            return;
        }
        target.damage(source, amount);
    }

    private static void feedback(LivingEntity target, boolean good) {
        if (target.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                    good ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.SMOKE,
                    target.getX(), target.getBodyY(0.5), target.getZ(),
                    12, 0.3, 0.5, 0.3, 0.02);
        }
    }

    private static void onHit(LivingEntity victim, DamageSource source, float damageTaken) {
        if (busy) {
            return;
        }
        busy = true;
        currentDamage = damageTaken;
        try {
            LivingEntity attacker = source.getAttacker() instanceof LivingEntity la && la != victim ? la : null;

            if (attacker != null) {
                ItemStack weapon = attacker.getMainHandStack();
                for (Ench ench : ENCHANTS) {
                    if (hasType(ench, "ATTACK")) {
                        int lvl = levelOf(attacker, ench.name(), weapon);
                        if (lvl > 0) {
                            fire(ench, lvl, attacker, victim, true);
                        }
                    }
                }
            }

            boolean fall = source.isOf(DamageTypes.FALL);
            for (EquipmentSlot slot : ARMOR) {
                ItemStack piece = victim.getEquippedStack(slot);
                if (piece.isEmpty()) {
                    continue;
                }
                for (Ench ench : ENCHANTS) {
                    if (hasType(ench, "DEFENSE") || (fall && hasType(ench, "FALL_DAMAGE"))) {
                        int lvl = levelOf(victim, ench.name(), piece);
                        if (lvl > 0) {
                            fire(ench, lvl, victim, attacker != null ? attacker : victim, false);
                        }
                    }
                }
            }

            if (source.getSource() instanceof ProjectileEntity projectile
                    && projectile.getOwner() instanceof LivingEntity shooter && shooter != victim) {
                ItemStack bow = shooter.getMainHandStack();
                for (Ench ench : ENCHANTS) {
                    if (hasType(ench, "SHOOT")) {
                        int lvl = levelOf(shooter, ench.name(), bow);
                        if (lvl > 0) {
                            fire(ench, lvl, shooter, victim, true);
                        }
                    }
                }
            }
        } finally {
            busy = false;
        }
    }

    private static boolean cancelsDamage(LivingEntity victim) {
        for (EquipmentSlot slot : ARMOR) {
            ItemStack piece = victim.getEquippedStack(slot);
            if (piece.isEmpty()) {
                continue;
            }
            for (Ench ench : ENCHANTS) {
                if (!hasType(ench, "DEFENSE")) {
                    continue;
                }
                int lvl = levelOf(victim, ench.name(), piece);
                if (lvl <= 0) {
                    continue;
                }
                Lvl def = ench.levels().get(lvl);
                if (def == null) {
                    continue;
                }
                boolean cancels = def.effects().stream().anyMatch(e -> e.startsWith("CANCEL_EVENT"));
                if (cancels && victim.getRandom().nextInt(100) < def.chance()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void onKill(LivingEntity victim, DamageSource source) {
        if (busy || !(source.getAttacker() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }
        busy = true;
        try {
            ItemStack weapon = attacker.getMainHandStack();
            for (Ench ench : ENCHANTS) {
                if (hasType(ench, "KILL")) {
                    int lvl = levelOf(attacker, ench.name(), weapon);
                    if (lvl > 0) {
                        fire(ench, lvl, attacker, victim, true);
                    }
                }
            }
        } finally {
            busy = false;
        }
    }

    /** DEATH: oʻlgan oʻyinchining zirhidagi enchantlar ishlaydi. */
    private static void onDeath(LivingEntity victim) {
        if (busy) {
            return;
        }
        busy = true;
        try {
            for (EquipmentSlot slot : ARMOR) {
                ItemStack piece = victim.getEquippedStack(slot);
                if (piece.isEmpty()) {
                    continue;
                }
                for (Ench ench : ENCHANTS) {
                    if (hasType(ench, "DEATH")) {
                        int lvl = levelOf(victim, ench.name(), piece);
                        if (lvl > 0) {
                            fire(ench, lvl, victim, victim, false);
                        }
                    }
                }
            }
        } finally {
            busy = false;
        }
    }

    private static void onTick(ServerPlayerEntity player) {
        if (busy) {
            return;
        }
        busy = true;
        try {
            List<ItemStack> held = new ArrayList<>();
            for (EquipmentSlot slot : ARMOR) {
                held.add(player.getEquippedStack(slot));
            }
            for (EquipmentSlot slot : HANDS) {
                held.add(player.getEquippedStack(slot));
            }
            for (ItemStack stack : held) {
                if (stack.isEmpty()) {
                    continue;
                }
                for (Ench ench : ENCHANTS) {
                    if (hasType(ench, "EFFECT_STATIC")) {
                        int lvl = levelOf(player, ench.name(), stack);
                        if (lvl > 0) {
                            fire(ench, lvl, player, player, true);
                        }
                    }
                }
            }
        } finally {
            busy = false;
        }
    }

    public static void register() {
        load();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> !cancelsDamage(victim));
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, damageTaken, blocked) ->
                onHit(victim, source, damageTaken));
        ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
            onKill(victim, source);
            onDeath(victim);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 10 != 0) {
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                onTick(player);
            }
        });
    }
}
