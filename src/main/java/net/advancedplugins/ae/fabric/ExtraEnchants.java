package net.advancedplugins.ae.fabric;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Kazish, uchish, o'q va mob turiga qarshi enchantlarning effektlari.
 */
public final class ExtraEnchants {

    private ExtraEnchants() {
    }

    private static boolean breaking = false;
    private static boolean bonusHit = false;
    private static final Set<UUID> winged = new HashSet<>();

    private static final List<String> BOW_ENCHANTS = List.of(
            "archer", "longbow", "marksman", "sniper", "deadshot", "striker", "hunter", "bowmaster");

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

    public static void register() {
        // Veinminer / Timber: bir xil blokni guruh boʻlib sindirish.
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (breaking || !(player instanceof ServerPlayerEntity serverPlayer)
                    || !(world instanceof ServerWorld serverWorld)) {
                return;
            }
            ItemStack tool = serverPlayer.getMainHandStack();
            int vein = levelOf(serverPlayer, "veinminer", tool);
            int timber = levelOf(serverPlayer, "timber", tool);
            if (vein > 0 && state.isIn(BlockTags.PICKAXE_MINEABLE)) {
                breakConnected(serverWorld, serverPlayer, pos, state.getBlock(), 16 * vein, false);
            } else if (timber > 0 && state.isIn(BlockTags.LOGS)) {
                breakConnected(serverWorld, serverPlayer, pos, state.getBlock(), 64, true);
            }
        });

        // Wings: botinkada uchish (ijodiy rejimda emas).
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 10 != 0) {
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.isCreative() || player.isSpectator()) {
                    continue;
                }
                boolean has = levelOf(player, "wings", player.getEquippedStack(EquipmentSlot.FEET)) > 0;
                if (has && !player.getAbilities().allowFlying) {
                    player.getAbilities().allowFlying = true;
                    player.sendAbilitiesUpdate();
                    winged.add(player.getUuid());
                } else if (!has && winged.remove(player.getUuid())) {
                    player.getAbilities().allowFlying = false;
                    player.getAbilities().flying = false;
                    player.sendAbilitiesUpdate();
                }
            }
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, damageTaken, blocked) -> {
            if (bonusHit) {
                return;
            }
            
            
        });
    }

    /** Blok guruhini BFS bilan topib sindiradi. limit - maksimal blok soni. */
    private static void breakConnected(ServerWorld world, ServerPlayerEntity player, BlockPos start,
                                       Block target, int limit, boolean diagonal) {
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && found.size() < limit) {
            BlockPos current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        if (!diagonal && Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) {
                            continue;
                        }
                        BlockPos next = current.add(dx, dy, dz);
                        if (seen.add(next) && world.getBlockState(next).isOf(target)) {
                            found.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }
        breaking = true;
        try {
            for (BlockPos pos : found) {
                world.breakBlock(pos, true, player);
            }
        } finally {
            breaking = false;
        }
    }

    private static void onProjectileHit(LivingEntity victim, DamageSource source, float damageTaken) {
        if (!(source.getSource() instanceof ProjectileEntity projectile)
                || !(projectile.getOwner() instanceof LivingEntity shooter)
                || shooter == victim) {
            return;
        }
        ItemStack bow = shooter.getMainHandStack();
        int bonus = 0;
        for (String name : BOW_ENCHANTS) {
            bonus += levelOf(shooter, name, bow);
        }
        if (bonus > 0) {
            bonusHit = true;
            try {
                victim.damage(source, damageTaken * 0.25f * bonus);
            } finally {
                bonusHit = false;
            }
        }
        int hellfire = levelOf(shooter, "hellfire", bow);
        if (hellfire > 0) {
            victim.setOnFireFor(4 * hellfire);
        }
    }

    private static void onMeleeBonus(LivingEntity victim, DamageSource source, float damageTaken) {
        if (!(source.getAttacker() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }
        ItemStack weapon = attacker.getMainHandStack();
        if (weapon.isEmpty()) {
            return;
        }
        EntityType<?> type = victim.getType();
        float bonus = 0f;
        if (type == EntityType.ENDERMAN || type == EntityType.ENDERMITE) {
            bonus += 0.3f * levelOf(attacker, "enderslayer", weapon);
        }
        if (type == EntityType.BLAZE || type == EntityType.WITHER_SKELETON
                || type == EntityType.MAGMA_CUBE || type == EntityType.GHAST) {
            bonus += 0.3f * levelOf(attacker, "netherslayer", weapon);
        }
        if (type == EntityType.ZOMBIE || type == EntityType.SKELETON || type == EntityType.DROWNED) {
            bonus += 0.3f * levelOf(attacker, "slayer", weapon);
        }
        if (type == EntityType.SKELETON || type == EntityType.WITHER_SKELETON || type == EntityType.STRAY) {
            bonus += 0.3f * levelOf(attacker, "bonecrusher", weapon);
        }
        if (victim instanceof AnimalEntity) {
            bonus += 0.3f * levelOf(attacker, "beastslayer", weapon);
        }
        if (type == EntityType.ENDER_DRAGON) {
            bonus += 0.3f * levelOf(attacker, "endmaster", weapon);
        }
        if (victim instanceof PlayerEntity) {
            bonus += 0.3f * levelOf(attacker, "deathpunch", weapon);
        }
        if (bonus > 0f) {
            bonusHit = true;
            try {
                victim.damage(source, damageTaken * bonus);
            } finally {
                bonusHit = false;
            }
        }

        // Thunderlord: chaqmoq urishi.
        int thunder = levelOf(attacker, "thunderlord", weapon);
        if (thunder > 0 && attacker.getRandom().nextFloat() < 0.1f * thunder
                && victim.getWorld() instanceof ServerWorld serverWorld) {
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(serverWorld);
            if (bolt != null) {
                bolt.setPosition(victim.getX(), victim.getY(), victim.getZ());
                serverWorld.spawnEntity(bolt);
            }
        }
    }
}
