package dev.skypocket.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Classifies the entity/block under the crosshair for per-target coloring.
 * Reuses the vanilla per-frame {@code Minecraft.hitResult} (no extra raycast).
 */
public final class CrosshairTargetClassifier {

    public enum Kind { NONE, FRIENDLY, NEUTRAL, HOSTILE, CONTAINER }

    private CrosshairTargetClassifier() {
    }

    public static Kind classify(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult entityHit) {
            return classifyEntity(entityHit.getEntity());
        }
        if (hit instanceof BlockHitResult blockHit && mc.level != null) {
            return isContainer(mc.level.getBlockEntity(blockHit.getBlockPos())) ? Kind.CONTAINER : Kind.NONE;
        }
        return Kind.NONE;
    }

    private static Kind classifyEntity(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return Kind.NONE;
        }
        if (isHostile(entity)) {
            return Kind.HOSTILE;
        }
        if (isNeutral(entity)) {
            return Kind.NEUTRAL;
        }
        return Kind.FRIENDLY;
    }

    public static boolean isHostile(Entity e) {
        return (e instanceof Monster || e instanceof Slime || e instanceof MagmaCube
                || e instanceof Ghast || e instanceof Phantom || e instanceof Hoglin || e instanceof Zoglin)
                && !(e instanceof Spider || e instanceof CaveSpider || e instanceof EnderMan
                     || e instanceof ZombifiedPiglin);
    }

    public static boolean isNeutral(Entity e) {
        return e instanceof Spider || e instanceof CaveSpider || e instanceof EnderMan
                || e instanceof ZombifiedPiglin || e instanceof PolarBear || e instanceof Wolf
                || e instanceof IronGolem || e instanceof Llama || e instanceof Bee
                || e instanceof Dolphin || e instanceof Panda || e instanceof Goat || e instanceof Pufferfish;
    }

    public static boolean isContainer(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
                || be instanceof EnderChestBlockEntity || be instanceof ShulkerBoxBlockEntity;
    }
}
