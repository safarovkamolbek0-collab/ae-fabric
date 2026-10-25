package net.advancedplugins.ae.fabric;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;

/** Effekt yaratish: kuch 4 dan oshmaydi. */
public final class Fx {

    private Fx() {
    }

    public static StatusEffectInstance inst(RegistryEntry<StatusEffect> effect, int duration, int amplifier,
                                            boolean ambient, boolean visible) {
        int amp = Math.min(Math.max(amplifier, 0), 4);
        return new StatusEffectInstance(effect, duration, amp, ambient, visible);
    }
}
