package dev.skypocket.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Access to a pass's custom uniform buffers, keyed by uniform block name.
 *
 * <p>This is what makes live parameters possible without reloading the chain: the buffer a
 * {@code PostPass} creates for a block is {@code USAGE_UNIFORM} only (no {@code COPY_DST}, so it
 * cannot be written from here either), but the pass reads this map <em>every frame</em> when it binds
 * its uniforms - so replacing an entry with a buffer we own and created with {@code COPY_DST} gives a
 * block whose values can be uploaded per frame.
 */
@Mixin(PostPass.class)
public interface PostPassAccessor {
    @Accessor("customUniforms")
    Map<String, GpuBuffer> sky$customUniforms();
}
