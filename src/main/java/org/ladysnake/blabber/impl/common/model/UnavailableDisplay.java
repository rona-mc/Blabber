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

import com.mojang.serialization.Codec;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.StringIdentifiable;

public enum UnavailableDisplay implements StringIdentifiable {
    GRAYED_OUT("grayed_out"), HIDDEN("hidden");

    public static final Codec<UnavailableDisplay> CODEC = StringIdentifiable.createCodec(UnavailableDisplay::values);
    public static final PacketCodec<PacketByteBuf, UnavailableDisplay> PACKET_CODEC = PacketCodec.ofStatic(
            PacketByteBuf::writeEnumConstant,
            buf -> buf.readEnumConstant(UnavailableDisplay.class)
    );

    private final String id;

    UnavailableDisplay(String id) {
        this.id = id;
    }

    @Override
    public String asString() {
        return this.id;
    }
}
