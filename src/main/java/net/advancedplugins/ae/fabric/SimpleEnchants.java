package net.advancedplugins.ae.fabric;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/**
 * Oddiy enchantlarning effektlari.
 * Hozircha ishlamaydigan (keyingi bosqichga qoldirilgan): wings, smelting, veinminer,
 * timber, replanter, telepathy, impact, armored, obsidianshield.
 */
public final class SimpleEnchants {

    private SimpleEnchants() {
    }

    /** Berilgan jonli mavjudotda (player/mob) stackdagi enchant darajasi (yoʻq boʻlsa 0). */
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

    private static boolean proc(LivingEntity entity, float chance) {
        return entity.getRandom().nextFloat() < chance;
    }

    public static void register() {
        // Har 20 tickda (1 soniya) passiv effektlarni yangilaymiz.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) {
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                applyPassives(player);
            }
        });

        // Experience: blok sindirilganda qoʻshimcha tajriba.
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            int level = levelOf(serverPlayer, "experience", serverPlayer.getMainHandStack());
            if (level > 0) {
                int amount = (world.getRandom().nextInt(4) + 2) * level;
                serverWorld.spawnEntity(new ExperienceOrbEntity(
                        serverWorld, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, amount));
            }
        });

        // Zarba olganda: lifesteal, poison, paralyze, blind, strike, reflect.
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, damageTaken, blocked) ->
                onHit(victim, source, damageTaken));

        // Jelly Legs: botinkali oyinchi yiqilganda zarar olmaydi.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) ->
                !(source.isOf(DamageTypes.FALL)
                        && levelOf(victim, "jellylegs", victim.getEquippedStack(EquipmentSlot.FEET)) > 0));
    }

    private static void applyPassives(ServerPlayerEntity player) {
        ItemStack hand = player.getMainHandStack();
        ItemStack head = player.getEquippedStack(EquipmentSlot.HEAD);
        ItemStack feet = player.getEquippedStack(EquipmentSlot.FEET);

        // Haste / Hasten: asbob qoʻlda boʻlsa tezroq qazish.
        int haste = Math.max(levelOf(player, "haste", hand), levelOf(player, "hasten", hand));
        if (haste > 0) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 60, haste - 1, true, false));
        }

        // Rebreather: asbob bilan suv ostida nafas.
        if (levelOf(player, "rebreather", hand) > 0) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 60, 0, true, false));
        }

        // Aquatic (bosh): suv ostida nafas.
        if (levelOf(player, "aquatic", head) > 0) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 60, 0, true, false));
        }

        // Glowing (bosh): tungi koʻrish.
        if (levelOf(player, "glowing", head) > 0) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, true, false));
        }

        // Springs (botinka): sakrash kuchayadi.
        int springs = levelOf(player, "springs", feet);
        if (springs > 0) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 60, springs - 1, true, false));
        }

        // Lava Walker (botinka): olov zarari yoʻq (taxminiy: olovga chidamlilik).
        if (levelOf(player, "lavawalker", feet) > 0) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 60, 0, true, false));
        }

        // Water Walker (botinka): suvda tezroq yurish (taxminiy: dolphin's grace).
        if (levelOf(player, "waterwalker", feet) > 0) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 60, 0, true, false));
        }
    }

    private static void onHit(LivingEntity victim, DamageSource source, float damageTaken) {
        // Reflect: oyinchi zarba olganda hujumchiga zararning bir qismini qaytaradi.
        if (victim instanceof ServerPlayerEntity
                && source.getAttacker() instanceof LivingEntity reflectTarget
                && reflectTarget != victim) {
            int reflect = levelOf(victim, "reflect", victim.getEquippedStack(EquipmentSlot.CHEST));
            if (reflect > 0 && reflectTarget.getWorld() instanceof ServerWorld serverWorld) {
                reflectTarget.damage(victim.getDamageSources().magic(), damageTaken * 0.2f * reflect);
            }
        }

        // Hujumchi tomonidagi enchantlar.
        if (!(source.getAttacker() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }
        ItemStack weapon = attacker.getMainHandStack();
        if (weapon.isEmpty()) {
            return;
        }

        int lifesteal = levelOf(attacker, "lifesteal", weapon);
        if (lifesteal > 0) {
            attacker.heal(damageTaken * 0.2f * lifesteal);
        }

        int poison = levelOf(attacker, "poison", weapon);
        if (poison > 0 && proc(attacker, 0.25f)) {
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, poison - 1, false, true));
        }

        int paralyze = levelOf(attacker, "paralyze", weapon);
        if (paralyze > 0 && proc(attacker, 0.25f)) {
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, paralyze - 1, false, true));
        }

        int blind = levelOf(attacker, "blind", weapon);
        if (blind > 0 && proc(attacker, 0.25f)) {
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0, false, true));
        }

        int strike = levelOf(attacker, "strike", weapon);
        if (strike > 0 && proc(attacker, 0.1f * strike) && victim.getWorld() instanceof ServerWorld serverWorld) {
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(serverWorld);
            if (bolt != null) {
                bolt.setPosition(victim.getX(), victim.getY(), victim.getZ());
                serverWorld.spawnEntity(bolt);
            }
        }
    }
}
