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

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.IContainerFactory;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.blabber.impl.common.machine.DialogueStateMachine;
import org.ladysnake.blabber.impl.common.packets.ChoiceAvailabilityPacket;

import java.util.Optional;

public class DialogueScreenHandlerFactory implements IContainerFactory<DialogueScreenHandler> {
    private final DialogueStateMachine dialogue;
    private final Component displayName;
    private final @Nullable Entity interlocutor;

    public DialogueScreenHandlerFactory(DialogueStateMachine dialogue, Component displayName, @Nullable Entity interlocutor) {
        this.dialogue = dialogue;
        this.displayName = displayName;
        this.interlocutor = interlocutor;
    }

    public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
        DialogueStateMachine.writeToPacket(buf, this.dialogue);
        buf.writeOptional(Optional.ofNullable(interlocutor), (b, e) -> b.writeVarInt(e.getId()));
        this.dialogue.createFullAvailabilityUpdatePacket().write(buf);
    }

    public Component getDisplayName() {
        return this.displayName;
    }

    @Override
    public DialogueScreenHandler create(int syncId, Inventory inv, FriendlyByteBuf buf) {
        DialogueStateMachine dialogue = new DialogueStateMachine(buf);
        Optional<Entity> interlocutor = buf.readOptional(FriendlyByteBuf::readVarInt).map(inv.player.level()::getEntity);
        ChoiceAvailabilityPacket choicesAvailability = new ChoiceAvailabilityPacket(buf);
        dialogue.applyAvailabilityUpdate(choicesAvailability);
        return new DialogueScreenHandler(BlabberRegistrar.DIALOGUE_SCREEN_HANDLER, syncId, dialogue, interlocutor.orElse(null));
    }
}
