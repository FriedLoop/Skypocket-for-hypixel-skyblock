package dev.skypocket.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the private text of a {@code TextDisplay} (e.g. the "HOTSPOT" marker). */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {
    @Invoker("getText")
    Component sky$getText();
}
