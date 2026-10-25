package net.advancedplugins.ae.fabric;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Replanter: pishgan ekin sindirilganda qayta ekiladi (bepul, urugʻ sarflanmaydi).
 * Trench: sindirilgan blok atrofidagi 3x3 tekislikdagi bloklarni ham sindiradi.
 */
public final class BreakExtras {

    private BreakExtras() {
    }

    private static boolean trenching = false;

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
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (trenching || !(world instanceof ServerWorld serverWorld)
                    || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            ItemStack tool = serverPlayer.getMainHandStack();

            // Replanter: pishgan ekin (yetilgan) qayta ekiladi.
            if (levelOf(serverPlayer, "replanter", tool) > 0
                    && state.getBlock() instanceof CropBlock crop
                    && state.contains(CropBlock.AGE)) {
                BlockState replanted = crop.getDefaultState().with(CropBlock.AGE, 0);
                serverWorld.setBlockState(pos, replanted, 3);
            }

            // Trench: 3x3 tekislikda qoʻshni bloklarni sindirish.
            if (levelOf(serverPlayer, "trench", tool) > 0) {
                breakArea(serverWorld, serverPlayer, pos);
            }
        });
    }

    /** Markaz atrofidagi 3x3 gorizontal tekislikdagi bloklarni sindiradi. */
    private static void breakArea(ServerWorld world, ServerPlayerEntity player, BlockPos center) {
        trenching = true;
        try {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos target = center.add(dx, 0, dz);
                    BlockState targetState = world.getBlockState(target);
                    if (targetState.isAir() || targetState.getHardness(world, target) < 0) {
                        continue;
                    }
                    world.breakBlock(target, true, player);
                }
            }
        } finally {
            trenching = false;
        }
    }
}
