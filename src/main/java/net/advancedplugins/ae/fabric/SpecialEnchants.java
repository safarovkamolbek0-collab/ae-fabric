package net.advancedplugins.ae.fabric;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * 25 ta qoʻshimcha enchant effekti: oʻq/trident, zarba, oʻldirish, blok sindirish.
 * Ba'zilari taxminiy (asl plagin mexanikasi oʻrniga yaqin effekt).
 */
public final class SpecialEnchants {

    private SpecialEnchants() {
    }

    private static boolean bonusHit = false;

    /** Oʻq yoki trident bilan urganda qoʻshimcha zarar beradiganlar (yoʻnalishi: otgan kishining qurolidagi enchant). */
    private static final List<String> PROJECTILE_BONUS = List.of(
            "frenzy", "piercing", "impact", "missile", "soulless", "neutralize");

    /** Oʻq bilan urganda effekt beradiganlar. */
    private static final String[] PROJECTILE_DEATH_HINT = {};

    private static int levelOf(LivingEntity holder, String name, net.minecraft.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return holder.getWorld().getRegistryManager()
                .get(RegistryKeys.ENCHANTMENT)
                .getEntry(Identifier.of(AEFabric.MOD_ID, name))
                .map(entry -> EnchantmentHelper.getLevel(entry, stack))
                .orElse(0);
    }

    private static void spawnXp(World world, double x, double y, double z, int amount) {
        if (world instanceof ServerWorld serverWorld && amount > 0) {
            serverWorld.spawnEntity(new ExperienceOrbEntity(serverWorld, x, y, z, amount));
        }
    }

    public static void register() {
        // Blok sindirilganda: multiplication, soulminer, lucky -> qoʻshimcha tajriba.
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            ItemStack tool = serverPlayer.getMainHandStack();
            int xp = 0;
            xp += 2 * levelOf(serverPlayer, "multiplication", tool);
            xp += 2 * levelOf(serverPlayer, "soulminer", tool);
            xp += 2 * levelOf(serverPlayer, "lucky", tool);
            spawnXp(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, xp);
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, damageTaken, blocked) -> {
            if (bonusHit) {
                return;
            }
            onProjectileHit(victim, source, damageTaken);
            onMeleeHit(victim, source);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> onKill(victim, source));
    }

    private static void onProjectileHit(LivingEntity victim, DamageSource source, float damageTaken) {
        if (!(source.getSource() instanceof ProjectileEntity projectile)
                || !(projectile.getOwner() instanceof LivingEntity shooter)
                || shooter == victim) {
            return;
        }
        ItemStack weapon = shooter.getMainHandStack();

        int bonus = 0;
        for (String name : PROJECTILE_BONUS) {
            bonus += levelOf(shooter, name, weapon);
        }
        if (bonus > 0) {
            bonusHit = true;
            try {
                victim.damage(source, damageTaken * 0.25f * bonus);
            } finally {
                bonusHit = false;
            }
        }

        int poseidon = levelOf(shooter, "poseidon", weapon);
        if (poseidon > 0) {
            victim.takeKnockback(0.5 * poseidon,
                    victim.getX() - shooter.getX(), victim.getZ() - shooter.getZ());
        }

        int virus = levelOf(shooter, "virus", weapon);
        if (virus > 0) {
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, virus - 1, false, true));
        }
        int snare = levelOf(shooter, "snare", weapon);
        if (snare > 0) {
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, snare - 1, false, true));
        }
        int perish = levelOf(shooter, "perish", weapon);
        if (perish > 0) {
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, perish - 1, false, true));
        }
        int chaos = levelOf(shooter, "chaos", weapon);
        if (chaos > 0 && shooter.getRandom().nextFloat() < 0.3f) {
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0, false, true));
        }
    }

    private static void onMeleeHit(LivingEntity victim, DamageSource source) {
        if (!(source.getAttacker() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }
        ItemStack weapon = attacker.getMainHandStack();
        if (weapon.isEmpty()) {
            return;
        }

        // Suspend: qurbon qisqa vaqtga havoda osiladi.
        int suspend = levelOf(attacker, "suspend", weapon);
        if (suspend > 0 && attacker.getRandom().nextFloat() < 0.15f * suspend) {
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 40, 0, false, true));
        }

        // Nightwalker: hujumchi tungi koʻrishni oladi.
        if (levelOf(attacker, "nightwalker", weapon) > 0) {
            attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 200, 0, false, true));
        }
    }

    private static void onKill(LivingEntity victim, DamageSource source) {
        if (!(source.getAttacker() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }
        ItemStack weapon = attacker.getMainHandStack();
        if (weapon.isEmpty()) {
            return;
        }
        World world = victim.getWorld();
        double x = victim.getX();
        double y = victim.getY() + 0.5;
        double z = victim.getZ();

        // Decapitation: boshni tashlaydi (faqat zombi, skelet, creeper va oʻyinchi).
        int decap = levelOf(attacker, "decapitation", weapon);
        if (decap > 0 && attacker.getRandom().nextFloat() < 0.1f * decap) {
            Item head = null;
            if (victim.getType() == EntityType.ZOMBIE) {
                head = Items.ZOMBIE_HEAD;
            } else if (victim.getType() == EntityType.SKELETON) {
                head = Items.SKELETON_SKULL;
            } else if (victim.getType() == EntityType.CREEPER) {
                head = Items.CREEPER_HEAD;
            } else if (victim instanceof ServerPlayerEntity) {
                head = Items.PLAYER_HEAD;
            }
            if (head != null) {
                victim.dropStack(new ItemStack(head));
            }
        }

        // Devour: hujumchi oʻldirganda sogʻayadi. Rebound: kichik sogʻayish.
        int devour = levelOf(attacker, "devour", weapon);
        if (devour > 0) {
            attacker.heal(2.0f * devour);
        }
        int rebound = levelOf(attacker, "rebound", weapon);
        if (rebound > 0) {
            attacker.heal(1.0f * rebound);
        }

        // Tajriba: reaper, soulgrind, inquisitive, diploid, spiritmaster, axeofspirits.
        int xp = 0;
        xp += 3 * levelOf(attacker, "reaper", weapon);
        xp += 3 * levelOf(attacker, "soulgrind", weapon);
        xp += 3 * levelOf(attacker, "inquisitive", weapon);
        xp += 3 * levelOf(attacker, "diploid", weapon);
        xp += 3 * levelOf(attacker, "spiritmaster", weapon);
        xp += 3 * levelOf(attacker, "axeofspirits", weapon);
        spawnXp(world, x, y, z, xp);
    }
}
