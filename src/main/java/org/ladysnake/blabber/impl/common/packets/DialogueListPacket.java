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

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import org.ladysnake.blabber.impl.common.DialogueRegistry;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class DialogueListPacket {
    private final Set<ResourceLocation> dialogueIds;

    public DialogueListPacket(Set<ResourceLocation> dialogueIds) {
        this.dialogueIds = dialogueIds;
    }

    public DialogueListPacket(FriendlyByteBuf buf) {
        this(buf.<ResourceLocation, Set<ResourceLocation>>readCollection(HashSet::new, FriendlyByteBuf::readResourceLocation));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeCollection(dialogueIds, FriendlyByteBuf::writeResourceLocation);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DialogueRegistry.setClientIds(dialogueIds);
        });
        ctx.get().setPacketHandled(true);
    }

    public Set<ResourceLocation> getDialogueIds() {
        return dialogueIds;
    }
}
