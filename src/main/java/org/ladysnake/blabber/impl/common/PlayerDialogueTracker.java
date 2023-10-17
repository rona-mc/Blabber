/*
 * Blabber
 * Copyright (C) 2022-2023 Ladysnake
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

import com.google.common.base.Preconditions;
import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.component.tick.ServerTickingComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.blabber.Blabber;
import org.ladysnake.blabber.impl.common.machine.DialogueStateMachine;
import org.ladysnake.blabber.impl.common.packets.ChoiceAvailabilityPacket;

import java.util.Optional;

public final class PlayerDialogueTracker implements ServerTickingComponent {
    public static final ComponentKey<PlayerDialogueTracker> KEY = ComponentRegistry.getOrCreate(Blabber.id("dialogue_tracker"), PlayerDialogueTracker.class);

    private final PlayerEntity player;
    private @Nullable DialogueStateMachine currentDialogue;

    public PlayerDialogueTracker(PlayerEntity player) {
        this.player = player;
    }

    public static PlayerDialogueTracker get(PlayerEntity player) {
        return KEY.get(player);
    }

    public void startDialogue(Identifier id) {
        this.startDialogue0(id);
        this.openDialogueScreen();
    }

    private DialogueStateMachine startDialogue0(Identifier id) {
        this.currentDialogue = BlabberRegistrar.startDialogue(this.player.getWorld(), id);
        if (this.player instanceof ServerPlayerEntity serverPlayer) {
            updateConditions(serverPlayer, this.currentDialogue);
        }
        return this.currentDialogue;
    }

    public void endDialogue() {
        this.currentDialogue = null;

        if (this.player instanceof ServerPlayerEntity sp && this.player.currentScreenHandler instanceof DialogueScreenHandler) {
            sp.closeHandledScreen();
        }
    }

    public Optional<DialogueStateMachine> getCurrentDialogue() {
        return Optional.ofNullable(this.currentDialogue);
    }

    @Override
    public void readFromNbt(NbtCompound tag) {
        if (tag.contains("current_dialogue_id", NbtElement.STRING_TYPE)) {
            Identifier dialogueId = Identifier.tryParse(tag.getString("current_dialogue_id"));
            if (dialogueId != null) {
                try {
                    DialogueStateMachine d = this.startDialogue0(dialogueId);
                    if (tag.contains("current_dialogue_state", NbtElement.STRING_TYPE)) {
                        d.selectState(tag.getString("current_dialogue_state"));
                    }
                } catch (IllegalArgumentException e) {
                    Blabber.LOGGER.warn("[Blabber] Unknown dialogue {}", dialogueId);
                }
            }
        }
    }

    @Override
    public void writeToNbt(NbtCompound tag) {
        if (this.currentDialogue != null) {
            tag.putString("current_dialogue_id", this.currentDialogue.getId().toString());
            tag.putString("current_dialogue_state", this.currentDialogue.getCurrentStateKey());
        }
    }

    @Override
    public void serverTick() {
        if (this.currentDialogue != null) {
            if (this.currentDialogue.isUnskippable() && this.player.currentScreenHandler == this.player.playerScreenHandler) {
                this.openDialogueScreen();
            }

            ChoiceAvailabilityPacket update = this.updateConditions((ServerPlayerEntity) this.player, this.currentDialogue);

            if (update != null) {
                ServerPlayNetworking.send((ServerPlayerEntity) player, update);
            }
        }
    }

    private @Nullable ChoiceAvailabilityPacket updateConditions(ServerPlayerEntity player, DialogueStateMachine currentDialogue) {
        if (currentDialogue.hasConditions()) {
            return currentDialogue.updateConditions(new LootContext.Builder(
                    new LootContextParameterSet.Builder(player.getServerWorld())
                            .add(LootContextParameters.ORIGIN, player.getPos())
                            .addOptional(LootContextParameters.THIS_ENTITY, player)
                            .build(LootContextTypes.COMMAND)
            ).build(Optional.empty()));
        }
        return null;
    }

    private void openDialogueScreen() {
        Preconditions.checkState(this.currentDialogue != null);
        this.player.openHandledScreen(new DialogueScreenHandlerFactory(this.currentDialogue, Text.of("Blabber Dialogue Screen")));
    }
}
