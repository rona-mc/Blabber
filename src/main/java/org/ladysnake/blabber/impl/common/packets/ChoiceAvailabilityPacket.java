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
package org.ladysnake.blabber.impl.common.packets;

import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.sjhub.blabber.network.ForgePacket;
import net.sjhub.blabber.network.PacketType;
import org.ladysnake.blabber.Blabber;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a list of dialogue choices which availability has changed
 */
public record ChoiceAvailabilityPacket(Map<String, Int2BooleanMap> updatedChoices) implements ForgePacket { // TODO
    public static final PacketType<ChoiceAvailabilityPacket> TYPE = PacketType.create(Blabber.id("choice_availability"), ChoiceAvailabilityPacket::new); // TODO

    public ChoiceAvailabilityPacket() {
        this(new HashMap<>());
    }

    public ChoiceAvailabilityPacket(FriendlyByteBuf buf) {
        this(buf.readMap(
                FriendlyByteBuf::readUtf,
                b -> b.readMap(Int2BooleanOpenHashMap::new, FriendlyByteBuf::readVarInt, FriendlyByteBuf::readBoolean)
        ));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeMap(
                this.updatedChoices(),
                FriendlyByteBuf::writeUtf,
                (b, updatedChoices) -> b.writeMap(updatedChoices, FriendlyByteBuf::writeVarInt, FriendlyByteBuf::writeBoolean)
        );
    }

    @Override
    public PacketType<?> getType() { // TODO
        return TYPE;
    }

    public void markUpdated(String stateKey, int choiceIndex, boolean newValue) {
        this.updatedChoices().computeIfAbsent(stateKey, s -> new Int2BooleanOpenHashMap()).put(choiceIndex, newValue);
    }
}
