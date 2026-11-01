package dev.skypocket.mixin;

import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the private {@code getFontSetRaw} lookup (falls back to the missing set). */
@Mixin(FontManager.class)
public interface FontManagerAccessor {
    @Invoker("getFontSetRaw")
    FontSet sky$invokeGetFontSetRaw(Identifier id);
}
