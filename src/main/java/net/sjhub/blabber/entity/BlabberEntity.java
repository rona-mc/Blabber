package net.sjhub.blabber.entity;

import com.mojang.authlib.GameProfile;
import dev.onyxstudios.cca.api.v3.component.ComponentAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public abstract class BlabberEntity extends ServerPlayer implements ComponentAccess {

    public BlabberEntity(MinecraftServer p_254143_, ServerLevel p_254435_, GameProfile p_253651_) {
        super(p_254143_, p_254435_, p_253651_);
    }
}
