package dev.skypocket.mixin;

import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Access to the projection objects {@code ShaderManager} owns.
 *
 * <p>Loading a post chain with our own uniform values means calling {@code PostChain.load} ourselves
 * (vanilla's {@code getPostChain} caches one chain per id and bakes the uniforms in at construction),
 * and that call needs exactly the two things the manager hands to its own chains: the shared
 * {@link Projection} and the {@link ProjectionMatrixBuffer} that uploads it. Reading them is
 * equivalent to what {@code ShaderManager}'s own compilation cache does.
 */
@Mixin(ShaderManager.class)
public interface ShaderManagerAccessor {
    @Accessor("postChainProjection")
    Projection sky$postChainProjection();

    @Accessor("postChainProjectionMatrixBuffer")
    ProjectionMatrixBuffer sky$postChainProjectionMatrixBuffer();
}
