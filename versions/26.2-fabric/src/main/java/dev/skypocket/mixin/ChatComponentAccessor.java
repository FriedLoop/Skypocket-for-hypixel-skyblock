package dev.skypocket.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Exposes the chat's backing lists so a compacted run can drop its previous line.
 *
 * <p>{@code allMessages} is the model, {@code trimmedMessages} is what the renderer actually draws - a
 * line removed from only one of them either stays on screen or leaves a hole.
 */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {

    @Accessor("allMessages")
    List<GuiMessage> skypocket$allMessages();

    @Accessor("trimmedMessages")
    List<GuiMessage.Line> skypocket$trimmedMessages();
}
