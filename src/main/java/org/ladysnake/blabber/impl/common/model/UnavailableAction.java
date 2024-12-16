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
package org.ladysnake.blabber.impl.common.model;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.blabber.impl.common.serialization.FailingOptionalFieldCodec;

import java.util.Optional;

public record UnavailableAction(UnavailableDisplay display, Optional<Component> message) {
    public static final Codec<UnavailableAction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UnavailableDisplay.CODEC.fieldOf("display").forGetter(UnavailableAction::display),
            FailingOptionalFieldCodec.of(ExtraCodecs.COMPONENT, "message").forGetter(UnavailableAction::message)
    ).apply(instance, UnavailableAction::new));

    public UnavailableAction(FriendlyByteBuf buf) {
        this(buf.readEnum(UnavailableDisplay.class), buf.readOptional(FriendlyByteBuf::readComponent));
    }

    public static void writeToPacket(FriendlyByteBuf buf, UnavailableAction action) {
        buf.writeEnum(action.display());
        buf.writeOptional(action.message(), FriendlyByteBuf::writeComponent);
    }

    public UnavailableAction parseText(@Nullable CommandSourceStack source, @Nullable Entity sender) throws CommandSyntaxException {
        Optional<Component> parsedMessage = message().isEmpty() ? Optional.empty() : Optional.of(Component.translatable(message().get().getString(), source, sender, 0));
        return new UnavailableAction(display(), parsedMessage);
    }
}
