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
package org.ladysnake.blabber.api.client.illustrations;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.blabber.impl.common.illustrations.entity.DialogueIllustrationNbtEntity;

public class NbtEntityIllustrationRenderer extends EntityIllustrationRenderer<DialogueIllustrationNbtEntity> {
    public NbtEntityIllustrationRenderer(DialogueIllustrationNbtEntity illustration) {
        super(illustration);
    }

    @Override
    protected @Nullable LivingEntity getRenderedEntity(Level world) {
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(illustration.id()).orElse(null);
        if (entityType == null) return null;

        if (entityType.create(world) instanceof LivingEntity living) {
            illustration.data().ifPresent(living::load);
            living.yBodyRotO = living.yBodyRot = 0.0f;
            living.yHeadRotO = living.yHeadRot = 0.0f;
            return living;
        }

        return null;
    }
}
