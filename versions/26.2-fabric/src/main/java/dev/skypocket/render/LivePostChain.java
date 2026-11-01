package dev.skypocket.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.JsonOps;
import dev.skypocket.mixin.PostChainAccessor;
import dev.skypocket.mixin.PostPassAccessor;
import dev.skypocket.mixin.ShaderManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A post-effect chain whose uniform values are ours to change while the game runs.
 *
 * <p><b>How the values get in.</b> A {@code PostPass} bakes its uniform values into a
 * {@code USAGE_UNIFORM} buffer when it is constructed - there is no setter, and that buffer cannot be
 * written either (no {@code COPY_DST}). It does, however, read its {@code customUniforms} map every
 * frame while binding uniforms, so the first time a chain is loaded this class <em>replaces</em> the
 * entry for the block with a buffer of its own, created with {@code COPY_DST} and serialised by MC's
 * own {@code Std140Builder} (so the layout is identical to what the engine would have produced).
 * From then on a value change is an upload of a few hundred bytes rather than a rebuild.
 *
 * <p>That matters twice over: dragging a slider updates at frame rate instead of through a debounce,
 * and effects whose values change <em>every</em> frame (camera matrices, for instance) become
 * possible at all - a per-frame chain reload would relink a pipeline each frame.
 *
 * <p>If the swap cannot be made (an unexpected chain shape, an allocation failure), the class falls
 * back to rebuilding: read the template once, patch the values into a copy of it and load that, with
 * {@link #REBUILD_INTERVAL_MS} collapsing the storm of slider steps into one rebuild at a time. A
 * rebuild re-links a pipeline rather than re-compiling GLSL - the device caches shader modules by
 * compilation key.
 *
 * <p><b>Resource reloads invalidate every chain.</b> Compiled programs are rebuilt on reload, so a
 * chain holding pre-reload objects must not be used again - vanilla's own {@code ShaderManager} drops
 * its chains for the same reason.
 */
public final class LivePostChain {
    private static final dev.skypocket.SkyPocketLog LOGGER =
            dev.skypocket.SkyPocketLog.of("postChain");
    /** Every instance, so a resource reload can drop them all (see {@link #installReloadHook()}). */
    private static final List<LivePostChain> ALL = new ArrayList<>();
    /** While a rebuild fallback is in use, rebuild at most this often. */
    private static final long REBUILD_INTERVAL_MS = 100;
    /** After a failed load, wait this long before trying again. */
    private static final long RETRY_INTERVAL_MS = 2000;

    /**
     * Installs the resource-reload hook, so a reload (F3+T) drops the chains: the device rebuilds its
     * compiled programs then, and a chain holding pre-reload objects must not be used again. Called
     * once from the client initializer.
     */
    public static void installReloadHook() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return; // no client yet: without the hook a reload simply keeps the previous chains
        }
        if (mc.getResourceManager() instanceof ReloadableResourceManager manager) {
            manager.registerReloadListener((shared, preparationExecutor, barrier, reloadExecutor) -> {
                for (LivePostChain chain : ALL) {
                    chain.invalidate();
                }
                return CompletableFuture.completedFuture(null);
            });
        }
    }

    /** A block value list for the common case of an effect whose uniforms are all plain floats. */
    public static List<UniformValue> floats(float... values) {
        List<UniformValue> list = new ArrayList<>(values.length);
        for (float value : values) {
            list.add(new UniformValue.FloatUniform(value));
        }
        return list;
    }

    /** Template JSON, e.g. {@code post_effect/grade.json}. */
    private final Identifier template;
    /** Name of the uniform block the parameters are patched into. */
    private final String block;
    /** The values the current chain holds, or null before the first build. */
    private List<UniformValue> built;
    /** Our writable stand-ins for the chain's static block buffers; empty while none were made. */
    private final List<GpuBuffer> writable = new ArrayList<>();
    /** Scratch upload buffer, grown as needed (client thread only). */
    private ByteBuffer upload;

    private PostChain chain;
    private PostChainConfig decoded;
    private boolean templateUnavailable;
    private boolean failureLogged;
    private boolean writableFailed;
    private long lastAttempt;
    private long nextRetry;

    LivePostChain(String path, String block) {
        this.template = Identifier.fromNamespaceAndPath("skypocket", path);
        this.block = block;
        ALL.add(this);
    }

    /** The chain to process, or null while it has not been built (or failed to build). */
    PostChain chain() {
        return chain;
    }

    /**
     * Whether value changes go in as uploads ({@code true}) or through the rebuild fallback
     * ({@code false}). Only worth knowing for the diagnostics: both are correct, the first is just
     * much cheaper, so a chain that is silently rebuilding every frame is a performance answer rather
     * than a visual one.
     */
    boolean liveUniforms() {
        return !writable.isEmpty();
    }

    /**
     * Applies {@code values}: an upload when the block is ours to write, otherwise a (debounced)
     * rebuild.
     *
     * <p>The comparison is structural - every {@code UniformValue} is a record, so a list of them
     * compares by value - which is also why callers can pass either a plain row of floats or a mix of
     * floats and matrices without this class knowing the difference.
     */
    void apply(List<UniformValue> values) {
        if (chain != null && values.equals(built)) {
            return;
        }
        if (!writable.isEmpty()) {
            upload(values);
            built = List.copyOf(values);
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextRetry) {
            return;
        }
        if (now - lastAttempt < REBUILD_INTERVAL_MS && chain != null) {
            // Mid-drag fallback: this frame keeps the previous values, the next change lands soon.
            return;
        }
        lastAttempt = now;
        load(values);
    }

    /** Drops the chain and the decoded template; the next frame loads both again. */
    void invalidate() {
        close();
        decoded = null;
        templateUnavailable = false;
        failureLogged = false;
        writableFailed = false;
        nextRetry = 0;
        built = null;
    }

    private void load(List<UniformValue> values) {
        Minecraft mc = Minecraft.getInstance();
        PostChainConfig config = template(mc);
        if (config == null) {
            nextRetry = System.currentTimeMillis() + RETRY_INTERVAL_MS;
            return;
        }
        try {
            ShaderManager shaders = mc.getShaderManager();
            ShaderManagerAccessor accessor = (ShaderManagerAccessor) shaders;
            PostChain fresh = PostChain.load(withValues(config, values), mc.getTextureManager(),
                    LevelTargetBundle.MAIN_TARGETS, PostChain.MAIN_TARGET_ID,
                    accessor.sky$postChainProjection(), accessor.sky$postChainProjectionMatrixBuffer());
            close();
            chain = fresh;
            built = List.copyOf(values);
            failureLogged = false;
            claimBlocks(values);
        } catch (ShaderManager.CompilationException | RuntimeException e) {
            nextRetry = System.currentTimeMillis() + RETRY_INTERVAL_MS;
            if (!failureLogged) {
                failureLogged = true;
                LOGGER.warn("failed to load post chain {} with custom values", template, e);
            }
        }
    }

    /**
     * Replaces each pass's static buffer for our block with a writable one of our own, so later value
     * changes are uploads. Anything unexpected leaves {@link #writable} empty and the rebuild path in
     * charge - slower, but it cannot leave a chain half-owned.
     */
    private void claimBlocks(List<UniformValue> values) {
        if (writableFailed) {
            return;
        }
        serialize(values);
        try {
            for (PostPass pass : ((PostChainAccessor) chain).sky$passes()) {
                Map<String, GpuBuffer> uniforms = ((PostPassAccessor) pass).sky$customUniforms();
                if (!uniforms.containsKey(block)) {
                    continue;
                }
                upload.position(0);
                GpuBuffer mine = RenderSystem.getDevice().createBuffer(
                        () -> "skypocket live uniform block",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, upload);
                uniforms.put(block, mine);
                writable.add(mine);
            }
        } catch (RuntimeException e) {
            writableFailed = true;
            closeWritable();
            LOGGER.warn("post chain {} keeps rebuilding its values: {}", template, e.toString());
        }
    }

    /** Uploads {@code values} into every writable block buffer. */
    private void upload(List<UniformValue> values) {
        serialize(values);
        for (GpuBuffer buffer : writable) {
            upload.position(0);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), upload);
        }
    }

    /** The template JSON, decoded once. Null when it is missing or invalid. */
    private PostChainConfig template(Minecraft mc) {
        if (decoded != null || templateUnavailable) {
            return decoded;
        }
        Optional<Resource> resource = mc.getResourceManager().getResource(template);
        if (resource.isEmpty()) {
            templateUnavailable = true;
            logUnusable("the resource is not in the mod jar", null);
            return null;
        }
        try (InputStream in = resource.get().open()) {
            JsonElement json = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            decoded = PostChainConfig.CODEC.parse(JsonOps.INSTANCE, json).result().orElse(null);
        } catch (Exception e) {
            decoded = null;
            logUnusable("it could not be read", e);
        }
        if (decoded == null) {
            templateUnavailable = true;
        }
        return decoded;
    }

    private void logUnusable(String reason, Exception cause) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        if (cause == null) {
            LOGGER.warn("post chain {} unusable: {}", template, reason);
        } else {
            LOGGER.warn("post chain {} unusable: {}", template, reason, cause);
        }
    }

    /** A copy of {@code config} with {@code values} in place of the template's block values. */
    private PostChainConfig withValues(PostChainConfig config, List<UniformValue> values) {
        List<PostChainConfig.Pass> passes = new ArrayList<>(config.passes().size());
        for (PostChainConfig.Pass pass : config.passes()) {
            Map<String, List<UniformValue>> uniforms = pass.uniforms();
            if (uniforms.containsKey(block)) {
                uniforms = new LinkedHashMap<>(uniforms);
                uniforms.put(block, values);
            }
            passes.add(new PostChainConfig.Pass(pass.vertexShaderId(), pass.fragmentShaderId(),
                    pass.inputs(), pass.outputTarget(), uniforms));
        }
        return new PostChainConfig(config.internalTargets(), List.copyOf(passes));
    }

    /**
     * Serialises a block the way MC's own pass construction would, so the layouts match exactly.
     * Leaves {@link #upload} positioned at 0 with its limit on the block's full (std140-padded) size.
     */
    private void serialize(List<UniformValue> values) {
        Std140SizeCalculator calculator = new Std140SizeCalculator();
        for (UniformValue value : values) {
            value.addSize(calculator);
        }
        int size = Math.max(calculator.get(), 16);
        if (upload == null || upload.capacity() < size) {
            upload = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
        }
        upload.clear();
        Std140Builder builder = Std140Builder.intoBuffer(upload);
        for (UniformValue value : values) {
            value.writeTo(builder);
        }
        // The builder leaves its own view of the buffer; the block's real size includes the padding the
        // calculator asked for, and a uniform buffer has to be bound at least that large.
        upload.position(0);
        upload.limit(size);
    }

    private void close() {
        closeWritable();
        if (chain != null) {
            chain.close();
            chain = null;
        }
    }

    private void closeWritable() {
        for (GpuBuffer buffer : writable) {
            buffer.close();
        }
        writable.clear();
    }
}
