package net.advancedplugins.ae.fabric;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Smelting: blok tushishi pishirilgan natija boʻladi (temir rudasi -> temir qolibi).
 * Telepathy: tushgan narsalar yerga emas, toʻgʻridan-toʻgʻri oʻyinchi inventariga tushadi.
 * Vanilla tushishi oʻrniga oʻz mantiqimiz ishlaydi.
 */
public final class BlockDrops {

    private BlockDrops() {
    }

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

    private static ItemStack smelt(ServerWorld world, ItemStack input) {
        SingleStackRecipeInput recipeInput = new SingleStackRecipeInput(input);
        var recipe = world.getRecipeManager().getFirstMatch(RecipeType.SMELTING, recipeInput, world);
        if (recipe.isPresent()) {
            ItemStack result = recipe.get().value().craft(recipeInput, world.getRegistryManager());
            if (!result.isEmpty()) {
                return result.copyWithCount(result.getCount() * input.getCount());
            }
        }
        return input;
    }

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerWorld serverWorld) || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return true;
            }
            if (serverPlayer.isCreative()) {
                return true;
            }
            ItemStack tool = serverPlayer.getMainHandStack();
            int smelting = levelOf(serverPlayer, "smelting", tool);
            int telepathy = levelOf(serverPlayer, "telepathy", tool);
            if (smelting == 0 && telepathy == 0) {
                return true;
            }

            List<ItemStack> drops = Block.getDroppedStacks(state, serverWorld, pos, blockEntity, serverPlayer, tool);

            // Blokni tushirmasdan oʻchiramiz (vanilla tushishisiz).
            world.breakBlock(pos, false, serverPlayer);

            for (ItemStack drop : drops) {
                ItemStack result = smelting > 0 ? smelt(serverWorld, drop) : drop;
                if (telepathy > 0) {
                    serverPlayer.getInventory().insertStack(result);
                    if (!result.isEmpty()) {
                        serverPlayer.dropItem(result, false);
                    }
                } else {
                    Block.dropStack(serverWorld, pos, result);
                }
            }
            // Vanilla sindirish hodisasini bekor qilamiz (blok allaqachon olib tashlangan).
            return false;
        });
    }
}
