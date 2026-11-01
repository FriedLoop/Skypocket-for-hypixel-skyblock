package dev.skypocket.render;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Classifies an entity into one of the hitbox-display categories so each can be
 * toggled independently. Containers are block entities, not entities, and are
 * handled by {@link HitboxRenderer} separately via {@link CrosshairTargetClassifier#isContainer}.
 */
public final class HitboxClassifier {

    public enum Category { PLAYER, PROJECTILE, HOSTILE, NEUTRAL, OTHER }

    private HitboxClassifier() {
    }

    public static Category classify(Entity entity) {
        if (entity instanceof Player) {
            return Category.PLAYER;
        }
        if (entity instanceof Projectile) {
            return Category.PROJECTILE;
        }
        if (entity instanceof LivingEntity) {
            if (CrosshairTargetClassifier.isHostile(entity)) {
                return Category.HOSTILE;
            }
            if (CrosshairTargetClassifier.isNeutral(entity)) {
                return Category.NEUTRAL;
            }
        }
        return Category.OTHER;
    }
}
