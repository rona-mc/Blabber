/*
 * Blabber
 * Copyright (C) 2022-2024 Ladysnake
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; If not, see <https://www.gnu.org/licenses>.
 */
package org.ladysnake.blabber;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.blabber.api.DialogueActionV2;

/**
 * @see DialogueActionV2
 * @see Blabber#registerAction(ResourceLocation, DialogueAction)
 */
@FunctionalInterface
public interface DialogueAction extends DialogueActionV2 {
    /**
     * Handles a dialogue action triggered by the given player.
     *
     * @param player the player executing the action
     */
    void handle(ServerPlayer player);

    @Override
    default void handle(ServerPlayer player, @Nullable Entity interlocutor) {
        this.handle(player);
    }
}
