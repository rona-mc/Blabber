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
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.util.ExtraCodecs;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.blabber.impl.common.InstancedDialogueAction;
import org.ladysnake.blabber.impl.common.serialization.FailingOptionalFieldCodec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record DialogueState(
        Component text,
        List<String> illustrations,
        List<DialogueChoice> choices,
        Optional<InstancedDialogueAction<?>> action,
        ChoiceResult type
) {
    public static final Codec<DialogueState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            // Kinda optional, but we still want errors if you got it wrong >:(
            FailingOptionalFieldCodec.of(ExtraCodecs.TEXT, "text", Component.empty()).forGetter(DialogueState::text),
            FailingOptionalFieldCodec.of(Codec.list(Codec.STRING), "illustrations", Collections.emptyList()).forGetter(DialogueState::illustrations),
            FailingOptionalFieldCodec.of(Codec.list(DialogueChoice.CODEC), "choices", List.of()).forGetter(DialogueState::choices),
            FailingOptionalFieldCodec.of(InstancedDialogueAction.CODEC, "action").forGetter(DialogueState::action),
            FailingOptionalFieldCodec.of(Codec.STRING.xmap(s -> Enum.valueOf(ChoiceResult.class, s.toUpperCase(Locale.ROOT)), Enum::name), "type", ChoiceResult.DEFAULT).forGetter(DialogueState::type)
    ).apply(instance, DialogueState::new));

    public static void writeToPacket(FriendlyByteBuf buf, DialogueState state) {
        buf.writeText(state.text());
        buf.writeCollection(state.illustrations(), FriendlyByteBuf::writeString);
        buf.writeCollection(state.choices(), DialogueChoice::writeToPacket);
        buf.writeEnumConstant(state.type());
        // not writing the action, the client most likely does not need to know about it
    }

    public DialogueState(FriendlyByteBuf buf) {
        this(buf.readText(), buf.readCollection(ArrayList::new, FriendlyByteBuf::readString), buf.readList(DialogueChoice::new), Optional.empty(), buf.readEnumConstant(ChoiceResult.class));
    }

    public String getNextState(int choice) {
        return this.choices.get(choice).next();
    }

    public DialogueState parseText(@Nullable CommandSourceStack source, @Nullable Entity sender) throws CommandSyntaxException {
        List<DialogueChoice> parsedChoices = new ArrayList<>(choices().size());
        for (DialogueChoice choice : choices()) {
            parsedChoices.add(choice.parseText(source, sender));
        }

        return new DialogueState(
                ComponentUtils.parse(source, text(), sender, 0),
                illustrations(),
                parsedChoices,
                action(),
                type()
        );
    }

    @Override
    public String toString() {
        return "DialogueState{" +
                "text='" + StringUtils.abbreviate(text.getString(), 20) + '\'' +
                ", illustrations=" + illustrations +
                ", choices=" + choices +
                ", type=" + type
                + this.action().map(InstancedDialogueAction::toString).map(s -> ", action=" + s).orElse("")
                + '}';
    }
}
