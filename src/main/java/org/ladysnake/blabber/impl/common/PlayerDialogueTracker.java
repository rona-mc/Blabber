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

import com.google.common.base.Preconditions;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.blabber.Blabber;
import org.ladysnake.blabber.impl.common.machine.DialogueStateMachine;
import org.ladysnake.blabber.impl.common.model.DialogueTemplate;
import org.ladysnake.blabber.impl.common.packets.ChoiceAvailabilityPacket;

import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Blabber.MOD_ID)
public final class PlayerDialogueTracker {
    public static final Capability<PlayerDialogueTracker> CAPABILITY = CapabilityManager.get(new CapabilityToken<>(){});

    private final Player player;
    private @Nullable DialogueStateMachine currentDialogue;
    private @Nullable Entity interlocutor;
    private @Nullable DeserializedState deserializedState;
    private int resumptionAttempts = 0;

    public PlayerDialogueTracker(Player player) {
        this.player = player;
    }

    public static PlayerDialogueTracker get(Player player) {
        LazyOptional<PlayerDialogueTracker> cap = player.getCapability(CAPABILITY);
        return cap.orElseThrow(() -> new IllegalStateException("Player " + player.getScoreboardName() + " does not have dialogue tracker capability"));
    }

    public void startDialogue(ResourceLocation id, @Nullable Entity interlocutor) throws CommandSyntaxException {
        DialogueTemplate template = DialogueRegistry.getOrEmpty(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown dialogue " + id));
        DialogueStateMachine currentDialogue = this.startDialogue0(
                id,
                template,
                template.start(),
                interlocutor
        );
        currentDialogue.getStartAction().ifPresent(a -> a.action().handle((ServerPlayer) this.player, interlocutor));
    }

    private DialogueStateMachine startDialogue0(ResourceLocation id, DialogueTemplate template, @Nullable String start, @Nullable Entity interlocutor) throws CommandSyntaxException {
        ServerPlayer serverPlayer = ((ServerPlayer) this.player);
        this.interlocutor = interlocutor;
        try {
            DialogueTemplate parsedTemplate = template.parseText(CommandDialogueAction.getSource(serverPlayer), serverPlayer);
            this.currentDialogue = new DialogueStateMachine(id, parsedTemplate, start);
            this.updateConditions(serverPlayer, this.currentDialogue);
            this.openDialogueScreen();
            return this.currentDialogue;
        } catch (CommandSyntaxException e) {
            this.interlocutor = null;
            throw e;
        }
    }

    public void endDialogue() {
        this.currentDialogue = null;
        this.interlocutor = null;

        if (this.player instanceof ServerPlayer sp && this.player.containerMenu instanceof DialogueScreenHandler) {
            sp.closeContainer();
        }
    }

    public Optional<DialogueStateMachine> getCurrentDialogue() {
        return Optional.ofNullable(this.currentDialogue);
    }

    public Optional<Entity> getInterlocutor() {
        return Optional.ofNullable(this.interlocutor);
    }

    public void updateDialogue() {
        DialogueStateMachine oldDialogue = this.currentDialogue;
        Entity oldInterlocutor = this.interlocutor;
        if (oldDialogue != null) {
            this.endDialogue();

            DialogueRegistry.getOrEmpty(oldDialogue.getId())
                    .ifPresent(template -> this.tryResumeDialogue(
                            oldDialogue.getId(),
                            template,
                            oldDialogue.getCurrentStateKey(),
                            oldInterlocutor
                    ));
        }
    }

    public void deserializeNBT(CompoundTag tag) {
        if (tag.contains("current_dialogue_id", Tag.TAG_STRING)) {
            ResourceLocation dialogueId = ResourceLocation.tryParse(tag.getString("current_dialogue_id"));
            if (dialogueId != null) {
                Optional<DialogueTemplate> dialogueTemplate = DialogueRegistry.getOrEmpty(dialogueId);
                if (dialogueTemplate.isPresent()) {
                    UUID interlocutorUuid = tag.hasUUID("interlocutor") ? tag.getUUID("interlocutor") : null;
                    String selectedState = tag.contains("current_dialogue_state", Tag.TAG_STRING) ? tag.getString("current_dialogue_state") : null;
                    this.deserializedState = new DeserializedState(dialogueId, dialogueTemplate.get(), selectedState, interlocutorUuid);
                }
            }
        }
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        if (this.currentDialogue != null) {
            tag.putString("current_dialogue_id", this.currentDialogue.getId().toString());
            tag.putString("current_dialogue_state", this.currentDialogue.getCurrentStateKey());
            if (this.interlocutor != null) {
                tag.putUUID("interlocutor", this.interlocutor.getUUID());
            }
        }
        return tag;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide) {
            get(event.player).serverTick();
        }
    }

    private void serverTick() {
        DeserializedState saved = this.deserializedState;
        ServerPlayer serverPlayer = (ServerPlayer) this.player;
        if (saved != null) {
            if (resumptionAttempts++ < 200) {   // only try for like, 10 seconds after joining the world
                Entity interlocutor;
                if (saved.interlocutorUuid() != null) {
                    interlocutor = serverPlayer.serverLevel().getEntity(saved.interlocutorUuid());
                    if (interlocutor == null) return;    // no one to talk to
                } else {
                    interlocutor = null;
                }
                tryResumeDialogue(saved.dialogueId(), saved.template(), saved.selectedState(), interlocutor);
            }
            this.resumptionAttempts = 0;
            this.deserializedState = null;
        }

        if (this.currentDialogue != null) {
            if (this.player.containerMenu == this.player.inventoryMenu) {
                if (this.currentDialogue.isUnskippable()) {
                    this.openDialogueScreen();
                } else {
                    this.endDialogue();
                    return;
                }
            }

            try {
                ChoiceAvailabilityPacket update = this.updateConditions(serverPlayer, this.currentDialogue);

                if (update != null) {
                    BlabberRegistrar.NETWORK.sendTo(update, serverPlayer.connection.connection, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
                }
            } catch (CommandSyntaxException e) {
                throw new IllegalStateException("Error while updating dialogue conditions", e);
            }
        }
    }

    private void tryResumeDialogue(ResourceLocation id, DialogueTemplate template, String selectedState, Entity interlocutor) {
        try {
            this.startDialogue0(id, template, selectedState, interlocutor);
        } catch (CommandSyntaxException e) {
            Blabber.LOGGER.error("(Blabber) Failed to load dialogue template {}", id, e);
        }
    }

    private @Nullable ChoiceAvailabilityPacket updateConditions(ServerPlayer player, DialogueStateMachine currentDialogue) throws CommandSyntaxException {
        if (currentDialogue.hasConditions()) {
            return currentDialogue.updateConditions(new LootContext.Builder(
                    new LootParams.Builder(player.serverLevel())
                            .withParameter(LootContextParams.ORIGIN, player.position())
                            .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                            .create(LootContextParamSets.COMMAND)
            ).create(null));
        }
        return null;
    }

    private void openDialogueScreen() {
        Preconditions.checkState(this.currentDialogue != null);
        this.player.openMenu((MenuProvider) new DialogueScreenHandlerFactory(this.currentDialogue, Component.literal("Blabber Dialogue Screen"), this.interlocutor));
    }

    public void copyFrom(PlayerDialogueTracker old) {
        CompoundTag nbt = old.serializeNBT();
        this.deserializeNBT(nbt);
    }

    private record DeserializedState(ResourceLocation dialogueId, DialogueTemplate template, String selectedState, @Nullable UUID interlocutorUuid) { }
}
