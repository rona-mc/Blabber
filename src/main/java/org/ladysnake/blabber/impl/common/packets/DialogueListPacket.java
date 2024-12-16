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

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.ladysnake.blabber.Blabber;

import java.util.HashSet;
import java.util.Set;

public record DialogueListPacket(Set<ResourceLocation> dialogueIds) implements FabricPacket {
    public static final PacketType<DialogueListPacket> TYPE = PacketType.create(Blabber.id("dialogue_list"), DialogueListPacket::new);

    public DialogueListPacket(FriendlyByteBuf buf) {
        this(buf.<ResourceLocation, Set<ResourceLocation>>readCollection(HashSet::new, FriendlyByteBuf::readResourceLocation));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeCollection(dialogueIds(), FriendlyByteBuf::writeResourceLocation);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
