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

import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.serialize.ConstantArgumentSerializer;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import org.ladysnake.blabber.Blabber;
import org.ladysnake.blabber.api.DialogueActionV2;
import org.ladysnake.blabber.api.illustration.DialogueIllustrationType;
import org.ladysnake.blabber.api.layout.DefaultLayoutParams;
import org.ladysnake.blabber.api.layout.DialogueLayoutType;
import org.ladysnake.blabber.impl.common.commands.SettingArgumentType;
import org.ladysnake.blabber.impl.common.machine.DialogueStateMachine;
import org.ladysnake.blabber.impl.common.packets.ChoiceAvailabilityPacket;
import org.ladysnake.blabber.impl.common.packets.DialogueListPacket;
import org.ladysnake.blabber.impl.common.packets.SelectedDialogueStatePacket;
import org.ladysnake.blabber.impl.common.settings.BlabberSettingsComponent;

import java.util.Optional;
import java.util.Set;

public final class BlabberRegistrar implements EntityComponentInitializer {
    public static final ScreenHandlerType<DialogueScreenHandler> DIALOGUE_SCREEN_HANDLER = Registry.register(Registries.SCREEN_HANDLER, Blabber.id("dialogue"), new ExtendedScreenHandlerType<>((syncId, inventory, buf) -> {
        DialogueStateMachine dialogue = new DialogueStateMachine(buf);
        Optional<Entity> interlocutor = buf.readOptional(FriendlyByteBuf::readVarInt).map(inventory.player.getWorld()::getEntityById);
        ChoiceAvailabilityPacket choicesAvailability = new ChoiceAvailabilityPacket(buf);
        dialogue.applyAvailabilityUpdate(choicesAvailability);
        return new DialogueScreenHandler(syncId, dialogue, interlocutor.orElse(null));
    }));
    public static final ResourceLocation DIALOGUE_ACTION = Blabber.id("dialogue_action");
    public static final RegistryKey<Registry<Codec<? extends DialogueActionV2>>> ACTION_REGISTRY_KEY = RegistryKey.ofRegistry(Blabber.id("dialogue_actions"));
    public static final Registry<Codec<? extends DialogueActionV2>> ACTION_REGISTRY = FabricRegistryBuilder.from(
            new SimpleRegistry<>(ACTION_REGISTRY_KEY, Lifecycle.stable(), false)
    ).buildAndRegister();

    public static final RegistryKey<Registry<DialogueIllustrationType<?>>> ILLUSTRATION_REGISTRY_KEY = RegistryKey.ofRegistry(Blabber.id("dialogue_illustrations"));
    public static final Registry<DialogueIllustrationType<?>> ILLUSTRATION_REGISTRY = FabricRegistryBuilder.from(
            new SimpleRegistry<>(ILLUSTRATION_REGISTRY_KEY, Lifecycle.stable(), false)
    ).buildAndRegister();

    public static final RegistryKey<Registry<DialogueLayoutType<?>>> LAYOUT_REGISTRY_KEY = RegistryKey.ofRegistry(Blabber.id("dialogue_layouts"));
    public static final Registry<DialogueLayoutType<?>> LAYOUT_REGISTRY = FabricRegistryBuilder.from(
            new SimpleRegistry<>(LAYOUT_REGISTRY_KEY, Lifecycle.stable(), false)
    ).buildAndRegister();
    public static final DialogueLayoutType<DefaultLayoutParams> CLASSIC_LAYOUT = new DialogueLayoutType<>(DefaultLayoutParams.CODEC, DefaultLayoutParams.DEFAULT, DefaultLayoutParams::new, DefaultLayoutParams::writeToPacket);
    public static final DialogueLayoutType<DefaultLayoutParams> RPG_LAYOUT = new DialogueLayoutType<>(DefaultLayoutParams.CODEC, DefaultLayoutParams.DEFAULT, DefaultLayoutParams::new, DefaultLayoutParams::writeToPacket);

    public static final SuggestionProvider<CommandSourceStack> ALL_DIALOGUES = SuggestionProviders.register(
            Blabber.id("available_dialogues"),
            (context, builder) -> CommandSource.suggestResourceLocations(context.getSource() instanceof CommandSourceStack ? DialogueRegistry.getIds() : DialogueRegistry.getClientIds(), builder)
    );

    public static void init() {
        Registry.register(Registries.LOOT_CONDITION_TYPE, Blabber.id("interlocutor_properties"), InterlocutorPropertiesLootCondition.TYPE);
        ArgumentTypeRegistry.registerArgumentType(Blabber.id("setting"), SettingArgumentType.class, ConstantArgumentSerializer.of(SettingArgumentType::setting));

        DialogueLoader.init();
        ServerPlayNetworking.registerGlobalReceiver(DIALOGUE_ACTION, (server, player, handler, buf, responseSender) -> {
            int choice = buf.readByte();
            server.execute(() -> {
                if (player.currentScreenHandler instanceof DialogueScreenHandler dialogueHandler) {
                    if (!dialogueHandler.makeChoice(player, choice)) {
                        responseSender.sendPacket(new SelectedDialogueStatePacket(dialogueHandler.getCurrentStateKey()));
                    }
                }
            });
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (ServerPlayNetworking.canSend(handler, DialogueListPacket.TYPE)) {
                Set<ResourceLocation> dialogueIds = DialogueRegistry.getIds();
                sender.sendPacket(new DialogueListPacket(dialogueIds));
            } else {
                Blabber.LOGGER.warn("{} does not have Blabber installed, this will cause issues if they trigger a dialogue", handler.getPlayer().getEntityName());
            }
        });
    }

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerForPlayers(PlayerDialogueTracker.KEY, PlayerDialogueTracker::new, RespawnCopyStrategy.ALWAYS_COPY);
        registry.registerForPlayers(BlabberSettingsComponent.KEY, BlabberSettingsComponent::new, RespawnCopyStrategy.ALWAYS_COPY);
    }
}
