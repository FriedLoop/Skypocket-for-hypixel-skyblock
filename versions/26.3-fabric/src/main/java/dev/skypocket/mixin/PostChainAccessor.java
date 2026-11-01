package dev.skypocket.mixin;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Access to a chain's passes, so {@code LivePostChain} can reach the uniform buffers of each pass (see
 * {@link PostPassAccessor}).
 */
@Mixin(PostChain.class)
public interface PostChainAccessor {
    @Accessor("passes")
    List<PostPass> sky$passes();
}
