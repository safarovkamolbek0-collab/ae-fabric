package net.advancedplugins.ae.fabric;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.List;

/**
 * Explosive, cleave, killaura, ragdoll, shockwave, magnet, slingshot, rush effektlari.
 * Ba'zilari taxminiy (aniq mexanika oʻrniga yaqin effekt).
 */
public final class CombatExtras {

    private CombatExtras() {
    }

    private static boolean bonusHit = false;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

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

    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, damageTaken, blocked) -> {
            if (bonusHit) {
                return;
            }
            onMeleeHit(victim, source, damageTaken);
            onProjectileHit(victim, source);
        });

        // Rush: elitra bilan uchganda tezlik.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 10 != 0) {
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (!player.isFallFlying()) {
                    continue;
                }
                int rush = levelOf(player, "rush", player.getEquippedStack(EquipmentSlot.CHEST));
                if (rush > 0) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, rush - 1, true, false));
                }
            }
        });
    }

    private static void onMeleeHit(LivingEntity victim, DamageSource source, float damageTaken) {
        if (!(source.getAttacker() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }
        ItemStack weapon = attacker.getMainHandStack();
        if (weapon.isEmpty()) {
            return;
        }

        // Magnet: qurbon hujumchiga tortiladi.
        int magnet = levelOf(attacker, "magnet", weapon);
        if (magnet > 0) {
            double dx = attacker.getX() - victim.getX();
            double dz = attacker.getZ() - victim.getZ();
            victim.addVelocity(dx * 0.2 * magnet, 0.1, dz * 0.2 * magnet);
        }

        // Cleave / Killaura: atrofdagi dushmanlarga ham zarar.
        int sweep = levelOf(attacker, "cleave", weapon) + levelOf(attacker, "killaura", weapon);
        if (sweep > 0) {
            List<LivingEntity> nearby = victim.getWorld().getEntitiesByClass(
                    LivingEntity.class,
                    victim.getBoundingBox().expand(2.5),
                    e -> e != attacker && e != victim);
            for (LivingEntity other : nearby) {
                bonusHit = true;
                try {
                    other.damage(source, damageTaken * 0.5f * sweep);
                } finally {
                    bonusHit = false;
                }
            }
        }

        // Ragdoll / Shockwave: qurbon zirhi hujumchini orqaga itaradi.
        int push = armorLevel(victim, "ragdoll") + armorLevel(victim, "shockwave");
        if (push > 0) {
            attacker.takeKnockback(0.4 * push,
                    victim.getX() - attacker.getX(), victim.getZ() - attacker.getZ());
        }
    }

    private static void onProjectileHit(LivingEntity victim, DamageSource source) {
        if (!(source.getSource() instanceof ProjectileEntity projectile)
                || !(projectile.getOwner() instanceof LivingEntity shooter)
                || shooter == victim) {
            return;
        }
        ItemStack bow = shooter.getMainHandStack();

        // Explosive: o'q urilgan joyda kichik portlash.
        int explosive = levelOf(shooter, "explosive", bow);
        if (explosive > 0) {
            victim.getWorld().createExplosion(shooter,
                    victim.getX(), victim.getY(), victim.getZ(),
                    1.0f * explosive, World.ExplosionSourceType.NONE);
        }

        // Slingshot: o'q urilgan qurbon orqaga uchadi.
        int slingshot = levelOf(shooter, "slingshot", bow);
        if (slingshot > 0) {
            victim.takeKnockback(0.5 * slingshot,
                    shooter.getX() - victim.getX(), shooter.getZ() - victim.getZ());
        }
    }
}
