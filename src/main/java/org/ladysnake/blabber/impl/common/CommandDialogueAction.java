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

import com.mojang.serialization.Codec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.ladysnake.blabber.DialogueAction;

public record CommandDialogueAction(String command) implements DialogueAction {
    public static final Codec<CommandDialogueAction> CODEC = Codec.STRING.xmap(CommandDialogueAction::new, CommandDialogueAction::command);

    @Override
    public void handle(ServerPlayer player) {
        player.server.getCommands().performPrefixedCommand(
                getSource(player),
                this.command()
        );
    }

    public static CommandSourceStack getSource(ServerPlayer player) {
        return player.createCommandSourceStack()
                .withSource(player.server)
                .withPermission(2);
    }
}
