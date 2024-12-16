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
package org.ladysnake.blabber.impl.common;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.world.level.storage.loot.Serializer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

public record InterlocutorPropertiesLootCondition(EntityPredicate predicate) implements LootItemCondition {
    public static final Codec<InterlocutorPropertiesLootCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ExtraCodecs.JSON_ELEMENT.xmap(EntityPredicate::fromJson, EntityPredicate::toJson)
                           .fieldOf("predicate")
                           .forGetter(InterlocutorPropertiesLootCondition::predicate)
    ).apply(instance, InterlocutorPropertiesLootCondition::new));

    public static final LootItemConditionType TYPE =
        new LootItemConditionType(new Serializer<InterlocutorPropertiesLootCondition>() {
            @Override
            public void toJson(
                final JsonObject json,
                final InterlocutorPropertiesLootCondition object,
                final JsonSerializationContext context
            ) {
                json.asMap().putAll(CODEC.encodeStart(JsonOps.INSTANCE, object)
                                         .result()
                                         .orElseThrow()
                                         .getAsJsonObject()
                                         .asMap());
            }

            @Override
            public InterlocutorPropertiesLootCondition fromJson(
                final JsonObject json,
                final JsonDeserializationContext context
            ) {
                return CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
            }
        });

    @Override
    public LootItemConditionType getType() {
        return TYPE;
    }

    @Override
    public Set<LootContextParam<?>> getRequiredParameters() {
        return Set.of(LootContextParams.ORIGIN);
    }

    @Override
    public boolean test(LootContext lootContext) {
        Entity entity = lootContext.get(LootContext.EntityTarget.THIS.getParameter());
        Vec3 origin = lootContext.get(LootContextParams.ORIGIN);
        Optional<Entity> interlocutor = PlayerDialogueTracker.KEY.maybeGet(entity).flatMap(PlayerDialogueTracker::getInterlocutor);
        return interlocutor.isPresent() && this.predicate.test(lootContext.getWorld(), origin, interlocutor.get());
    }
}
