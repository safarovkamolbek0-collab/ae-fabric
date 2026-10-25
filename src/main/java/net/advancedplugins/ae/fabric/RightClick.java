package net.advancedplugins.ae.fabric;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.util.TypedActionResult;

/** RIGHT_CLICK: oʻyinchi qoʻlidagi narsa bilan oʻng tugma bosganda enchantlar ishlaydi. */
public final class RightClick {

    private RightClick() {
    }

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!world.isClient()) {
                AEEngine.fireOnStack("RIGHT_CLICK", player, player, stack);
            }
            return TypedActionResult.pass(stack);
        });
    }
}
