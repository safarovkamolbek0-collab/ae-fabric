package net.advancedplugins.ae.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.stream.Collectors;

/**
 * /ae, /customenchants, /advancedenchantments buyruqlari.
 * /ae list - barcha enchantlar, /ae help <nom> - enchant haqida maʼlumot.
 */
public final class AECommands {

    private AECommands() {
    }

    private static final List<String> ROOTS = List.of("ae", "customenchants", "advancedenchantments");

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            for (String root : ROOTS) {
                dispatcher.register(build(root));
            }
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> build(String root) {
        return CommandManager.literal(root)
                .executes(ctx -> {
                    info(ctx.getSource());
                    return 1;
                })
                .then(CommandManager.literal("list")
                        .executes(ctx -> {
                            list(ctx.getSource());
                            return 1;
                        }))
                .then(CommandManager.literal("help")
                        .then(CommandManager.argument("enchant", StringArgumentType.word())
                                .executes(ctx -> {
                                    help(ctx.getSource(), StringArgumentType.getString(ctx, "enchant"));
                                    return 1;
                                })));
    }

    private static void info(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal(
                "AdvancedEnchantments (Fabric port) | Buyruqlar: /ae list, /ae help <nom>, /enchant ae_fabric:<nom> <daraja>"), false);
    }

    private static void list(ServerCommandSource source) {
        Registry<Enchantment> registry = source.getServer().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        String names = registry.getIds().stream()
                .filter(id -> id.getNamespace().equals(AEFabric.MOD_ID))
                .map(Identifier::getPath)
                .sorted()
                .collect(Collectors.joining(", "));
        source.sendFeedback(() -> Text.literal("Enchantlar: " + names), false);
    }

    private static void help(ServerCommandSource source, String input) {
        String name = input.startsWith(AEFabric.MOD_ID + ":") ? input.substring(AEFabric.MOD_ID.length() + 1) : input;
        List<String> lines = AEEngine.describe(name);
        if (lines.isEmpty()) {
            source.sendFeedback(() -> Text.literal("Bunday enchant topilmadi: " + name), false);
            return;
        }
        for (String line : lines) {
            source.sendFeedback(() -> Text.literal(line), false);
        }
    }
}
