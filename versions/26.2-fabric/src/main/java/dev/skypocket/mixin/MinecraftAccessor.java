package dev.skypocket.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the private {@code fontManager} field for building a custom font. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("fontManager")
    FontManager sky$getFontManager();
}
