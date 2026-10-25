package net.advancedplugins.ae.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Klient tooltip: AE enchantlarining nomini darajasiga qarab rangga boʻyaydi.
 */
public final class ClientTooltips implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> colorEnchants(stack, lines));
    }

    private static Formatting colorFor(int level) {
        if (level <= 1) {
            return Formatting.GREEN;
        }
        if (level == 2) {
            return Formatting.AQUA;
        }
        if (level == 3) {
            return Formatting.LIGHT_PURPLE;
        }
        return Formatting.GOLD;
    }

    private static void colorEnchants(ItemStack stack, List<Text> lines) {
        ItemEnchantmentsComponent enchants = stack.getOrDefault(
                DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);

        for (RegistryEntry<Enchantment> entry : enchants.getEnchantments()) {
            String id = entry.getKey()
                    .map(key -> key.getValue().getNamespace() + ":" + key.getValue().getPath())
                    .orElse("");
            if (!id.startsWith(AEFabric.MOD_ID + ":")) {
                continue;
            }
            int level = enchants.getLevel(entry);
            // Daraja raqamisiz nom (masalan, "Haste I" -> "Haste").
            String baseName = Enchantment.getName(entry, 1).getString();
            String nameOnly = baseName.replaceAll("\\s+[IVXLCDM0-9]+$", "").trim();

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).getString();
                if (line.startsWith(nameOnly)) {
                    lines.set(i, Enchantment.getName(entry, level).copy().formatted(colorFor(level)));
                    break;
                }
            }
        }
    }
}
