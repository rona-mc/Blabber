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
package org.ladysnake.blabber.impl.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;
import org.ladysnake.blabber.Blabber;

import static net.minecraft.commands.Commands.literal;

public final class BlabberCommand {
    public static final PermissionNode<Boolean> DIALOGUE_START = new PermissionNode<>(
        Blabber.MOD_ID,
        "dialogue.start",
        PermissionTypes.BOOLEAN,
        (player, uuid, contexts) -> player != null && player.hasPermissions(2)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal(Blabber.MOD_ID)
            .requires(source -> source.hasPermission(2) && (source.getPlayer() == null || PermissionAPI.getPermission(source.getPlayer(), DIALOGUE_START)))
            .then(DialogueSubCommand.dialogueSubtree())
            .then(SettingsSubCommand.settingsSubtree())
        );
    }
}
