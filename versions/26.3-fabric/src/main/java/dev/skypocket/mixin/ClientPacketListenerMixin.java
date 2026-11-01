package dev.skypocket.mixin;

import dev.skypocket.net.PingMonitor;
import dev.skypocket.render.HotspotTracker;
import dev.skypocket.render.TpsEstimator;
import dev.skypocket.skyblock.MiningBreakTracker;
import dev.skypocket.skyblock.TabListText;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds the server's game time into {@link TpsEstimator} for the multiplayer TPS
 * estimate. Each time-sync packet carries the server's authoritative game time.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleSetTime", at = @At("HEAD"))
    private void sky$onSetTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
        TpsEstimator.onServerTime(packet.gameTime());
    }

    @Inject(method = "handlePongResponse", at = @At("HEAD"))
    private void sky$onPong(ClientboundPongResponsePacket packet, CallbackInfo ci) {
        PingMonitor.onPong(packet);
    }

    /**
     * Feeds every received particle into {@link HotspotTracker} for the fishing
     * hotspot ring-radius estimation. TAIL (not HEAD) guarantees main-thread
     * execution: the handler begins with the same-thread check, which throws on the
     * network thread and skips everything after it.
     */
    @Inject(method = "handleParticleEvent", at = @At("TAIL"))
    private void sky$onParticle(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        String type = BuiltInRegistries.PARTICLE_TYPE.getKey(packet.getParticle().getType()).toString();
        HotspotTracker.onParticle(packet.getX(), packet.getY(), packet.getZ(), type);
    }

    /**
     * Authoritative block changes: the server confirming a mined block is gone.
     * TAIL on purpose (main thread, and the packet is still readable for both kinds).
     */
    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void sky$onBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        MiningBreakTracker.onServerBlockChange(packet.getPos(), sky$blockId(packet.getBlockState()));
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void sky$onChunkBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        packet.runUpdates((pos, state) ->
                MiningBreakTracker.onServerBlockChange(pos, sky$blockId(state)));
    }

    /** Keeps the tab header/footer text (Hypixel renders its "Stats:" block there). */
    @Inject(method = "handleTabListCustomisation", at = @At("TAIL"))
    private void sky$onTabListCustomisation(ClientboundTabListPacket packet, CallbackInfo ci) {
        TabListText.onTabList(packet.header(), packet.footer());
    }

    private static String sky$blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }
}
