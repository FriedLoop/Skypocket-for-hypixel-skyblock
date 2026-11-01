package dev.skypocket.mixin;

import dev.skypocket.chat.ChatCompactor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds every chat line through {@link ChatCompactor}.
 *
 * <p>Both hooks land on the private {@code addMessage}, which is the single funnel every chat source
 * goes through (player chat, system messages, action bar excluded) - so this applies to all chat
 * messages rather than to one kind of sender.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    /** Rewrites the text before it is stored, so the counter is part of the message itself. */
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;"
            + "Lnet/minecraft/network/chat/MessageSignature;"
            + "Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;"
            + "Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"), argsOnly = true, index = 1)
    private Component sky$compact(Component message) {
        return ChatCompactor.incoming((ChatComponent) (Object) this, message);
    }

    /**
     * Takes the message object as it is built.
     *
     * <p>Reading the tail of {@code allMessages} after {@code addMessage} returned picked up whatever
     * happened to be last - chat queues the entry first - so a repeated line removed an unrelated
     * message and the duplicates piled up instead of collapsing.
     */
    @Inject(method = "addMessageToQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V",
            at = @At("HEAD"))
    private void sky$onQueue(GuiMessage message, CallbackInfo ci) {
        ChatCompactor.onCreated(message);
    }

    @Inject(method = "addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V",
            at = @At("HEAD"))
    private void sky$onDisplay(GuiMessage message, CallbackInfo ci) {
        ChatCompactor.onCreated(message);
    }
}
